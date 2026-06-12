package ru.iopump.qa.allure.service;

import com.google.common.base.Preconditions;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.iopump.qa.allure.entity.UserEntity;
import ru.iopump.qa.allure.entity.UserRole;
import ru.iopump.qa.allure.repo.ApiTokenRepository;
import ru.iopump.qa.allure.repo.UserRepository;
import ru.iopump.qa.allure.security.CurrentUserProvider;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Administrative user-management operations invoked by {@code AdminUsersController}.
 * <p>
 * All mutating methods receive the acting admin's {@link UserEntity} so the
 * self-protection guards can compare against it without relying on
 * {@code SecurityContextHolder}. Guards enforced here (never in the controller):
 * <ul>
 *   <li>main-admin: cannot delete, cannot revoke admin role, cannot be reset by
 *       anyone except themselves</li>
 *   <li>self: cannot delete yourself, cannot revoke your own admin, cannot block
 *       yourself</li>
 * </ul>
 * Temporary passwords are 12 base62 chars from {@link SecureRandom}. Plain value
 * is returned in {@link TempPasswordResult} exactly once — only the hash is
 * persisted.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserManagementService {

    public static final int TEMP_PASSWORD_LENGTH = 12;
    private static final char[] BASE62 =
        "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    private final UserRepository userRepository;
    private final ApiTokenRepository apiTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional(readOnly = true)
    public List<UserEntity> list() {
        return userRepository.findAllByOrderByUsernameAsc();
    }

    public TempPasswordResult createUser(@NonNull String username, @NonNull String displayName) {
        Preconditions.checkArgument(!username.isBlank(), "username must not be blank");
        Preconditions.checkArgument(!displayName.isBlank(), "displayName must not be blank");
        // Normalize once so the duplicate guard and the persisted value agree — otherwise a
        // whitespace-padded input (e.g. "alice ") slips past findByUsername yet collides with
        // the trimmed "alice" on the unique constraint at flush, surfacing as a raw 500.
        final String normalizedUsername = username.trim();
        final String normalizedDisplayName = displayName.trim();
        userRepository.findByUsername(normalizedUsername).ifPresent(existing -> {
            throw new IllegalArgumentException("User already exists: " + normalizedUsername);
        });
        final String tempPassword = generateTempPassword();
        final UserEntity created = UserEntity.builder()
            .id(UUID.randomUUID())
            .username(normalizedUsername)
            .displayName(normalizedDisplayName)
            .role(UserRole.USER)
            .createdAt(Instant.now())
            .passwordHash(passwordEncoder.encode(tempPassword))
            .passwordTemporary(true)
            .blocked(false)
            .mainAdmin(false)
            .build();
        final UserEntity saved = userRepository.save(created);
        log.info("Admin created user '{}' (id={}) with temporary password", normalizedUsername, saved.getId());
        return new TempPasswordResult(saved, tempPassword);
    }

    public void delete(@NonNull UUID targetId, @NonNull UserEntity actor) {
        final UserEntity target = load(targetId);
        if (target.isMainAdmin()) {
            throw new MainAdminProtectionException("The main administrator cannot be deleted.");
        }
        if (isSystemAccount(target)) {
            throw new SystemAccountProtectionException("The system guest account cannot be deleted.");
        }
        if (isSelf(target, actor)) {
            throw new SelfProtectionException("You cannot delete your own account.");
        }
        // Remove owned API tokens first: app_api_token.user_id is a NOT NULL FK with no
        // cascade, so deleting the user directly would fail with an integrity violation.
        final int removedTokens = apiTokenRepository.deleteAllByUserId(target.getId());
        userRepository.delete(target);
        log.info("Admin '{}' deleted user '{}' (id={}) and {} API token(s)",
            actor.getUsername(), target.getUsername(), target.getId(), removedTokens);
    }

    public UserEntity grantAdmin(@NonNull UUID targetId, @NonNull UserEntity actor) {
        final UserEntity target = load(targetId);
        if (isSystemAccount(target)) {
            throw new SystemAccountProtectionException("The system guest account cannot be granted administrator rights.");
        }
        target.setRole(UserRole.ADMIN);
        final UserEntity saved = userRepository.save(target);
        log.info("Admin '{}' granted ADMIN to user '{}'", actor.getUsername(), target.getUsername());
        return saved;
    }

    public UserEntity revokeAdmin(@NonNull UUID targetId, @NonNull UserEntity actor) {
        final UserEntity target = load(targetId);
        if (target.isMainAdmin()) {
            throw new MainAdminProtectionException("The main administrator cannot be demoted.");
        }
        if (isSelf(target, actor)) {
            throw new SelfProtectionException("You cannot revoke your own administrator role.");
        }
        target.setRole(UserRole.USER);
        final UserEntity saved = userRepository.save(target);
        log.info("Admin '{}' revoked ADMIN from user '{}'", actor.getUsername(), target.getUsername());
        return saved;
    }

    public TempPasswordResult resetPassword(@NonNull UUID targetId, @NonNull UserEntity actor) {
        final UserEntity target = load(targetId);
        if (isSystemAccount(target)) {
            throw new SystemAccountProtectionException("The system guest account has no local password to reset.");
        }
        if (target.isMainAdmin() && !isSelf(target, actor)) {
            throw new MainAdminProtectionException(
                "Only the main administrator can reset their own password.");
        }
        final String tempPassword = generateTempPassword();
        target.setPasswordHash(passwordEncoder.encode(tempPassword));
        target.setPasswordTemporary(true);
        final UserEntity saved = userRepository.save(target);
        log.info("Admin '{}' reset password for user '{}'", actor.getUsername(), target.getUsername());
        return new TempPasswordResult(saved, tempPassword);
    }

    public UserEntity block(@NonNull UUID targetId, @NonNull UserEntity actor) {
        final UserEntity target = load(targetId);
        if (target.isMainAdmin()) {
            throw new MainAdminProtectionException("The main administrator cannot be blocked.");
        }
        if (isSystemAccount(target)) {
            throw new SystemAccountProtectionException("The system guest account cannot be blocked.");
        }
        if (isSelf(target, actor)) {
            throw new SelfProtectionException("You cannot block your own account.");
        }
        target.setBlocked(true);
        final UserEntity saved = userRepository.save(target);
        log.info("Admin '{}' blocked user '{}'", actor.getUsername(), target.getUsername());
        return saved;
    }

    public UserEntity unblock(@NonNull UUID targetId, @NonNull UserEntity actor) {
        final UserEntity target = load(targetId);
        target.setBlocked(false);
        final UserEntity saved = userRepository.save(target);
        log.info("Admin '{}' unblocked user '{}'", actor.getUsername(), target.getUsername());
        return saved;
    }

    ///// PRIVATE /////

    private UserEntity load(UUID id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + id));
    }

    private static boolean isSelf(UserEntity target, UserEntity actor) {
        return target.getId() != null && target.getId().equals(actor.getId());
    }

    /**
     * The seeded {@code guest} row backs anonymous access and must be protected from
     * delete / block / grant-admin / reset-password — matched by reserved username
     * so the guard holds even if {@code mainAdmin} is false.
     */
    private static boolean isSystemAccount(UserEntity target) {
        return CurrentUserProvider.GUEST_USERNAME.equals(target.getUsername());
    }

    private String generateTempPassword() {
        final byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        BigInteger number = new BigInteger(1, bytes);
        final StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        final BigInteger base = BigInteger.valueOf(BASE62.length);
        while (sb.length() < TEMP_PASSWORD_LENGTH) {
            BigInteger[] divmod = number.divideAndRemainder(base);
            sb.append(BASE62[divmod[1].intValue()]);
            number = divmod[0];
            if (number.signum() == 0) {
                // Top-up — extremely unlikely from 16 random bytes but safe.
                secureRandom.nextBytes(bytes);
                number = new BigInteger(1, bytes);
            }
        }
        return sb.toString();
    }

    /**
     * Carrier returned from {@link #createUser(String, String)} and
     * {@link #resetPassword(UUID, UserEntity)} — the plain temporary password is
     * exposed here exactly once.
     */
    public record TempPasswordResult(UserEntity user, String temporaryPassword) {
    }
}
