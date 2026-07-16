package ru.iopump.qa.allure.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.iopump.qa.allure.entity.ApiTokenEntity;
import ru.iopump.qa.allure.entity.UserEntity;
import ru.iopump.qa.allure.entity.UserRole;
import ru.iopump.qa.allure.repo.ApiTokenRepository;
import ru.iopump.qa.allure.repo.UserRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiTokenServiceTest {

    private static final int LOOKUP_LENGTH = 8;
    private static final int HASH_HEX_LENGTH = 64;
    private static final int MIN_PLAIN_TOKEN_LENGTH = 20;
    private static final String TOKEN_PREFIX = "bqa_";

    private static final int USER_LIMIT = 10;
    private static final int ADMIN_LIMIT = 50;

    @Mock
    private ApiTokenRepository apiTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenPolicy tokenPolicy;

    @Mock
    private AuthStampService authStampService;

    @InjectMocks
    private ApiTokenService apiTokenService;

    private UserEntity owner;

    @BeforeEach
    void setUp() {
        owner = UserEntity.builder()
            .id(UUID.randomUUID())
            .username("alice")
            .displayName("Alice")
            .role(UserRole.USER)
            .createdAt(Instant.now())
            .build();
    }

    @Test
    @DisplayName("should generate a base62 token with the project prefix")
    void generatePrefixAndLength() {
        // WHEN — generate a new plain token value
        final String token = apiTokenService.generate();

        // THEN — prefixed and long enough to be meaningful
        assertThat(token)
            .as("generated plain token must start with the project prefix")
            .startsWith(TOKEN_PREFIX);
        assertThat(token.length())
            .as("generated plain token length in chars")
            .isGreaterThan(MIN_PLAIN_TOKEN_LENGTH);
    }

    @Test
    @DisplayName("should hash plain token to 64 hex chars (SHA-256)")
    void hashIsSha256Hex() {
        // WHEN — hash a known value
        final String hash = apiTokenService.hash("bqa_abc123");

        // THEN — 64 lowercase hex chars
        assertThat(hash)
            .as("hash length is SHA-256 hex digest length")
            .hasSize(HASH_HEX_LENGTH);
        assertThat(hash)
            .as("hash is lowercase hex")
            .matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("should take first 8 chars of hash as the lookup column")
    void lookupIsPrefixOfHash() {
        // GIVEN — a known hash
        final String hash = apiTokenService.hash("bqa_value");

        // WHEN — derive the lookup value
        final String lookup = apiTokenService.lookup(hash);

        // THEN — matches the first 8 chars of the hash
        assertThat(lookup)
            .as("lookup is the 8-char prefix of the SHA-256 hash")
            .isEqualTo(hash.substring(0, LOOKUP_LENGTH));
    }

    @Test
    @DisplayName("should persist hashed token and return plain value once when createToken succeeds")
    void createTokenPersistsHashAndReturnsPlain() {
        // GIVEN — USER role under limit with no existing active tokens
        when(tokenPolicy.maxActiveTokens(UserRole.USER)).thenReturn(USER_LIMIT);
        when(apiTokenRepository.countActiveByUserId(eq(owner.getId()), any(Instant.class))).thenReturn(0L);

        // WHEN — create a token with a 30-day TTL
        final Duration ttl = Duration.ofDays(30);
        final ApiTokenService.TokenIssueResult result =
            apiTokenService.createToken(owner, "ci-pipeline", ttl);

        // THEN — plain value is returned, entity persisted with hash and lookup, expiresAt computed
        assertThat(result.plainToken())
            .as("plain token returned from createToken must carry the project prefix")
            .startsWith(TOKEN_PREFIX);
        final ArgumentCaptor<ApiTokenEntity> captor = ArgumentCaptor.forClass(ApiTokenEntity.class);
        verify(apiTokenRepository).save(captor.capture());
        final ApiTokenEntity saved = captor.getValue();
        assertThat(saved.getTokenHash())
            .as("persisted hash equals SHA-256 of plain token")
            .isEqualTo(apiTokenService.hash(result.plainToken()));
        assertThat(saved.getTokenLookup())
            .as("persisted lookup equals 8-char prefix of hash")
            .isEqualTo(saved.getTokenHash().substring(0, LOOKUP_LENGTH));
        assertThat(saved.getName())
            .as("persisted name matches form input")
            .isEqualTo("ci-pipeline");
        assertThat(saved.getExpiresAt())
            .as("expiresAt equals createdAt plus the requested TTL")
            .isEqualTo(saved.getCreatedAt().plus(ttl));
        assertThat(saved.getRevokedAt())
            .as("new token is not revoked")
            .isNull();
        assertThat(saved.getUser())
            .as("owner linked on new token")
            .isEqualTo(owner);
        // AND — the owning user row is locked before the count+insert (concurrency guard)
        verify(userRepository).findByIdForUpdate(owner.getId());
    }

    @Test
    @DisplayName("should persist token with null expiresAt when TTL is null (never expires)")
    void createTokenWithNullTtlPersistsNullExpiresAt() {
        // GIVEN — USER role under limit
        when(tokenPolicy.maxActiveTokens(UserRole.USER)).thenReturn(USER_LIMIT);
        when(apiTokenRepository.countActiveByUserId(eq(owner.getId()), any(Instant.class))).thenReturn(0L);

        // WHEN — create a token with no TTL
        apiTokenService.createToken(owner, "perma", null);

        // THEN — entity has null expiresAt
        final ArgumentCaptor<ApiTokenEntity> captor = ArgumentCaptor.forClass(ApiTokenEntity.class);
        verify(apiTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getExpiresAt())
            .as("null TTL must produce null expiresAt column")
            .isNull();
    }

    @Test
    @DisplayName("should return owning user and delegate lastUsedAt stamping to AuthStampService when authenticate hits an active token")
    void authenticateHappyPath() {
        // GIVEN — a live plain token and a matching persisted entity
        final String plain = apiTokenService.generate();
        final String hash = apiTokenService.hash(plain);
        final ApiTokenEntity entity = ApiTokenEntity.builder()
            .id(UUID.randomUUID())
            .user(owner)
            .name("live")
            .tokenHash(hash)
            .tokenLookup(hash.substring(0, LOOKUP_LENGTH))
            .createdAt(Instant.now().minusSeconds(60))
            .build();
        when(apiTokenRepository.findByTokenLookup(hash.substring(0, LOOKUP_LENGTH)))
            .thenReturn(List.of(entity));
        final Instant before = Instant.now();

        // WHEN — authenticate with the plain value
        final Optional<UserEntity> result = apiTokenService.authenticate(plain);

        // THEN — owner returned; lastUsedAt stamping is delegated to AuthStampService
        // (best-effort, REQUIRES_NEW) with the token id and a fresh timestamp
        assertThat(result)
            .as("authenticate must return the owning UserEntity")
            .contains(owner);
        final ArgumentCaptor<Instant> touchedAt = ArgumentCaptor.forClass(Instant.class);
        verify(authStampService).touchTokenLastUsed(eq(entity.getId()), touchedAt.capture());
        assertThat(touchedAt.getValue())
            .as("lastUsedAt stamp delegated to AuthStampService must fall between the pre-call instant and now")
            .isBetween(before, Instant.now());
    }

    @Test
    @DisplayName("should return empty Optional without touching repo when token lacks project prefix")
    void authenticateIgnoresNonProjectPrefix() {
        // WHEN — authenticate something with a foreign prefix
        final Optional<UserEntity> result = apiTokenService.authenticate("ghp_somegithubtoken12345");

        // THEN — empty result and no DB lookup at all
        assertThat(result)
            .as("tokens without the project prefix must be ignored")
            .isEmpty();
        verify(apiTokenRepository, never()).findByTokenLookup(any());
    }

    @Test
    @DisplayName("should reject authenticate when token is revoked")
    void authenticateRejectsRevoked() {
        // GIVEN — a revoked persisted token matching the plain value
        final String plain = apiTokenService.generate();
        final String hash = apiTokenService.hash(plain);
        final ApiTokenEntity entity = ApiTokenEntity.builder()
            .id(UUID.randomUUID())
            .user(owner)
            .name("revoked")
            .tokenHash(hash)
            .tokenLookup(hash.substring(0, LOOKUP_LENGTH))
            .createdAt(Instant.now().minusSeconds(60))
            .revokedAt(Instant.now().minusSeconds(30))
            .build();
        when(apiTokenRepository.findByTokenLookup(hash.substring(0, LOOKUP_LENGTH)))
            .thenReturn(List.of(entity));

        // WHEN — authenticate
        final Optional<UserEntity> result = apiTokenService.authenticate(plain);

        // THEN — empty
        assertThat(result)
            .as("revoked tokens must not authenticate")
            .isEmpty();
    }

    @Test
    @DisplayName("should reject authenticate when token is expired")
    void authenticateRejectsExpired() {
        // GIVEN — an expired persisted token matching the plain value
        final String plain = apiTokenService.generate();
        final String hash = apiTokenService.hash(plain);
        final ApiTokenEntity entity = ApiTokenEntity.builder()
            .id(UUID.randomUUID())
            .user(owner)
            .name("expired")
            .tokenHash(hash)
            .tokenLookup(hash.substring(0, LOOKUP_LENGTH))
            .createdAt(Instant.now().minusSeconds(3600))
            .expiresAt(Instant.now().minusSeconds(60))
            .build();
        when(apiTokenRepository.findByTokenLookup(hash.substring(0, LOOKUP_LENGTH)))
            .thenReturn(List.of(entity));

        // WHEN — authenticate
        final Optional<UserEntity> result = apiTokenService.authenticate(plain);

        // THEN — empty
        assertThat(result)
            .as("expired tokens must not authenticate")
            .isEmpty();
    }

    @Test
    @DisplayName("should set revokedAt timestamp and return true when revoking an active token")
    void revokeActiveToken() {
        // GIVEN — an active token owned by 'owner'
        final UUID tokenId = UUID.randomUUID();
        final ApiTokenEntity entity = ApiTokenEntity.builder()
            .id(tokenId)
            .user(owner)
            .name("kill-me")
            .tokenHash("dummyhash")
            .tokenLookup("dummyhas".substring(0, 8))
            .createdAt(Instant.now().minusSeconds(60))
            .build();
        when(apiTokenRepository.findByIdAndUserId(tokenId, owner.getId()))
            .thenReturn(Optional.of(entity));
        final Instant before = Instant.now();

        // WHEN — revoke
        final boolean revoked = apiTokenService.revoke(owner, tokenId);

        // THEN — true returned, revokedAt stamped within the call window, save called
        assertThat(revoked)
            .as("revoke of an active token returns true")
            .isTrue();
        assertThat(entity.getRevokedAt())
            .as("revokedAt must be stamped between the pre-call instant and now")
            .isBetween(before, Instant.now());
        verify(apiTokenRepository).save(entity);
    }

    @Test
    @DisplayName("should return false when revoking an unknown token id")
    void revokeUnknownToken() {
        // GIVEN — repo returns empty for the lookup
        final UUID tokenId = UUID.randomUUID();
        when(apiTokenRepository.findByIdAndUserId(tokenId, owner.getId()))
            .thenReturn(Optional.empty());

        // WHEN — revoke
        final boolean revoked = apiTokenService.revoke(owner, tokenId);

        // THEN — false and no save attempt
        assertThat(revoked)
            .as("revoke of unknown token id returns false")
            .isFalse();
        verify(apiTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("should throw TokenLimitExceededException when a USER has reached the per-role active-token limit")
    void createTokenThrowsWhenUserLimitReached() {
        // GIVEN — USER owner sitting exactly at the mocked per-role limit
        // (GUEST is excluded from this scenario: createToken hard-rejects GUEST before
        // the limit check ever runs — see createTokenRejectsGuestOwner)
        when(tokenPolicy.maxActiveTokens(UserRole.USER)).thenReturn(USER_LIMIT);
        when(apiTokenRepository.countActiveByUserId(eq(owner.getId()), any(Instant.class)))
            .thenReturn((long) USER_LIMIT);

        // WHEN / THEN — the next token is rejected; nothing is persisted
        assertThatThrownBy(() -> apiTokenService.createToken(owner, "excess", null))
            .as("owner at the active-token limit must raise TokenLimitExceededException")
            .isInstanceOf(TokenLimitExceededException.class)
            .hasMessageContaining(USER_LIMIT + " of " + USER_LIMIT);
        verify(apiTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("should reject creating a token for a GUEST owner before the limit check runs")
    void createTokenRejectsGuestOwner() {
        // GIVEN — a persisted GUEST-role owner
        final UserEntity guest = UserEntity.builder()
            .id(UUID.randomUUID())
            .username("guest")
            .displayName("Guest")
            .role(UserRole.GUEST)
            .createdAt(Instant.now())
            .build();

        // WHEN / THEN — rejected at the role check, before any repository interaction
        assertThatThrownBy(() -> apiTokenService.createToken(guest, "guest-token", null))
            .as("GUEST accounts must never be able to own an API token")
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Guest accounts cannot own API tokens");
        verify(apiTokenRepository, never()).save(any());
        verify(tokenPolicy, never()).maxActiveTokens(any());
    }

    @Test
    @DisplayName("should lock the owner row before counting active tokens when createToken runs")
    void createTokenLocksOwnerRowBeforeCounting() {
        // GIVEN — USER role under limit with no existing active tokens
        when(tokenPolicy.maxActiveTokens(UserRole.USER)).thenReturn(USER_LIMIT);
        when(apiTokenRepository.countActiveByUserId(eq(owner.getId()), any(Instant.class))).thenReturn(0L);

        // WHEN — create a token
        apiTokenService.createToken(owner, "ci-pipeline", null);

        // THEN — the pessimistic write lock on the owner row is taken before the count,
        // so concurrent creates serialize and cannot both pass the per-user cap
        final InOrder inOrder = inOrder(userRepository, apiTokenRepository);
        inOrder.verify(userRepository).findByIdForUpdate(owner.getId());
        inOrder.verify(apiTokenRepository).countActiveByUserId(eq(owner.getId()), any(Instant.class));
        inOrder.verify(apiTokenRepository).save(any(ApiTokenEntity.class));
    }

    @Test
    @DisplayName("should allow creating a new token after one of the limit's active tokens is revoked")
    void createTokenAllowsAfterRevokingOne() {
        // GIVEN — USER owner with one fewer than the limit active tokens (one of the originals revoked)
        final long activeAfterRevoke = USER_LIMIT - 1L;
        when(tokenPolicy.maxActiveTokens(UserRole.USER)).thenReturn(USER_LIMIT);
        when(apiTokenRepository.countActiveByUserId(eq(owner.getId()), any(Instant.class)))
            .thenReturn(activeAfterRevoke);

        // WHEN — create a new token
        final ApiTokenService.TokenIssueResult result =
            apiTokenService.createToken(owner, "replacement", Duration.ofDays(7));

        // THEN — token is persisted and plain value returned
        assertThat(result.plainToken())
            .as("new plain token issued after revoking a previous one must carry the project prefix")
            .startsWith(TOKEN_PREFIX);
        verify(apiTokenRepository).save(any(ApiTokenEntity.class));
    }

    @Test
    @DisplayName("should return empty Optional when owner is blocked even if token is otherwise valid")
    void authenticate_returnsEmptyWhenOwnerBlocked() {
        // GIVEN — a valid, active token whose owner has been blocked
        final UserEntity blockedOwner = UserEntity.builder()
            .id(UUID.randomUUID())
            .username("blockeduser")
            .displayName("Blocked")
            .role(UserRole.USER)
            .createdAt(Instant.now())
            .blocked(true)
            .build();
        final String plain = apiTokenService.generate();
        final String hash = apiTokenService.hash(plain);
        final ApiTokenEntity entity = ApiTokenEntity.builder()
            .id(UUID.randomUUID())
            .user(blockedOwner)
            .name("ci-token")
            .tokenHash(hash)
            .tokenLookup(hash.substring(0, LOOKUP_LENGTH))
            .createdAt(Instant.now().minusSeconds(60))
            .build();
        when(apiTokenRepository.findByTokenLookup(hash.substring(0, LOOKUP_LENGTH)))
            .thenReturn(List.of(entity));

        // WHEN — authenticate with the plain value
        final Optional<UserEntity> result = apiTokenService.authenticate(plain);

        // THEN — empty because owner is blocked
        assertThat(result)
            .as("a valid token whose owner is blocked must not authenticate")
            .isEmpty();
    }

    @Test
    @DisplayName("should lift the effective cap when admin role grants 50 active tokens")
    void createTokenAllowsAdminUpToAdminLimit() {
        // GIVEN — admin with 49 active tokens, policy limit 50
        final UserEntity admin = UserEntity.builder()
            .id(UUID.randomUUID())
            .username("root")
            .displayName("Root")
            .role(UserRole.ADMIN)
            .createdAt(Instant.now())
            .build();
        final long activeBelowAdminLimit = ADMIN_LIMIT - 1L;
        when(tokenPolicy.maxActiveTokens(UserRole.ADMIN)).thenReturn(ADMIN_LIMIT);
        when(apiTokenRepository.countActiveByUserId(eq(admin.getId()), any(Instant.class)))
            .thenReturn(activeBelowAdminLimit);

        // WHEN — create the 50th token
        final ApiTokenService.TokenIssueResult result =
            apiTokenService.createToken(admin, "admin-token", null);

        // THEN — issued
        assertThat(result.plainToken())
            .as("admin can create up to the admin limit")
            .startsWith(TOKEN_PREFIX);
        verify(apiTokenRepository).save(any(ApiTokenEntity.class));
    }
}
