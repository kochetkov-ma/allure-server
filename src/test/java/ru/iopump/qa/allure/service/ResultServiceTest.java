package ru.iopump.qa.allure.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import ru.iopump.qa.util.FileUtil;

@Slf4j
public class ResultServiceTest {

    private ResultService resultService;

    @Before
    public void setUp() {
        resultService = new ResultService(
            FileUtil.getClassPathMainDir().resolve("test")
        );
    }

    @Test
    public void unzipAndStorePositive() throws IOException {
        Resource resource = new ClassPathResource("allure-results.zip");
        Path path = resultService.unzipAndStore(resource.getInputStream());
        log.info("UnZip to: {}", path);

        resource = new ClassPathResource("allure-results-2.zip");
        path = resultService.unzipAndStore(resource.getInputStream());
        log.info("UnZip to: {}", path);

        resource = new ClassPathResource("allure-results-empty-folder.zip");
        path = resultService.unzipAndStore(resource.getInputStream());
        log.info("UnZip to: {}", path);
    }

    @Test
    public void unzipAndStoreNegative() {
        assertThatThrownBy(() -> resultService.unzipAndStore(new ClassPathResource("allure-results.json").getInputStream()))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> resultService.unzipAndStore(new ClassPathResource("allure-results.7z").getInputStream()))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> resultService.unzipAndStore(new ClassPathResource("allure-results-json").getInputStream()))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> resultService.unzipAndStore(new ClassPathResource("allure-results-empty.zip").getInputStream()))
            .isInstanceOf(IllegalArgumentException.class);
    }
}