package ru.iopump.qa.allure.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import io.qameta.allure.entity.ExecutorInfo;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import ru.iopump.qa.util.FileUtil;

public class ReportsServiceTest {

    private ReportService allure;

    @Before
    public void setUp() {
        allure = new ReportService(FileUtil.getClassPathMainDir().resolve("test").toString(), new ObjectMapper());
    }

    @Test
    public void generate() throws IOException {
        ExecutorInfo executorInfo = new ExecutorInfo();
        executorInfo.setBuildName("111");
        executorInfo.setReportName("max");

        Resource resource = new ClassPathResource("/cb5193c3-bd9f-4e1f-9d0b-c4d667bc3dd8");
        Path resultPath = resource.getFile().toPath();
        Path reportPath = Paths.get("branch");
        List<Path> resultCollection = ImmutableList.of(resultPath);

        Path report = allure.getReportsPath().resolve(allure.generate(reportPath, resultCollection, false, executorInfo));
        Path firstHistory = report.resolve("history");
        long firstHistorySize = FileUtils.sizeOfDirectory(firstHistory.toFile());
        assertThat(report).isDirectoryContaining(path -> "index.html".equals(path.getFileName().toString()));

        report = allure.getReportsPath().resolve(allure.generate(reportPath, resultCollection, false, executorInfo));
        assertThat(report).isDirectoryContaining(path -> "index.html".equals(path.getFileName().toString()));
        Path nextHistory = report.resolve("history");
        assertThat(FileUtils.sizeOfDirectory(nextHistory.toFile())).isGreaterThan(firstHistorySize);
    }

    @Test(expected = IllegalArgumentException.class)
    public void generateFail() throws IOException {
        Path resultPath = Paths.get("notExists");
        Path reportPath = Paths.get("branch");
        List<Path> resultCollection = ImmutableList.of(resultPath);

        Path report = allure.generate(reportPath, resultCollection, false, null);
        Path firstHistory = report.resolve("history");
    }
}
