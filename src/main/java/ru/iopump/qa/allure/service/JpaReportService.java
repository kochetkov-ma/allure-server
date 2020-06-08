package ru.iopump.qa.allure.service; //NOPMD

import static org.apache.commons.io.FileUtils.deleteQuietly;
import static ru.iopump.qa.allure.gui.DateTimeResolver.zeroZone;
import static ru.iopump.qa.allure.helper.ExecutorCiPlugin.JSON_FILE_NAME;
import static ru.iopump.qa.allure.service.PathUtil.str;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import io.qameta.allure.entity.ExecutorInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.iopump.qa.allure.AppCfg;
import ru.iopump.qa.allure.entity.ReportEntity;
import ru.iopump.qa.allure.helper.AllureReportGenerator;
import ru.iopump.qa.allure.helper.OldReportsFormatConverterHelper;
import ru.iopump.qa.allure.helper.ServeRedirectHelper;
import ru.iopump.qa.allure.repo.JpaReportRepository;

@Component
@Slf4j
@Transactional
public class JpaReportService {

    public static final String REPORT_PATH_DEFAULT = "allure/reports/";

    @Getter
    private final Path reportsDir;
    private final AppCfg cfg;
    private final ObjectMapper objectMapper;
    private final AllureReportGenerator reportGenerator;
    private final ServeRedirectHelper redirection;
    private final JpaReportRepository repository;

    private final AtomicBoolean init = new AtomicBoolean();

    public JpaReportService(AppCfg cfg,
                            ObjectMapper objectMapper,
                            JpaReportRepository repository,
                            AllureReportGenerator reportGenerator,
                            ServeRedirectHelper redirection
    ) {
        this.reportsDir = Paths.get(cfg.reportsDir());
        this.cfg = cfg;
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.reportGenerator = reportGenerator;
        this.redirection = redirection;
    }

    @PostConstruct
    protected void initRedirection() {
        repository.findByActiveTrue().forEach(
            e -> redirection.mapRequestTo(e.getPath(), reportsDir.resolve(e.getUuid().toString()).toString())
        );
    }

    public Collection<ReportEntity> clearAllHistory() {
        final Collection<ReportEntity> entitiesActive = repository.findByActiveTrue();
        final Collection<ReportEntity> entitiesInactive = repository.deleteByActiveFalse();

        // delete active history
        entitiesActive
            .forEach(e -> deleteQuietly(reportsDir.resolve(e.getUuid().toString()).resolve("history").toFile()));

        // delete active history
        entitiesInactive
            .forEach(e -> deleteQuietly(reportsDir.resolve(e.getUuid().toString()).toFile()));

        return entitiesInactive;
    }

    public void internalDeleteByUUID(UUID uuid) throws IOException {
        repository.deleteById(uuid);
        FileUtils.deleteDirectory(reportsDir.resolve(uuid.toString()).toFile());
    }

    public Collection<ReportEntity> deleteAll() throws IOException {
        var res = getAll();
        repository.deleteAll();
        FileUtils.deleteDirectory(reportsDir.toFile());
        return res;
    }

    public Collection<ReportEntity> getAll() {
        return repository.findAll(Sort.by("createdDateTime").descending());
    }

