package ru.iopump.qa.allure.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.iopump.qa.allure.entity.UserEntity;
import ru.iopump.qa.allure.entity.UserRole;
import ru.iopump.qa.allure.repo.UserRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link CurrentUserProvider#current()} — every fallback branch to
 * the seeded/guest placeholder, plus the happy-path principal resolution.
 */
@ExtendWith(MockitoExtension.class)
class CurrentUserProviderTest {

    private static final String RESOLVABLE_USERNAME = "alice";

    @Mock
    private UserRepository userRepository;

    @Mock
    private Authentication authentication;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("should return the seeded guest when the security context holds no authentication")
    void current_returnsSeededGuest_whenAuthenticationIsNull() {
        // GIVEN — guest cache seeded, no authentication placed in the security context
        final UserEntity seededGuest = seededGuest();
        final CurrentUserProvider currentUserProvider = providerWithSeededGuest(seededGuest);
        SecurityContextHolder.clearContext();

        // WHEN
        final UserEntity result = currentUserProvider.current();

        // THEN — the cached, persisted guest row is returned
        assertThat(result.getId())
            .as("null authentication must resolve to the seeded guest's id")
            .isEqualTo(seededGuest.getId());
        assertThat(result.getRole())
            .as("resolved user's role must be GUEST")
            .isEqualTo(UserRole.GUEST);
    }

    @Test
    @DisplayName("should return the seeded guest when the authentication is present but not authenticated")
    void current_returnsSeededGuest_whenNotAuthenticated() {
        // GIVEN — guest cache seeded, an authentication present but isAuthenticated()==false
        final UserEntity seededGuest = seededGuest();
        final CurrentUserProvider currentUserProvider = providerWithSeededGuest(seededGuest);
        when(authentication.isAuthenticated()).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // WHEN
        final UserEntity result = currentUserProvider.current();

        // THEN
        assertThat(result.getId())
            .as("unauthenticated principal must resolve to the seeded guest's id")
            .isEqualTo(seededGuest.getId());
        assertThat(result.getRole())
            .as("resolved user's role must be GUEST")
            .isEqualTo(UserRole.GUEST);
    }

    @ParameterizedTest(name = "name=\"{0}\"")
    @DisplayName("should return the seeded guest when the authenticated principal's name is null, blank, or the anonymous marker")
    @NullSource
    @ValueSource(strings = {"", "   ", "anonymousUser"})
    void current_returnsSeededGuest_whenNameIsNullBlankOrAnonymous(String name) {
        // GIVEN — guest cache seeded, authenticated principal with a null/blank/anonymous name
        final UserEntity seededGuest = seededGuest();
        final CurrentUserProvider currentUserProvider = providerWithSeededGuest(seededGuest);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(name);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // WHEN
        final UserEntity result = currentUserProvider.current();

        // THEN
        assertThat(result.getId())
            .as("name '%s' must resolve to the seeded guest's id", name)
            .isEqualTo(seededGuest.getId());
        assertThat(result.getRole())
            .as("resolved user's role must be GUEST")
            .isEqualTo(UserRole.GUEST);
    }

    @Test
    @DisplayName("should return the seeded guest when the authenticated principal's username is not found in the repository")
    void current_returnsSeededGuest_whenUsernameNotFoundInRepository() {
        // GIVEN — guest cache seeded, authenticated principal whose username has no matching row
        final UserEntity seededGuest = seededGuest();
        final CurrentUserProvider currentUserProvider = providerWithSeededGuest(seededGuest);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(RESOLVABLE_USERNAME);
        when(userRepository.findByUsername(RESOLVABLE_USERNAME)).thenReturn(Optional.empty());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // WHEN
        final UserEntity result = currentUserProvider.current();

        // THEN
        assertThat(result.getId())
            .as("unresolvable username must fall back to the seeded guest's id")
            .isEqualTo(seededGuest.getId());
        assertThat(result.getRole())
            .as("resolved user's role must be GUEST")
            .isEqualTo(UserRole.GUEST);
    }

    @Test
    @DisplayName("should return the persisted user when the authenticated principal's username resolves via the repository")
    void current_returnsResolvedUser_whenPrincipalMapsToPersistedUser() {
        // GIVEN — a Basic/token-authenticated principal whose username maps to a persisted user
        final UserEntity resolvedUser = resolvableUser();
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(RESOLVABLE_USERNAME);
        when(userRepository.findByUsername(RESOLVABLE_USERNAME)).thenReturn(Optional.of(resolvedUser));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        final CurrentUserProvider currentUserProvider = new CurrentUserProvider(userRepository);

        // WHEN
        final UserEntity result = currentUserProvider.current();

        // THEN
        assertThat(result.getId())
            .as("resolvable principal must return the exact persisted user's id")
            .isEqualTo(resolvedUser.getId());
        assertThat(result.getUsername())
            .as("resolved user's username must match the authenticated principal's name")
            .isEqualTo(RESOLVABLE_USERNAME);
        assertThat(result.getRole())
            .as("resolved user's role must be USER")
            .isEqualTo(UserRole.USER);
    }

    @Test
    @DisplayName("should return a transient guest placeholder when the guest cache has never been seeded")
    void current_returnsTransientGuestPlaceholder_whenGuestCacheEmpty() {
        // GIVEN — a fresh provider whose guest cache was never populated (refreshGuestCache not called),
        // and no authentication in the security context
        final CurrentUserProvider currentUserProvider = new CurrentUserProvider(userRepository);
        SecurityContextHolder.clearContext();

        // WHEN
        final UserEntity result = currentUserProvider.current();

        // THEN — a transient placeholder with a null id, so templates can render without an NPE
        assertThat(result.getId())
            .as("transient guest placeholder must carry a null id (never persisted)")
            .isNull();
        assertThat(result.getUsername())
            .as("transient guest placeholder username must be the guest constant")
            .isEqualTo(CurrentUserProvider.GUEST_USERNAME);
        assertThat(result.getDisplayName())
            .as("transient guest placeholder display name must be 'Guest'")
            .isEqualTo("Guest");
        assertThat(result.getRole())
            .as("transient guest placeholder role must be GUEST")
            .isEqualTo(UserRole.GUEST);
        assertThat(result.getCreatedAt())
            .as("transient guest placeholder createdAt must be the epoch sentinel")
            .isEqualTo(Instant.EPOCH);
    }

    ///// helpers /////

    private CurrentUserProvider providerWithSeededGuest(UserEntity seededGuest) {
        when(userRepository.findByUsername(CurrentUserProvider.GUEST_USERNAME)).thenReturn(Optional.of(seededGuest));
        final CurrentUserProvider provider = new CurrentUserProvider(userRepository);
        provider.refreshGuestCache();
        return provider;
    }

    private static UserEntity seededGuest() {
        return UserEntity.builder()
            .id(UUID.randomUUID())
            .username(CurrentUserProvider.GUEST_USERNAME)
            .displayName("Guest")
            .role(UserRole.GUEST)
            .createdAt(Instant.now())
            .build();
    }

    private static UserEntity resolvableUser() {
        return UserEntity.builder()
            .id(UUID.randomUUID())
            .username(RESOLVABLE_USERNAME)
            .displayName("Alice")
            .role(UserRole.USER)
            .createdAt(Instant.now())
            .build();
    }
}
