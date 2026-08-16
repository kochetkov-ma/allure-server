package ru.iopump.qa.allure.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import ru.iopump.qa.allure.properties.AllureProperties;
import ru.iopump.qa.allure.service.SystemSettingsService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the report-content gate under a NON-DEFAULT {@code allure.reports.dir}
 * (review finding H2). {@code RedirectConfiguration} serves reports from the configured dir, so a
 * matcher hardcoded to {@code /allure/**} left every report anonymously readable once an operator
 * changed the storage prefix, even with {@code requireApiAuth=true}. The matchers are now derived
 * from the same property, so both toggle states behave exactly as on the default layout.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:allure-custom-reports-dir-test-db;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "allure.reports.dir=build/h2-custom-reports/",
    "app.security.require-api-auth=false",
    "basic.auth.enable=false",
    "gg.jte.development-mode=false",
    "gg.jte.use-precompiled-templates=true"
})
class CustomReportsDirAuthIntegrationTest {

    private static final String REPORT_FOLDER = "h2-custom-dir";
    private static final String INDEX_HTML = "<html><body>h2</body></html>";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SystemSettingsService systemSettingsService;

    @Autowired
    private AllureProperties allureProperties;

    private Path seededReportDir;
    private String reportRequestPath;

    @AfterEach
    void cleanup() throws IOException {
        if (seededReportDir != null && Files.exists(seededReportDir)) {
            Files.deleteIfExists(seededReportDir.resolve("index.html"));
            Files.deleteIfExists(seededReportDir);
        }
        // Always restore the default OPEN posture so a failing test does not leak state.
        systemSettingsService.updateRequireApiAuth(false, "test-actor");
    }

    @Test
    @DisplayName("should return 401 for anonymous GET of a custom-dir report when requireApiAuth is true")
    void customReportsDir_anonymousBlocked_whenRequireApiAuthTrue() throws Exception {
        // GIVEN — reports are served from a non-default dir and the runtime toggle is on
        assertThat(allureProperties.reports().dir())
            .as("test precondition: the reports dir must not be the default /allure prefix")
            .isEqualTo("build/h2-custom-reports/");
        seedReportIndex();
        systemSettingsService.updateRequireApiAuth(true, "test-actor");

        // WHEN — anonymous GET of the report content on its configured path
        // THEN — 401: the derived matcher gates the custom prefix exactly like /allure/**
        mockMvc.perform(get(reportRequestPath))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("should return 200 for anonymous GET of a custom-dir report when requireApiAuth is false")
    void customReportsDir_anonymousAllowed_whenRequireApiAuthFalse() throws Exception {
        // GIVEN — the same non-default dir with the toggle off (default open posture)
        seedReportIndex();
        assertThat(systemSettingsService.isRequireApiAuth())
            .as("test precondition: requireApiAuth must be false for the guest-fallback case")
            .isFalse();

        // WHEN — anonymous GET of the report content
        // THEN — 200: the guest read-only fallback still serves it, so the 401 above is a
        // security decision and not a missing route
        mockMvc.perform(get(reportRequestPath))
            .andExpect(status().isOk());
    }

    ///// helpers /////

    private void seedReportIndex() throws IOException {
        final Path reportsRoot = allureProperties.reports().dirPath();
        seededReportDir = reportsRoot.resolve(REPORT_FOLDER);
        Files.createDirectories(seededReportDir);
        Files.writeString(seededReportDir.resolve("index.html"), INDEX_HTML, StandardCharsets.UTF_8);

        reportRequestPath = "/" + allureProperties.reports().dir() + REPORT_FOLDER + "/index.html";
    }
}