    public ReportEntity generate(@NonNull String reportPath,
                                 @NonNull List<Path> resultDirs,
                                 boolean clearResults,
                                 @Nullable ExecutorInfo executorInfo,
                                 String baseUrl
    ) throws IOException {
        if (cfg.supportOldFormat() && init.compareAndSet(false, true)) {
            var old = new OldReportsFormatConverterHelper(cfg).convertOldFormat();
            repository.saveAll(old);
            old.forEach(e -> redirection.mapRequestTo(e.getPath(), reportsDir.resolve(e.getUuid().toString()).toString()));
        }
        // Preconditions
        Preconditions.checkArgument(!resultDirs.isEmpty());
        resultDirs.forEach(i -> Preconditions.checkArgument(Files.exists(i), "Result '%s' doesn't exist", i));

        // New report destination and entity
        final UUID uuid = UUID.randomUUID();

        // Find prev report if present
        final Optional<ReportEntity> prevEntity = repository.findByPathOrderByCreatedDateTimeDesc(reportPath)
            .stream()
            .findFirst();

        // New uuid directory
        final Path destination = reportsDir.resolve(uuid.toString());

        // Copy history from prev report
        final Optional<Path> historyO = prevEntity
            .flatMap(e -> copyHistory(reportsDir.resolve(e.getUuid().toString()), uuid.toString()))
            .or(Optional::empty);

        try {
            // Add history to results if exists
            final List<Path> resultDirsToGenerate = historyO
                .map(history -> (List<Path>) ImmutableList.<Path>builder().addAll(resultDirs).add(history).build())
                .orElse(resultDirs);

            // Add CI executor information
            addExecutionInfo(
                resultDirs.get(0),
                executorInfo,
                baseUrl + str(reportsDir.resolve(uuid.toString())) + "/index.html",
                uuid
            );

            // Generate new report with history
            reportGenerator.generate(destination, resultDirsToGenerate);

            log.info("Report '{}' generated according to results '{}'", destination, resultDirsToGenerate);
        } finally {

            // Delete tmp history
            historyO.ifPresent(h -> deleteQuietly(h.toFile()));
            if (clearResults) {
                resultDirs.forEach(r -> deleteQuietly(r.toFile()));
            }
        }

        // New report entity
        final ReportEntity newEntity = ReportEntity.builder()
            .uuid(uuid)
            .path(reportPath)
            .createdDateTime(LocalDateTime.now(zeroZone()))
            .url(cfg.reportsPath() + reportPath)
            .level(prevEntity.map(e -> e.getLevel() + 1).orElse(0L))
            .active(true)
            .size(ReportEntity.sizeKB(destination))
            .build();

        // Add request mapping
        redirection.mapRequestTo(newEntity.getPath(), reportsDir.resolve(uuid.toString()).toString());

        // Persist
        handleMaxHistory(newEntity);
        repository.save(newEntity);

        // Disable prev report
        prevEntity.ifPresent(e -> e.setActive(false));

        return newEntity;
    }

    ///// PRIVATE /////

    //region Private
    private void handleMaxHistory(ReportEntity created) {
        var max = cfg.maxReportHistoryLevel();

        if (created.getLevel() >= max) { // Check reports count in history
            // Get all sorted reports
            var allReports = repository.findByPathOrderByCreatedDateTimeDesc(created.getPath());

            // If size more than max history
            if (allReports.size() >= max) {
                log.info("Current report count '{}' exceed max history report count '{}'",
                    allReports.size(),
                    max
                );

                // Delete last after max history
                long deleted = allReports.stream()
                    .skip(max)
                    .peek(e -> log.info("Report '{}' will be deleted", e))
                    .peek(e -> deleteQuietly(reportsDir.resolve(e.getUuid().toString()).toFile()))
                    .peek(repository::delete)
                    .count();

                // Update level (safety)
                created.setLevel(Math.max(created.getLevel() - deleted, 0));
            }
        }
    }

    @SneakyThrows
    private Optional<Path> copyHistory(Path reportPath, String prevReportWithHistoryUuid) {
        // History dir in report dir
        final Path sourceHistory = reportPath.resolve("history");

        // If History dir exists
        if (Files.exists(sourceHistory) && Files.isDirectory(sourceHistory)) {
            // Create tmp history dir
            final Path tmpHistory = reportsDir.resolve("history").resolve(prevReportWithHistoryUuid);
            FileUtils.moveDirectoryToDirectory(sourceHistory.toFile(), tmpHistory.toFile(), true);
            log.info("Report '{}' history is '{}'", reportPath, tmpHistory);
            return Optional.of(tmpHistory);
        } else {
            // Or nothing
            return Optional.empty();
        }
    }

    private void addExecutionInfo(Path resultPathWithInfo,
                                  ExecutorInfo executor,
                                  String reportUrl,
                                  UUID uuid) throws IOException {

        var executorInfo = Optional.ofNullable(executor).orElse(new ExecutorInfo());
        executorInfo.setReportUrl(reportUrl);

        if (StringUtils.isBlank(executorInfo.getName())) {
            executorInfo.setName("Remote executor");
        }
        if (StringUtils.isBlank(executorInfo.getType())) {
            executorInfo.setType("CI");
        }
        if (StringUtils.isBlank(executorInfo.getReportName())) {
            executorInfo.setName("Allure server generated " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        if (StringUtils.isBlank(executorInfo.getReportName())) {
            executorInfo.setReportName(uuid.toString());
        }
        final ObjectWriter writer = objectMapper.writer(new DefaultPrettyPrinter());
        final Path executorPath = resultPathWithInfo.resolve(JSON_FILE_NAME);
        writer.writeValue(executorPath.toFile(), executorInfo);
        log.info("Executor information added to '{}' : {}", executorPath, executorInfo);
    }
    //endregion
}
