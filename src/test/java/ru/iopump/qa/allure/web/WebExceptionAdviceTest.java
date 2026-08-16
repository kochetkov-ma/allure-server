package ru.iopump.qa.allure.web;

import gg.jte.springframework.boot.autoconfigure.JteAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.iopump.qa.allure.config.RedirectConfiguration;
import ru.iopump.qa.allure.config.WebConfiguration;
import ru.iopump.qa.allure.entity.UserEntity;
import ru.iopump.qa.allure.entity.UserRole;
import ru.iopump.qa.allure.properties.AllureProperties;
import ru.iopump.qa.allure.security.CurrentUserProvider;
import ru.iopump.qa.allure.service.ApiTokenService;
import ru.iopump.qa.allure.service.JpaReportService;
import ru.iopump.qa.allure.service.ResultService;
import ru.iopump.qa.allure.service.UserManagementService;
import ru.iopump.qa.allure.service.UserNotFoundException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WebExceptionAdvice} translation tests.
 * <ul>
 *   <li>BUG-002 regression — {@code ConstraintViolationException}/bind errors become a flash redirect.</li>
 *   <li>R2-13 — a malformed UUID on bulk-delete ({@code MethodArgumentTypeMismatchException})
 *       becomes a flash redirect, not a raw 400 (covers reports and results).</li>
 *   <li>R2-14 — the typed {@code UserNotFoundException} becomes a flash redirect, while an
 *       unexpected {@code IllegalArgumentException} is NOT swallowed (it propagates as a 500).</li>
 * </ul>
 */
