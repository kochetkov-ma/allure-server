package ru.iopump.qa.allure.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;
import ru.iopump.qa.allure.entity.ReportEntity;
import ru.iopump.qa.allure.model.ReportGenerateRequest;
import ru.iopump.qa.allure.model.ReportSpec;
import ru.iopump.qa.allure.properties.AllureProperties;
import ru.iopump.qa.allure.security.CurrentUserProvider;
import ru.iopump.qa.allure.service.ApiTokenService;
import ru.iopump.qa.allure.service.JpaReportService;
import ru.iopump.qa.allure.service.ResultService;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @WebMvcTest} slice for {@link AllureReportController}. Security is excluded
 * (endpoint authorization is covered by the security integration suite); the RFC 7807
 * {@link GlobalExceptionHandler} is imported explicitly so ProblemDetail translation
 * still applies inside the narrowed slice.
 */
@WebMvcTest(
    controllers = AllureReportController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import(GlobalExceptionHandler.class)
@EnableConfigurationProperties(AllureProperties.class)
class AllureReportControllerTest {

    private static final String EXISTING_UUID = "a1913f97-a5b5-469b-8459-d7dd66ef55bc";
    private static final String MISSING_UUID = "b2a24f08-b6c6-57a3-9561-def178a77ce0";
    private static final String MALFORMED_UUID = "not-a-uuid";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JpaReportService reportService;

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

    // ─────────────────────────────────── A1 tests ──────────────────────────────────────

    @Test
    @DisplayName("should return 204 No Content when deleting existing report by UUID")
    void deleteReport_existingUuid_returns204() throws Exception {
        // GIVEN – deleteByUuid returns the deleted entity; returning a stub satisfies the mock
        when(reportService.deleteByUuid(EXISTING_UUID)).thenReturn(new ReportEntity());

        // WHEN
        MvcResult result = mockMvc.perform(delete("/api/report/{uuid}", EXISTING_UUID))
            .andExpect(status().isNoContent())
            .andReturn();

        // THEN
        assertThat(result.getResponse().getContentLength())
            .as("response body must be empty for 204")
            .isEqualTo(0);
        verify(reportService).deleteByUuid(EXISTING_UUID);
    }

    @Test
    @DisplayName("should return 404 ProblemDetail when deleting report with unknown UUID")
    void deleteReport_missingUuid_returns404ProblemDetail() throws Exception {
        // GIVEN
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Report '" + MISSING_UUID + "' not found"))
            .when(reportService).deleteByUuid(MISSING_UUID);

        // WHEN
        MvcResult result = mockMvc.perform(delete("/api/report/{uuid}", MISSING_UUID))
            .andExpect(status().isNotFound())
            .andReturn();

        // THEN
        String body = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getContentType())
            .as("content-type must be application/problem+json")
            .contains("application/problem+json");

        @SuppressWarnings("unchecked")
        Map<String, Object> problem = objectMapper.readValue(body, Map.class);
        assertThat(problem)
            .as("ProblemDetail must contain status=404")
            .containsEntry("status", 404);
    }

    @Test
    @DisplayName("should return 400 ProblemDetail with errors when UUID path variable is malformed")
    void deleteReport_malformedUuid_returns400ProblemDetail() throws Exception {
        // GIVEN – MALFORMED_UUID does not match UUID_PATTERN, no service mock needed

        // WHEN
        MvcResult result = mockMvc.perform(delete("/api/report/{uuid}", MALFORMED_UUID))
            .andExpect(status().isBadRequest())
            .andReturn();

        // THEN
        String body = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getContentType())
            .as("content-type must be application/problem+json")
            .contains("application/problem+json");

        @SuppressWarnings("unchecked")
        Map<String, Object> problem = objectMapper.readValue(body, Map.class);
        assertThat(problem)
            .as("ProblemDetail must contain status=400")
            .containsEntry("status", 400);
        assertThat(problem)
            .as("ProblemDetail must contain errors entry for the path parameter violation")
            .containsKey("errors");
    }

    @Test
    @DisplayName("should return 200 and empty list when deleting all reports (bulk delete regression)")
    void deleteAll_noParams_returns200EmptyList() throws Exception {
        // GIVEN
        when(reportService.deleteAll()).thenReturn(Collections.emptyList());

        // WHEN
        MvcResult result = mockMvc.perform(delete("/api/report"))
            .andExpect(status().isOk())
            .andReturn();

        // THEN
        String body = result.getResponse().getContentAsString();
        assertThat(body)
            .as("response body must be a JSON array (empty list)")
            .isEqualTo("[]");
        verify(reportService).deleteAll();
    }

    // ─────────────────────────────────── A2 tests ──────────────────────────────────────

    @Test
    @DisplayName("should return 400 ProblemDetail with errors.results when POST /api/report with empty results list")
    void generateReport_emptyResults_returns400WithErrorsMap() throws Exception {
        // GIVEN
        ReportSpec spec = new ReportSpec();
        spec.setPath(new String[]{"branch", "job"});
        ReportGenerateRequest req = new ReportGenerateRequest();
        req.setReportSpec(spec);
        req.setResults(Collections.emptyList());

        // WHEN
        MvcResult result = mockMvc.perform(post("/api/report")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andReturn();

        // THEN
        String body = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getContentType())
            .as("error response must be application/problem+json")
            .contains("application/problem+json");

        @SuppressWarnings("unchecked")
        Map<String, Object> problem = objectMapper.readValue(body, Map.class);
        assertThat(problem)
            .as("ProblemDetail must contain status=400")
            .containsEntry("status", 400);
        assertThat(problem)
            .as("ProblemDetail must contain errors entry")
            .containsKey("errors");

        @SuppressWarnings("unchecked")
        Map<String, Object> errors = (Map<String, Object>) problem.get("errors");
        assertThat(errors)
            .as("errors map must reference the 'results' field")
            .containsKey("results");
    }

    @Test
    @DisplayName("should return 400 ProblemDetail with path-related error when POST /api/report with null reportSpec.path")
    void generateReport_nullReportSpecPath_returns400WithErrorsMap() throws Exception {
        // GIVEN – path intentionally null → @NotEmpty violation on reportSpec.path
        ReportSpec spec = new ReportSpec();
        ReportGenerateRequest req = new ReportGenerateRequest();
        req.setReportSpec(spec);
        req.setResults(Collections.singletonList(EXISTING_UUID));

        // WHEN
        MvcResult result = mockMvc.perform(post("/api/report")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andReturn();

        // THEN
        String body = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getContentType())
            .as("error response must be application/problem+json")
            .contains("application/problem+json");

        @SuppressWarnings("unchecked")
        Map<String, Object> problem = objectMapper.readValue(body, Map.class);
        assertThat(problem)
            .as("ProblemDetail must contain status=400")
            .containsEntry("status", 400);
        assertThat(problem)
            .as("ProblemDetail must contain errors entry")
            .containsKey("errors");

        @SuppressWarnings("unchecked")
        Map<String, Object> errors = (Map<String, Object>) problem.get("errors");
        assertThat(errors.keySet())
            .as("errors map must reference a path-related field (reportSpec.path or pathSegmentsNotBlank)")
            .anySatisfy(key -> assertThat(key).as("field name must contain 'path'").containsIgnoringCase("path"));
    }

    @Test
    @DisplayName("should return 400 ProblemDetail when POST /api/report with blank segment in reportSpec.path")
    void generateReport_blankPathSegment_returns400WithErrorsMap() throws Exception {
        // GIVEN – blank segment "" violates @AssertTrue isPathSegmentsNotBlank
        ReportSpec spec = new ReportSpec();
        spec.setPath(new String[]{"foo", "", "bar"});
        ReportGenerateRequest req = new ReportGenerateRequest();
        req.setReportSpec(spec);
        req.setResults(Collections.singletonList(EXISTING_UUID));

        // WHEN
        MvcResult result = mockMvc.perform(post("/api/report")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andReturn();

        // THEN
        String body = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getContentType())
            .as("error response must be application/problem+json")
            .contains("application/problem+json");

        @SuppressWarnings("unchecked")
        Map<String, Object> problem = objectMapper.readValue(body, Map.class);
        assertThat(problem)
            .as("ProblemDetail must contain status=400")
            .containsEntry("status", 400);
        assertThat(problem)
            .as("ProblemDetail must contain errors entry")
            .containsKey("errors");
    }
}
