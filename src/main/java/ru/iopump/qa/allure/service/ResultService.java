package ru.iopump.qa.allure.service;

import com.google.common.base.Preconditions;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import ru.iopump.qa.allure.helper.MoveFileVisitor;
import ru.iopump.qa.allure.model.ResultResponse;
import ru.iopump.qa.allure.properties.AllureProperties;
import ru.iopump.qa.util.FileUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static java.nio.file.Files.isDirectory;

@Getter
@Component
@Slf4j
public class ResultService {
    private final Path storagePath;
    private final long maxUncompressedBytes;
    private final long maxEntries;

    @Autowired
    public ResultService(AllureProperties cfg) {
        this(Paths.get(cfg.resultsDir()), cfg.upload());
    }

    ResultService(final Path storagePath) {
        this(storagePath, new AllureProperties.Upload());
    }

    ResultService(final Path storagePath, final AllureProperties.Upload upload) {
        this.storagePath = storagePath;
        this.maxUncompressedBytes = upload.maxUncompressedBytes();
        this.maxEntries = upload.maxEntries();
    }

    public ResultResponse internalDeleteByUUID(String uuid) throws IOException {
        var p = storagePath.resolve(uuid);
        long size = FileUtils.sizeOfDirectory(p.toFile()) / 1024;
        LocalDateTime localDateTime = LocalDateTime.MIN;
        try {
            BasicFileAttributes attr = Files.readAttributes(p, BasicFileAttributes.class);
            localDateTime = LocalDateTime.ofInstant(attr.creationTime().toInstant(), ZoneId.systemDefault());
        } catch (IOException e) {
            if (log.isErrorEnabled()) {
                log.error("Error getting created date time of " + p, e);
            }
        }
        var res = ResultResponse.builder().uuid(p.getFileName().toString()).created(localDateTime).size(size).build();

        FileUtils.deleteDirectory(storagePath.resolve(uuid).toFile());
        return res;
    }

    public void deleteAll() throws IOException {
        FileUtils.deleteDirectory(storagePath.toFile());
    }

    public Collection<Path> getAll() throws IOException {
        if (!Files.exists(storagePath)) {
            return Collections.emptySet();
        }
        try (Stream<Path> walk = Files.walk(storagePath, 1)) {
            return walk.skip(1)
                .filter(p -> isDirectory(p))
                .collect(Collectors.toUnmodifiableSet());
        }
    }

    /**
     * Check archive, unzip and save to the file system under this service's default {@link #storagePath}.
     * Directory with uuid name will contain archive content.
     *
     * @param archiveInputStream Will be closed automatically.
     * @return Directory that contains the archive's content.
     * @throws IOException IO Error
     */
    @NonNull
    public Path unzipAndStore(@NonNull InputStream archiveInputStream) throws IOException {
        return unzipAndStore(archiveInputStream, storagePath);
    }

    /**
     * Check archive, unzip and save to the file system under an explicit target root.
     * Directory with uuid name will contain archive content.
     * <p>
     * This overload allows callers that are themselves Spring-managed beans (e.g. report service)
     * to reuse this unzip primitive against a different target directory without instantiating a
     * second {@code ResultService} bypassing Spring.
     *
     * @param archiveInputStream Will be closed automatically.
     * @param targetRoot         Directory under which a new {@code <uuid>} folder will be created.
     * @return Directory that contains the archive's content.
     * @throws IOException IO Error
     */
    @NonNull
    public Path unzipAndStore(@NonNull InputStream archiveInputStream, @NonNull Path targetRoot) throws IOException {
        Preconditions.checkArgument(archiveInputStream.available() > 0,
            "Passed InputStream is empty");
        Path tmpResultDirectory = null;
        Path resultDirectory = null;
        try (InputStream io = archiveInputStream) {
            final String uuid = UUID.randomUUID().toString();
            tmpResultDirectory = targetRoot.resolve(uuid + "_tmp");
            resultDirectory = targetRoot.resolve(uuid);
            Files.createDirectories(resultDirectory);
            checkAndUnzipTo(io, tmpResultDirectory);
            move(tmpResultDirectory, resultDirectory);
        } catch (Exception ex) {
            if (resultDirectory != null) {
                // Clean on error
                FileUtils.deleteQuietly(resultDirectory.toFile());
            }
            if (tmpResultDirectory != null) {
                // Clean on error
                FileUtils.deleteQuietly(tmpResultDirectory.toFile());
            }
            throw ex; // And re-throw
        }
        log.info("Archive content saved to '{}'", resultDirectory);
        return resultDirectory;
    }

