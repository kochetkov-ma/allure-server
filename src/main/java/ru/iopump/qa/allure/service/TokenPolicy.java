package ru.iopump.qa.allure.service;

import lombok.NonNull;
import org.springframework.stereotype.Component;
import ru.iopump.qa.allure.entity.UserRole;

import java.util.Map;

/**
 * Per-role cap on the number of simultaneously active (not revoked and not
 * expired) API tokens a single user may hold.
 * <p>
 * Kept outside {@link ru.iopump.qa.allure.entity.UserRole} on purpose — role is
 * a pure enum flag, while quota is a security/business policy that may later
 * depend on environment or configuration.
 */
@Component
public final class TokenPolicy {

    // Guests own zero tokens — ApiTokenService.createToken hard-rejects the role; the
    // zero cap keeps the policy consistent with that single source of truth.
    public static final int GUEST_MAX_ACTIVE_TOKENS = 0;
    public static final int USER_MAX_ACTIVE_TOKENS = 10;
    public static final int ADMIN_MAX_ACTIVE_TOKENS = 50;

    private final Map<UserRole, Integer> limitsByRole = Map.of(
        UserRole.GUEST, GUEST_MAX_ACTIVE_TOKENS,
        UserRole.USER, USER_MAX_ACTIVE_TOKENS,
        UserRole.ADMIN, ADMIN_MAX_ACTIVE_TOKENS
    );

    /**
     * @return the maximum number of active tokens allowed for {@code role}.
     */
    public int maxActiveTokens(@NonNull UserRole role) {
        final Integer limit = limitsByRole.get(role);
        if (limit == null) {
            throw new IllegalStateException("No token limit configured for role " + role);
        }
        return limit;
    }
}
