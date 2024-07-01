package ru.iopump.qa.allure.helper.plugin;

import io.qameta.allure.core.LaunchResults;
import io.qameta.allure.summary.SummaryData;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static ru.iopump.qa.allure.helper.Util.MAPPER;

@Slf4j
public class CustomReportMetaPlugin implements AllureServerPlugin {

    private final static String LOGO_FILE = "logo.png";
    private final static String LOGO_DIR = "plugin/custom-logo";
    private final static String LOGO_STYLE_FILE = "styles.css";

    private final static String SUMMARY_DIR = "widgets/summary.json";

    @Override
    public void onGenerationStart(Collection<Path> resultsDirectories, Context context) {
    }

    @SneakyThrows
    @Override
    public void onGenerationFinish(Path reportDirectory, Collection<LaunchResults> launchResults, Context context) {

        var logo = context.getAllureProperties().logo();

        if (logo != null) {
            var bytes = logo.isReadable() ? logo.getContentAsByteArray() : null;
            if (bytes != null) {
                //noinspection resource
                var customLogoDirectory = Files
                    .find(reportDirectory, 3, (path, basicFileAttributes) -> basicFileAttributes.isDirectory() && path.toString().endsWith(LOGO_DIR))
                    .findFirst()
                    .orElseThrow(() -> new InternalError("Custom logo plugin directory not found..."));

                var logoName = Objects.requireNonNullElse(logo.getFilename(), LOGO_FILE);
                var customLogoPath = customLogoDirectory.resolve(logoName);
                var customLogoCssPath = customLogoDirectory.resolve(LOGO_STYLE_FILE);

                Files.write(customLogoPath, bytes);

                String cssForNewLogo = new String(new ClassPathResource("static/" + LOGO_STYLE_FILE).getContentAsByteArray(), UTF_8)
                    .replace("img.png", logoName);
                Files.writeString(customLogoCssPath, cssForNewLogo, UTF_8, Files.exists(customLogoCssPath) ? TRUNCATE_EXISTING : CREATE_NEW);
                log.info("{}: {} copied to {}", getName(), logoName, customLogoPath);
            }
        }

        var title = context.getAllureProperties().title();
        if (title != null) {
            //noinspection resource
            var summaryPath = Files
                .find(reportDirectory, 3, (path, basicFileAttributes) -> basicFileAttributes.isRegularFile() && path.toString().endsWith(SUMMARY_DIR))
                .findFirst()
                .orElseThrow(() -> new InternalError("Summary file not found..."));

            var summaryData = MAPPER.readValue(summaryPath.toFile(), SummaryData.class);
            summaryData.setReportName(title);
            var newSummary = MAPPER.writeValueAsString(summaryData);
            Files.writeString(summaryPath, newSummary, UTF_8, TRUNCATE_EXISTING);
            log.info("{}: Summary file updated with new title: {}", getName(), title);
        }

    }

    @Override
    public String getName() {
        return "Logo Plugin";
    }
}
