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
import ru.iopump.qa.allure.service.JpaReportService;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    value = ReportsWebController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import({WebExceptionAdvice.class, WebConfiguration.class, RedirectConfiguration.class, JteAutoConfiguration.class})
@EnableConfigurationProperties(AllureProperties.class)
class ReportsWebControllerTest {

    private static final String VALID_UUID = "a1913f97-a5b5-469b-8459-d7dd66ef55bc";
    private static final String REDIRECT_REPORTS = "/app/reports";
    private static final String FLASH_KEY = "flash";
    private static final String FLASH_LEVEL_KEY = "level";
    private static final String FLASH_MESSAGE_KEY = "message";
    private static final String LEVEL_SUCCESS = "success";
    private static final String LEVEL_ERROR = "error";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JpaReportService reportService;

    // ─────────────────────────── upload ───────────────────────────────────────

    @Test
    @DisplayName("should redirect with success flash when POST /upload with valid zip and non-blank path")
    void uploadHappyPath() throws Exception {
        // GIVEN — valid path and non-empty zip; service creates and returns the report entity
        ReportEntity entity = new ReportEntity();
        entity.setUuid(UUID.fromString(VALID_UUID));
        when(reportService.uploadReport(any(), any(), any(), any())).thenReturn(entity);

        MockMultipartFile zipFile = new MockMultipartFile(
            "file", "report.zip", "application/zip", "PK dummy".getBytes()
        );

        // WHEN — multipart upload with path param
        MvcResult result = mockMvc.perform(multipart("/app/reports/upload")
                .file(zipFile)
                .param("path", "branch/job"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(REDIRECT_REPORTS))
            .andReturn();

        // THEN — success flash and service called once
        Map<?, ?> flash = extractFlash(result);
        assertThat(flash.get(FLASH_LEVEL_KEY))
            .as("upload success: flash level must be 'success'")
            .isEqualTo(LEVEL_SUCCESS);
        verify(reportService).uploadReport(any(), any(), any(), any());
    }

    // ─────────────────────────── delete ───────────────────────────────────────

    @Test
    @DisplayName("should redirect with success flash when DELETE /app/reports/{uuid} with valid UUID")
    void deleteHappyPath() throws Exception {
        // GIVEN — deleteByUuid returns the deleted entity
        ReportEntity entity = new ReportEntity();
        entity.setUuid(UUID.fromString(VALID_UUID));
        when(reportService.deleteByUuid(VALID_UUID)).thenReturn(entity);

        // WHEN — DELETE request with valid UUID
        MvcResult result = mockMvc.perform(delete("/app/reports/{uuid}", VALID_UUID))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(REDIRECT_REPORTS))
            .andReturn();

        // THEN — success flash and service called once
        Map<?, ?> flash = extractFlash(result);
        assertThat(flash.get(FLASH_LEVEL_KEY))
            .as("delete success: flash level must be 'success'")
            .isEqualTo(LEVEL_SUCCESS);
        verify(reportService).deleteByUuid(VALID_UUID);
    }

    @Test
    @DisplayName("should redirect with error flash when DELETE /app/reports/{uuid} with value rejected by service")
    void deleteInvalidUuid() throws Exception {
        // GIVEN — service throws IllegalArgumentException for an invalid UUID value
        when(reportService.deleteByUuid("bad-value")).thenThrow(new IllegalArgumentException("bad UUID format"));

        // WHEN — DELETE request where the service rejects the value
        MvcResult result = mockMvc.perform(delete("/app/reports/{uuid}", "bad-value"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(REDIRECT_REPORTS))
            .andReturn();

        // THEN — error flash surfaced by the controller's catch(IllegalArgumentException)
        Map<?, ?> flash = extractFlash(result);
        assertThat(flash.get(FLASH_LEVEL_KEY))
            .as("rejected delete: flash level must be 'error'")
            .isEqualTo(LEVEL_ERROR);
        verify(reportService).deleteByUuid("bad-value");
    }

    // ─────────────────────────── grid OOB ─────────────────────────────────────

    @Test
    @DisplayName("should return 200 with OOB counter fragment when GET /app/reports/grid?q=xyz filters to empty list")
    void gridOobCounter() throws Exception {
        // GIVEN — service returns an empty collection so the filtered list is 0
        when(reportService.getAll()).thenReturn(Collections.emptyList());

        // WHEN — GET grid fragment with a query that matches nothing
        MvcResult result = mockMvc.perform(get("/app/reports/grid").param("q", "xyz"))
            .andExpect(status().isOk())
            .andReturn();

        // THEN — OOB counter paragraph rendered with the correct id and hx-swap-oob marker
        String body = result.getResponse().getContentAsString();
        assertThat(body)
            .as("grid fragment must contain the OOB reports-count element id")
            .contains("id=\"reports-count\"");
        assertThat(body)
            .as("grid fragment must carry hx-swap-oob attribute for htmx OOB swap")
            .contains("hx-swap-oob=\"true\"");
        assertThat(body)
            .as("grid fragment must show 0 report(s) when list is empty")
            .contains("0 report(s)");
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
