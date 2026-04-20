package ru.iopump.qa.allure.web;

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
import gg.jte.springframework.boot.autoconfigure.JteAutoConfiguration;
import ru.iopump.qa.allure.config.RedirectConfiguration;
import ru.iopump.qa.allure.config.WebConfiguration;
import ru.iopump.qa.allure.properties.AllureProperties;
import ru.iopump.qa.allure.service.JpaReportService;
import ru.iopump.qa.allure.service.ResultService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BUG-002 regression — {@link WebExceptionAdvice} must intercept {@code ConstraintViolationException}
 * from {@code @Validated} controller methods, add an error flash, and redirect to {@code /app/results}.
 */
@WebMvcTest(
    value = ResultsWebController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import({WebExceptionAdvice.class, WebConfiguration.class, RedirectConfiguration.class, JteAutoConfiguration.class})
@EnableConfigurationProperties(AllureProperties.class)
class WebExceptionAdviceTest {

    private static final String REDIRECT_RESULTS = "/app/results";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResultService resultService;

    @MockitoBean
    private JpaReportService reportService;

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
        Map<?, ?> flash = (Map<?, ?>) result.getFlashMap().get("flash");
        assertThat(flash)
            .as("flash map must be present in redirect attributes")
            .isNotNull();
        assertThat((String) flash.get("level"))
            .as("flash level must be 'error' for rejected form")
            .isEqualTo("error");
        assertThat((String) flash.get("message"))
            .as("flash message must describe the rejection")
            .contains("rejected");
    }
}
