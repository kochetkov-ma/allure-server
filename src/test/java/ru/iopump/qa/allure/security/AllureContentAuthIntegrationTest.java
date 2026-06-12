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
 * Integration test for the {@code /allure/**} (generated report content) authorization
 * gate (review finding F9). Report content is routed through the SAME
 * {@code apiAuthorizationManager} rule as {@code /api/**}, so the runtime
 * {@code requireApiAuth} toggle governs both:
 * <ul>
 *   <li>{@code requireApiAuth=true}  — anonymous GET {@code /allure/**} is blocked (401).</li>
 *   <li>{@code requireApiAuth=false} — guest fallback serves report content (200).</li>
 * </ul>
 * A throwaway {@code index.html} is seeded under the configured reports dir so the
 * permitted case resolves to a concrete 200 (proving the request passed the security
 * gate AND reached the resource handler), then removed in {@link #cleanup()}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:allure-content-auth-test-db;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.security.require-api-auth=false",
    "basic.auth.enable=false",
    "gg.jte.development-mode=false",
    "gg.jte.use-precompiled-templates=true"
})
class AllureContentAuthIntegrationTest {

    private static final String REPORT_FOLDER = "f9-content-auth";
    private static final String INDEX_HTML = "<html><body>f9</body></html>";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SystemSettingsService systemSettingsService;

    @Autowired
    private AllureProperties allureProperties;

    private Path seededReportDir;
    private String allureRequestPath;

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
    @DisplayName("should return 200 for anonymous GET /allure/** when requireApiAuth is false (guest fallback)")
    void allureContent_anonymousAllowed_whenRequireApiAuthFalse() throws Exception {
        // GIVEN — requireApiAuth is false (bootstrap default) and a real report index exists on disk
        assertThat(systemSettingsService.isRequireApiAuth())
            .as("test precondition: requireApiAuth must be false for the guest-fallback case")
            .isFalse();
        seedReportIndex();

        // WHEN — anonymous GET of the report content
        // THEN — the security gate permits anonymous (guest fallback) and the resource handler
        // resolves the seeded index → concrete 200
        mockMvc.perform(get(allureRequestPath))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should return 401 for anonymous GET /allure/** when requireApiAuth is true")
    void allureContent_anonymousBlocked_whenRequireApiAuthTrue() throws Exception {
        // GIVEN — a real report index on disk AND requireApiAuth toggled to true at runtime
        seedReportIndex();
        systemSettingsService.updateRequireApiAuth(true, "test-actor");
        assertThat(systemSettingsService.isRequireApiAuth())
            .as("requireApiAuth must be true after the update")
            .isTrue();

        // WHEN — anonymous GET of the same report content
        // THEN — 401: the gate denies before the resource handler runs (report content is locked
        // down by the SAME rule as /api/**), even though the file exists
        mockMvc.perform(get(allureRequestPath))
            .andExpect(status().isUnauthorized());
    }

    ///// helpers /////

    private void seedReportIndex() throws IOException {
        // Resolve the exact dir the RedirectConfiguration resource handler serves from, so the
        // /allure/<dir>/<folder>/ request maps to this seeded index.html.
        final Path reportsRoot = allureProperties.reports().dirPath();
        seededReportDir = reportsRoot.resolve(REPORT_FOLDER);
        Files.createDirectories(seededReportDir);
        Files.writeString(seededReportDir.resolve("index.html"), INDEX_HTML, StandardCharsets.UTF_8);

        // RedirectConfiguration maps "/<dir>/**"; a directory request resolves to its index.html.
        allureRequestPath = "/" + allureProperties.reports().dir() + REPORT_FOLDER + "/index.html";
    }
}
