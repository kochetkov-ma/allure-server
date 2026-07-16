package ru.iopump.qa.allure.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.iopump.qa.allure.security.CurrentUserProvider;
import ru.iopump.qa.allure.service.ApiTokenService;
import ru.iopump.qa.allure.service.ResultService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @WebMvcTest} slice for {@link AllureResultController}. Security is excluded
 * (endpoint authorization is covered by the security integration suite); the RFC 7807
 * {@link GlobalExceptionHandler} is imported explicitly so ProblemDetail translation
 * still applies inside the narrowed slice.
 */
@WebMvcTest(
    controllers = AllureResultController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import(GlobalExceptionHandler.class)
class AllureResultControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ResultService resultService;

    // ApiTokenAuthenticationFilter is a @Component servlet Filter, auto-detected by the
    // @WebMvcTest slice scan even with Boot's security auto-configuration excluded; its
    // ApiTokenService constructor dependency must still be satisfiable.
    @MockitoBean
    private ApiTokenService apiTokenService;

    // GlobalModelAdvice is a @ControllerAdvice scanned application-wide by @WebMvcTest
    // (its basePackageClasses scoping only limits where it applies at runtime, not
    // whether it is instantiated); its CurrentUserProvider dependency must be mockable.
    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    // ─────────────────────────────────── A2 tests ──────────────────────────────────────

    @Test
    @DisplayName("should return 400 ProblemDetail when POST /api/result with zero-byte multipart file")
    void uploadResults_emptyFile_returns400ProblemDetail() throws Exception {
        // GIVEN
        MockMultipartFile emptyFile = new MockMultipartFile(
            "allureResults",
            "allure-results.zip",
            "application/zip",
            new byte[0]
        );

        // WHEN
        MvcResult result = mockMvc.perform(multipart("/api/result").file(emptyFile))
            .andExpect(status().isBadRequest())
            .andReturn();

        // THEN
        String body = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getContentType())
            .as("error response content-type must be application/problem+json")
            .contains("application/problem+json");

        @SuppressWarnings("unchecked")
        Map<String, Object> problem = objectMapper.readValue(body, Map.class);
        assertThat(problem)
            .as("ProblemDetail must contain status=400")
            .containsEntry("status", 400);
        assertThat((String) problem.get("detail"))
            .as("detail must mention the parameter name allureResults")
            .containsIgnoringCase("allureResults");
    }

    @Test
    @DisplayName("should return 400 ProblemDetail when POST /api/result with wrong content-type text/plain")
    void uploadResults_wrongContentType_returns400ProblemDetail() throws Exception {
        // GIVEN
        MockMultipartFile wrongType = new MockMultipartFile(
            "allureResults",
            "allure-results.zip",
            MediaType.TEXT_PLAIN_VALUE,
            "some text content".getBytes()
        );

        // WHEN
        MvcResult result = mockMvc.perform(multipart("/api/result").file(wrongType))
            .andExpect(status().isBadRequest())
            .andReturn();

        // THEN
        String body = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getContentType())
            .as("error response content-type must be application/problem+json")
            .contains("application/problem+json");

        @SuppressWarnings("unchecked")
        Map<String, Object> problem = objectMapper.readValue(body, Map.class);
        assertThat(problem)
            .as("ProblemDetail must contain status=400")
            .containsEntry("status", 400);
        assertThat((String) problem.get("detail"))
            .as("detail must mention Content-Type rejection")
            .containsIgnoringCase("content-type");
    }

    @Test
    @DisplayName("should return 400 ProblemDetail when POST /api/result with non-.zip file extension")
    void uploadResults_wrongFileExtension_returns400ProblemDetail() throws Exception {
        // GIVEN
        MockMultipartFile wrongExt = new MockMultipartFile(
            "allureResults",
            "allure-results.tar.gz",
            "application/zip",
            "fake zip content".getBytes()
        );

        // WHEN
        MvcResult result = mockMvc.perform(multipart("/api/result").file(wrongExt))
            .andExpect(status().isBadRequest())
            .andReturn();

        // THEN
        String body = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getContentType())
            .as("error response content-type must be application/problem+json")
            .contains("application/problem+json");

        @SuppressWarnings("unchecked")
        Map<String, Object> problem = objectMapper.readValue(body, Map.class);
        assertThat(problem)
            .as("ProblemDetail must contain status=400")
            .containsEntry("status", 400);
        assertThat((String) problem.get("detail"))
            .as("detail must mention .zip extension requirement")
            .containsIgnoringCase(".zip");
    }
}
