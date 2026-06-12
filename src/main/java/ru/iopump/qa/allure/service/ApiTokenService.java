package ru.iopump.qa.allure.service;

import com.google.common.base.Preconditions;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.iopump.qa.allure.entity.ApiTokenEntity;
import ru.iopump.qa.allure.entity.UserEntity;
import ru.iopump.qa.allure.repo.ApiTokenRepository;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and validates personal API tokens.
 * <p>
 * Token format: {@code bqa_<base62 of 32 random bytes>}. Only the SHA-256 hex
 * hash of the plain token is persisted. The first 8 hex chars of the hash are
 * duplicated into {@link ApiTokenEntity#getTokenLookup()} to support an indexed
 * narrowing query before a constant-time equality check on the full hash.
 * <p>
 * Plain token value is returned exactly once from {@link #createToken(UserEntity, String, Duration)}
 * and never stored. Clients that lose it must revoke + create a new one.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ApiTokenService {

    public static final String TOKEN_PREFIX = "bqa_";
    static final int RANDOM_BYTES = 32;
    static final int LOOKUP_LENGTH = 8;
    private static final String SHA_256 = "SHA-256";
    private static final char[] BASE62 =
        "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    private final ApiTokenRepository apiTokenRepository;
    private final TokenPolicy tokenPolicy;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generate a new plain token string (prefixed, base62). Not persisted.
     */
    public String generate() {
        byte[] random = new byte[RANDOM_BYTES];
        secureRandom.nextBytes(random);
        return TOKEN_PREFIX + encodeBase62(random);
    }

    /**
     * SHA-256 hex hash of the full plain token.
     */
    public String hash(@NonNull String plainToken) {
        Preconditions.checkArgument(!plainToken.isBlank(), "plainToken must not be blank");
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            byte[] bytes = digest.digest(plainToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    /**
     * First {@value #LOOKUP_LENGTH} hex chars of the hash — indexed column.
     */
    public String lookup(@NonNull String hash) {
        Preconditions.checkArgument(hash.length() >= LOOKUP_LENGTH,
            "hash must be at least %s chars", LOOKUP_LENGTH);
        return hash.substring(0, LOOKUP_LENGTH);
    }

    /**
     * Create and persist a new token for {@code owner}. Returns both the entity
     * id and the plain token value — the plain value is available ONLY here.
     *
     * @param owner token owner (must be persisted)
     * @param name  human-readable name (e.g. "ci-pipeline")
     * @param ttl   optional lifetime; {@code null} means no expiration
     */
    public TokenIssueResult createToken(@NonNull UserEntity owner,
                                        @NonNull String name,
                                        Duration ttl) {
        Preconditions.checkArgument(!name.isBlank(), "token name must not be blank");
        Preconditions.checkArgument(owner.getId() != null, "owner must be persisted");

        final Instant now = Instant.now();
        final int limit = tokenPolicy.maxActiveTokens(owner.getRole());
        final long active = apiTokenRepository.countActiveByUserId(owner.getId(), now);
        if (active >= limit) {
            log.warn("Token creation denied for user '{}' (role={}): {} of {} active tokens",
                owner.getUsername(), owner.getRole(), active, limit);
            throw new TokenLimitExceededException(owner.getRole(), active, limit);
        }

        final String plain = generate();
        final String hash = hash(plain);

        final ApiTokenEntity entity = ApiTokenEntity.builder()
            .id(UUID.randomUUID())
            .user(owner)
            .name(name.trim())
            .tokenHash(hash)
            .tokenLookup(lookup(hash))
            .createdAt(now)
            .expiresAt(ttl == null ? null : now.plus(ttl))
            .build();
        apiTokenRepository.save(entity);
        log.info("API token '{}' issued for user '{}' (id={}, ttl={})",
            entity.getName(), owner.getUsername(), entity.getId(), ttl);
        return new TokenIssueResult(entity.getId(), plain);
    }

    /**
     * Revoke a token owned by {@code owner}. Idempotent on an already-revoked token.
     *
     * @return {@code true} if the call actually flipped a token from active to revoked
     */
    public boolean revoke(@NonNull UserEntity owner, @NonNull UUID tokenId) {
        Preconditions.checkArgument(owner.getId() != null, "owner must be persisted");
        return apiTokenRepository.findByIdAndUserId(tokenId, owner.getId())
            .map(token -> {
                if (token.isRevoked()) {
                    return false;
                }
                token.setRevokedAt(Instant.now());
                apiTokenRepository.save(token);
                log.info("API token '{}' (id={}) revoked for user '{}'",
                    token.getName(), token.getId(), owner.getUsername());
                return true;
            })
            .orElse(false);
    }

    /**
     * Validate a bearer token value. Returns the owning user when the token is
     * active, not revoked and not expired. Side-effect: updates {@code lastUsedAt}.
     */
    @Transactional
    public Optional<UserEntity> authenticate(@NonNull String plainToken) {
        if (!plainToken.startsWith(TOKEN_PREFIX) || plainToken.length() < TOKEN_PREFIX.length() + 16) {
            return Optional.empty();
        }
        final String hash = hash(plainToken);
        final String lookup = lookup(hash);
        final Instant now = Instant.now();
        return apiTokenRepository.findByTokenLookup(lookup).stream()
            .filter(token -> constantTimeEquals(token.getTokenHash(), hash))
            .findFirst()
            .flatMap(token -> {
                if (!token.isActive(now)) {
                    return Optional.empty();
                }
                final UserEntity owner = token.getUser();
                if (owner != null && owner.isBlocked()) {
                    log.debug("Token '{}' authenticated but owner '{}' is blocked — rejecting",
                        token.getId(), owner.getUsername());
                    return Optional.empty();
                }
                token.setLastUsedAt(now);
                apiTokenRepository.save(token);
                return Optional.of(owner);
            });
    }

    @Transactional(readOnly = true)
    public java.util.List<ApiTokenEntity> listAll(@NonNull UserEntity owner) {
        Preconditions.checkArgument(owner.getId() != null, "owner must be persisted");
        return apiTokenRepository.findAllByUserIdOrderByCreatedAtDesc(owner.getId());
    }

    /**
     * @return number of active (not revoked, not expired) tokens owned by {@code owner}.
     */
    @Transactional(readOnly = true)
    public long countActive(@NonNull UserEntity owner) {
        Preconditions.checkArgument(owner.getId() != null, "owner must be persisted");
        return apiTokenRepository.countActiveByUserId(owner.getId(), Instant.now());
    }

    ///// PRIVATE /////

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null || expected.length() != actual.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < expected.length(); i++) {
            diff |= expected.charAt(i) ^ actual.charAt(i);
        }
        return diff == 0;
    }

    private static String encodeBase62(byte[] bytes) {
        // Positive BigInteger → base62 alphabet. Leading zero bytes preserved as leading '0' chars.
        BigInteger number = new BigInteger(1, bytes);
        StringBuilder sb = new StringBuilder();
        final BigInteger base = BigInteger.valueOf(BASE62.length);
        while (number.signum() > 0) {
            BigInteger[] divmod = number.divideAndRemainder(base);
            sb.append(BASE62[divmod[1].intValue()]);
            number = divmod[0];
        }
        for (byte b : bytes) {
            if (b == 0) {
                sb.append(BASE62[0]);
            } else {
                break;
            }
        }
        return sb.reverse().toString();
    }

    /**
     * Outcome of {@link #createToken(UserEntity, String, Duration)}.
     */
    public record TokenIssueResult(UUID entityId, String plainToken) {
    }
}
