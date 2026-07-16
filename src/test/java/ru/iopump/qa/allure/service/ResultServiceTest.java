package ru.iopump.qa.allure.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.iopump.qa.allure.properties.AllureProperties;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResultServiceTest {

    private static final String ZIP_SLIP_ENTRY = "../escape.txt";
    private static final String EMPTY_FOLDER_ENTRY_NAME = "allure-results";

    /**
     * Both {@code allure-results.zip} (entries nested under an {@code allure-results/} wrapper)
     * and {@code allure-results-2.zip} (the same 31 entries, flat at the zip root) must extract
     * to this exact flattened set — {@link ResultService#unzipAndStore} promotes/merges any
     * top-level {@code allure-.+} directory into the returned result directory.
     */
    private static final Set<String> ALLURE_RESULTS_ENTRY_NAMES = Set.of(
        "0d54ee65-6b95-4cc8-90ac-889b6105b0d4-result.json",
        "267fe70f-f409-4914-b5d0-6f8d5c8c222b-attachment.png",
        "29dbf2a9-730a-44a7-964f-ce9d1744a025-result.json",
        "42c4c9d6-b6d9-4f0e-9f97-9f60e83b04dd-attachment.html",
        "4a4805c1-228d-4bd6-bacd-7e6046eb9242-attachment.png",
        "5098fd0d-ca53-4e5e-b09a-043b0cab71c7-attachment.png",
        "515a5fdc-a366-4453-a707-8d38b46f3d4c-attachment.png",
        "5c68f42f-58a2-41c2-b7b5-cc44beb18d62-result.json",
        "5e0e1c2d-3ab2-44bf-b74a-637e8882cd37-container.json",
        "5f0637c7-b26e-4b57-a044-d3584e7f6a1e-attachment.png",
        "6bd2ac6e-59e7-4ac3-aa9a-c40ba26ba4dc-container.json",
        "74716b7b-0ce3-449a-973e-00708722475f-container.json",
        "79a83547-d5d0-4b16-8b5a-21022d1b3914-result.json",
        "7fbd4171-d527-4e97-824c-86c6c6f3a0aa-container.json",
        "818bd326-92cf-4219-af2f-5ff0ec922a37-result.json",
        "8abcbc0b-1e1d-4724-aff6-3546cf511266-container.json",
        "9668666d-d3fe-48b6-9f69-a33027042b0a-container.json",
        "a6497052-0c3d-473d-ba95-e6214993903d-result.json",
        "ab2d6ed3-1873-4a24-8aa2-f4ad700a845f-attachment.txt",
        "ab4705b2-c34b-4db0-af9c-75fda0c2a84c-result.json",
        "b3524365-87bb-4bb7-80b8-7e3d5df40a94-container.json",
        "b3f67e21-d0bf-4c78-907e-a530107e81f5-result.json",
        "b94cb890-d842-4781-afd5-48e78782ed4d-result.json",
        "bddd8b7b-a5f1-404f-81d2-d7cac8d30d46-container.json",
        "d91f6ab4-5f03-4969-9203-38167db52c03-attachment.png",
        "ee6fb80e-3fb5-4f04-bf8f-40e22b8ee985-attachment.html",
        "executor.json",
        "f288da16-5270-4919-bce4-4baae308931d-result.json",
        "f5b35e80-a1f2-49f2-bf94-d772e9ac4fdc-container.json",
        "f92f926b-55ee-4a49-8d06-f3637466f8e9-attachment.txt",
        "fcb452df-e62b-466e-8887-3b212376f702-container.json"
    );

    private ResultService resultService;

    @BeforeEach
    void setUp(@TempDir Path storagePath) {
        resultService = new ResultService(storagePath);
    }

    @ParameterizedTest(name = "fixture \"{0}\"")
    @DisplayName("should unzip and flatten allure-results content into a directory under the storage root")
    @ValueSource(strings = {"allure-results.zip", "allure-results-2.zip"})
    void unzipAndStorePositive_flattensAllureResultsContent(String fixtureName) throws IOException {
        // GIVEN — a fixture zip; one nests entries under an 'allure-results/' wrapper directory,
        // the other stores the same entries flat at the zip root
        final Resource resource = new ClassPathResource(fixtureName);

        // WHEN — unzipping into the service's storage root
        final Path resultDirectory = resultService.unzipAndStore(resource.getInputStream());

        // THEN — the returned path is a directory created directly under the storage root
        assertThat(resultDirectory)
            .as("fixture '%s': returned path must exist on disk", fixtureName)
            .exists();
        assertThat(Files.isDirectory(resultDirectory))
            .as("fixture '%s': returned path must be a directory", fixtureName)
            .isTrue();
        assertThat(resultDirectory.getParent())
            .as("fixture '%s': returned directory must live directly under the storage root", fixtureName)
            .isEqualTo(resultService.getStoragePath());

        // AND — every fixture entry is present, flattened (no nested 'allure-results/' wrapper survives)
        assertThat(listFileNames(resultDirectory))
            .as("fixture '%s': extracted directory must contain exactly the fixture's flattened entries", fixtureName)
            .isEqualTo(ALLURE_RESULTS_ENTRY_NAMES);
    }

    @Test
    @DisplayName("should materialize a zip's lone directory entry as a single empty regular file")
    void unzipAndStorePositive_directoryOnlyZipBecomesSingleEmptyFile() throws IOException {
        // GIVEN — a fixture zip containing only one empty directory entry, 'allure-results/',
        // with no nested file entries to trigger the file-to-directory promotion below
        final Resource resource = new ClassPathResource("allure-results-empty-folder.zip");

        // WHEN — unzipping into the service's storage root
        final Path resultDirectory = resultService.unzipAndStore(resource.getInputStream());

        // THEN — exactly one filesystem entry is extracted: a regular file named after the zip's
        // directory entry (ResultService#fromZip checks Files.isDirectory on a path that cannot yet
        // exist, so a directory-only entry falls through to createFile instead of createDir)
        final List<Path> children = listChildren(resultDirectory);
        assertThat(children)
            .as("directory-only fixture must extract to exactly one filesystem entry")
            .hasSize(1);
        final Path onlyChild = children.get(0);
        assertThat(onlyChild.getFileName().toString())
            .as("the sole extracted entry must be named after the zip's directory entry")
            .isEqualTo(EMPTY_FOLDER_ENTRY_NAME);
        assertThat(Files.isRegularFile(onlyChild))
            .as("the directory zip entry must be materialized as a regular file, not a directory")
            .isTrue();
        assertThat(Files.size(onlyChild))
            .as("the materialized file must be empty, matching the zip's empty directory entry")
            .isEqualTo(0L);
    }

    @ParameterizedTest(name = "fixture \"{0}\"")
    @DisplayName("should reject a non-zip, malformed-zip, extensionless or empty upload with IllegalArgumentException")
    @ValueSource(strings = {
        "allure-results.json",
        "allure-results.7z",
        "allure-results-json",
        "allure-results-empty.zip"
    })
    void unzipAndStoreNegative(String fixtureName) {
        // GIVEN — a fixture that is not a valid, non-empty zip archive

        // WHEN / THEN — rejected before any extraction happens
        assertThatThrownBy(() -> resultService.unzipAndStore(new ClassPathResource(fixtureName).getInputStream()))
            .as("fixture '%s' must be rejected as an invalid archive", fixtureName)
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should reject zip-slip entry and not write outside the per-UUID dir")
    void unzipAndStoreRejectsZipSlip(@TempDir Path targetRoot) {
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

    @Test
    @DisplayName("should reject an upload whose entry count exceeds a tiny maxEntries cap with 413 and clean up the partial extraction")
    void unzipAndStoreRejectsWhenEntryCountExceedsCap(@TempDir Path storagePath) throws IOException {
        // GIVEN a service capped at 1 entry, far below the 31-entry fixture
        final ResultService cappedService = new ResultService(storagePath, new AllureProperties.Upload(null, 1L));
        final Resource resource = new ClassPathResource("allure-results.zip");

        // WHEN unzipping the fixture
        // THEN it fails with a 413 Payload Too Large ResponseStatusException
        assertThatThrownBy(() -> cappedService.unzipAndStore(resource.getInputStream()))
            .as("entry count exceeding maxEntries must be rejected")
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .as("rejection status must be 413 Payload Too Large")
            .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);

        // AND no partial output survives under the storage root
        assertThat(listChildren(storagePath))
            .as("partial extraction must be fully cleaned up after the entry-count cap is tripped")
            .isEmpty();
    }

    @Test
    @DisplayName("should reject an upload whose cumulative uncompressed size exceeds a tiny byte cap with 413 and clean up the partial extraction")
    void unzipAndStoreRejectsWhenUncompressedSizeExceedsCap(@TempDir Path storagePath) throws IOException {
        // GIVEN a service capped at 1 uncompressed byte, far below any fixture entry's declared size
        final ResultService cappedService = new ResultService(storagePath, new AllureProperties.Upload(1L, null));
        final Resource resource = new ClassPathResource("allure-results.zip");

        // WHEN unzipping the fixture
        // THEN it fails with a 413 Payload Too Large ResponseStatusException on the first oversized entry
        assertThatThrownBy(() -> cappedService.unzipAndStore(resource.getInputStream()))
            .as("uncompressed size exceeding maxUncompressedBytes must be rejected")
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .as("rejection status must be 413 Payload Too Large")
            .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);

        // AND no partial output survives under the storage root
        assertThat(listChildren(storagePath))
            .as("partial extraction must be fully cleaned up after the byte cap is tripped")
            .isEmpty();
    }

    ///// helpers /////

    private static Set<String> listFileNames(Path directory) throws IOException {
        try (Stream<Path> listing = Files.list(directory)) {
            return listing.map(p -> p.getFileName().toString()).collect(Collectors.toSet());
        }
    }

    private static List<Path> listChildren(Path directory) throws IOException {
        try (Stream<Path> listing = Files.list(directory)) {
            return listing.collect(Collectors.toList());
        }
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
