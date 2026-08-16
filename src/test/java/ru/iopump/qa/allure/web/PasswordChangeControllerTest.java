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
import org.springframework.security.authentication.BadCredentialsException;
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
import ru.iopump.qa.allure.service.PasswordChangeService;
import ru.iopump.qa.allure.service.WeakPasswordException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    value = PasswordChangeController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import({WebExceptionAdvice.class, WebConfiguration.class, RedirectConfiguration.class,
    JteAutoConfiguration.class, GlobalModelAdvice.class})
@EnableConfigurationProperties({AllureProperties.class, BasicProperties.class})
class PasswordChangeControllerTest {

    private static final String PASSWORD_PATH = "/app/profile/password";
    private static final String PASSWORD_PATH_FORCED = "/app/profile/password?forced=true";
    private static final String REDIRECT_REPORTS = "/app/reports";
    private static final String FLASH_KEY = "flash";
    private static final String FLASH_LEVEL_KEY = "level";
    private static final String FLASH_MESSAGE_KEY = "message";
    private static final String LEVEL_SUCCESS = "success";
    private static final String LEVEL_ERROR = "error";
    private static final String CURRENT_PASSWORD = "oldpass1";
    private static final String NEW_PASSWORD = "newpass99";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PasswordChangeService passwordChangeService;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    @MockitoBean
    private ApiTokenService apiTokenService;

    private UserEntity authenticatedUser;

    @BeforeEach
    void setUp() {
        authenticatedUser = UserEntity.builder()
            .id(UUID.randomUUID())
            .username("alice")
            .displayName("Alice")
            .role(UserRole.USER)
            .createdAt(Instant.now())
            .passwordTemporary(false)
            .blocked(false)
            .build();
        when(currentUserProvider.current()).thenReturn(authenticatedUser);
    }

    @Test
    @DisplayName("should render password change form with 200 when GET /app/profile/password as authenticated user")
    void get_rendersForm_whenAuthenticated() throws Exception {
        // GIVEN — authenticated user; forced=false (default)

        // WHEN — GET the form
        MvcResult result = mockMvc.perform(get(PASSWORD_PATH))
            .andExpect(status().isOk())
            .andReturn();

        // THEN — response body contains the change-password form elements
        final String body = result.getResponse().getContentAsString();
        assertThat(body)
            .as("password change page must contain the current-password input")
            .contains("currentPassword");
        assertThat(body)
            .as("password change page must contain the new-password input")
            .contains("newPassword");
        assertThat(body)
            .as("password change page must contain the confirm-password input")
            .contains("confirmPassword");
    }

    @Test
    @DisplayName("should redirect to /app/reports with success flash when POST /app/profile/password succeeds")
    void post_changesPassword_andRedirects() throws Exception {
        // GIVEN — service accepts the change silently
        doNothing().when(passwordChangeService).change(
            eq(authenticatedUser.getId()), eq("oldpass1"), eq("newpass99"));

        // WHEN — POST the form with matching passwords
        MvcResult result = mockMvc.perform(post(PASSWORD_PATH)
                .param("currentPassword", "oldpass1")
                .param("newPassword", "newpass99")
                .param("confirmPassword", "newpass99"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(REDIRECT_REPORTS))
            .andReturn();

        // THEN — success flash and service invoked
        final Map<?, ?> flash = extractFlashMap(result, FLASH_KEY);
        assertThat(flash.get(FLASH_LEVEL_KEY))
            .as("successful password change must set 'success' flash level")
            .isEqualTo(LEVEL_SUCCESS);
        verify(passwordChangeService).change(
            eq(authenticatedUser.getId()), eq("oldpass1"), eq("newpass99"));
    }

    @Test
    @DisplayName("should redirect to /app/profile/password when POST with forced path and temp password is changed")
    void post_whenTempAndForced_redirectsToProfile() throws Exception {
        // GIVEN — user with temp password flag set; service accepts the change
        final UserEntity tempUser = UserEntity.builder()
            .id(UUID.randomUUID())
            .username("tempuser")
            .displayName("Temp User")
            .role(UserRole.USER)
            .createdAt(Instant.now())
            .passwordTemporary(true)
            .blocked(false)
            .build();
        when(currentUserProvider.current()).thenReturn(tempUser);
        doNothing().when(passwordChangeService).change(
            eq(tempUser.getId()), any(), any());

        // WHEN — POST the form; after success the controller redirects to /app/reports
        mockMvc.perform(post(PASSWORD_PATH)
                .param("currentPassword", "tmppass1")
                .param("newPassword", "goodpass99")
                .param("confirmPassword", "goodpass99"))
            // THEN — redirect goes to /app/reports (which triggers ForcePasswordChangeFilter
            //         to pass through because passwordTemporary is cleared by the service)
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(REDIRECT_REPORTS));

        verify(passwordChangeService).change(
            eq(tempUser.getId()), eq("tmppass1"), eq("goodpass99"));
    }

