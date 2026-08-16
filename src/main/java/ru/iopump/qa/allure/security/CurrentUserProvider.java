package ru.iopump.qa.allure.security;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.iopump.qa.allure.entity.UserEntity;
import ru.iopump.qa.allure.entity.UserRole;
import ru.iopump.qa.allure.repo.UserRepository;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Resolves the current {@link UserEntity} from Spring's {@link SecurityContextHolder}.
 * <p>
 * Falls back to the seeded {@code guest} user when no authentication is present or
 * the principal cannot be mapped to a persisted row. The guest reference is cached
 * in an {@link AtomicReference} to avoid a database round-trip on every render of
 * the layout's user-menu partial.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CurrentUserProvider {

    public static final String GUEST_USERNAME = "guest";

    private final UserRepository userRepository;
    private final AtomicReference<UserEntity> guestCache = new AtomicReference<>();

    @PostConstruct
    void init() {
        refreshGuestCache();
    }

    /**
     * Populate/refresh the cached guest user reference. Safe to call from
     * {@link ru.iopump.qa.allure.config.UserSeeder} after it creates the row.
     */
    @Transactional(readOnly = true)
    public void refreshGuestCache() {
        userRepository.findByUsername(GUEST_USERNAME).ifPresent(guestCache::set);
    }

    /**
     * @return the current user: authenticated principal, or seeded guest, or a
     * transient guest placeholder if the DB has not been seeded yet.
     */
    @Transactional(readOnly = true)
    public UserEntity current() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return guest();
        }
        // Both Basic auth and X-API-Token auth expose the principal as a UserDetails whose
        // getName() is the username, so a single findByUsername lookup resolves either path.
        final String name = authentication.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return guest();
        }
        return userRepository.findByUsername(name).orElseGet(this::guest);
    }

    private UserEntity guest() {
        final UserEntity cached = guestCache.get();
        if (cached != null) {
            return cached;
        }
        // Fallback: seeder has not run yet. Return a transient placeholder with a
        // null id so templates can still render the avatar without an NPE.
        return UserEntity.builder()
            .id(null)
            .username(GUEST_USERNAME)
            .displayName("Guest")
            .role(UserRole.GUEST)
            .createdAt(java.time.Instant.EPOCH)
            .build();
    }
}
