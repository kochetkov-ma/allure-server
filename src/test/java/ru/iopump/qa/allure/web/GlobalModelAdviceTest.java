package ru.iopump.qa.allure.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.iopump.qa.allure.entity.UserEntity;
import ru.iopump.qa.allure.entity.UserRole;
import ru.iopump.qa.allure.security.CurrentUserProvider;
import ru.iopump.qa.allure.web.dto.CurrentUserView;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link GlobalModelAdvice} — the {@code currentUser} (a
 * {@link CurrentUserView}, never the JPA entity), {@code isAdmin} and
 * {@code signInRequired} model attributes every {@code /app/**} template depends on.
 * Verifies the guest-vs-admin branches the templates use to toggle the Sign-in link,
 * admin nav and write controls, and that the BCrypt-bearing entity never leaks into
 * the view layer.
 */
class GlobalModelAdviceTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private final GlobalModelAdvice advice = new GlobalModelAdvice(currentUserProvider);

    @Test
    @DisplayName("should expose a non-entity CurrentUserView with admin flags for a persisted ADMIN user")
    void adminUser_viewExposesAdminFlags() {
        // GIVEN — a persisted ADMIN user is current
        final UserEntity admin = UserEntity.builder()
            .id(ADMIN_ID)
            .username("admin")
            .displayName("Administrator")
            .role(UserRole.ADMIN)
            .passwordHash("$2a$10$secret-hash")
            .build();
        when(currentUserProvider.current()).thenReturn(admin);

        // WHEN — the advice builds the currentUser view
        final CurrentUserView view = advice.currentUser();

        // THEN — the view carries exactly the admin projection, never the entity
        assertThat(view)
            .as("currentUser must be a CurrentUserView, not the JPA UserEntity")
            .isInstanceOf(CurrentUserView.class)
            .isEqualTo(new CurrentUserView("admin", "Administrator", UserRole.ADMIN, true, true, false, true, false));
        assertThat(advice.isAdmin())
            .as("isAdmin must be true for an ADMIN role")
            .isTrue();
        assertThat(advice.signInRequired())
            .as("signInRequired must be false for a non-guest user")
            .isFalse();
        assertThat(advice.authEnabled())
            .as("authEnabled is always true post-refactor")
            .isTrue();
    }

    @Test
    @DisplayName("should expose a guest CurrentUserView with isAdmin=false and signInRequired=true for the GUEST fallback")
    void guestUser_viewExposesGuestFlags() {
        // GIVEN — the guest fallback (unpersisted) is current
        final UserEntity guest = UserEntity.builder()
            .username("guest")
            .displayName("Guest")
            .role(UserRole.GUEST)
            .build();
        when(currentUserProvider.current()).thenReturn(guest);

        // WHEN — the advice builds the currentUser view
        final CurrentUserView view = advice.currentUser();

        // THEN — the guest view hides admin nav and write controls and requires sign-in
        assertThat(view)
            .as("currentUser must be the guest projection")
            .isEqualTo(new CurrentUserView("guest", "Guest", UserRole.GUEST, false, false, true, false, false));
        assertThat(advice.isAdmin())
            .as("isAdmin must be false for a GUEST role")
            .isFalse();
        assertThat(advice.signInRequired())
            .as("signInRequired must be true for a GUEST role")
            .isTrue();
    }

    @Test
    @DisplayName("should expose an authenticated non-admin CurrentUserView for a persisted regular USER")
    void regularUser_viewExposesAuthenticatedNonAdminFlags() {
        // GIVEN — a persisted regular USER is current
        final UserEntity user = UserEntity.builder()
            .id(USER_ID)
            .username("bob")
            .displayName("Bob")
            .role(UserRole.USER)
            .build();
        when(currentUserProvider.current()).thenReturn(user);

        // WHEN — the advice builds the currentUser view
        final CurrentUserView view = advice.currentUser();

        // THEN — a regular user is authenticated, not guest, not admin
        assertThat(view)
            .as("currentUser must be the authenticated regular-user projection")
            .isEqualTo(new CurrentUserView("bob", "Bob", UserRole.USER, true, true, false, false, false));
        assertThat(advice.isAdmin())
            .as("isAdmin must be false for a USER role")
            .isFalse();
        assertThat(advice.signInRequired())
            .as("signInRequired must be false for a persisted USER role")
            .isFalse();
    }
}
