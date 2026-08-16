package ru.iopump.qa.allure.web.dto;

import ru.iopump.qa.allure.entity.UserEntity;
import ru.iopump.qa.allure.entity.UserRole;

/**
 * Read-only view of the current principal for server-rendered {@code /app/**} templates.
 * <p>
 * Exposes only the fields the views need (name/role/flags) and never the JPA
 * {@link UserEntity} — in particular the BCrypt {@code passwordHash} stays out of the
 * view layer. Built once per request in {@code GlobalModelAdvice} from the resolved
 * {@link UserEntity} (which may be the seeded guest or a transient guest fallback).
 *
 * @param username          login name; {@code "guest"} for the anonymous fallback
 * @param displayName        human-readable name; {@code "Guest"} for the anonymous fallback
 * @param role               resolved role; {@link UserRole#GUEST} for the anonymous fallback
 * @param persisted          {@code true} when the principal is backed by a database row
 *                           (a persisted guest is still {@code persisted})
 * @param authenticated      {@code true} only for a persisted, non-guest principal
 * @param guest              {@code true} for the guest account or the unpersisted fallback
 * @param admin              {@code true} when {@link #role()} is {@link UserRole#ADMIN}
 * @param passwordTemporary  {@code true} when the principal must rotate a temporary password
 */
public record CurrentUserView(
    String username,
    String displayName,
    UserRole role,
    boolean persisted,
    boolean authenticated,
    boolean guest,
    boolean admin,
    boolean passwordTemporary
) {

    /**
     * Build the view from the resolved current user. A {@code null} entity is treated as
     * the anonymous guest fallback so templates can render without a null check.
     */
    public static CurrentUserView from(UserEntity entity) {
        if (entity == null) {
            return new CurrentUserView("guest", "Guest", UserRole.GUEST, false, false, true, false, false);
        }
        final UserRole role = entity.getRole() == null ? UserRole.GUEST : entity.getRole();
        final boolean persisted = entity.getId() != null;
        final boolean guest = !persisted || role == UserRole.GUEST;
        final boolean authenticated = persisted && role != UserRole.GUEST;
        final boolean admin = role == UserRole.ADMIN;
        return new CurrentUserView(
            entity.getUsername(),
            entity.getDisplayName(),
            role,
            persisted,
            authenticated,
            guest,
            admin,
            entity.isPasswordTemporary()
        );
    }
}
