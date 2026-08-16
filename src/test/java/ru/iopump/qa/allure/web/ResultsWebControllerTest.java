package ru.iopump.qa.allure.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import gg.jte.springframework.boot.autoconfigure.JteAutoConfiguration;
import ru.iopump.qa.allure.config.RedirectConfiguration;
import ru.iopump.qa.allure.config.WebConfiguration;
import ru.iopump.qa.allure.entity.ReportEntity;
import ru.iopump.qa.allure.properties.AllureProperties;
import ru.iopump.qa.allure.security.CurrentUserProvider;
import ru.iopump.qa.allure.service.ApiTokenService;
import ru.iopump.qa.allure.service.JpaReportService;
import ru.iopump.qa.allure.service.ResultService;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    value = ResultsWebController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import({WebExceptionAdvice.class, WebConfiguration.class, RedirectConfiguration.class, JteAutoConfiguration.class})
@EnableConfigurationProperties(AllureProperties.class)
class ResultsWebControllerTest {

    private static final String VALID_UUID = "a1913f97-a5b5-469b-8459-d7dd66ef55bc";
    private static final String REDIRECT_RESULTS = "/app/results";
    private static final String FLASH_KEY = "flash";
    private static final String FLASH_LEVEL_KEY = "level";
    private static final String FLASH_MESSAGE_KEY = "message";
    private static final String LEVEL_SUCCESS = "success";
    private static final String LEVEL_ERROR = "error";

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

    // ─────────────────────────── upload ───────────────────────────────────────

    @Test
    @DisplayName("should redirect with success flash when POST /upload with valid zip file")
    void uploadHappyPath() throws Exception {
        // GIVEN — valid non-empty zip file and resultService returns a stored path
        MockMultipartFile zipFile = new MockMultipartFile(
            "file", "allure-results.zip", "application/zip", "PK dummy".getBytes()
        );
        Path storedPath = Path.of("allure/results/" + VALID_UUID);
        when(resultService.unzipAndStore(any())).thenReturn(storedPath);

        // WHEN — multipart upload
        MvcResult result = mockMvc.perform(multipart("/app/results/upload").file(zipFile))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(REDIRECT_RESULTS))
            .andReturn();

        // THEN — success flash and service invoked once
        Map<?, ?> flash = extractFlash(result);
        assertThat(flash.get(FLASH_LEVEL_KEY))
            .as("upload success: flash level must be 'success'")
            .isEqualTo(LEVEL_SUCCESS);
        verify(resultService).unzipAndStore(any());
    }

    @Test
    @DisplayName("should redirect with error flash and NOT call service when POST /upload with empty file")
    void uploadBlankFileNegative() throws Exception {
        // GIVEN — empty multipart file (0 bytes) — controller rejects before calling service
        MockMultipartFile emptyFile = new MockMultipartFile(
            "file", "allure-results.zip", "application/zip", new byte[0]
        );

        // WHEN — multipart upload with empty content
        MvcResult result = mockMvc.perform(multipart("/app/results/upload").file(emptyFile))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(REDIRECT_RESULTS))
            .andReturn();

        // THEN — error flash, service NOT called
        Map<?, ?> flash = extractFlash(result);
        assertThat(flash.get(FLASH_LEVEL_KEY))
            .as("empty upload: flash level must be 'error'")
            .isEqualTo(LEVEL_ERROR);
        verify(resultService, never()).unzipAndStore(any());
    }

    // ─────────────────────────── generate ─────────────────────────────────────

    @Test
    @DisplayName("should redirect with success flash when POST /generate with valid reportPath and UUID")
    void generateHappyPath() throws Exception {
        // GIVEN — valid UUID and reportPath; service and resultService respond successfully
        ReportEntity entity = new ReportEntity();
        entity.setUuid(UUID.fromString(VALID_UUID));
        when(reportService.generate(any(), any(), anyBoolean(), any(), any())).thenReturn(entity);
        when(resultService.getStoragePath()).thenReturn(Path.of("allure/results"));

        // WHEN — form post with valid reportPath, resultUuids, and deleteResults=false
        MvcResult result = mockMvc.perform(post("/app/results/generate")
                .contentType("application/x-www-form-urlencoded")
                .param("reportPath", "main")
                .param("resultUuids", VALID_UUID)
                .param("deleteResults", "false"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(REDIRECT_RESULTS))
            .andReturn();

        // THEN — success flash
        Map<?, ?> flash = extractFlash(result);
        assertThat(flash.get(FLASH_LEVEL_KEY))
            .as("generate success: flash level must be 'success'")
            .isEqualTo(LEVEL_SUCCESS);
        verify(reportService).generate(any(), any(), anyBoolean(), any(), any());
    }

    // ─────────────────────────── delete ───────────────────────────────────────

    @Test
    @DisplayName("should redirect with success flash when DELETE /app/results/{uuid} with valid UUID")
    void deleteHappyPath() throws Exception {
        // GIVEN — resultService.internalDeleteByUUID completes without exception (default mock behaviour)

        // WHEN — DELETE request with valid UUID path variable
        MvcResult result = mockMvc.perform(delete("/app/results/{uuid}", VALID_UUID))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(REDIRECT_RESULTS))
            .andReturn();

        // THEN — success flash and service called once
        Map<?, ?> flash = extractFlash(result);
        assertThat(flash.get(FLASH_LEVEL_KEY))
            .as("delete success: flash level must be 'success'")
            .isEqualTo(LEVEL_SUCCESS);
        verify(resultService).internalDeleteByUUID(VALID_UUID);
    }

    @Test
    @DisplayName("should redirect with error flash and NOT call service when DELETE /app/results/{uuid} with invalid UUID pattern")
    void deleteInvalidUuid() throws Exception {
        // GIVEN — path variable 'not-a-uuid' fails @Pattern(regexp = UUID_PATTERN)
        // WebExceptionAdvice intercepts ConstraintViolationException and redirects to /app/results

        // WHEN — DELETE with a non-UUID path variable
        MvcResult result = mockMvc.perform(delete("/app/results/{uuid}", "not-a-uuid"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(REDIRECT_RESULTS))
            .andReturn();

        // THEN — error flash from WebExceptionAdvice, service NOT called
        Map<?, ?> flash = extractFlash(result);
        assertThat(flash.get(FLASH_LEVEL_KEY))
            .as("invalid UUID: flash level must be 'error'")
            .isEqualTo(LEVEL_ERROR);
        assertThat((String) flash.get(FLASH_MESSAGE_KEY))
            .as("error message must mention the constraint rejection")
            .contains("Request rejected:");
        verify(resultService, never()).internalDeleteByUUID(any());
    }

    //// helpers ////

    @SuppressWarnings("unchecked")
    private static Map<?, ?> extractFlash(MvcResult result) {
        Object flashValue = result.getFlashMap().get(FLASH_KEY);
        assertThat(flashValue)
            .as("flash attribute under key 'flash' must be present in redirect attributes")
            .isInstanceOf(Map.class);
        return (Map<?, ?>) flashValue;
    }
}
