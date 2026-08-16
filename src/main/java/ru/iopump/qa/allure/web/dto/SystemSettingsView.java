package ru.iopump.qa.allure.web.dto;

import ru.iopump.qa.allure.service.SystemSettingsService;

import java.time.Instant;

/**
 * Read-only view of the runtime system settings for the admin settings template.
 * <p>
 * Mirrors {@link SystemSettingsService.Snapshot} so that {@code admin/settings/index.jte}
 * binds to a {@code web/dto} type and never imports the service layer. Built in
 * {@code AdminSettingsController} from the cached snapshot.
 *
 * @param requireApiAuth     whether {@code /api/**} requires authentication
 * @param updatedAt          instant of the last change
 * @param updatedByUsername  username of the last editor, or {@code null} if never changed
 */
public record SystemSettingsView(
    boolean requireApiAuth,
    Instant updatedAt,
    String updatedByUsername
) {

    public static SystemSettingsView from(SystemSettingsService.Snapshot snapshot) {
        return new SystemSettingsView(
            snapshot.requireApiAuth(),
            snapshot.updatedAt(),
            snapshot.updatedByUsername()
        );
    }
}
