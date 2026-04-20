package ru.iopump.qa.allure.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ru.iopump.qa.allure.model.ResultResponse;
import ru.iopump.qa.allure.model.UploadResponse;
import ru.iopump.qa.allure.service.PathUtil;
import ru.iopump.qa.allure.service.ResultService;
import ru.iopump.qa.util.StreamUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@RestController
@Slf4j
@Validated
@RequestMapping(path = "/api/result")
public class AllureResultController {
    final static String CACHE = "results";
    private static final String ZIP_MIME = "application/zip";
    private static final String ZIP_MIME_X = "application/x-zip-compressed";

    private final ResultService resultService;

    @Operation(summary = "Delete all allure results")
    @DeleteMapping
    @CacheEvict(value = CACHE, allEntries = true) // clear cache
    public Collection<ResultResponse> deleteAllResults() throws IOException {
        var res = getAllResult();
        resultService.deleteAll();
        return res;
    }

    @Operation(summary = "Delete allure result by uuid")
    @DeleteMapping(path = "/{uuid}")
    @CacheEvict(value = CACHE, allEntries = true)
    public ResultResponse deleteResult(
        @PathVariable @NotBlank @Pattern(regexp = PathUtil.UUID_PATTERN) String uuid
    ) throws IOException {
        return resultService.internalDeleteByUUID(uuid);
    }

    @Operation(summary = "Get allure result by uuid")
    @GetMapping(path = "/{uuid}")
    public ResultResponse getResult(@PathVariable @NotBlank @Pattern(regexp = PathUtil.UUID_PATTERN) String uuid) throws IOException {
        return StreamUtil.stream(getAllResult())
            .filter(i -> uuid.equalsIgnoreCase(i.getUuid()))
            .findFirst()
            .orElse(ResultResponse.builder().build());
    }

    @Operation(summary = "Get all uploaded allure results archives")
    @GetMapping
    @Cacheable(CACHE) // caching results
    public Collection<ResultResponse> getAllResult() throws IOException {
        return StreamUtil.stream(resultService.getAll()).map(p -> {
            long size = FileUtils.sizeOfDirectory(p.toFile()) / 1024;
            LocalDateTime localDateTime = LocalDateTime.MIN;
            try {
                BasicFileAttributes attr = Files.readAttributes(p, BasicFileAttributes.class);
                localDateTime = LocalDateTime.ofInstant(attr.creationTime().toInstant(), ZoneOffset.UTC);
            } catch (IOException e) {
                if (log.isErrorEnabled()) {
                    log.error("Error getting created date time of " + p, e);
                }
            }

            return ResultResponse.builder().uuid(p.getFileName().toString()).created(localDateTime).size(size).build();

        }).collect(Collectors.toUnmodifiableSet());
    }

    @SneakyThrows
    @Operation(summary = "Upload allure-results.zip with allure results files before generating report. " +
        "Don't forgot memorize uuid from response for further report generation"
    )
    @PostMapping(consumes = {"multipart/form-data"})
    @ResponseStatus(HttpStatus.CREATED)
    @CacheEvict(value = CACHE, allEntries = true) // update results cache
    public UploadResponse uploadResults(
        @Parameter(description = "File as multipart body. File must be an zip archive and not be empty. Nested type is 'application/zip'",
            name = "allureResults",
            example = "allure-result.zip",
            required = true,
            content = @Content(mediaType = "application/zip")
        )
        @RequestParam MultipartFile allureResults
    ) {

        requireNonEmptyZip(allureResults, "allureResults");

        // Unzip and save
        Path path = resultService.unzipAndStore(allureResults.getInputStream());
        log.info("File saved to file system '{}'", allureResults);
        return UploadResponse.builder().fileName(allureResults.getOriginalFilename()).uuid(path.getFileName().toString()).build();
    }

    /**
     * Validates the multipart upload at the controller boundary: non-null, non-empty,
     * correct Content-Type (when present) and {@code .zip} extension (when filename present).
     * Any violation is translated to HTTP 400 via {@link GlobalExceptionHandler}.
     */
    private static void requireNonEmptyZip(MultipartFile file, String paramName) {
        if (file == null || file.isEmpty() || file.getSize() == 0L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Parameter '" + paramName + "' must be a non-empty multipart file");
        }
        final String contentType = file.getContentType();
        if (StringUtils.isNotBlank(contentType)
            && !StringUtils.equalsAny(contentType, ZIP_MIME, ZIP_MIME_X)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Content-Type must be '" + ZIP_MIME + "' but was '" + contentType + "'");
        }
        final String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && !originalFilename.endsWith(".zip")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "File must have '.zip' extension but was '" + originalFilename + "'");
        }
    }
}
