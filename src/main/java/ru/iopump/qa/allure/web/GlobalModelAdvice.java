package ru.iopump.qa.allure.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import ru.iopump.qa.allure.entity.UserEntity;
import ru.iopump.qa.allure.security.CurrentUserProvider;
import ru.iopump.qa.allure.web.dto.CurrentUserView;

/**
 * Exposes model attributes to every server-rendered view under {@code /app/**}:
 * <ul>
 *   <li>{@code currentUser} — a {@link CurrentUserView} (never null; guest fallback)
 *       built from the resolved {@link UserEntity}; the entity itself (and its
 *       {@code passwordHash}) never reaches the view layer</li>
 *   <li>{@code authEnabled} — always {@code true} post-refactor; kept for template
 *       backward compatibility</li>
 *   <li>{@code isAdmin} — {@code true} when the resolved user is an admin</li>
 *   <li>{@code signInRequired} — {@code true} when the current user is the guest
 *       fallback (unpersisted or role = GUEST) and therefore should see a Sign-in link</li>
 * </ul>
 */
@ControllerAdvice(basePackageClasses = {
    ResultsWebController.class,
    ReportsWebController.class,
    AboutWebController.class,
    ProfileWebController.class,
    AdminUsersController.class,
    AdminSettingsController.class,
    PasswordChangeController.class
})
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final CurrentUserProvider currentUserProvider;

    @ModelAttribute("currentUser")
    public CurrentUserView currentUser() {
        return CurrentUserView.from(currentUserProvider.current());
    }

    @ModelAttribute("authEnabled")
    public Boolean authEnabled() {
        return Boolean.TRUE;
    }

    @ModelAttribute("isAdmin")
    public Boolean isAdmin() {
        return currentUser().admin();
    }

    @ModelAttribute("signInRequired")
    public Boolean signInRequired() {
        return currentUser().guest();
    }
}
