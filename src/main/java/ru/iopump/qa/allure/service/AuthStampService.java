package ru.iopump.qa.allure.service;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.iopump.qa.allure.repo.ApiTokenRepository;
import ru.iopump.qa.allure.repo.UserRepository;

import java.time.Instant;
import java.util.UUID;

/**
 * Best-effort "last activity" stamps for the auth path.
 * <p>
 * Each method runs in its OWN transaction ({@link Propagation#REQUIRES_NEW}) so a
 * stamp failure commits/rolls back independently and cannot mark the caller's
 * (auth or login) transaction rollback-only. Without this isolation, a failed
 * bulk {@code @Modifying} update would poison the outer transaction and re-surface
 * as {@code UnexpectedRollbackException} at the proxy commit boundary — outside the
 * caller's try/catch — turning a valid authentication into an HTTP 500.
 * <p>
 * The stamps use non-versioned bulk updates (they never bump the entity
 * {@code @Version}), so concurrent stateless clients sharing a token/user cannot
 * collide on an optimistic-lock check. Any failure is swallowed and logged at debug.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthStampService {

    private final ApiTokenRepository apiTokenRepository;
    private final UserRepository userRepository;

    /**
     * Stamp {@code lastUsedAt} on a token in a fresh transaction. Never fails the caller.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void touchTokenLastUsed(@NonNull UUID tokenId, @NonNull Instant now) {
        try {
            apiTokenRepository.touchLastUsedAt(tokenId, now);
        } catch (RuntimeException ex) {
            log.debug("Failed to stamp lastUsedAt for token '{}' (ignored)", tokenId, ex);
        }
    }

    /**
     * Stamp {@code lastLoginAt} on a user in a fresh transaction. Never fails the caller.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void touchUserLastLogin(@NonNull UUID userId, @NonNull Instant now) {
        try {
            userRepository.touchLastLoginAt(userId, now);
        } catch (RuntimeException ex) {
            log.debug("Failed to stamp lastLoginAt for user '{}' (ignored)", userId, ex);
        }
    }
}
