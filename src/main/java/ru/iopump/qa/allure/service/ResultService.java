package ru.iopump.qa.allure.service;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.iopump.qa.util.FileUtil;

@Component
@Slf4j
public class ResultService {
    private static final String STORAGE_PATH_DEFAULT = "./allure/results";
    @Getter
    private final Path storagePath;

    public ResultService(@Value("${allure.results.dir:" + STORAGE_PATH_DEFAULT + "}") String storagePath) {
        this.storagePath = Paths.get(storagePath);
    }

    public ResultService() {
        this(STORAGE_PATH_DEFAULT);
    }

    public void deleteAll() throws IOException {
        FileUtils.deleteDirectory(storagePath.toFile());
    }

    public Collection<Path> getAll() throws IOException {
        if (!Files.exists(storagePath)) {
            return Collections.emptySet();
        }
        return Files.walk(storagePath, 1).skip(1).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Check archive, unzip and save to the file system.
     * Directory with uuid name will contain archive content.
     *
     * @param archiveInputStream Will be closed automatically.
     * @return Directory that contains the archive's content.
     * @throws IOException IO Error
     */
    @NonNull
    public Path unzipAndStore(@NonNull InputStream archiveInputStream) throws IOException {
        Preconditions.checkArgument(archiveInputStream.available() > 0,
            "Passed InputStream is empty");
        final Path tmpResultDirectory;
        final Path resultDirectory;
        try (InputStream io = archiveInputStream) {
            final String uuid = UUID.randomUUID().toString();
            tmpResultDirectory = storagePath.resolve(uuid + "_tmp");
            resultDirectory = storagePath.resolve(uuid);
            Files.createDirectories(resultDirectory);
            checkAndUnzipTo(io, tmpResultDirectory);
            move(tmpResultDirectory, resultDirectory);
        }
        log.info("Archive content saved to '{}'", resultDirectory);
        return resultDirectory;
    }

    private void checkAndUnzipTo(InputStream zipArchiveIo, Path unzipTo) throws IOException {
        ZipInputStream zis = new ZipInputStream(zipArchiveIo);
        byte[] buffer = new byte[1024];
        ZipEntry zipEntry = zis.getNextEntry();
        if (zipEntry == null) {
            throw new IllegalArgumentException("Passed InputStream is not a Zip Archive or empty");
        }
        while (zipEntry != null) {
            final Path newFile = fromZip(unzipTo, zipEntry);
            try (final OutputStream fos = Files.newOutputStream(newFile)) {
                int len;
                while ((len = zis.read(buffer)) > 0) { //NOPMD
                    fos.write(buffer, 0, len);
                }
            }
            log.info("Unzip new entry '{}'", newFile);
            zipEntry = zis.getNextEntry();
        }
        zis.closeEntry();
        zis.close();

        log.info("Unzipping successfully finished to '{}'", unzipTo);
    }

    private void move(Path from, Path to) throws IOException {
        Files.find(from,
            1,
            (path, basicFileAttributes)
                -> basicFileAttributes.isDirectory() && path.getFileName().toString().matches("allure-results?"))
            .forEach(
                nestedResultDir -> {
                    try {
                        Files.walkFileTree(nestedResultDir, new MoveFileVisitor(to));
                    } catch (IOException e) {
                        throw new RuntimeException("Walk error " + nestedResultDir, e); //NOPMD
                    }
                }
            );
        Files.walkFileTree(from, new MoveFileVisitor(to));
    }

    private Path fromZip(Path unzipTo, ZipEntry zipEntry) {
        final Path entryPath = Paths.get(zipEntry.getName());
        final Path destinationFileOrDir = unzipTo.resolve(entryPath);

        if (Files.isDirectory(destinationFileOrDir)) {
            FileUtil.createDir(destinationFileOrDir);
        } else {
            FileUtil.createFile(destinationFileOrDir);
        }

        return destinationFileOrDir;
    }

}
