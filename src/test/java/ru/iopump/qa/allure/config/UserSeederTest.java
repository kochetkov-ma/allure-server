package ru.iopump.qa.allure.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.iopump.qa.allure.entity.UserEntity;
import ru.iopump.qa.allure.entity.UserRole;
import ru.iopump.qa.allure.properties.BasicProperties;
import ru.iopump.qa.allure.repo.UserRepository;
import ru.iopump.qa.allure.security.CurrentUserProvider;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSeederTest {

    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "admin";
    private static final String CUSTOM_PASSWORD = "Str0ng-Operator-Secret";
    private static final String ENCODED = "$2a$encoded";

    @Mock
    private UserRepository userRepository;

    @Mock
    private BasicProperties basicProperties;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private UserSeeder userSeeder;

    @BeforeEach
    void setUp() {
        userSeeder = new UserSeeder(userRepository, basicProperties, passwordEncoder, currentUserProvider);
        lenient().when(basicProperties.username()).thenReturn(DEFAULT_USERNAME);
        lenient().when(basicProperties.password()).thenReturn(DEFAULT_PASSWORD);
        lenient().when(passwordEncoder.encode(anyString())).thenReturn(ENCODED);
        lenient().when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("should seed the guest account with GUEST role and no password on a fresh database")
    void run_seedsGuest_onFreshDatabase() {
        // GIVEN — empty database: no guest, no main admin
        when(userRepository.findByUsername(CurrentUserProvider.GUEST_USERNAME)).thenReturn(Optional.empty());
        when(userRepository.findByUsername(DEFAULT_USERNAME)).thenReturn(Optional.empty());
        when(userRepository.findByMainAdminTrue()).thenReturn(Optional.empty());

        // WHEN — the seeder runs
        userSeeder.run(new DefaultApplicationArguments());

        // THEN — a guest row is persisted with the expected shape
        final UserEntity guest = capturePersisted(CurrentUserProvider.GUEST_USERNAME);
        assertThat(guest.getRole())
            .as("seeded guest must have GUEST role")
            .isEqualTo(UserRole.GUEST);
        assertThat(guest.getPasswordHash())
            .as("seeded guest must have no local password")
            .isNull();
        assertThat(guest.isMainAdmin())
            .as("seeded guest must not be a main admin")
            .isFalse();
    }

    @Test
    @DisplayName("should seed the main admin with forced password rotation when the default password is in effect")
    void run_seedsMainAdmin_withForcedRotation_onDefaultPassword() {
        // GIVEN — fresh database and the shipped default admin/admin credential
        when(userRepository.findByUsername(CurrentUserProvider.GUEST_USERNAME)).thenReturn(Optional.empty());
        when(userRepository.findByUsername(DEFAULT_USERNAME)).thenReturn(Optional.empty());
        when(userRepository.findByMainAdminTrue()).thenReturn(Optional.empty());

        // WHEN — the seeder runs
        userSeeder.run(new DefaultApplicationArguments());

        // THEN — the main admin is created with the encoded hash and flagged temporary
        final UserEntity admin = capturePersisted(DEFAULT_USERNAME);
        assertThat(admin.isMainAdmin())
            .as("seeded admin must be the main admin")
            .isTrue();
        assertThat(admin.getRole())
            .as("seeded admin must have ADMIN role")
            .isEqualTo(UserRole.ADMIN);
        assertThat(admin.getPasswordHash())
            .as("seeded admin must carry the encoded bootstrap password")
            .isEqualTo(ENCODED);
        assertThat(admin.isPasswordTemporary())
            .as("default-password admin must be forced to rotate on first login")
            .isTrue();
    }

    @Test
    @DisplayName("should seed the main admin without forced rotation when an operator-supplied password is used")
    void run_seedsMainAdmin_withoutForcedRotation_onCustomPassword() {
        // GIVEN — fresh database and a non-default operator password
        when(basicProperties.password()).thenReturn(CUSTOM_PASSWORD);
        when(userRepository.findByUsername(CurrentUserProvider.GUEST_USERNAME)).thenReturn(Optional.empty());
        when(userRepository.findByUsername(DEFAULT_USERNAME)).thenReturn(Optional.empty());
        when(userRepository.findByMainAdminTrue()).thenReturn(Optional.empty());

        // WHEN — the seeder runs
        userSeeder.run(new DefaultApplicationArguments());

        // THEN — the main admin keeps its operator password without forced rotation
        final UserEntity admin = capturePersisted(DEFAULT_USERNAME);
        assertThat(admin.isPasswordTemporary())
            .as("operator-supplied password must not force rotation")
            .isFalse();
    }

    @Test
    @DisplayName("should not create a second main admin when one already exists")
    void run_isNoOp_whenMainAdminAlreadyExists() {
        // GIVEN — guest exists and a main admin is already present
        final UserEntity existingAdmin = UserEntity.builder()
            .id(UUID.randomUUID())
            .username("root")
            .displayName("Root")
            .role(UserRole.ADMIN)
            .createdAt(Instant.now())
            .mainAdmin(true)
            .build();
        when(userRepository.findByUsername(CurrentUserProvider.GUEST_USERNAME))
            .thenReturn(Optional.of(guestRow()));
        when(userRepository.findByMainAdminTrue()).thenReturn(Optional.of(existingAdmin));

        // WHEN — the seeder runs again (idempotency)
        userSeeder.run(new DefaultApplicationArguments());

        // THEN — no new main admin is created and the existing one is left untouched
        verify(userRepository, never()).save(any(UserEntity.class));
        verify(userRepository, never()).findByUsername(DEFAULT_USERNAME);
    }

    @Test
    @DisplayName("should promote an existing same-named user to main admin and keep their existing password")
    void run_promotesExistingUser_keepsExistingPassword() {
        // GIVEN — no main admin yet, but a same-named non-admin user with a real password
        final UserEntity collidingUser = UserEntity.builder()
            .id(UUID.randomUUID())
            .username(DEFAULT_USERNAME)
            .displayName("Pre-existing")
            .role(UserRole.USER)
            .createdAt(Instant.now())
            .passwordHash("$2a$existinghash")
            .passwordTemporary(false)
            .mainAdmin(false)
            .build();
        when(userRepository.findByUsername(CurrentUserProvider.GUEST_USERNAME))
            .thenReturn(Optional.of(guestRow()));
        when(userRepository.findByUsername(DEFAULT_USERNAME)).thenReturn(Optional.of(collidingUser));
        when(userRepository.findByMainAdminTrue()).thenReturn(Optional.empty(), Optional.of(collidingUser));

        // WHEN — the seeder runs
        userSeeder.run(new DefaultApplicationArguments());

        // THEN — the user is promoted but keeps its own password hash (not overwritten)
        assertThat(collidingUser.isMainAdmin())
            .as("colliding user must be promoted to main admin")
            .isTrue();
        assertThat(collidingUser.getRole())
            .as("colliding user must be promoted to ADMIN role")
            .isEqualTo(UserRole.ADMIN);
        assertThat(collidingUser.getPasswordHash())
            .as("existing password hash must be preserved during promotion")
            .isEqualTo("$2a$existinghash");
        verify(userRepository).save(collidingUser);
    }

    @Test
    @DisplayName("should backfill the bootstrap password with forced rotation when promoting a user with a blank hash")
    void run_promotesExistingUser_backfillsBlankPassword_withForcedRotation() {
        // GIVEN — no main admin, a same-named user with a blank password hash, default credential
        final UserEntity blankUser = UserEntity.builder()
            .id(UUID.randomUUID())
            .username(DEFAULT_USERNAME)
            .displayName("Blank")
            .role(UserRole.USER)
            .createdAt(Instant.now())
            .passwordHash(null)
            .mainAdmin(false)
            .build();
        when(userRepository.findByUsername(CurrentUserProvider.GUEST_USERNAME))
            .thenReturn(Optional.of(guestRow()));
        when(userRepository.findByUsername(DEFAULT_USERNAME)).thenReturn(Optional.of(blankUser));
        when(userRepository.findByMainAdminTrue()).thenReturn(Optional.empty(), Optional.of(blankUser));

        // WHEN — the seeder runs
        userSeeder.run(new DefaultApplicationArguments());

        // THEN — the bootstrap password is backfilled and rotation is forced
        assertThat(blankUser.getPasswordHash())
            .as("blank password must be backfilled with the encoded bootstrap password")
            .isEqualTo(ENCODED);
        assertThat(blankUser.isPasswordTemporary())
            .as("backfilled default password must force rotation on first login")
            .isTrue();
    }

    @Test
    @DisplayName("should reject the reserved 'guest' username as the configured main admin")
    void createMainAdmin_rejectsReservedGuestUsername() {
        // GIVEN — guest row exists, no main admin yet, and the operator misconfigured
        //         basic.auth.username to the reserved anonymous-fallback name
        when(basicProperties.username()).thenReturn(CurrentUserProvider.GUEST_USERNAME);
        when(userRepository.findByUsername(CurrentUserProvider.GUEST_USERNAME))
            .thenReturn(Optional.of(guestRow()));
        when(userRepository.findByMainAdminTrue()).thenReturn(Optional.empty());

        // WHEN — the seeder runs / THEN — the reserved name is rejected fail-fast
        assertThatThrownBy(() -> userSeeder.run(new DefaultApplicationArguments()))
            .as("seeding the reserved 'guest' username as main admin must fail fast")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(CurrentUserProvider.GUEST_USERNAME);
    }

    @Test
    @DisplayName("should refresh the guest cache after seeding")
    void run_refreshesGuestCache() {
        // GIVEN — fresh database
        when(userRepository.findByUsername(CurrentUserProvider.GUEST_USERNAME)).thenReturn(Optional.empty());
        when(userRepository.findByUsername(DEFAULT_USERNAME)).thenReturn(Optional.empty());
        when(userRepository.findByMainAdminTrue()).thenReturn(Optional.empty());

        // WHEN — the seeder runs
        userSeeder.run(new DefaultApplicationArguments());

        // THEN — the guest cache is refreshed so anonymous access sees the persisted row
        verify(currentUserProvider).refreshGuestCache();
    }

    private UserEntity capturePersisted(String username) {
        final ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        final List<UserEntity> saved = captor.getAllValues();
        return saved.stream()
            .filter(u -> username.equals(u.getUsername()))
            .reduce((first, second) -> second)
            .orElseThrow(() -> new AssertionError("No persisted user with username: " + username));
    }

    private UserEntity guestRow() {
        return UserEntity.builder()
            .id(UUID.randomUUID())
            .username(CurrentUserProvider.GUEST_USERNAME)
            .displayName("Guest")
            .role(UserRole.GUEST)
            .createdAt(Instant.now())
            .mainAdmin(false)
            .build();
    }
}
