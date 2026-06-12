package ru.iopump.qa.allure.config;

import com.google.common.base.Preconditions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.iopump.qa.allure.entity.UserEntity;
import ru.iopump.qa.allure.entity.UserRole;
import ru.iopump.qa.allure.properties.BasicProperties;
import ru.iopump.qa.allure.repo.UserRepository;
import ru.iopump.qa.allure.security.CurrentUserProvider;

import java.time.Instant;
import java.util.UUID;

/**
 * Idempotent bootstrap of the baseline user rows:
 * <ul>
 *   <li>{@code guest} — anonymous fallback used when no authentication is present.
 *       Has no local password (cannot log in).</li>
 *   <li>Main administrator — the only user flagged {@code mainAdmin=true}.
 *       If no main-admin row exists the seeder creates one from
 *       {@link BasicProperties}; if one already exists (possibly renamed) it is
 *       left untouched to preserve operator-applied changes.</li>
 * </ul>
 * Running as an {@link ApplicationRunner} (not {@code @PostConstruct}) guarantees
 * the JPA layer is fully initialised before the first INSERT.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserSeeder implements ApplicationRunner {

    /**
     * The publicly-known default bootstrap password shipped in {@code application.yaml}
     * ({@code basic.auth.password}). When the effective password equals this value the
     * seeded main admin is flagged for forced rotation on first login.
     */
    static final String DEFAULT_BOOTSTRAP_PASSWORD = "admin";

    private final UserRepository userRepository;
    private final BasicProperties basicProperties;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedGuest();
        seedMainAdmin();
        currentUserProvider.refreshGuestCache();
    }

    private void seedGuest() {
        userRepository.findByUsername(CurrentUserProvider.GUEST_USERNAME).orElseGet(() -> {
            final UserEntity guest = UserEntity.builder()
                .id(UUID.randomUUID())
                .username(CurrentUserProvider.GUEST_USERNAME)
                .displayName("Guest")
                .role(UserRole.GUEST)
                .createdAt(Instant.now())
                .passwordHash(null)
                .passwordTemporary(false)
                .blocked(false)
                .mainAdmin(false)
                .build();
            userRepository.save(guest);
            log.info("Seeded user '{}' with role {}", guest.getUsername(), guest.getRole());
            return guest;
        });
    }

    private void seedMainAdmin() {
        userRepository.findByMainAdminTrue().ifPresentOrElse(
            existing -> log.info("Main administrator already present: '{}'", existing.getUsername()),
            this::createMainAdmin
        );
    }

    private void createMainAdmin() {
        final String username = basicProperties.username();
        // The 'guest' username is reserved for the passwordless anonymous fallback
        // (see CurrentUserProvider#current). Promoting it to ADMIN would both destroy
        // the guest fallback and grant admin to anonymous visitors — fail fast on the
        // misconfiguration instead of corrupting the reserved row.
        Preconditions.checkArgument(!CurrentUserProvider.GUEST_USERNAME.equals(username),
            "basic.auth.username must not be '%s' — that name is reserved for the anonymous "
                + "guest fallback. Choose a different administrator username.",
            CurrentUserProvider.GUEST_USERNAME);
        final boolean forceRotation = isDefaultBootstrapPassword();
        if (forceRotation) {
            log.warn("Main administrator '{}' is being bootstrapped with the publicly-known default "
                + "password — first login will be forced to set a new password. Set BASIC_AUTH_PASSWORD "
                + "to a strong secret to avoid this.", username);
        }
        userRepository.findByUsername(username).ifPresent(existing -> {
            // Collision with a non-main-admin row of the same name — promote it.
            existing.setMainAdmin(true);
            existing.setRole(UserRole.ADMIN);
            if (existing.getPasswordHash() == null || existing.getPasswordHash().isBlank()) {
                existing.setPasswordHash(passwordEncoder.encode(basicProperties.password()));
                existing.setPasswordTemporary(forceRotation);
            }
            userRepository.save(existing);
            log.info("Promoted existing user '{}' to main administrator", username);
        });
        if (userRepository.findByMainAdminTrue().isPresent()) {
            return;
        }
        final UserEntity admin = UserEntity.builder()
            .id(UUID.randomUUID())
            .username(username)
            .displayName("Administrator")
            .role(UserRole.ADMIN)
            .createdAt(Instant.now())
            .passwordHash(passwordEncoder.encode(basicProperties.password()))
            .passwordTemporary(forceRotation)
            .blocked(false)
            .mainAdmin(true)
            .build();
        userRepository.save(admin);
        log.info("Seeded main administrator '{}'", username);
    }

    /**
     * The bootstrap admin must be forced to rotate its password whenever the shipped
     * default credential ({@code admin}) is in effect — only an operator-supplied
     * non-default password skips forced rotation.
     */
    private boolean isDefaultBootstrapPassword() {
        return DEFAULT_BOOTSTRAP_PASSWORD.equals(basicProperties.password());
    }
}
