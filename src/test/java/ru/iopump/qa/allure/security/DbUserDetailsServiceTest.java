package ru.iopump.qa.allure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import ru.iopump.qa.allure.entity.UserEntity;
import ru.iopump.qa.allure.entity.UserRole;
import ru.iopump.qa.allure.repo.UserRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link DbUserDetailsService} — the Basic-auth enforcement point.
 * Covers the two security-critical branches: no-local-password and blocked.
 */
@ExtendWith(MockitoExtension.class)
class DbUserDetailsServiceTest {

    private static final String USERNAME = "bob";
    private static final String HASH = "$2a$10$abcdefghijklmnopqrstuv";

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DbUserDetailsService service;

    @Test
    @DisplayName("should throw UsernameNotFoundException when the user does not exist")
    void loadUser_missing_throws() {
        // GIVEN — no row for the username
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

        // WHEN / THEN — UsernameNotFoundException
        assertThatThrownBy(() -> service.loadUserByUsername(USERNAME))
            .as("missing user must be rejected with UsernameNotFoundException")
            .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    @DisplayName("should throw UsernameNotFoundException when the user has a null password hash (guest)")
    void loadUser_nullHash_throws() {
        // GIVEN — a user with no local password (e.g. guest / OAuth-only)
        when(userRepository.findByUsername(USERNAME))
            .thenReturn(Optional.of(user(USERNAME, null, false, UserRole.GUEST)));

        // WHEN / THEN — local login is impossible
        assertThatThrownBy(() -> service.loadUserByUsername(USERNAME))
            .as("user without a password hash must be rejected for Basic auth")
            .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    @DisplayName("should throw UsernameNotFoundException when the user has a blank password hash")
    void loadUser_blankHash_throws() {
        // GIVEN — a user with a blank password hash
        when(userRepository.findByUsername(USERNAME))
            .thenReturn(Optional.of(user(USERNAME, "   ", false, UserRole.USER)));

        // WHEN / THEN — blank hash treated as no local password
        assertThatThrownBy(() -> service.loadUserByUsername(USERNAME))
            .as("user with a blank password hash must be rejected for Basic auth")
            .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    @DisplayName("should return an account-locked UserDetails when the user is blocked")
    void loadUser_blocked_returnsLocked() {
        // GIVEN — a blocked user with a valid hash
        when(userRepository.findByUsername(USERNAME))
            .thenReturn(Optional.of(user(USERNAME, HASH, true, UserRole.USER)));

        // WHEN — loading the user
        final UserDetails details = service.loadUserByUsername(USERNAME);

        // THEN — account is locked so Spring rejects the login
        assertThat(details.isAccountNonLocked())
            .as("blocked user must produce isAccountNonLocked()==false")
            .isFalse();
    }

    @Test
    @DisplayName("should return an unlocked UserDetails with the ROLE_ authority for an active user")
    void loadUser_active_returnsUnlockedWithRole() {
        // GIVEN — an active admin user
        when(userRepository.findByUsername(USERNAME))
            .thenReturn(Optional.of(user(USERNAME, HASH, false, UserRole.ADMIN)));

        // WHEN — loading the user
        final UserDetails details = service.loadUserByUsername(USERNAME);

        // THEN — unlocked and carries ROLE_ADMIN
        assertThat(details.isAccountNonLocked())
            .as("active user must be unlocked")
            .isTrue();
        assertThat(details.getAuthorities())
            .as("authorities must contain the ROLE_ prefixed role")
            .extracting(a -> a.getAuthority())
            .containsExactly("ROLE_ADMIN");
    }

    private static UserEntity user(String username, String hash, boolean blocked, UserRole role) {
        return UserEntity.builder()
            .id(UUID.randomUUID())
            .username(username)
            .displayName(username)
            .role(role)
            .createdAt(Instant.now())
            .passwordHash(hash)
            .passwordTemporary(false)
            .blocked(blocked)
            .mainAdmin(false)
            .build();
    }
}