@WebMvcTest(
    value = {ResultsWebController.class, ReportsWebController.class, AdminUsersController.class},
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import({WebExceptionAdvice.class, WebConfiguration.class, RedirectConfiguration.class, JteAutoConfiguration.class})
@EnableConfigurationProperties(AllureProperties.class)
class WebExceptionAdviceTest {

    private static final String REDIRECT_RESULTS = "/app/results";
    private static final String REDIRECT_REPORTS = "/app/reports";
    private static final String REDIRECT_ADMIN_USERS = "/app/admin/users";
    private static final String FLASH_KEY = "flash";
    private static final String FLASH_LEVEL_KEY = "level";
    private static final String FLASH_MESSAGE_KEY = "message";
    private static final String LEVEL_ERROR = "error";
    private static final String SAFE_NOT_FOUND_MESSAGE = "The requested user could not be found.";
    private static final String INTERNAL_BUG_MESSAGE = "internal-state-leak: null index 7";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResultService resultService;

    @MockitoBean
    private JpaReportService reportService;

    @MockitoBean
    private ApiTokenService apiTokenService;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    @MockitoBean
    private UserManagementService userManagementService;

    @Test
    @DisplayName("should redirect to /app/results with error flash when POST /generate has blank reportPath and invalid UUID")
    void constraintViolationOnGenerate_redirectsWithErrorFlash() throws Exception {
        // GIVEN — blank reportPath and non-UUID resultUuids → @Valid on GenerateForm causes BindingResult errors
        // The controller handles errors via its own BindingResult check and redirects with error flash.

        // WHEN — form post with blank reportPath (empty list), invalid UUID, deleteResults=false
        MvcResult result = mockMvc.perform(post("/app/results/generate")
                .contentType("application/x-www-form-urlencoded")
                .param("reportPath", "")
                .param("resultUuids", "fake-uuid")
                .param("deleteResults", "false"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(REDIRECT_RESULTS))
            .andReturn();

        // THEN — flash attribute under key "flash" must carry level=error
        final Map<?, ?> flash = extractFlash(result);
        assertThat((String) flash.get(FLASH_LEVEL_KEY))
            .as("flash level must be 'error' for rejected form")
            .isEqualTo(LEVEL_ERROR);
        assertThat((String) flash.get(FLASH_MESSAGE_KEY))
            .as("flash message must describe the rejection")
            .contains("rejected");
    }

    @Test
    @DisplayName("R2-13: should redirect to /app/results with error flash when results bulk-delete receives a malformed UUID (not a raw 400)")
    void malformedUuidOnResultsBulkDelete_redirectsWithErrorFlash() throws Exception {
        // GIVEN — a tampered POST carrying a non-UUID 'uuids' value → MethodArgumentTypeMismatchException

        // WHEN — bulk-delete with a malformed UUID token
        MvcResult result = mockMvc.perform(post("/app/results/bulk-delete")
                .param("uuids", "not-a-uuid"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(REDIRECT_RESULTS))
            .andReturn();

        // THEN — the type-mismatch handler translates it into an error flash, not a whitelabel 400
        final Map<?, ?> flash = extractFlash(result);
        assertThat((String) flash.get(FLASH_LEVEL_KEY))
            .as("malformed UUID must produce an 'error' flash level instead of a raw 400")
            .isEqualTo(LEVEL_ERROR);
        assertThat((String) flash.get(FLASH_MESSAGE_KEY))
            .as("error flash must name the rejected parameter without leaking the raw value")
            .contains("uuids");
    }

    @Test
    @DisplayName("R2-13: should redirect to /app/reports with error flash when reports bulk-delete receives a malformed UUID (not a raw 400)")
    void malformedUuidOnReportsBulkDelete_redirectsWithErrorFlash() throws Exception {
        // GIVEN — a tampered POST carrying a non-UUID 'uuids' value → MethodArgumentTypeMismatchException

        // WHEN — bulk-delete with a malformed UUID token
        MvcResult result = mockMvc.perform(post("/app/reports/bulk-delete")
                .param("uuids", "1234-not-a-uuid"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(REDIRECT_REPORTS))
            .andReturn();

        // THEN — the type-mismatch handler translates it into an error flash, not a whitelabel 400
        final Map<?, ?> flash = extractFlash(result);
        assertThat((String) flash.get(FLASH_LEVEL_KEY))
            .as("malformed UUID must produce an 'error' flash level instead of a raw 400")
            .isEqualTo(LEVEL_ERROR);
        assertThat((String) flash.get(FLASH_MESSAGE_KEY))
            .as("error flash must name the rejected parameter")
            .contains("uuids");
    }

    @Test
    @DisplayName("R2-14: should redirect to /app/admin/users with error flash when an admin mutation raises UserNotFoundException (not a 500)")
    void userNotFoundOnAdminMutation_redirectsWithErrorFlash() throws Exception {
        // GIVEN — an admin actor and a stale user id whose mutation raises the typed not-found
        final UUID staleId = UUID.randomUUID();
        when(currentUserProvider.current()).thenReturn(adminActor());
        when(userManagementService.grantAdmin(eq(staleId), any()))
            .thenThrow(new UserNotFoundException(SAFE_NOT_FOUND_MESSAGE));

        // WHEN — POST the grant-admin mutation against the stale id
        MvcResult result = mockMvc.perform(post("/app/admin/users/" + staleId + "/grant-admin"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(REDIRECT_ADMIN_USERS))
            .andReturn();

        // THEN — the typed handler produces a safe error flash instead of a whitelabel 500
        final Map<?, ?> flash = extractFlash(result);
        assertThat((String) flash.get(FLASH_LEVEL_KEY))
            .as("UserNotFoundException must produce an 'error' flash level instead of a raw 500")
            .isEqualTo(LEVEL_ERROR);
        assertThat((String) flash.get(FLASH_MESSAGE_KEY))
            .as("error flash must carry the human-safe not-found message")
            .isEqualTo(SAFE_NOT_FOUND_MESSAGE);
    }

    @Test
    @DisplayName("R2-14: should NOT swallow an unexpected IllegalArgumentException — it propagates as a genuine 500")
    void unexpectedIllegalArgument_isNotSwallowed_propagatesAs500() throws Exception {
        // GIVEN — an admin actor and a service that fails with a genuine programming-bug IAE
        final UUID id = UUID.randomUUID();
        when(currentUserProvider.current()).thenReturn(adminActor());
        when(userManagementService.grantAdmin(eq(id), any()))
            .thenThrow(new IllegalArgumentException(INTERNAL_BUG_MESSAGE));

        // WHEN/THEN — the advice must NOT translate a bare IllegalArgumentException; it surfaces
        // as a real failure (no flash redirect masking the internal message).
        assertThatThrownBy(() -> mockMvc.perform(post("/app/admin/users/" + id + "/grant-admin")))
            .as("an unexpected IllegalArgumentException must propagate, not be masked as a flash redirect")
            .satisfies(thrown -> assertThat(rootCause(thrown))
                .as("root cause must be the original IllegalArgumentException")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(INTERNAL_BUG_MESSAGE));
    }

    ///// helpers /////

    private static UserEntity adminActor() {
        return UserEntity.builder()
            .id(UUID.randomUUID())
            .username("admin")
            .displayName("Admin")
            .role(UserRole.ADMIN)
            .createdAt(Instant.now())
            .passwordTemporary(false)
            .blocked(false)
            .build();
    }

    private static Map<?, ?> extractFlash(MvcResult result) {
        final Object flashValue = result.getFlashMap().get(FLASH_KEY);
        assertThat(flashValue)
            .as("flash attribute under key '%s' must be present in redirect attributes", FLASH_KEY)
            .isInstanceOf(Map.class);
        return (Map<?, ?>) flashValue;
    }

    private static Throwable rootCause(Throwable thrown) {
        Throwable current = thrown;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
