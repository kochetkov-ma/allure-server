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
import ru.iopump.qa.allure.service.SystemSettingsService;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @WebMvcTest} slice for {@link AdminSettingsController}. Security is excluded so the
 * slice focuses on HTTP behaviour: the GET render, the {@code requireApiAuth} toggle including
 * the absent-checkbox → {@code false} default binding, and the flash wiring. The class-level
 * {@code @PreAuthorize("hasRole('ADMIN')")} admin gate is verified by a dedicated annotation
 * test (mirroring {@code AdminUsersControllerTest}) to avoid the CGLIB-proxy / MVC-mapping
 * conflict {@code @EnableMethodSecurity} introduces in a slice.
 */
@WebMvcTest(
    value = AdminSettingsController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import({WebExceptionAdvice.class, WebConfiguration.class, RedirectConfiguration.class,
    JteAutoConfiguration.class, GlobalModelAdvice.class})
@EnableConfigurationProperties({AllureProperties.class, BasicProperties.class})
class AdminSettingsControllerTest {

    private static final String SETTINGS_PATH = "/app/admin/settings";
    private static final String TOGGLE_PATH = "/app/admin/settings/require-api-auth";
    private static final String FLASH_KEY = "flash";
    private static final String FLASH_LEVEL_KEY = "level";
    private static final String FLASH_MESSAGE_KEY = "message";
    private static final String LEVEL_SUCCESS = "success";
    private static final String ADMIN_USERNAME = "admin";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SystemSettingsService systemSettingsService;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    @MockitoBean
    private ApiTokenService apiTokenService;

    private UserEntity adminActor;

    @BeforeEach
    void setUp() {
        adminActor = UserEntity.builder()
            .id(UUID.randomUUID())
            .username(ADMIN_USERNAME)
            .displayName("Admin")
            .role(UserRole.ADMIN)
            .createdAt(Instant.now())
            .build();
        when(currentUserProvider.current()).thenReturn(adminActor);
    }

    @Test
    @DisplayName("should carry @PreAuthorize('hasRole(ADMIN)') at class level so non-admins are rejected with 403")
    void controller_requiresAdminRole() {
        // GIVEN — the controller class
        final PreAuthorize annotation = AdminSettingsController.class.getAnnotation(PreAuthorize.class);

        // THEN — the ADMIN gate is present
        assertThat(annotation)
            .as("AdminSettingsController must be annotated with @PreAuthorize at class level")
            .isNotNull();
        assertThat(annotation.value())
            .as("@PreAuthorize expression must enforce the ADMIN role")
            .isEqualTo("hasRole('ADMIN')");
    }

    @Test
    @DisplayName("should render the settings page with 200 when GET /app/admin/settings")
    void index_rendersSettingsPage() throws Exception {
        // GIVEN — a current settings snapshot
        when(systemSettingsService.current())
            .thenReturn(new SystemSettingsService.Snapshot(false, Instant.now(), ADMIN_USERNAME));

        // WHEN — GET the settings page
        MvcResult result = mockMvc.perform(get(SETTINGS_PATH))
            .andExpect(status().isOk())
            .andReturn();

        // THEN — the page renders the system-settings heading
        final String body = result.getResponse().getContentAsString();
        assertThat(body)
            .as("settings page must contain the System settings heading")
            .contains("System settings");
        verify(systemSettingsService).current();
    }

    @Test
    @DisplayName("should enable API auth and flash success when POST /require-api-auth with requireApiAuth=true")
    void toggle_enable_persistsTrueAndFlashesSuccess() throws Exception {
        // GIVEN — admin actor (from setUp)
        when(systemSettingsService.updateRequireApiAuth(true, ADMIN_USERNAME))
            .thenReturn(new SystemSettingsService.Snapshot(true, Instant.now(), ADMIN_USERNAME));

        // WHEN — POST the toggle with the checkbox checked
        MvcResult result = mockMvc.perform(post(TOGGLE_PATH)
                .param("requireApiAuth", "true"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(SETTINGS_PATH))
            .andReturn();

        // THEN — service persists true and success flash explains the new REQUIRED state
        verify(systemSettingsService).updateRequireApiAuth(true, ADMIN_USERNAME);
        final Map<?, ?> flash = extractFlashMap(result);
        assertThat(flash.get(FLASH_LEVEL_KEY))
            .as("enabling API auth must produce a 'success' flash level")
            .isEqualTo(LEVEL_SUCCESS);
        assertThat(flash.get(FLASH_MESSAGE_KEY))
            .as("success message must state API auth is now REQUIRED")
            .asString()
            .contains("REQUIRED");
    }

    @Test
    @DisplayName("should default requireApiAuth to false and flash success when POST /require-api-auth without the checkbox param")
    void toggle_absentCheckbox_defaultsToFalse() throws Exception {
        // GIVEN — admin actor; the unchecked HTML checkbox sends no requireApiAuth param,
        // so the controller binds defaultValue="false".
        when(systemSettingsService.updateRequireApiAuth(false, ADMIN_USERNAME))
            .thenReturn(new SystemSettingsService.Snapshot(false, Instant.now(), ADMIN_USERNAME));

        // WHEN — POST the toggle with no requireApiAuth param at all
        MvcResult result = mockMvc.perform(post(TOGGLE_PATH))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(SETTINGS_PATH))
            .andReturn();

        // THEN — service persists false (the absent-checkbox default) with an OPTIONAL message
        verify(systemSettingsService).updateRequireApiAuth(false, ADMIN_USERNAME);
        final Map<?, ?> flash = extractFlashMap(result);
        assertThat(flash.get(FLASH_LEVEL_KEY))
            .as("disabling API auth must produce a 'success' flash level")
            .isEqualTo(LEVEL_SUCCESS);
        assertThat(flash.get(FLASH_MESSAGE_KEY))
            .as("success message must state API auth is now OPTIONAL")
            .asString()
            .contains("OPTIONAL");
    }

    ///// helpers /////

    private static Map<?, ?> extractFlashMap(MvcResult result) {
        final Object flashValue = result.getFlashMap().get(FLASH_KEY);
        assertThat(flashValue)
            .as("flash attribute under key 'flash' must be present in redirect attributes")
            .isInstanceOf(Map.class);
        return (Map<?, ?>) flashValue;
    }
}
