package ru.iopump.qa.allure.service;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import ru.iopump.qa.util.FileUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Slf4j
public class ResultServiceTest {

    private static final String ZIP_SLIP_ENTRY = "../escape.txt";

    private ResultService resultService;

    @BeforeEach
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

    @Test
    @DisplayName("should reject zip-slip entry and not write outside the per-UUID dir")
    public void unzipAndStoreRejectsZipSlip(@TempDir Path targetRoot) {
        // GIVEN a zip whose only entry escapes the target dir via '../'
        final InputStream maliciousZip = zipWithEntry(ZIP_SLIP_ENTRY, "pwned");

        // WHEN unzipping into the temp target root
        // THEN it fails with an IOException naming the offending entry
        assertThatThrownBy(() -> resultService.unzipAndStore(maliciousZip, targetRoot))
            .as("zip-slip entry must be rejected")
            .isInstanceOf(IOException.class)
            .hasMessageContaining(ZIP_SLIP_ENTRY);

        // AND no file is created outside the per-UUID dir (sibling of targetRoot)
        assertThat(targetRoot.getParent().resolve("escape.txt"))
            .as("escaped file must not exist next to the target root")
            .doesNotExist();
        assertThat(targetRoot.resolve("escape.txt"))
            .as("escaped file must not exist inside the target root")
            .doesNotExist();
    }

    private static InputStream zipWithEntry(String entryName, String content) {
        try {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                zos.putNextEntry(new ZipEntry(entryName));
                zos.write(content.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            return new ByteArrayInputStream(baos.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot build test zip", e);
        }
    }
}
