package ru.iopump.qa.allure.web;

import gg.jte.springframework.boot.autoconfigure.JteAutoConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.iopump.qa.allure.config.RedirectConfiguration;
import ru.iopump.qa.allure.config.WebConfiguration;
import ru.iopump.qa.allure.entity.UserEntity;
import ru.iopump.qa.allure.entity.UserRole;
import ru.iopump.qa.allure.properties.AllureProperties;
import ru.iopump.qa.allure.properties.BasicProperties;
import ru.iopump.qa.allure.security.CurrentUserProvider;
import ru.iopump.qa.allure.service.ApiTokenService;
import ru.iopump.qa.allure.service.MainAdminProtectionException;
import ru.iopump.qa.allure.service.SelfProtectionException;
import ru.iopump.qa.allure.service.UserManagementService;
import ru.iopump.qa.allure.service.UserNotFoundException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @WebMvcTest} slice for {@link AdminUsersController}.
 * <p>
 * Security is excluded so the slice tests focus on HTTP behavior: flash messages,
 * redirect targets, and service interaction. The {@code @PreAuthorize("hasRole('ADMIN')")}
 * annotation is verified by a dedicated unit test ({@link #list_requiresAdminRole})
 * which inspects the annotation directly, avoiding the CGLIB-proxy / MVC-mapping
 * conflict that {@code @EnableMethodSecurity} introduces in a {@code @WebMvcTest} slice.
 * End-to-end authorization coverage is provided in the integration tests.
 */
@WebMvcTest(
    value = AdminUsersController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import({WebExceptionAdvice.class, WebConfiguration.class, RedirectConfiguration.class,
    JteAutoConfiguration.class, GlobalModelAdvice.class})
@EnableConfigurationProperties({AllureProperties.class, BasicProperties.class})
class AdminUsersControllerTest {

    private static final String USERS_PATH = "/app/admin/users";
    private static final String FLASH_KEY = "flash";
    private static final String FLASH_JUST_CREATED = "justCreatedUser";
    private static final String FLASH_JUST_RESET = "justResetUser";
    private static final String FLASH_LEVEL_KEY = "level";
    private static final String LEVEL_SUCCESS = "success";
    private static final String LEVEL_ERROR = "error";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserManagementService userManagementService;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    @MockitoBean
    private ApiTokenService apiTokenService;

    private UserEntity adminActor;
    private UserEntity mainAdminEntity;
    private UserEntity regularUser;

    @BeforeEach
    void setUp() {
        adminActor = UserEntity.builder()
            .id(UUID.randomUUID())
            .username("admin")
            .displayName("Admin")
            .role(UserRole.ADMIN)
            .createdAt(Instant.now())
            .mainAdmin(false)
            .build();

        mainAdminEntity = UserEntity.builder()
            .id(UUID.randomUUID())
            .username("root")
            .displayName("Root")
            .role(UserRole.ADMIN)
            .createdAt(Instant.now())
            .mainAdmin(true)
            .build();

        regularUser = UserEntity.builder()
            .id(UUID.randomUUID())
            .username("bob")
            .displayName("Bob")
            .role(UserRole.USER)
            .createdAt(Instant.now())
            .mainAdmin(false)
            .build();

        when(currentUserProvider.current()).thenReturn(adminActor);
    }

    @Test
    @DisplayName("should carry @PreAuthorize('hasRole(ADMIN)') on class level ensuring USER role is rejected with 403")
    void list_requiresAdminRole() {
        // GIVEN — the controller class
        final PreAuthorize annotation = AdminUsersController.class.getAnnotation(PreAuthorize.class);

        // THEN — annotation is present with the correct ADMIN role expression
        assertThat(annotation)
            .as("AdminUsersController must be annotated with @PreAuthorize at class level")
            .isNotNull();
        assertThat(annotation.value())
            .as("@PreAuthorize expression must enforce ADMIN role to ensure USER role is rejected with 403")
            .isEqualTo("hasRole('ADMIN')");
    }

    @Test
    @DisplayName("should redirect to /app/admin/users with justCreatedUser flash when POST creates a user")
    void createUser_succeeds_andReturnsTempPasswordFlash() throws Exception {
        // GIVEN — service creates the user and returns a temp password
        final UserEntity newUser = UserEntity.builder()
            .id(UUID.randomUUID())
            .username("charlie")
            .displayName("Charlie")
            .role(UserRole.USER)
            .createdAt(Instant.now())
            .passwordTemporary(true)
            .build();
        when(userManagementService.createUser(eq("charlie"), anyString()))
            .thenReturn(new UserManagementService.TempPasswordResult(newUser, "tempPass123X"));

        // WHEN — POST create-user form
        MvcResult result = mockMvc.perform(post(USERS_PATH)
                .param("username", "charlie")
                .param("displayName", "Charlie"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(USERS_PATH))
            .andReturn();

        // THEN — justCreatedUser flash carries the temporary password
        final Map<?, ?> justCreated = extractFlashMap(result, FLASH_JUST_CREATED);
        assertThat(justCreated.get("username"))
            .as("justCreatedUser flash must carry the new username")
            .isEqualTo("charlie");
        assertThat(justCreated.get("temporaryPassword"))
            .as("justCreatedUser flash must carry the plain temporary password exactly once")
            .isEqualTo("tempPass123X");

        final Map<?, ?> flash = extractFlashMap(result, FLASH_KEY);
        assertThat(flash.get(FLASH_LEVEL_KEY))
            .as("user creation success flash level must be 'success'")
            .isEqualTo(LEVEL_SUCCESS);
        verify(userManagementService).createUser(eq("charlie"), anyString());
    }

    @Test
    @DisplayName("should return 403-equivalent redirect with error flash when DELETE targets the main admin")
    void deleteMainAdmin_returns403() throws Exception {
        // GIVEN — service throws MainAdminProtectionException for the main admin
        doThrow(new MainAdminProtectionException("The main administrator cannot be deleted."))
            .when(userManagementService).delete(eq(mainAdminEntity.getId()), any(UserEntity.class));

        // WHEN — DELETE request to the main admin's endpoint
        MvcResult result = mockMvc.perform(delete(USERS_PATH + "/" + mainAdminEntity.getId()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(USERS_PATH))
            .andReturn();

        // THEN — error flash contains the protection message
        final Map<?, ?> flash = extractFlashMap(result, FLASH_KEY);
        assertThat(flash.get(FLASH_LEVEL_KEY))
            .as("main-admin delete protection must produce an 'error' flash level")
            .isEqualTo(LEVEL_ERROR);
        assertThat(flash.get("message"))
            .as("error flash must include 'main administrator' text from the exception")
            .asString()
            .contains("main administrator");
    }

    @Test
    @DisplayName("should redirect with error flash when DELETE targets the actor themselves")
    void deleteSelf_returns403() throws Exception {
        // GIVEN — service throws SelfProtectionException because actor == target
        doThrow(new SelfProtectionException("You cannot delete your own account."))
            .when(userManagementService).delete(eq(adminActor.getId()), any(UserEntity.class));

        // WHEN — DELETE self
        MvcResult result = mockMvc.perform(delete(USERS_PATH + "/" + adminActor.getId()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(USERS_PATH))
            .andReturn();

        // THEN — error flash produced by handleSelfProtection
        final Map<?, ?> flash = extractFlashMap(result, FLASH_KEY);
        assertThat(flash.get(FLASH_LEVEL_KEY))
            .as("self-delete protection must produce an 'error' flash level")
            .isEqualTo(LEVEL_ERROR);
        assertThat(flash.get("message"))
            .as("error flash message must mention account deletion")
            .asString()
            .contains("delete");
    }

    @Test
    @DisplayName("should redirect with success flash and blocked=true after POST /{id}/block")
    void blockUser_persistsFlag() throws Exception {
        // GIVEN — service blocks the target user and returns it
        final UserEntity blocked = UserEntity.builder()
            .id(regularUser.getId())
            .username(regularUser.getUsername())
            .displayName(regularUser.getDisplayName())
            .role(UserRole.USER)
            .createdAt(regularUser.getCreatedAt())
            .blocked(true)
            .build();
        when(userManagementService.block(eq(regularUser.getId()), any(UserEntity.class)))
            .thenReturn(blocked);

        // WHEN — POST block action
        MvcResult result = mockMvc.perform(post(USERS_PATH + "/" + regularUser.getId() + "/block"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(USERS_PATH))
            .andReturn();

        // THEN — success flash mentioning the blocked user
        final Map<?, ?> flash = extractFlashMap(result, FLASH_KEY);
        assertThat(flash.get(FLASH_LEVEL_KEY))
            .as("block action must produce a 'success' flash level")
            .isEqualTo(LEVEL_SUCCESS);
        assertThat(flash.get("message"))
            .as("success message must reference the blocked username")
            .asString()
            .contains("bob");
        verify(userManagementService).block(eq(regularUser.getId()), any(UserEntity.class));
    }

    @Test
    @DisplayName("should redirect with justResetUser flash carrying temporary password on POST /{id}/reset-password")
    void resetPassword_flashHasTempPlain() throws Exception {
        // GIVEN — service generates a temp password for the target user
        when(userManagementService.resetPassword(eq(regularUser.getId()), any(UserEntity.class)))
            .thenReturn(new UserManagementService.TempPasswordResult(regularUser, "ResetTmp9z"));

        // WHEN — POST reset-password
        MvcResult result = mockMvc.perform(post(USERS_PATH + "/" + regularUser.getId() + "/reset-password"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(USERS_PATH))
            .andReturn();

        // THEN — justResetUser flash carries the temporary password
        final Map<?, ?> justReset = extractFlashMap(result, FLASH_JUST_RESET);
        assertThat(justReset.get("username"))
            .as("justResetUser flash must carry the target's username")
            .isEqualTo("bob");
        assertThat(justReset.get("temporaryPassword"))
            .as("justResetUser flash must carry the newly generated plain temporary password")
            .isEqualTo("ResetTmp9z");

        final Map<?, ?> flash = extractFlashMap(result, FLASH_KEY);
        assertThat(flash.get(FLASH_LEVEL_KEY))
            .as("reset-password success flash level must be 'success'")
            .isEqualTo(LEVEL_SUCCESS);
        verify(userManagementService).resetPassword(eq(regularUser.getId()), any(UserEntity.class));
    }

    @Test
    @DisplayName("should redirect with error flash and NOT call service when POST creates a user with an invalid (space) username")
    void createUser_invalidUsername_redirectsWithErrorFlash() throws Exception {
        // GIVEN — username containing a space violates @Pattern("[A-Za-z0-9._-]+");
        // the @Valid @ModelAttribute has no BindingResult so Spring raises
        // MethodArgumentNotValidException, handled by WebExceptionAdvice.

        // WHEN — POST create-user form with an invalid username
        MvcResult result = mockMvc.perform(post(USERS_PATH)
                .param("username", "bad name")
                .param("displayName", "Bad Name"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(USERS_PATH))
            .andReturn();

        // THEN — error flash from the bind-exception handler, service never invoked
        final Map<?, ?> flash = extractFlashMap(result, FLASH_KEY);
        assertThat(flash.get(FLASH_LEVEL_KEY))
            .as("invalid username must produce an 'error' flash level instead of a raw 400")
            .isEqualTo(LEVEL_ERROR);
        assertThat(flash.get("message"))
            .as("error flash must mention the rejected form")
            .asString()
            .contains("Form rejected");
        verify(userManagementService, never()).createUser(anyString(), anyString());
    }

    @Test
    @DisplayName("should redirect with error flash when DELETE targets a stale UUID that no longer exists")
    void deleteStaleUser_redirectsWithErrorFlash() throws Exception {
        // GIVEN — service throws the typed UserNotFoundException for a missing UUID
        // (stale grid / double-submit). WebExceptionAdvice translates it to a flash error.
        final UUID staleId = UUID.randomUUID();
        doThrow(new UserNotFoundException("User not found: " + staleId))
            .when(userManagementService).delete(eq(staleId), any(UserEntity.class));

        // WHEN — DELETE the stale id
        MvcResult result = mockMvc.perform(delete(USERS_PATH + "/" + staleId))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(USERS_PATH))
            .andReturn();

        // THEN — error flash carrying the not-found message, no 500
        final Map<?, ?> flash = extractFlashMap(result, FLASH_KEY);
        assertThat(flash.get(FLASH_LEVEL_KEY))
            .as("stale-UUID delete must produce an 'error' flash level instead of a raw 500")
            .isEqualTo(LEVEL_ERROR);
        assertThat(flash.get("message"))
            .as("error flash must surface the 'User not found' detail")
            .asString()
            .contains("User not found");
    }

    @Test
    @DisplayName("should redirect with error flash when POST /{id}/grant-admin targets a stale UUID")
    void grantAdminStaleUser_redirectsWithErrorFlash() throws Exception {
        // GIVEN — service throws the typed UserNotFoundException for a missing UUID
        final UUID staleId = UUID.randomUUID();
        doThrow(new UserNotFoundException("User not found: " + staleId))
            .when(userManagementService).grantAdmin(eq(staleId), any(UserEntity.class));

        // WHEN — POST grant-admin on the stale id
        MvcResult result = mockMvc.perform(post(USERS_PATH + "/" + staleId + "/grant-admin"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(USERS_PATH))
            .andReturn();

        // THEN — error flash, not a 500
        final Map<?, ?> flash = extractFlashMap(result, FLASH_KEY);
        assertThat(flash.get(FLASH_LEVEL_KEY))
            .as("stale-UUID grant-admin must produce an 'error' flash level instead of a raw 500")
            .isEqualTo(LEVEL_ERROR);
        assertThat(flash.get("message"))
            .as("error flash must surface the 'User not found' detail")
            .asString()
            .contains("User not found");
    }

    ///// helpers /////

    private static Map<?, ?> extractFlashMap(MvcResult result, String key) {
        final Object flashValue = result.getFlashMap().get(key);
        assertThat(flashValue)
            .as("flash attribute under key '%s' must be present in redirect attributes", key)
            .isInstanceOf(Map.class);
        return (Map<?, ?>) flashValue;
    }
}
