package ru.iopump.qa.allure.entity;

import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
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
 * Persistent user record. Avoids {@code @Data} because that would derive
 * {@code hashCode} from every column and break JPA identity across a detach/reattach
 * cycle. Equality is intentionally based on the primary key alone.
 * <p>
 * Password is stored as a BCrypt hash in {@link #passwordHash}. A null hash means
 * the account is local-login-disabled (e.g. seeded {@code guest}, OAuth-only users).
 * {@link #passwordTemporary} flags hashes that must be rotated on next login —
 * enforced by {@link ru.iopump.qa.allure.security.ForcePasswordChangeFilter}.
 * {@link #mainAdmin} marks the bootstrap administrator and carries extra
 * protections (no delete, no demote, only self can reset password).
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(of = {"id", "username", "role", "blocked", "mainAdmin"})
public class UserEntity {

    @Id
    private UUID id;

    @NotBlank
    @Column(nullable = false, unique = true, length = 128)
    private String username;

    @NotBlank
    @Column(nullable = false, length = 128)
    private String displayName;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserRole role;

    @NotNull
    @Column(nullable = false)
    private Instant createdAt;

    @Nullable
    @Column(name = "password_hash", length = 128)
    private String passwordHash;

    @Column(name = "password_temporary", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean passwordTemporary = false;

    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean blocked = false;

    @Column(name = "main_admin", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean mainAdmin = false;

    @Nullable
    @Column(name = "last_login_at")
    private Instant lastLoginAt;
}