    private void checkAndUnzipTo(InputStream zipArchiveIo, Path unzipTo) throws IOException {
        byte[] buffer = new byte[1024];
        long totalBytes = 0;
        long entryCount = 0;
        try (ZipInputStream zis = new ZipInputStream(zipArchiveIo)) {
            ZipEntry zipEntry = zis.getNextEntry();
            if (zipEntry == null) {
                throw new IllegalArgumentException("Passed InputStream is not a Zip Archive or empty");
            }
            while (zipEntry != null) {
                if (++entryCount > maxEntries) {
                    throw tooLarge("Zip entry count exceeds the limit of " + maxEntries + " entries (possible zip bomb)");
                }
                // Cheap early reject on the DECLARED size. It is optional (-1 when unknown) and spoofable,
                // so real enforcement is the cumulative byte counter on the stream below.
                final long declaredSize = zipEntry.getSize();
                if (declaredSize > maxUncompressedBytes) {
                    throw tooLarge("Zip entry '" + zipEntry.getName() + "' declares size " + declaredSize
                        + " bytes, exceeding the uncompressed limit of " + maxUncompressedBytes + " bytes");
                }
                final Path newFile = fromZip(unzipTo, zipEntry);
                try (final OutputStream fos = Files.newOutputStream(newFile)) {
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        totalBytes += len;
                        if (totalBytes > maxUncompressedBytes) {
                            throw tooLarge("Cumulative uncompressed size exceeds the limit of "
                                + maxUncompressedBytes + " bytes (possible zip bomb)");
                        }
                        fos.write(buffer, 0, len);
                    }
                }
                log.debug("Unzip new entry '{}'", newFile);
                zipEntry = zis.getNextEntry();
            }
        }
        log.info("Unzipping successfully finished to '{}'", unzipTo);
    }

    /**
     * Build a 413 Payload Too Large signal (mapped by the central exception handler) for a rejected
     * upload. Thrown mid-extraction; the caller's on-error cleanup removes the partial output.
     */
    private static ResponseStatusException tooLarge(String reason) {
        log.warn("Upload rejected: {}", reason);
        return new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, reason);
    }

    private void move(Path from, Path to) throws IOException {
        try (Stream<Path> nested = Files.find(from,
            1,
            (path, basicFileAttributes)
                -> basicFileAttributes.isDirectory() && (path.getFileName().toString()
                .matches("allure-.+|report.*")))) {
            nested.forEach(
                nestedResultDir -> {
                    try {
                        Files.walkFileTree(nestedResultDir, new MoveFileVisitor(to));
                    } catch (IOException e) {
                        throw new RuntimeException("Walk error " + nestedResultDir, e);
                    }
                }
            );
        }
        Files.walkFileTree(from, new MoveFileVisitor(to));
    }

    private Path fromZip(Path unzipTo, ZipEntry zipEntry) throws IOException {
        final String entryName = zipEntry.getName();
        if (Paths.get(entryName).isAbsolute()) {
            throw new IOException("Zip entry has absolute path: " + entryName);
        }
        final Path root = unzipTo.normalize();
        final Path destinationFileOrDir = root.resolve(entryName).normalize();
        if (!destinationFileOrDir.startsWith(root)) {
            throw new IOException("Zip entry escapes target dir: " + entryName);
        }

        if (isDirectory(destinationFileOrDir)) {
            FileUtil.createDir(destinationFileOrDir);
        } else {
            FileUtil.createFile(destinationFileOrDir);
        }

        return destinationFileOrDir;
    }

}
