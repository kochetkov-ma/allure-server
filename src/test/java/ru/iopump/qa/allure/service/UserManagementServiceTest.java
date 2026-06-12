package ru.iopump.qa.allure.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.iopump.qa.allure.entity.UserEntity;
import ru.iopump.qa.allure.entity.UserRole;
import ru.iopump.qa.allure.repo.ApiTokenRepository;
import ru.iopump.qa.allure.repo.UserRepository;
import ru.iopump.qa.allure.security.CurrentUserProvider;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    private static final int TEMP_PASSWORD_LENGTH = UserManagementService.TEMP_PASSWORD_LENGTH;

    // Static actor for @MethodSource (must be static). The not-found throw happens in load()
    // before any self/main-admin guard runs, so only a non-null id is required here.
    private static final UserEntity ADMIN_FOR_PARAMS = UserEntity.builder()
        .id(UUID.randomUUID())
        .username("param-admin")
        .displayName("Param Admin")
        .role(UserRole.ADMIN)
        .createdAt(Instant.now())
        .mainAdmin(false)
        .build();

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApiTokenRepository apiTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserManagementService userManagementService;

    private UserEntity admin;
    private UserEntity mainAdmin;
    private UserEntity otherUser;
    private UserEntity guest;

    @BeforeEach
    void setUp() {
        admin = UserEntity.builder()
            .id(UUID.randomUUID())
            .username("admin")
            .displayName("Admin")
            .role(UserRole.ADMIN)
            .createdAt(Instant.now())
            .mainAdmin(false)
            .build();

        mainAdmin = UserEntity.builder()
            .id(UUID.randomUUID())
            .username("root")
            .displayName("Root Admin")
            .role(UserRole.ADMIN)
            .createdAt(Instant.now())
            .mainAdmin(true)
            .build();

        otherUser = UserEntity.builder()
            .id(UUID.randomUUID())
            .username("bob")
            .displayName("Bob")
            .role(UserRole.USER)
            .createdAt(Instant.now())
            .mainAdmin(false)
            .build();

        guest = UserEntity.builder()
            .id(UUID.randomUUID())
            .username(CurrentUserProvider.GUEST_USERNAME)
            .displayName("Guest")
            .role(UserRole.GUEST)
            .createdAt(Instant.now())
            .mainAdmin(false)
            .build();
    }

    @Test
    @DisplayName("should generate temp password and store its hash when createUser is called")
    void createUser_generatesTempPassword_andStoresHash() {
        // GIVEN — no existing user with that name; encoder returns a stable hash
        when(userRepository.findByUsername("newbie")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$hash");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        // WHEN — create a new user
        final UserManagementService.TempPasswordResult result =
            userManagementService.createUser("newbie", "Newbie User");

        // THEN — temp password is correct length, hash is persisted
        assertThat(result.temporaryPassword())
            .as("generated temporary password must have the expected length")
            .hasSize(TEMP_PASSWORD_LENGTH);

        final ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        final UserEntity saved = captor.getValue();
        assertThat(saved.getPasswordHash())
            .as("saved entity must carry the encoded hash, not the plain password")
            .isEqualTo("$2a$hash");
        assertThat(saved.isPasswordTemporary())
            .as("newly created user must have password flagged as temporary")
            .isTrue();
        assertThat(saved.getRole())
            .as("newly created user must have USER role")
            .isEqualTo(UserRole.USER);
    }

    @Test
    @DisplayName("should treat a whitespace-padded username as a duplicate of the trimmed existing user")
    void createUser_paddedUsernameCollidesWithTrimmedExisting() {
        // GIVEN — an existing user "alice"; the duplicate guard must consult the trimmed value
        final UserEntity existingAlice = UserEntity.builder()
            .id(UUID.randomUUID())
            .username("alice")
            .displayName("Alice")
            .role(UserRole.USER)
            .createdAt(Instant.now())
            .mainAdmin(false)
            .build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existingAlice));

        // WHEN / THEN — submitting "alice " (trailing whitespace) must collide with "alice"
        assertThatThrownBy(() -> userManagementService.createUser("alice ", "Alice Padded"))
            .as("a whitespace-padded username must be normalized and collide with the existing trimmed user")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("User already exists: alice");
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("should throw MainAdminProtectionException when trying to delete the main admin")
    void delete_throwsWhenTargetIsMainAdmin() {
        // GIVEN — target is the main admin
        when(userRepository.findById(mainAdmin.getId())).thenReturn(Optional.of(mainAdmin));

        // WHEN / THEN — deletion of main admin is rejected
        assertThatThrownBy(() -> userManagementService.delete(mainAdmin.getId(), admin))
            .as("deleting the main admin must throw MainAdminProtectionException")
            .isInstanceOf(MainAdminProtectionException.class)
            .hasMessageContaining("main administrator");
    }

    @Test
    @DisplayName("should throw SelfProtectionException when admin tries to delete themselves")
    void delete_throwsWhenTargetIsSelf() {
        // GIVEN — actor and target are the same user
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

        // WHEN / THEN — self-deletion is rejected
        assertThatThrownBy(() -> userManagementService.delete(admin.getId(), admin))
            .as("an admin trying to delete their own account must throw SelfProtectionException")
            .isInstanceOf(SelfProtectionException.class)
            .hasMessageContaining("delete");
    }

    @Test
    @DisplayName("should throw MainAdminProtectionException when revoking admin from the main admin")
    void revokeAdmin_throwsWhenTargetIsMainAdmin() {
        // GIVEN — target is main admin
        when(userRepository.findById(mainAdmin.getId())).thenReturn(Optional.of(mainAdmin));

        // WHEN / THEN — revoking admin role of main admin is rejected
        assertThatThrownBy(() -> userManagementService.revokeAdmin(mainAdmin.getId(), admin))
            .as("revoking admin from the main admin must throw MainAdminProtectionException")
            .isInstanceOf(MainAdminProtectionException.class)
            .hasMessageContaining("demoted");
    }

    @Test
    @DisplayName("should throw SelfProtectionException when admin revokes their own admin role")
    void revokeAdmin_throwsWhenTargetIsSelf() {
        // GIVEN — actor tries to revoke their own role
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

        // WHEN / THEN — self-demotion is rejected
        assertThatThrownBy(() -> userManagementService.revokeAdmin(admin.getId(), admin))
            .as("an admin revoking their own role must throw SelfProtectionException")
            .isInstanceOf(SelfProtectionException.class)
            .hasMessageContaining("revoke");
    }

    @Test
    @DisplayName("should throw SelfProtectionException when admin tries to block themselves")
    void block_throwsWhenTargetIsSelf() {
        // GIVEN — actor and target are the same
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

        // WHEN / THEN — self-blocking is rejected
        assertThatThrownBy(() -> userManagementService.block(admin.getId(), admin))
            .as("an admin blocking their own account must throw SelfProtectionException")
            .isInstanceOf(SelfProtectionException.class)
            .hasMessageContaining("block");
    }

    @Test
    @DisplayName("should throw MainAdminProtectionException when another admin tries to block the main admin")
    void block_throwsWhenTargetIsMainAdmin() {
        // GIVEN — a different actor targets the main admin
        when(userRepository.findById(mainAdmin.getId())).thenReturn(Optional.of(mainAdmin));

        // WHEN / THEN — blocking the main admin must be rejected so it cannot be locked out
        assertThatThrownBy(() -> userManagementService.block(mainAdmin.getId(), admin))
            .as("blocking the main admin must throw MainAdminProtectionException")
            .isInstanceOf(MainAdminProtectionException.class)
            .hasMessageContaining("main administrator cannot be blocked");
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("should delete owned API tokens before deleting the user")
    void delete_removesOwnedApiTokens_beforeDeletingUser() {
        // GIVEN — a deletable target that owns API tokens
        when(userRepository.findById(otherUser.getId())).thenReturn(Optional.of(otherUser));
        when(apiTokenRepository.deleteAllByUserId(otherUser.getId())).thenReturn(2);

        // WHEN — admin deletes the user
        userManagementService.delete(otherUser.getId(), admin);

        // THEN — tokens are removed first, then the user (avoiding the FK violation)
        final InOrder inOrder = inOrder(apiTokenRepository, userRepository);
        inOrder.verify(apiTokenRepository).deleteAllByUserId(otherUser.getId());
        inOrder.verify(userRepository).delete(otherUser);
    }

    @Test
    @DisplayName("should throw SystemAccountProtectionException when deleting the guest account")
    void delete_throwsWhenTargetIsGuest() {
        // GIVEN — target is the seeded guest system account
        when(userRepository.findById(guest.getId())).thenReturn(Optional.of(guest));

        // WHEN / THEN — the guest account must not be deletable
        assertThatThrownBy(() -> userManagementService.delete(guest.getId(), admin))
            .as("deleting the guest account must throw SystemAccountProtectionException")
            .isInstanceOf(SystemAccountProtectionException.class)
            .hasMessageContaining("guest account cannot be deleted");
        verify(userRepository, never()).delete(any(UserEntity.class));
        verify(apiTokenRepository, never()).deleteAllByUserId(any(UUID.class));
    }

    @Test
    @DisplayName("should throw SystemAccountProtectionException when blocking the guest account")
    void block_throwsWhenTargetIsGuest() {
        // GIVEN — target is the seeded guest system account
        when(userRepository.findById(guest.getId())).thenReturn(Optional.of(guest));

        // WHEN / THEN — the guest account must not be blockable
        assertThatThrownBy(() -> userManagementService.block(guest.getId(), admin))
            .as("blocking the guest account must throw SystemAccountProtectionException")
            .isInstanceOf(SystemAccountProtectionException.class)
            .hasMessageContaining("guest account cannot be blocked");
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("should throw SystemAccountProtectionException when granting admin to the guest account")
    void grantAdmin_throwsWhenTargetIsGuest() {
        // GIVEN — target is the seeded guest system account
        when(userRepository.findById(guest.getId())).thenReturn(Optional.of(guest));

        // WHEN / THEN — the guest account must not be promotable to admin
        assertThatThrownBy(() -> userManagementService.grantAdmin(guest.getId(), admin))
            .as("granting admin to the guest account must throw SystemAccountProtectionException")
            .isInstanceOf(SystemAccountProtectionException.class)
            .hasMessageContaining("guest account cannot be granted");
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("should throw SystemAccountProtectionException when resetting the guest account password")
    void resetPassword_throwsWhenTargetIsGuest() {
        // GIVEN — target is the seeded guest system account
        when(userRepository.findById(guest.getId())).thenReturn(Optional.of(guest));

        // WHEN / THEN — the guest account has no local password to reset
        assertThatThrownBy(() -> userManagementService.resetPassword(guest.getId(), admin))
            .as("resetting the guest account password must throw SystemAccountProtectionException")
            .isInstanceOf(SystemAccountProtectionException.class)
            .hasMessageContaining("guest account has no local password");
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("should throw MainAdminProtectionException when actor resets main admin password and is not main admin")
    void resetPassword_throwsWhenTargetIsMainAdmin_andActorIsDifferent() {
        // GIVEN — actor is a different admin, target is main admin
        when(userRepository.findById(mainAdmin.getId())).thenReturn(Optional.of(mainAdmin));

        // WHEN / THEN — only the main admin can reset their own password
        assertThatThrownBy(() -> userManagementService.resetPassword(mainAdmin.getId(), admin))
            .as("only the main admin can reset their own password; another admin must be rejected")
            .isInstanceOf(MainAdminProtectionException.class)
            .hasMessageContaining("main administrator");
    }

    @Test
    @DisplayName("should allow the main admin to reset their own password")
    void resetPassword_allowsSelfResetForMainAdmin() {
        // GIVEN — main admin is both actor and target
        when(userRepository.findById(mainAdmin.getId())).thenReturn(Optional.of(mainAdmin));
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$newhash");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        // WHEN — main admin resets their own password
        final UserManagementService.TempPasswordResult result =
            userManagementService.resetPassword(mainAdmin.getId(), mainAdmin);

        // THEN — temp password returned and entity saved with new hash flagged as temporary
        assertThat(result.temporaryPassword())
            .as("reset must produce a temporary password of the expected length")
            .hasSize(TEMP_PASSWORD_LENGTH);
        assertThat(result.user().isPasswordTemporary())
            .as("main admin self-reset must mark password as temporary")
            .isTrue();
        verify(userRepository).save(mainAdmin);
    }

    static Stream<Arguments> missingTargetMutations() {
        return Stream.of(
            Arguments.of("delete",
                (BiConsumer<UserManagementService, UUID>) (svc, id) -> svc.delete(id, ADMIN_FOR_PARAMS)),
            Arguments.of("block",
                (BiConsumer<UserManagementService, UUID>) (svc, id) -> svc.block(id, ADMIN_FOR_PARAMS)),
            Arguments.of("unblock",
                (BiConsumer<UserManagementService, UUID>) (svc, id) -> svc.unblock(id, ADMIN_FOR_PARAMS)),
            Arguments.of("grantAdmin",
                (BiConsumer<UserManagementService, UUID>) (svc, id) -> svc.grantAdmin(id, ADMIN_FOR_PARAMS)),
            Arguments.of("revokeAdmin",
                (BiConsumer<UserManagementService, UUID>) (svc, id) -> svc.revokeAdmin(id, ADMIN_FOR_PARAMS)),
            Arguments.of("resetPassword",
                (BiConsumer<UserManagementService, UUID>) (svc, id) -> svc.resetPassword(id, ADMIN_FOR_PARAMS))
        );
    }

    @ParameterizedTest(name = "{0} should throw UserNotFoundException for a missing target")
    @MethodSource("missingTargetMutations")
    @DisplayName("should throw UserNotFoundException when an admin mutation targets a missing user")
    void mutation_throwsUserNotFoundWhenTargetMissing(String mutation,
                                                      BiConsumer<UserManagementService, UUID> action) {
        // GIVEN — the target id does not resolve to any persisted user
        final UUID missingId = UUID.randomUUID();
        when(userRepository.findById(missingId)).thenReturn(Optional.empty());

        // WHEN / THEN — every admin mutation must surface a typed, human-safe not-found error
        assertThatThrownBy(() -> action.accept(userManagementService, missingId))
            .as("admin mutation '%s' against a missing target must throw UserNotFoundException", mutation)
            .isInstanceOf(UserNotFoundException.class)
            .hasMessageContaining("User not found: " + missingId);
        verify(userRepository, never()).save(any(UserEntity.class));
        verify(userRepository, never()).delete(any(UserEntity.class));
    }
}
