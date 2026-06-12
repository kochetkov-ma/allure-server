package ru.iopump.qa.allure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.iopump.qa.allure.properties.AllureProperties;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BrandingSweepConfiguration")
class BrandingSweepConfigurationTest {

    private static final String ORIGINAL_INDEX = """
        <!DOCTYPE html>
        <html><head>
            <title>Allure Report</title>
        </head><body><div id="content"></div></body></html>
        """;

    private final BrandingService brandingService = new BrandingService();

    private static AllureProperties propertiesPointingAt(Path reportsRoot) {
        var reports = new AllureProperties.Reports(reportsRoot.toString(), "reports/", 20);
        return new AllureProperties(reports, "allure/results/", "yy/MM/dd HH:mm:ss", null, null, null);
    }

    @Test
    @DisplayName("should brand an unbranded report directory and skip an already branded one")
    void should_brand_unbranded_and_skip_branded(@TempDir Path reportsRoot) throws Exception {
        // GIVEN one unbranded report and one already-branded report under the reports root
        Path unbranded = Files.createDirectory(reportsRoot.resolve("unbranded"));
        Files.writeString(unbranded.resolve("index.html"), ORIGINAL_INDEX, StandardCharsets.UTF_8);

        Path branded = Files.createDirectory(reportsRoot.resolve("branded"));
        Files.writeString(branded.resolve("index.html"), ORIGINAL_INDEX, StandardCharsets.UTF_8);
        brandingService.applyBranding(branded);
        String brandedBefore = Files.readString(branded.resolve("index.html"), StandardCharsets.UTF_8);

        var sweep = new BrandingSweepConfiguration(brandingService, propertiesPointingAt(reportsRoot));

        // WHEN the startup sweep runs
        sweep.sweepOnStartup();

        // THEN the unbranded report receives the branding asset and head injection
        assertThat(Files.exists(unbranded.resolve("brew-brand.css")))
            .as("previously unbranded report is branded by the sweep").isTrue();
        assertThat(Files.readString(unbranded.resolve("index.html"), StandardCharsets.UTF_8))
            .as("unbranded report index.html patched with branding stylesheet")
            .contains("<link rel=\"stylesheet\" href=\"brew-brand.css\">");

        // THEN the already-branded report is left byte-for-byte unchanged (idempotent)
        assertThat(Files.readString(branded.resolve("index.html"), StandardCharsets.UTF_8))
            .as("already branded report is not re-patched")
            .isEqualTo(brandedBefore);
    }

    @Test
    @DisplayName("should no-op when the reports root does not exist")
    void should_no_op_when_reports_root_missing(@TempDir Path tempDir) {
        // GIVEN a reports root path that does not exist on disk
        Path missingRoot = tempDir.resolve("does-not-exist");
        var sweep = new BrandingSweepConfiguration(brandingService, propertiesPointingAt(missingRoot));

        // WHEN the startup sweep runs / THEN it returns quietly without creating the directory
        sweep.sweepOnStartup();
        assertThat(Files.exists(missingRoot))
            .as("missing reports root is not created by the sweep").isFalse();
    }

    @Test
    @DisplayName("should isolate a failing report directory and still brand the rest")
    void should_isolate_per_directory_failure(@TempDir Path reportsRoot) throws Exception {
        // GIVEN a healthy report and a directory whose index.html is a directory (read fails)
        Path healthy = Files.createDirectory(reportsRoot.resolve("healthy"));
        Files.writeString(healthy.resolve("index.html"), ORIGINAL_INDEX, StandardCharsets.UTF_8);

        Path broken = Files.createDirectory(reportsRoot.resolve("broken"));
        // index.html as a directory: isRegularFile is false -> branding skips it silently, sweep continues
        Files.createDirectory(broken.resolve("index.html"));

        var sweep = new BrandingSweepConfiguration(brandingService, propertiesPointingAt(reportsRoot));

        // WHEN the startup sweep runs
        sweep.sweepOnStartup();

        // THEN the healthy report is still branded despite the malformed sibling
        assertThat(Files.exists(healthy.resolve("brew-brand.css")))
            .as("healthy report branded even when a sibling directory is malformed").isTrue();
        assertThat(Files.exists(broken.resolve("brew-brand.css")))
            .as("malformed report directory is left untouched").isFalse();
    }
}
