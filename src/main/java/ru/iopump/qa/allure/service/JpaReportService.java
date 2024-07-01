package ru.iopump.qa.allure.service; //NOPMD

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import io.qameta.allure.entity.ExecutorInfo;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import ru.iopump.qa.allure.entity.ReportEntity;
import ru.iopump.qa.allure.helper.AllureReportGenerator;
import ru.iopump.qa.allure.helper.ServeRedirectHelper;
import ru.iopump.qa.allure.properties.AllureProperties;
import ru.iopump.qa.allure.repo.JpaReportRepository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Optional.ofNullable;
import static org.apache.commons.io.FileUtils.deleteQuietly;
import static ru.iopump.qa.allure.gui.DateTimeResolver.zeroZone;
import static ru.iopump.qa.allure.helper.ExecutorCiPlugin.JSON_FILE_NAME;
import static ru.iopump.qa.allure.helper.Util.join;
import static ru.iopump.qa.allure.service.PathUtil.str;

@Component
@Slf4j
@Transactional
public class JpaReportService {

    @Getter
    private final Path reportsDir;
    private final AllureProperties cfg;
    private final ObjectMapper objectMapper;
    private final AllureReportGenerator reportGenerator;
    private final ServeRedirectHelper redirection;
    private final JpaReportRepository repository;
    private final ResultService reportUnzipService;

    private final AtomicBoolean init = new AtomicBoolean();

    public JpaReportService(AllureProperties cfg,
                            ObjectMapper objectMapper,
                            JpaReportRepository repository,
                            AllureReportGenerator reportGenerator,
                            ServeRedirectHelper redirection
    ) {
        this.reportsDir = cfg.reports().dirPath();
        this.cfg = cfg;
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.reportGenerator = reportGenerator;
        this.redirection = redirection;
        this.reportUnzipService = new ResultService(reportsDir);
    }

    @PostConstruct
    protected void initRedirection() {
        repository.findByActiveTrue().forEach(
            e -> redirection.mapRequestTo(join(cfg.reports().path(), e.getPath()), reportsDir.resolve(e.getUuid().toString()).toString())
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

    public Collection<ReportEntity> deleteAllOlderThanDate(LocalDateTime date) {
        final Collection<ReportEntity> res = repository.findAllByCreatedDateTimeIsBefore(date);
        res.forEach(e -> {
            repository.deleteById(e.getUuid());
            deleteQuietly(reportsDir.resolve(e.getUuid().toString()).toFile());
        });
        return res;
    }

    public Collection<ReportEntity> getAll() {
        return repository.findAll(Sort.by("createdDateTime").descending());
    }

    @SneakyThrows
    public ReportEntity uploadReport(@NonNull String reportPath,
                                     @NonNull InputStream archiveInputStream,
                                     @Nullable ExecutorInfo executorInfo,
                                     String baseUrl) {

        // New report destination and entity
        final Path destination = reportUnzipService.unzipAndStore(archiveInputStream);
        final UUID uuid = UUID.fromString(destination.getFileName().toString());
        Preconditions.checkArgument(
            Files.list(destination).anyMatch(path -> path.endsWith("index.html")),
            "Uploaded archive is not an Allure Report"
        );

        // Find prev report if present
        final Optional<ReportEntity> prevEntity = repository.findByPathOrderByCreatedDateTimeDesc(reportPath)
            .stream()
            .findFirst();

        // Add CI executor information
        var safeExecutorInfo = addExecutionInfo(
            destination,
            executorInfo,
            baseUrl + str(reportsDir.resolve(uuid.toString())) + "/index.html",
            uuid
        );

        log.info("Report '{}' loaded", destination);

        // New report entity
        final ReportEntity newEntity = ReportEntity.builder()
            .uuid(uuid)
            .path(reportPath)
            .createdDateTime(LocalDateTime.now(zeroZone()))
            .url(join(baseUrl, cfg.reports().dir(), uuid.toString()) + "/")
            .level(prevEntity.map(e -> e.getLevel() + 1).orElse(0L))
            .active(true)
            .size(ReportEntity.sizeKB(destination))
            .buildUrl(
                // Взять Build Url
                ofNullable(safeExecutorInfo.getBuildUrl())
                    // Or Build Name
                    .or(() -> ofNullable(safeExecutorInfo.getBuildName()))
                    // Or Executor Name
                    .or(() -> ofNullable(safeExecutorInfo.getName()))
                    // Or Executor Type
                    .orElse(safeExecutorInfo.getType())
            )
            .build();

        // Add request mapping
        redirection.mapRequestTo(newEntity.getPath(), reportsDir.resolve(uuid.toString()).toString());

        // Persist
        handleMaxHistory(newEntity);
        repository.saveAndFlush(newEntity);

        // Disable prev report
        prevEntity.ifPresent(e -> e.setActive(false));

        return newEntity;
    }

    public ReportEntity generate(@NonNull String reportPath,
                                 @NonNull List<Path> resultDirs,
                                 boolean clearResults,
                                 @Nullable ExecutorInfo executorInfo,
                                 String baseUrl
    ) throws IOException {
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

        // Add CI executor information
        var safeExecutorInfo = addExecutionInfo(
            resultDirs.get(0),
            executorInfo,
            baseUrl + str(reportsDir.resolve(uuid.toString())) + "/index.html",
            uuid
        );

        var reportUrl = join(baseUrl, cfg.reports().dir(), uuid.toString()) + "/";
        try {
            // Add history to results if exists
            final List<Path> resultDirsToGenerate = historyO
                .map(history -> (List<Path>) ImmutableList.<Path>builder().addAll(resultDirs).add(history).build())
                .orElse(resultDirs);

            // Generate new report with history
            reportGenerator.generate(destination, resultDirsToGenerate, reportUrl);

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
            .url(reportUrl)
            .level(prevEntity.map(e -> e.getLevel() + 1).orElse(0L))
            .active(true)
            .size(ReportEntity.sizeKB(destination))
            .buildUrl(
                // Взять Build Url
                ofNullable(safeExecutorInfo.getBuildUrl())
                    // Or Build Name
                    .or(() -> ofNullable(safeExecutorInfo.getBuildName()))
                    // Or Executor Name
                    .or(() -> ofNullable(safeExecutorInfo.getName()))
                    // Or Executor Type
                    .orElse(safeExecutorInfo.getType())
            )
            .build();

        // Add request mapping
        redirection.mapRequestTo(newEntity.getPath(), reportsDir.resolve(uuid.toString()).toString());

        // Persist
        handleMaxHistory(newEntity);
        repository.saveAndFlush(newEntity);

        // Disable prev report
        prevEntity.ifPresent(e -> e.setActive(false));

        return newEntity;
    }

    ///// PRIVATE /////

    //region Private
    private void handleMaxHistory(ReportEntity created) {
        var max = cfg.reports().historyLevel();

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

    @NotNull
    private ExecutorInfo addExecutionInfo(Path resultPathWithInfo,
                                          ExecutorInfo executor,
                                          String reportUrl,
                                          UUID uuid) throws IOException {

        var executorInfo = ofNullable(executor).orElse(new ExecutorInfo());
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
        return executorInfo;
    }
    //endregion
}
