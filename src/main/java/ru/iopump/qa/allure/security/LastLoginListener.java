package ru.iopump.qa.allure.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.iopump.qa.allure.entity.UserEntity;
import ru.iopump.qa.allure.repo.UserRepository;
import ru.iopump.qa.allure.service.AuthStampService;

import java.time.Duration;
import java.time.Instant;

/**
 * Stamps {@link UserEntity#getLastLoginAt()} when a principal authenticates
 * successfully via Basic auth (or any mechanism firing
 * {@link AuthenticationSuccessEvent}). The admin users grid renders this column,
 * so without this listener it would never be populated.
 * <p>
 * API-token authentication does not fire {@code AuthenticationSuccessEvent}
 * (the token filter sets the context directly); for token requests the
 * {@code lastUsedAt} of the individual token is the relevant timestamp and is
 * stamped by {@code ApiTokenService.authenticate}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LastLoginListener {

    /**
     * Minimum age of the previous stamp before a re-authentication triggers a new write.
     * Stateless Basic API clients re-auth on every request; without this throttle each
     * request would issue a redundant UPDATE.
     */
    private static final Duration STAMP_INTERVAL = Duration.ofMinutes(1);

    private final UserRepository userRepository;
    private final AuthStampService authStampService;

    @EventListener
    @Transactional
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        try {
            final Authentication authentication = event.getAuthentication();
            if (authentication == null) {
                return;
            }
            final String username = authentication.getName();
            if (username == null || username.isBlank() || "anonymousUser".equals(username)) {
                return;
            }
            final Instant now = Instant.now();
            userRepository.findByUsername(username).ifPresent(user -> {
                final Instant last = user.getLastLoginAt();
                if (last != null && last.isAfter(now.minus(STAMP_INTERVAL))) {
                    return;
                }
                // Stamp in a fresh transaction (REQUIRES_NEW): a write failure commits/rolls
                // back independently and cannot mark this listener's transaction rollback-only.
                authStampService.touchUserLastLogin(user.getId(), now);
                log.debug("Stamped lastLoginAt for '{}'", username);
            });
        } catch (RuntimeException ex) {
            // A failure here must never bubble back into the authentication flow and turn
            // a valid login into an HTTP 500.
            log.debug("Failed to stamp lastLoginAt (ignored)", ex);
        }
    }
}
