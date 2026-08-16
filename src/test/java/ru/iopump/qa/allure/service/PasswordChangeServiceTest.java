package ru.iopump.qa.allure.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.iopump.qa.allure.entity.UserEntity;
import ru.iopump.qa.allure.entity.UserRole;
import ru.iopump.qa.allure.repo.UserRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordChangeServiceTest {

    private static final int MIN_LENGTH = PasswordChangeService.MIN_PASSWORD_LENGTH;
    private static final String CURRENT_PLAIN = "currentPass1";
    private static final String CURRENT_HASH = "$2a$currentHash";
    private static final String NEW_VALID = "newpassword99";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PasswordChangeService passwordChangeService;

    private UserEntity user;

    @BeforeEach
    void setUp() {
        user = UserEntity.builder()
            .id(UUID.randomUUID())
            .username("alice")
            .displayName("Alice")
            .role(UserRole.USER)
            .createdAt(Instant.now())
            .passwordHash(CURRENT_HASH)
            .passwordTemporary(true)
            .blocked(false)
            .build();
    }

    @Test
    @DisplayName("should throw BadCredentialsException when current password does not match the stored hash")
    void change_throwsWhenCurrentPasswordWrong() {
        // GIVEN — user exists but supplied current password is wrong
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(CURRENT_PLAIN, CURRENT_HASH)).thenReturn(false);

        // WHEN / THEN — change is rejected
        assertThatThrownBy(() -> passwordChangeService.change(user.getId(), CURRENT_PLAIN, NEW_VALID))
            .as("wrong current password must throw BadCredentialsException")
            .isInstanceOf(BadCredentialsException.class)
            .hasMessageContaining("incorrect");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("should throw WeakPasswordException when new password is shorter than the minimum length")
    void change_throwsWhenNewPasswordTooShort() {
        // GIVEN — new password is exactly one char shorter than the minimum
        final String tooShort = "x".repeat(MIN_LENGTH - 1);

        // WHEN / THEN — validation fires before any DB access
        assertThatThrownBy(() -> passwordChangeService.change(user.getId(), CURRENT_PLAIN, tooShort))
            .as("new password below minimum length must throw WeakPasswordException")
            .isInstanceOf(WeakPasswordException.class)
            .hasMessageContaining(String.valueOf(MIN_LENGTH));
        verify(userRepository, never()).findById(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("should throw BadCredentialsException when user has no stored password hash")
    void change_throwsWhenUserHasNoPasswordHash() {
        // GIVEN — user exists but has a null hash (OAuth-only or guest account)
        final UserEntity noHashUser = UserEntity.builder()
            .id(user.getId())
            .username("oauth-user")
            .displayName("OAuth User")
            .role(UserRole.USER)
            .createdAt(Instant.now())
            .passwordHash(null)
            .passwordTemporary(false)
            .build();
        when(userRepository.findById(noHashUser.getId())).thenReturn(Optional.of(noHashUser));

        // WHEN / THEN — cannot change a password when none is set
        assertThatThrownBy(() -> passwordChangeService.change(noHashUser.getId(), CURRENT_PLAIN, NEW_VALID))
            .as("account with no password hash must throw BadCredentialsException")
            .isInstanceOf(BadCredentialsException.class)
            .hasMessageContaining("no local password");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("should clear passwordTemporary flag and save updated hash when change succeeds")
    void change_clearsPasswordTemporaryFlag() {
        // GIVEN — user with a temporary password hash; current password matches
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(CURRENT_PLAIN, CURRENT_HASH)).thenReturn(true);
        when(passwordEncoder.encode(NEW_VALID)).thenReturn("$2a$newHash");

        // WHEN — change password
        passwordChangeService.change(user.getId(), CURRENT_PLAIN, NEW_VALID);

        // THEN — user saved with temporary flag cleared and new hash stored
        final ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        final UserEntity saved = captor.getValue();
        assertThat(saved.isPasswordTemporary())
            .as("passwordTemporary flag must be cleared after a successful change")
            .isFalse();
        assertThat(saved.getPasswordHash())
            .as("stored hash must reflect the new password encoding")
            .isEqualTo("$2a$newHash");
    }
}
