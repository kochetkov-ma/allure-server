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

    private final UserRepository userRepository;

    @EventListener
    @Transactional
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        final Authentication authentication = event.getAuthentication();
        if (authentication == null) {
            return;
        }
        final String username = authentication.getName();
        if (username == null || username.isBlank() || "anonymousUser".equals(username)) {
            return;
        }
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setLastLoginAt(Instant.now());
            userRepository.save(user);
            log.debug("Stamped lastLoginAt for '{}'", username);
        });
    }
}
