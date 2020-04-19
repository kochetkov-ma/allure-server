package ru.iopump.qa.allure.service;

import static ru.iopump.qa.allure.service.ExecutorCiPlugin.JSON_FILE_NAME;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import io.qameta.allure.ConfigurationBuilder;
import io.qameta.allure.ReportGenerator;
import io.qameta.allure.allure2.Allure2Plugin;
import io.qameta.allure.category.CategoriesPlugin;
import io.qameta.allure.category.CategoriesTrendPlugin;
import io.qameta.allure.context.FreemarkerContext;
import io.qameta.allure.context.JacksonContext;
import io.qameta.allure.context.MarkdownContext;
import io.qameta.allure.context.RandomUidContext;
import io.qameta.allure.core.AttachmentsPlugin;
import io.qameta.allure.core.Configuration;
import io.qameta.allure.core.MarkdownDescriptionsPlugin;
import io.qameta.allure.core.ReportWebPlugin;
import io.qameta.allure.core.TestsResultsPlugin;
import io.qameta.allure.duration.DurationPlugin;
import io.qameta.allure.duration.DurationTrendPlugin;
import io.qameta.allure.entity.ExecutorInfo;
import io.qameta.allure.environment.Allure1EnvironmentPlugin;
import io.qameta.allure.history.HistoryPlugin;
import io.qameta.allure.history.HistoryTrendPlugin;
import io.qameta.allure.launch.LaunchPlugin;
import io.qameta.allure.mail.MailPlugin;
import io.qameta.allure.owner.OwnerPlugin;
import io.qameta.allure.retry.RetryPlugin;
import io.qameta.allure.retry.RetryTrendPlugin;
import io.qameta.allure.severity.SeverityPlugin;
import io.qameta.allure.status.StatusChartPlugin;
import io.qameta.allure.suites.SuitesPlugin;
import io.qameta.allure.summary.SummaryPlugin;
import io.qameta.allure.tags.TagsPlugin;
import io.qameta.allure.timeline.TimelinePlugin;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ReportService {

    public static final String REPORT_PATH_DEFAULT = "allure/reports/";
    @Getter
    private final Path reportsPath;
    private final ObjectMapper objectMapper;
    private final ReportGenerator reportGenerator;

    public ReportService(@Value("${allure.reports.dir:" + REPORT_PATH_DEFAULT + "}") String reportsPath, ObjectMapper objectMapper) {
        this.reportsPath = Paths.get(reportsPath);
        this.objectMapper = objectMapper;
        this.reportGenerator = new ReportGenerator(configuration());
    }

    public ReportService() {
        this(REPORT_PATH_DEFAULT, new ObjectMapper());
    }

    private static Configuration configuration() {
        return new ConfigurationBuilder()
            .fromExtensions(
                Arrays.asList(
                    new JacksonContext(),
                    new MarkdownContext(),
                    new FreemarkerContext(),
                    new RandomUidContext(),
                    new MarkdownDescriptionsPlugin(),
                    new RetryPlugin(),
                    new RetryTrendPlugin(),
                    new TagsPlugin(),
                    new SeverityPlugin(),
                    new OwnerPlugin(),
                    new HistoryPlugin(),
                    new HistoryTrendPlugin(),
                    new CategoriesPlugin(),
                    new CategoriesTrendPlugin(),
                    new DurationPlugin(),
                    new DurationTrendPlugin(),
                    new StatusChartPlugin(),
                    new TimelinePlugin(),
                    new SuitesPlugin(),
                    new ReportWebPlugin(),
                    new TestsResultsPlugin(),
                    new AttachmentsPlugin(),
                    new MailPlugin(),
                    new SummaryPlugin(),
                    new ExecutorCiPlugin(),
                    new LaunchPlugin(),
                    new Allure2Plugin(),
                    new Allure1EnvironmentPlugin()
                )
            ).build();
    }

    public Collection<Path> deleteAll() throws IOException {
        var res = getAll();
        FileUtils.deleteDirectory(reportsPath.toFile());
        return res;
    }

    public Collection<Path> getAll() throws IOException {
        if (!Files.exists(reportsPath)) {
            return Collections.emptySet();
        }
        return Files.walk(reportsPath)
            .skip(1)
            .filter(file -> "index.html".equals(file.getFileName().toString()))
            .map(Path::getParent)
            .map(reportsPath::relativize)
            .collect(Collectors.toUnmodifiableSet());
    }

    public Path generate(@NonNull Path relatedPath,
                         @NonNull List<Path> resultDirs,
                         boolean clearResults,
                         @Nullable ExecutorInfo executorInfo) throws IOException {
        Preconditions.checkArgument(!resultDirs.isEmpty());
        resultDirs.forEach(i -> Preconditions.checkArgument(Files.exists(i), "Result '%s' doesn't exist", i));

        final Path destination = reportsPath.resolve(relatedPath);
        Optional<Path> historyO = copyHistory(destination);
        try {
            // Add history to results if exists
            final List<Path> resultDirsToGenerate = historyO
                .map(history -> (List<Path>) ImmutableList.<Path>builder().addAll(resultDirs).add(history).build())
                .orElse(resultDirs);

            // Add CI executor information
            addExecutionInfo(resultDirs.get(0), executorInfo);

            // Delete prev report dir
            FileUtils.deleteQuietly(destination.toFile());
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
        return reportsPath.relativize(destination);
    }

    private Optional<Path> copyHistory(Path reportPath) throws IOException {
        // History dir in report dir
        final Path sourceHistory = reportPath.resolve("history");

        // If History dir exists
        if (Files.exists(sourceHistory) && Files.isDirectory(sourceHistory)) {
            // Create tmp history dir
            final Path tmpHistory = reportsPath.resolve("history").resolve(UUID.randomUUID().toString());
            FileUtils.moveDirectoryToDirectory(sourceHistory.toFile(), tmpHistory.toFile(), true);
            log.info("Report '{}' history is '{}'", reportPath, tmpHistory);
            return Optional.of(tmpHistory);
        } else {
            // Or nothing
            return Optional.empty();
        }
    }

    private void addExecutionInfo(Path resultPathWithInfo, @Nullable ExecutorInfo executorInfo) throws IOException {
        if (executorInfo == null) {
            executorInfo = new ExecutorInfo();
        }
        if (StringUtils.isBlank(executorInfo.getName())) {
            executorInfo.setName("Remote executor");
        }
        if (StringUtils.isBlank(executorInfo.getType())) {
            executorInfo.setName("CI");
        }
        if (StringUtils.isBlank(executorInfo.getReportName())) {
            executorInfo.setName("Allure server generated " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        final ObjectWriter writer = objectMapper.writer(new DefaultPrettyPrinter());
        final Path executorPath = resultPathWithInfo.resolve(JSON_FILE_NAME);
        writer.writeValue(executorPath.toFile(), executorInfo);
        log.info("Executor information added to '{}' : {}", executorPath, executorInfo);
    }
}
