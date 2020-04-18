package ru.iopump.qa.allure.service;

import com.google.common.collect.ImmutableList;
import io.qameta.allure.ConfigurationBuilder;
import io.qameta.allure.ReportGenerator;
import io.qameta.allure.core.Configuration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;
import ru.iopump.qa.util.FileUtil;

@Component
public class AllureService {

    private static final String REPORT_PATH_DEFAULT = "./allure/reports";
    private final Path reportsPath;
    private final Configuration configuration;
    private final ReportGenerator reportGenerator;

    public AllureService(@Value("${allure.reports.dir:" + REPORT_PATH_DEFAULT + "}") String reportsPath) {
        this.reportsPath = Paths.get(reportsPath);
        this.configuration = new ConfigurationBuilder().useDefault().build();
        this.reportGenerator = new ReportGenerator(configuration);
    }

    public AllureService() {
        this(REPORT_PATH_DEFAULT);
    }

    public Path generate(@NonNull Path relatedPath, @NonNull List<Path> resultDirs) throws IOException {
        final Path destination = reportsPath.resolve(relatedPath);
        final List<Path> resultDirsToGenerate = copyHistory(destination)
            .map(history -> (List<Path>) ImmutableList.<Path>builder().addAll(resultDirs).add(history).build())
            .orElse(resultDirs);
        FileSystemUtils.deleteRecursively(destination);
        reportGenerator.generate(destination, resultDirsToGenerate);
        return destination;
    }

    private Optional<Path> copyHistory(Path reportPath) throws IOException {
        final Path sourceHistory = reportPath.resolve("history");
        if (Files.exists(sourceHistory) && Files.isDirectory(sourceHistory)) {
            final Path tmpHistory = reportsPath.resolve("history").resolve(UUID.randomUUID().toString());
            FileUtil.createDir(tmpHistory); // new history dir
            Files.walkFileTree(sourceHistory, new MoveFileVisitor(tmpHistory)); // move content
            return Optional.of(tmpHistory);
        } else {
            return Optional.empty();
        }
    }
}