    @Test
    @DisplayName("should redirect to self with error flash and NOT call service when new password and confirmation differ")
    void post_confirmationMismatch_redirectsSelfWithErrorAndNoServiceCall() throws Exception {
        // GIVEN — authenticated user (from setUp); confirmPassword differs from newPassword

        // WHEN — POST with mismatching confirmation
        MvcResult result = mockMvc.perform(post(PASSWORD_PATH)
                .param("currentPassword", CURRENT_PASSWORD)
                .param("newPassword", NEW_PASSWORD)
                .param("confirmPassword", "different99"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(PASSWORD_PATH))
            .andReturn();

        // THEN — error flash about the mismatch; service never invoked
        final Map<?, ?> flash = extractFlashMap(result, FLASH_KEY);
        assertThat(flash.get(FLASH_LEVEL_KEY))
            .as("confirmation mismatch must set 'error' flash level")
            .isEqualTo(LEVEL_ERROR);
        assertThat(flash.get(FLASH_MESSAGE_KEY))
            .as("error flash must mention that the confirmation does not match")
            .asString()
            .contains("do not match");
        verify(passwordChangeService, never()).change(any(), any(), any());
    }

    @Test
    @DisplayName("should redirect to self with error flash when the current password is wrong (BadCredentialsException)")
    void post_badCredentials_redirectsSelfWithErrorFlash() throws Exception {
        // GIVEN — service rejects the wrong current password with BadCredentialsException
        doThrow(new BadCredentialsException("Current password is incorrect."))
            .when(passwordChangeService).change(eq(authenticatedUser.getId()), eq(CURRENT_PASSWORD), eq(NEW_PASSWORD));

        // WHEN — POST with matching confirmation but wrong current password
        MvcResult result = mockMvc.perform(post(PASSWORD_PATH)
                .param("currentPassword", CURRENT_PASSWORD)
                .param("newPassword", NEW_PASSWORD)
                .param("confirmPassword", NEW_PASSWORD))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(PASSWORD_PATH))
            .andReturn();

        // THEN — error flash from the BadCredentials handler (not a raw 500)
        final Map<?, ?> flash = extractFlashMap(result, FLASH_KEY);
        assertThat(flash.get(FLASH_LEVEL_KEY))
            .as("bad current password must set 'error' flash level instead of a raw 500")
            .isEqualTo(LEVEL_ERROR);
        assertThat(flash.get(FLASH_MESSAGE_KEY))
            .as("error flash must carry the BadCredentials message")
            .asString()
            .contains("incorrect");
    }

    @Test
    @DisplayName("should redirect to self with error flash when the new password is too weak (WeakPasswordException)")
    void post_weakPassword_redirectsSelfWithErrorFlash() throws Exception {
        // GIVEN — service rejects the new password with WeakPasswordException
        doThrow(new WeakPasswordException("Password must be at least 8 characters."))
            .when(passwordChangeService).change(eq(authenticatedUser.getId()), eq(CURRENT_PASSWORD), eq(NEW_PASSWORD));

        // WHEN — POST with matching confirmation but a weak new password
        MvcResult result = mockMvc.perform(post(PASSWORD_PATH)
                .param("currentPassword", CURRENT_PASSWORD)
                .param("newPassword", NEW_PASSWORD)
                .param("confirmPassword", NEW_PASSWORD))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(PASSWORD_PATH))
            .andReturn();

