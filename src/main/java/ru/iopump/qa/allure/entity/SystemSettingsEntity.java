package ru.iopump.qa.allure.entity;

import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * Singleton row holding runtime-adjustable system flags. There is exactly one row
 * identified by {@link #SINGLETON_ID} — admin-panel writes overwrite in place.
 */
@Entity
@Table(name = "app_system_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString
public class SystemSettingsEntity {

    public static final UUID SINGLETON_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Id
    private UUID id;

    @Column(name = "require_api_auth", nullable = false)
    @Builder.Default
    private boolean requireApiAuth = false;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Nullable
    @Column(name = "updated_by_username", length = 128)
    private String updatedByUsername;
}
