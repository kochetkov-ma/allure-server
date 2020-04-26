package ru.iopump.qa.allure.service; //NOPMD

import static ru.iopump.qa.allure.helper.ExecutorCiPlugin.JSON_FILE_NAME;

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
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.iopump.qa.allure.entity.ReportEntity;
import ru.iopump.qa.allure.helper.AllureReportGenerator;
import ru.iopump.qa.allure.helper.ServeRedirectHelper;
import ru.iopump.qa.allure.repo.JpaReportRepository;

@Component
@Slf4j
@Transactional
public class JpaReportService {

    public static final String REPORT_PATH_DEFAULT = "allure/reports/";

    @Getter
    private final Path reportsPath;
    private final ObjectMapper objectMapper;
    private final AllureReportGenerator reportGenerator;
    private final ServeRedirectHelper redirection;
    private final JpaReportRepository repository;
    private final String reportPathBaseDir;

    public JpaReportService(@Value("${allure.reports.dir:" + REPORT_PATH_DEFAULT + "}") String reportsPath,
                            ObjectMapper objectMapper,
                            JpaReportRepository repository,
                            AllureReportGenerator reportGenerator,
                            ServeRedirectHelper redirection
    ) {
        this.reportsPath = Paths.get(reportsPath);
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.reportGenerator = reportGenerator;
        this.redirection = redirection;
        this.reportPathBaseDir = redirection.getReportsBase();
    }

    @PostConstruct
    protected void initRedirection() {
        repository.findAll().forEach(
            e -> redirection.mapRequestTo(e.getPath(), reportsPath.resolve(e.getUuid().toString()).toString())
        );
    }

    public Collection<ReportEntity> deleteAll() throws IOException {
        var res = getAll();
        repository.deleteAll();
        FileUtils.deleteDirectory(reportsPath.toFile());
        return res;
    }

    public Collection<ReportEntity> getAll() {
        return repository.findAll();
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

        final ReportEntity newEntity = ReportEntity.builder()
            .uuid(uuid)
            .path(reportPath)
            .url(baseUrl + reportPathBaseDir + reportPath)
            .active(true).build();
        final Optional<ReportEntity> prevEntity = repository.findByPathAndActiveTrueOrderByCreatedDateTimeDesc(reportPath)
            .stream()
            .findFirst();

        final Path destination = reportsPath.resolve(uuid.toString());

        // Copy history from prev report
        final Optional<Path> historyO = prevEntity
            .flatMap(e -> copyHistory(reportsPath.resolve(e.getUuid().toString()), uuid.toString()))
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
                prevEntity
                    .map(e -> baseUrl + reportsPath.resolve(e.getUuid().toString()).toString() + "/index.html")
                    .orElse(null)
            );

            // Generate new report with history
            reportGenerator.generate(destination, resultDirsToGenerate);
            log.info("Report '{}' generated according to results '{}'", destination, resultDirsToGenerate);
        } finally {
            // Delete tmp history
            historyO.ifPresent(h -> FileUtils.deleteQuietly(h.toFile()));
            if (clearResults) {
                resultDirs.forEach(r -> FileUtils.deleteQuietly(r.toFile()));
            }
        }

        redirection.mapRequestTo(newEntity.getPath(), reportsPath.resolve(uuid.toString()).toString());

        try {
            repository.save(newEntity);
        } finally {
            prevEntity.ifPresent(e -> e.setActive(false));
        }

        return newEntity;
    }

    ///// PRIVATE /////

    //region Private
    @SneakyThrows
    private Optional<Path> copyHistory(Path reportPath, String prevReportWithHistoryUuid) {
        // History dir in report dir
        final Path sourceHistory = reportPath.resolve("history");

        // If History dir exists
        if (Files.exists(sourceHistory) && Files.isDirectory(sourceHistory)) {
            // Create tmp history dir
            final Path tmpHistory = reportsPath.resolve("history").resolve(prevReportWithHistoryUuid);
            FileUtils.moveDirectoryToDirectory(sourceHistory.toFile(), tmpHistory.toFile(), true);
            log.info("Report '{}' history is '{}'", reportPath, tmpHistory);
            return Optional.of(tmpHistory);
        } else {
            // Or nothing
            return Optional.empty();
        }
    }

    private void addExecutionInfo(Path resultPathWithInfo, @Nullable ExecutorInfo executor, String prevReportUrl) throws IOException {
        var executorInfo = Optional.ofNullable(executor).orElse(new ExecutorInfo());
        if (StringUtils.isBlank(executorInfo.getName())) {
            executorInfo.setName("Remote executor");
        }
        if (StringUtils.isBlank(executorInfo.getType())) {
            executorInfo.setName("CI");
        }
        if (StringUtils.isBlank(executorInfo.getReportName())) {
            executorInfo.setName("Allure server generated " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        if (StringUtils.isNotBlank(prevReportUrl)) {
            executorInfo.setReportUrl(prevReportUrl);
        }
        final ObjectWriter writer = objectMapper.writer(new DefaultPrettyPrinter());
        final Path executorPath = resultPathWithInfo.resolve(JSON_FILE_NAME);
        writer.writeValue(executorPath.toFile(), executorInfo);
        log.info("Executor information added to '{}' : {}", executorPath, executorInfo);
    }
    //endregion
}
