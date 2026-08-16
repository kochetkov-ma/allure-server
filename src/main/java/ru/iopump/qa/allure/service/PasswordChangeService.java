package ru.iopump.qa.allure.service;

import com.google.common.base.Preconditions;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.iopump.qa.allure.entity.UserEntity;
import ru.iopump.qa.allure.repo.UserRepository;

import java.util.UUID;

/**
 * Lets a user rotate their own password. Enforces: current-password match,
 * minimum length on the new value, and clears the {@code passwordTemporary}
 * flag so {@code ForcePasswordChangeFilter} stops redirecting.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PasswordChangeService {

    public static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public void change(@NonNull UUID userId, @NonNull String currentPlain, @NonNull String newPlain) {
        Preconditions.checkArgument(!currentPlain.isBlank(), "current password must not be blank");
        if (newPlain.length() < MIN_PASSWORD_LENGTH) {
            throw new WeakPasswordException(
                "New password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        final UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new BadCredentialsException("This account has no local password.");
        }
        if (!passwordEncoder.matches(currentPlain, user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPlain));
        user.setPasswordTemporary(false);
        userRepository.save(user);
        log.info("User '{}' changed their password", user.getUsername());
    }
}
