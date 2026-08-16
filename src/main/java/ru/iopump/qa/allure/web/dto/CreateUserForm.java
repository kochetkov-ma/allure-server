package ru.iopump.qa.allure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Binds the "New user" dialog form on {@code /app/admin/users}. Only a username
 * is requested — a temporary password is generated server-side and shown once.
 */
public record CreateUserForm(
    @NotBlank @Size(max = 128)
    @Pattern(regexp = "[A-Za-z0-9._-]+", message = "username may contain letters, digits, '.', '_' and '-' only")
    String username,
    @Size(max = 128) String displayName
) {

    public String effectiveDisplayName() {
        if (displayName == null || displayName.isBlank()) {
            return username;
        }
        return displayName.trim();
    }
}
