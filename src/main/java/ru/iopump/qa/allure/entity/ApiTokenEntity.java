package ru.iopump.qa.allure.entity;

import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
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
 * Persisted personal API token. The plain token value is NEVER stored — only the
 * SHA-256 hex {@link #tokenHash} is. The first 8 chars of the hash are copied into
 * {@link #tokenLookup} to support an indexed equality lookup without scanning every
 * row (the filter needs O(1) on the hot path for every API request).
 */
@Entity
@Table(
    name = "app_api_token",
    indexes = {@Index(name = "idx_api_token_lookup", columnList = "token_lookup")}
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(of = {"id", "name", "createdAt", "expiresAt", "revokedAt"})
public class ApiTokenEntity {

    @Id
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @NotBlank
    @Column(nullable = false, length = 128)
    private String name;

    @NotBlank
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @NotBlank
    @Column(name = "token_lookup", nullable = false, length = 16)
    private String tokenLookup;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Nullable
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Nullable
    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Nullable
    @Column(name = "revoked_at")
    private Instant revokedAt;

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    public boolean isActive(Instant now) {
        return !isRevoked() && !isExpired(now);
    }
}
