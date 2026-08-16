package ru.iopump.qa.allure.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Binds the {@code /app/profile/password} form. Length/strength is enforced in
 * {@code PasswordChangeService} so the service-layer guarantees the same contract
 * regardless of which caller invokes it.
 */
public record PasswordChangeForm(
    @NotBlank String currentPassword,
    @NotBlank String newPassword,
    @NotBlank String confirmPassword
) {
    public boolean confirmed() {
        return newPassword != null && newPassword.equals(confirmPassword);
    }
}
