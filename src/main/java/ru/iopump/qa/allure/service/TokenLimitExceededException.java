package ru.iopump.qa.allure.service;

import lombok.Getter;
import ru.iopump.qa.allure.entity.UserRole;

/**
 * Raised when a user would exceed their per-role active API token quota.
 * Active = not revoked AND (no expiration OR expiration in the future).
 */
@Getter
public final class TokenLimitExceededException extends RuntimeException {

    private final UserRole role;
    private final long currentActive;
    private final int limit;

    public TokenLimitExceededException(UserRole role, long currentActive, int limit) {
        super("Token limit reached (" + currentActive + " of " + limit
            + "). Revoke an old one first.");
        this.role = role;
        this.currentActive = currentActive;
        this.limit = limit;
    }
}
