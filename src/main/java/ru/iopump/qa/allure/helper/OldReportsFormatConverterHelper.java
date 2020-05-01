package ru.iopump.qa.allure.helper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Component;
import ru.iopump.qa.allure.AppCfg;
import ru.iopump.qa.allure.entity.ReportEntity;
import ru.iopump.qa.allure.service.PathUtil;
import ru.iopump.qa.util.Str;

@Slf4j
@Component
public class OldReportsFormatConverterHelper {

    private final Path reportsDir;
    private final String reportsPath;
    private final String baseUrl;

    public OldReportsFormatConverterHelper(AppCfg cfg, String baseUrl) {
        this(Paths.get(cfg.reportsDir()), cfg.reportsPath(), baseUrl);
    }

    OldReportsFormatConverterHelper(Path reportsDir, String reportsPath, String baseUrl) {
        this.reportsDir = reportsDir;
        this.reportsPath = reportsPath;
        this.baseUrl = baseUrl;
    }

    public Collection<ReportEntity> convertOldFormat() throws IOException {

        if (hasOldFormatReports()) {

            final Collection<Path> oldReports = Files.walk(reportsDir)
                .parallel()
                .filter(p -> "index.html".equalsIgnoreCase(p.getFileName().toString()))
                .map(Path::getParent)
                .filter(this::isOldFormat)
                .collect(Collectors.toList());
            log.info("Found '{}' old reports: {}", oldReports.size(), oldReports);

            return oldReports.stream()
                .map(dir -> {
                    final UUID uuid = UUID.randomUUID();
                    final File destination = reportsDir.resolve(uuid.toString()).toFile();
                    final String thisReportPath = reportsDir.relativize(dir).toString();

                    try {
                        FileUtils.moveDirectory(dir.toFile(), destination);
                        FileUtils.deleteDirectory(dir.toFile());

                        log.info("Report moved from '{}' to '{}'", dir, destination);
                        return ReportEntity.builder()
                            .uuid(uuid)
                            .path(thisReportPath)
                            .createdDateTime(LocalDateTime.now())
                            .url(baseUrl + reportsPath + thisReportPath)
                            .level(0)
                            .active(true)
                            .build();

                    } catch (IOException e) {
                        log.error(Str.frm("Error moving report '{}' to '{}'", dir, destination), e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableList());
        }
        return Collections.emptyList();
    }

    /**
     * Fast check report directory.
     */
    protected boolean hasOldFormatReports() throws IOException {

        return !Files.walk(reportsDir, 1)
            .skip(1)
            .parallel()
            .allMatch(this::isNewFormat);
    }

    protected boolean isNewFormat(Path p) {
        return Files.isDirectory(p) &&
            (p.getFileName().toString().matches(PathUtil.UUID_PATTERN)
                || p.getFileName().toString().equalsIgnoreCase("history"));
    }

    private boolean isOldFormat(Path p) {
        return !isNewFormat(p);
    }
}
