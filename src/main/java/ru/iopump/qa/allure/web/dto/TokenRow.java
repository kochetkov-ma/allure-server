package ru.iopump.qa.allure.web.dto;

import ru.iopump.qa.allure.entity.ApiTokenEntity;

import java.time.Instant;
import java.util.UUID;

/**
 * Row projection for the tokens table on {@code /app/profile}. All timestamps
 * are ISO-8601 {@link Instant#toString()} values so the template can format them
 * client-side in the visitor's local timezone.
 */
public record TokenRow(
    UUID id,
    String name,
    String createdAt,
    String expiresAt,
    String lastUsedAt,
    boolean revoked,
    boolean expired
) {

    public static TokenRow from(ApiTokenEntity entity, Instant now) {
        return new TokenRow(
            entity.getId(),
            entity.getName(),
            entity.getCreatedAt() == null ? "" : entity.getCreatedAt().toString(),
            entity.getExpiresAt() == null ? "" : entity.getExpiresAt().toString(),
            entity.getLastUsedAt() == null ? "" : entity.getLastUsedAt().toString(),
            entity.isRevoked(),
            entity.isExpired(now)
        );
    }

    public String status() {
        if (revoked) {
            return "revoked";
        }
        if (expired) {
            return "expired";
        }
        return "active";
    }
}