        // THEN — error flash from the WeakPassword handler
        final Map<?, ?> flash = extractFlashMap(result, FLASH_KEY);
        assertThat(flash.get(FLASH_LEVEL_KEY))
            .as("weak new password must set 'error' flash level instead of a raw 500")
            .isEqualTo(LEVEL_ERROR);
        assertThat(flash.get(FLASH_MESSAGE_KEY))
            .as("error flash must carry the WeakPassword message")
            .asString()
            .contains("at least 8 characters");
    }

    @Test
    @DisplayName("should redirect to self with error flash and NOT call service when the actor is an anonymous/guest user")
    void post_anonymousActor_redirectsSelfWithErrorAndNoServiceCall() throws Exception {
        // GIVEN — the resolved actor is the unpersisted guest fallback (no id)
        final UserEntity guest = UserEntity.builder()
            .username("guest")
            .displayName("Guest")
            .role(UserRole.GUEST)
            .build();
        when(currentUserProvider.current()).thenReturn(guest);

        // WHEN — POST a valid form
        MvcResult result = mockMvc.perform(post(PASSWORD_PATH)
                .param("currentPassword", CURRENT_PASSWORD)
                .param("newPassword", NEW_PASSWORD)
                .param("confirmPassword", NEW_PASSWORD))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(PASSWORD_PATH))
            .andReturn();

        // THEN — error flash about needing to sign in; service never invoked
        final Map<?, ?> flash = extractFlashMap(result, FLASH_KEY);
        assertThat(flash.get(FLASH_LEVEL_KEY))
            .as("anonymous actor must set 'error' flash level")
            .isEqualTo(LEVEL_ERROR);
        assertThat(flash.get(FLASH_MESSAGE_KEY))
            .as("error flash must explain that sign-in is required")
            .asString()
            .contains("signed in");
        verify(passwordChangeService, never()).change(any(), any(), any());
    }

    @Test
    @DisplayName("should redirect to self with error flash and NOT call service when a required password field is blank (F9 bind error)")
    void post_blankField_redirectsSelfWithErrorAndNoServiceCall() throws Exception {
        // GIVEN — currentPassword blank violates @NotBlank; the @Valid @ModelAttribute now has an
        // adjacent BindingResult, so the controller itself catches the error and emits a forced-aware
        // flash redirect (here: a non-forced user → bare self URL) instead of escalating to the
        // global WebExceptionAdvice and a raw 400.

        // WHEN — POST with a blank current password
        MvcResult result = mockMvc.perform(post(PASSWORD_PATH)
                .param("currentPassword", "")
                .param("newPassword", NEW_PASSWORD)
                .param("confirmPassword", NEW_PASSWORD))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(PASSWORD_PATH))
            .andReturn();

        // THEN — error flash from the in-controller binding check; service never invoked
        final Map<?, ?> flash = extractFlashMap(result, FLASH_KEY);
        assertThat(flash.get(FLASH_LEVEL_KEY))
            .as("blank required field must produce an 'error' flash level instead of a raw 400")
            .isEqualTo(LEVEL_ERROR);
        assertThat(flash.get(FLASH_MESSAGE_KEY))
            .as("error flash must mention the rejected form")
            .asString()
            .contains("Form rejected");
        verify(passwordChangeService, never()).change(any(), any(), any());
    }

    @Test
    @DisplayName("R2-8: should redirect to self WITH ?forced=true when a forced change fails on confirmation mismatch")
    void post_forcedConfirmationMismatch_keepsForcedBanner() throws Exception {
        // GIVEN — a forced rotation (?forced=true) submitted by an authenticated user whose
        //         confirmation does not match the new password
        // WHEN — POST with mismatching confirmation and the forced flag
        mockMvc.perform(post(PASSWORD_PATH)
                .param("forced", "true")
                .param("currentPassword", CURRENT_PASSWORD)
                .param("newPassword", NEW_PASSWORD)
                .param("confirmPassword", "different99"))
            // THEN — the error redirect retains ?forced=true so the rotation banner persists
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(PASSWORD_PATH_FORCED));

        verify(passwordChangeService, never()).change(any(), any(), any());
    }

    @Test
    @DisplayName("R2-8: should redirect to self WITH ?forced=true when a temp-password user's forced change fails on bad credentials")
    void post_forcedBadCredentials_keepsForcedBanner() throws Exception {
        // GIVEN — a temp-password (must-rotate) user whose wrong current password is rejected.
        //         The submitting form may omit ?forced, so the persisted passwordTemporary flag
        //         is the authoritative signal that the change is forced.
        final UserEntity tempUser = UserEntity.builder()
            .id(UUID.randomUUID())
            .username("tempuser")
            .displayName("Temp User")
            .role(UserRole.USER)
            .createdAt(Instant.now())
            .passwordTemporary(true)
            .blocked(false)
            .build();
        when(currentUserProvider.current()).thenReturn(tempUser);
        doThrow(new BadCredentialsException("Current password is incorrect."))
            .when(passwordChangeService).change(eq(tempUser.getId()), eq(CURRENT_PASSWORD), eq(NEW_PASSWORD));

        // WHEN — POST with a wrong current password and NO explicit forced param
        mockMvc.perform(post(PASSWORD_PATH)
                .param("currentPassword", CURRENT_PASSWORD)
                .param("newPassword", NEW_PASSWORD)
                .param("confirmPassword", NEW_PASSWORD))
            // THEN — the error redirect still carries ?forced=true (derived from passwordTemporary)
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(PASSWORD_PATH_FORCED));
    }

    @Test
    @DisplayName("R3: should redirect to self WITH ?forced=true when a forced change hits a bean-validation (blank field) error")
    void post_forcedBlankField_keepsForcedBanner() throws Exception {
        // GIVEN — a forced rotation (?forced=true) by an authenticated user whose currentPassword
        //         is blank, violating @NotBlank. The bean-validation error must be handled IN the
        //         controller (not escalate to the global advice) so ?forced=true is preserved.

        // WHEN — POST with a blank current password and the forced flag
        MvcResult result = mockMvc.perform(post(PASSWORD_PATH)
                .param("forced", "true")
                .param("currentPassword", "")
                .param("newPassword", NEW_PASSWORD)
                .param("confirmPassword", NEW_PASSWORD))
            // THEN — the error redirect retains ?forced=true so the rotation banner persists
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(PASSWORD_PATH_FORCED))
            .andReturn();

        // THEN — error flash naming the rejected form; service never invoked
        final Map<?, ?> flash = extractFlashMap(result, FLASH_KEY);
        assertThat(flash.get(FLASH_LEVEL_KEY))
            .as("blank required field on a forced change must set 'error' flash level")
            .isEqualTo(LEVEL_ERROR);
        assertThat(flash.get(FLASH_MESSAGE_KEY))
            .as("error flash must mention the rejected form")
            .asString()
            .contains("Form rejected");
        verify(passwordChangeService, never()).change(any(), any(), any());
    }

    @Test
    @DisplayName("R3: should redirect to self WITH ?forced=true when a temp-password user hits a bean-validation error with NO explicit forced param")
    void post_tempUserBlankField_keepsForcedBanner() throws Exception {
        // GIVEN — a temp-password (must-rotate) user submitting WITHOUT ?forced. The persisted
        //         passwordTemporary flag is the authoritative forced signal, so a bean-validation
        //         error must still preserve ?forced=true.
        final UserEntity tempUser = UserEntity.builder()
            .id(UUID.randomUUID())
            .username("tempuser")
            .displayName("Temp User")
            .role(UserRole.USER)
            .createdAt(Instant.now())
            .passwordTemporary(true)
            .blocked(false)
            .build();
        when(currentUserProvider.current()).thenReturn(tempUser);

        // WHEN — POST with a blank new password and NO explicit forced param
        mockMvc.perform(post(PASSWORD_PATH)
                .param("currentPassword", CURRENT_PASSWORD)
                .param("newPassword", "")
                .param("confirmPassword", NEW_PASSWORD))
            // THEN — the error redirect still carries ?forced=true (derived from passwordTemporary)
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(PASSWORD_PATH_FORCED));

        verify(passwordChangeService, never()).change(any(), any(), any());
    }

    @Test
    @DisplayName("R3: should redirect to self WITHOUT ?forced=true when a non-forced user hits a bean-validation (blank field) error")
    void post_nonForcedBlankField_doesNotAddForcedFlag() throws Exception {
        // GIVEN — an ordinary authenticated user (passwordTemporary=false from setUp), no forced param,
        //         a blank currentPassword violating @NotBlank.

        // WHEN — POST with a blank current password and no forced flag
        MvcResult result = mockMvc.perform(post(PASSWORD_PATH)
                .param("currentPassword", "")
                .param("newPassword", NEW_PASSWORD)
                .param("confirmPassword", NEW_PASSWORD))
            // THEN — the error redirect is the plain self URL (no spurious forced banner)
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(PASSWORD_PATH))
            .andReturn();

        // THEN — error flash naming the rejected form; service never invoked
        final Map<?, ?> flash = extractFlashMap(result, FLASH_KEY);
        assertThat(flash.get(FLASH_LEVEL_KEY))
            .as("non-forced blank field must set 'error' flash level")
            .isEqualTo(LEVEL_ERROR);
        assertThat(flash.get(FLASH_MESSAGE_KEY))
            .as("error flash must mention the rejected form")
            .asString()
            .contains("Form rejected");
        verify(passwordChangeService, never()).change(any(), any(), any());
    }

    @Test
    @DisplayName("R2-8: should redirect to self WITHOUT ?forced=true when a non-forced change fails (ordinary user, no temp password)")
    void post_nonForcedMismatch_doesNotAddForcedFlag() throws Exception {
        // GIVEN — an ordinary authenticated user (passwordTemporary=false from setUp), no forced param
        // WHEN — POST with mismatching confirmation
        mockMvc.perform(post(PASSWORD_PATH)
                .param("currentPassword", CURRENT_PASSWORD)
                .param("newPassword", NEW_PASSWORD)
                .param("confirmPassword", "different99"))
            // THEN — the error redirect is the plain self URL (no spurious forced banner)
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(PASSWORD_PATH));

        verify(passwordChangeService, never()).change(any(), any(), any());
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
