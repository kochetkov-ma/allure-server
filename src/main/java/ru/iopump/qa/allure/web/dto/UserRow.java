package ru.iopump.qa.allure.web.dto;

import ru.iopump.qa.allure.entity.UserEntity;
import ru.iopump.qa.allure.entity.UserRole;
import ru.iopump.qa.allure.security.CurrentUserProvider;

import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Read-only row view of {@link UserEntity} for the admin users table. Flags
 * {@code canDelete} / {@code canToggleAdmin} / {@code canResetPassword} /
 * {@code canBlock} encode the self / main-admin / system-guest protections that
 * {@code UserManagementService} enforces, so the JTE template hides buttons that
 * the service would reject rather than re-deriving the rules.
 */
public record UserRow(
    UUID id,
    String username,
    String displayName,
    UserRole role,
    boolean blocked,
    boolean mainAdmin,
    boolean passwordTemporary,
    String createdAt,
    String lastLoginAt,
    boolean isSelf,
    boolean canDelete,
    boolean canToggleAdmin,
    boolean canResetPassword,
    boolean canBlock
) {

    public static UserRow from(UserEntity entity, UserEntity actor) {
        final boolean self = actor != null && actor.getId() != null
            && actor.getId().equals(entity.getId());
        final boolean main = entity.isMainAdmin();
        // The shared guest system account is never a target of block/delete/role mutations —
        // the service rejects them, so hide the buttons that would otherwise 500/flash an error.
        final boolean guest = CurrentUserProvider.GUEST_USERNAME.equals(entity.getUsername())
            || entity.getRole() == UserRole.GUEST;
        final boolean canDelete = !main && !self && !guest;
        final boolean canToggleAdmin = !main && !self && !guest;
        final boolean canResetPassword = (!main || self) && !guest;
        final boolean canBlock = !main && !self && !guest;
        final DateTimeFormatter fmt = DateTimeFormatter.ISO_INSTANT;
        return new UserRow(
            entity.getId(),
            entity.getUsername(),
            entity.getDisplayName(),
            entity.getRole(),
            entity.isBlocked(),
            main,
            entity.isPasswordTemporary(),
            entity.getCreatedAt() == null ? "" : fmt.format(entity.getCreatedAt()),
            entity.getLastLoginAt() == null ? "" : fmt.format(entity.getLastLoginAt()),
            self,
            canDelete,
            canToggleAdmin,
            canResetPassword,
            canBlock
        );
    }
}
