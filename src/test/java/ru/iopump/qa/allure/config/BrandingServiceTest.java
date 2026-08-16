package ru.iopump.qa.allure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BrandingService")
class BrandingServiceTest {

    private static final String ORIGINAL_INDEX = """
        <!DOCTYPE html>
        <html><head>
            <title>Allure Report</title>
            <link rel="icon" href="favicon.ico">
        </head><body><div id="content"></div></body></html>
        """;

    private final BrandingService service = new BrandingService();

    @Test
    @DisplayName("should inject branding assets and patch head when applied to a fresh report")
    void should_inject_branding_assets_and_patch_head_on_first_apply(@TempDir Path dir) throws Exception {
        // GIVEN a fresh Allure report directory
        Files.writeString(dir.resolve("index.html"), ORIGINAL_INDEX, StandardCharsets.UTF_8);

        // WHEN branding is applied once
        service.applyBranding(dir);

        // THEN the three branding assets are written next to index.html
        assertThat(Files.exists(dir.resolve("brew-brand.css")))
            .as("brew-brand.css marker created").isTrue();
        assertThat(Files.exists(dir.resolve("brew-brand.js")))
            .as("brew-brand.js created").isTrue();
        assertThat(Files.exists(dir.resolve("favicon.svg")))
            .as("favicon.svg created").isTrue();

        // THEN index.html references all branding assets injected before </head>
        String patched = Files.readString(dir.resolve("index.html"), StandardCharsets.UTF_8);
        assertThat(patched)
            .as("index.html references branding assets injected before </head>")
            .contains("<link rel=\"icon\" href=\"favicon.svg\" type=\"image/svg+xml\">")
            .contains("<link rel=\"stylesheet\" href=\"brew-brand.css\">")
            .contains("<script defer src=\"brew-brand.js\"></script>");
        assertThat(patched.indexOf("brew-brand.css"))
            .as("branding markup is injected before the closing </head> tag")
            .isLessThan(patched.indexOf("</head>"));
    }

    @Test
    @DisplayName("should be a no-op when re-applied to an already branded report")
    void should_be_idempotent_when_applied_twice(@TempDir Path dir) throws Exception {
        // GIVEN an already-branded report
        Files.writeString(dir.resolve("index.html"), ORIGINAL_INDEX, StandardCharsets.UTF_8);
        service.applyBranding(dir);
        String afterFirst = Files.readString(dir.resolve("index.html"), StandardCharsets.UTF_8);

        // WHEN branding is re-applied
        service.applyBranding(dir);

        // THEN index.html is byte-for-byte identical (no duplicate <link>/<script>)
        String afterSecond = Files.readString(dir.resolve("index.html"), StandardCharsets.UTF_8);
        assertThat(afterSecond)
            .as("second apply produces identical html (idempotent)")
            .isEqualTo(afterFirst);
    }

    @Test
    @DisplayName("should skip a directory that has no index.html")
    void should_skip_directories_without_index_html(@TempDir Path dir) throws Exception {
        // GIVEN a directory without index.html (e.g. nested 'history' dir)

        // WHEN branding is applied
        service.applyBranding(dir);

        // THEN nothing is written to the non-report directory
        try (var entries = Files.list(dir)) {
            assertThat(entries.count())
                .as("nothing written to non-report directory")
                .isEqualTo(0L);
        }
    }
}
