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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.iopump.qa.allure.model.ResultResponse;
import ru.iopump.qa.allure.security.CurrentUserProvider;
import ru.iopump.qa.allure.service.ApiTokenService;
import ru.iopump.qa.allure.service.ResultService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

    private static final String MISSING_UUID = "11111111-1111-4111-8111-111111111111";
    private static final String EXISTING_UUID = "22222222-2222-4222-8222-222222222222";
    private static final String MALFORMED_UUID = "not-a-uuid";

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

    @Test
    @DisplayName("should return 404 ProblemDetail when DELETE /api/result with unknown UUID")
    void deleteResult_missingUuid_returns404ProblemDetail() throws Exception {
        // GIVEN
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Result '" + MISSING_UUID + "' not found"))
            .when(resultService).internalDeleteByUUID(MISSING_UUID);

        // WHEN
        MvcResult result = mockMvc.perform(delete("/api/result/{uuid}", MISSING_UUID))
            .andExpect(status().isNotFound())
            .andReturn();

        // THEN
        String body = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getContentType())
            .as("error response content-type must be application/problem+json")
            .contains("application/problem+json");

        @SuppressWarnings("unchecked")
        Map<String, Object> problem = objectMapper.readValue(body, Map.class);
        assertThat(problem)
            .as("ProblemDetail must contain status=404")
            .containsEntry("status", 404);
        assertThat((String) problem.get("detail"))
            .as("detail must name the missing result uuid")
            .contains(MISSING_UUID);
    }

    @Test
    @DisplayName("should return 400 ProblemDetail when DELETE /api/result UUID path variable is malformed")
    void deleteResult_malformedUuid_returns400ProblemDetail() throws Exception {
        // GIVEN - MALFORMED_UUID does not match UUID_PATTERN, so the service is never reached

        // WHEN
        MvcResult result = mockMvc.perform(delete("/api/result/{uuid}", MALFORMED_UUID))
            .andExpect(status().isBadRequest())
            .andReturn();

        // THEN
        assertThat(result.getResponse().getContentType())
            .as("error response content-type must be application/problem+json")
            .contains("application/problem+json");

        @SuppressWarnings("unchecked")
        Map<String, Object> problem = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        assertThat(problem)
            .as("ProblemDetail must contain status=400")
            .containsEntry("status", 400);
        verifyNoInteractions(resultService);
    }

    @Test
    @DisplayName("should return 200 with result metadata when DELETE /api/result with an existing UUID")
    void deleteResult_existingUuid_returns200WithMetadata() throws Exception {
        // GIVEN
        when(resultService.internalDeleteByUUID(EXISTING_UUID))
            .thenReturn(ResultResponse.builder().uuid(EXISTING_UUID).size(42L).build());

        // WHEN
        MvcResult result = mockMvc.perform(delete("/api/result/{uuid}", EXISTING_UUID))
            .andExpect(status().isOk())
            .andReturn();

        // THEN
        @SuppressWarnings("unchecked")
        Map<String, Object> response = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        assertThat(response)
            .as("response must echo the deleted result uuid")
            .containsEntry("uuid", EXISTING_UUID);
        verify(resultService).internalDeleteByUUID(EXISTING_UUID);
    }
}
