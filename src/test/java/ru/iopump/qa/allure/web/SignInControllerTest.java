package ru.iopump.qa.allure.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.iopump.qa.allure.security.CurrentUserProvider;
import ru.iopump.qa.allure.service.ApiTokenService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @WebMvcTest} slice for {@link SignInController}. Security is excluded so the slice
 * focuses on the redirect contract: once the handler is reached (authentication already
 * enforced by {@code SecurityConfiguration}), it bounces to the reports page.
 */
@WebMvcTest(
    value = SignInController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class SignInControllerTest {

    private static final String SIGNIN_PATH = "/app/signin";
    private static final String REDIRECT_REPORTS = "/app/reports";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiTokenService apiTokenService;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    @Test
    @DisplayName("should redirect to /app/reports when GET /app/signin is reached")
    void signIn_redirectsToReports() throws Exception {
        // GIVEN — the sign-in entry point (authentication already implied)

        // WHEN — GET /app/signin
        // THEN — 3xx redirect to the reports landing page
        mockMvc.perform(get(SIGNIN_PATH))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(REDIRECT_REPORTS));
    }
}
