package ru.iopump.qa.allure.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.iopump.qa.allure.entity.UserEntity;
import ru.iopump.qa.allure.security.CurrentUserProvider;
import ru.iopump.qa.allure.service.MainAdminProtectionException;
import ru.iopump.qa.allure.service.SelfProtectionException;
import ru.iopump.qa.allure.service.UserManagementService;
import ru.iopump.qa.allure.web.dto.CreateUserForm;
import ru.iopump.qa.allure.web.dto.UserRow;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin-only user-management UI at {@code /app/admin/users}. All actions require
 * {@code ROLE_ADMIN}; self-protection and main-admin-protection are enforced in
 * {@link UserManagementService}, translated to flash errors by
 * {@link #handleSelfProtection(SelfProtectionException, RedirectAttributes)}.
 */
@Controller
@RequestMapping("/app/admin/users")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminUsersController {

    private static final String VIEW_INDEX = "admin/users/index";
    private static final String REDIRECT_INDEX = "redirect:/app/admin/users";
    private static final String FLASH_KEY = "flash";
    private static final String FLASH_JUST_CREATED = "justCreatedUser";
    private static final String FLASH_JUST_RESET = "justResetUser";

    private final UserManagementService userManagementService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public String index(Model model) {
        final UserEntity actor = currentUserProvider.current();
        final List<UserRow> rows = userManagementService.list().stream()
            .map(u -> UserRow.from(u, actor))
            .toList();
        model.addAttribute("users", rows);
        model.addAttribute("title", "Users");
        model.addAttribute("activeNav", "admin-users");
        return VIEW_INDEX;
    }

    @PostMapping
    public String createUser(@Valid @ModelAttribute CreateUserForm form,
                             RedirectAttributes flash) {
        try {
            final UserManagementService.TempPasswordResult result =
                userManagementService.createUser(form.username(), form.effectiveDisplayName());
            flash.addFlashAttribute(FLASH_JUST_CREATED, Map.of(
                "username", result.user().getUsername(),
                "temporaryPassword", result.temporaryPassword()
            ));
            flash.addFlashAttribute(FLASH_KEY, toastMap("success",
                "User '" + form.username() + "' created. Share the temporary password below — it will not be shown again."));
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute(FLASH_KEY, toastMap("error", ex.getMessage()));
        }
        return REDIRECT_INDEX;
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable UUID id, RedirectAttributes flash) {
        final UserEntity actor = currentUserProvider.current();
        userManagementService.delete(id, actor);
        flash.addFlashAttribute(FLASH_KEY, toastMap("success", "User deleted."));
        return REDIRECT_INDEX;
    }

    @PostMapping("/{id}/grant-admin")
    public String grantAdmin(@PathVariable UUID id, RedirectAttributes flash) {
        final UserEntity actor = currentUserProvider.current();
        final UserEntity updated = userManagementService.grantAdmin(id, actor);
        flash.addFlashAttribute(FLASH_KEY, toastMap("success",
            "'" + updated.getUsername() + "' is now ADMIN."));
        return REDIRECT_INDEX;
    }

    @PostMapping("/{id}/revoke-admin")
    public String revokeAdmin(@PathVariable UUID id, RedirectAttributes flash) {
        final UserEntity actor = currentUserProvider.current();
        final UserEntity updated = userManagementService.revokeAdmin(id, actor);
        flash.addFlashAttribute(FLASH_KEY, toastMap("success",
            "Admin role revoked from '" + updated.getUsername() + "'."));
        return REDIRECT_INDEX;
    }

    @PostMapping("/{id}/reset-password")
    public String resetPassword(@PathVariable UUID id, RedirectAttributes flash) {
        final UserEntity actor = currentUserProvider.current();
        final UserManagementService.TempPasswordResult result =
            userManagementService.resetPassword(id, actor);
        flash.addFlashAttribute(FLASH_JUST_RESET, Map.of(
            "username", result.user().getUsername(),
            "temporaryPassword", result.temporaryPassword()
        ));
        flash.addFlashAttribute(FLASH_KEY, toastMap("success",
            "Temporary password generated for '" + result.user().getUsername() + "'."));
        return REDIRECT_INDEX;
    }

    @PostMapping("/{id}/block")
    public String block(@PathVariable UUID id, RedirectAttributes flash) {
        final UserEntity actor = currentUserProvider.current();
        final UserEntity updated = userManagementService.block(id, actor);
        flash.addFlashAttribute(FLASH_KEY, toastMap("success",
            "User '" + updated.getUsername() + "' blocked."));
        return REDIRECT_INDEX;
    }

    @PostMapping("/{id}/unblock")
    public String unblock(@PathVariable UUID id, RedirectAttributes flash) {
        final UserEntity actor = currentUserProvider.current();
        final UserEntity updated = userManagementService.unblock(id, actor);
        flash.addFlashAttribute(FLASH_KEY, toastMap("success",
            "User '" + updated.getUsername() + "' unblocked."));
        return REDIRECT_INDEX;
    }

    @ExceptionHandler({SelfProtectionException.class, MainAdminProtectionException.class})
    public String handleSelfProtection(SelfProtectionException ex, RedirectAttributes flash) {
        log.warn("Admin action blocked by self/main-admin protection: {}", ex.getMessage());
        flash.addFlashAttribute(FLASH_KEY, toastMap("error", ex.getMessage()));
        return REDIRECT_INDEX;
    }

    private static Map<String, String> toastMap(String level, String message) {
        final Map<String, String> map = new LinkedHashMap<>();
        map.put("level", level);
        map.put("message", message == null ? "" : message);
        return map;
    }
}
