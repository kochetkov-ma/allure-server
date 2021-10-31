package ru.iopump.qa.allure.controller; //NOPMD

import com.google.common.base.Preconditions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.iopump.qa.allure.model.ResultResponse;
import ru.iopump.qa.allure.model.UploadResponse;
import ru.iopump.qa.allure.service.PathUtil;
import ru.iopump.qa.allure.service.ResultService;
import ru.iopump.qa.util.StreamUtil;

import javax.servlet.http.HttpServletResponse;
import javax.validation.ConstraintViolationException;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.stream.Collectors;

import static ru.iopump.qa.allure.gui.DateTimeResolver.zeroZone;

@RequiredArgsConstructor
@RestController
@Slf4j
@Validated
@RequestMapping(path = "/api/result")
public class AllureResultController {
    final static String CACHE = "results";
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
                localDateTime = LocalDateTime.ofInstant(attr.creationTime().toInstant(), zeroZone());
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

        final String contentType = allureResults.getContentType();

        // Check Content-Type
        if (StringUtils.isNotBlank(contentType)) {
            Preconditions.checkArgument(StringUtils.equalsAny(contentType, "application/zip", "application/x-zip-compressed"),
                    "Content-Type must be '%s' but '%s'", "application/zip", contentType);
        }

        // Check Extension
        if (allureResults.getOriginalFilename() != null) {
            Preconditions.checkArgument(allureResults.getOriginalFilename().endsWith(".zip"),
                    "File must have '.zip' extension but '%s'", allureResults.getOriginalFilename());
        }

        // Unzip and save
        Path path = resultService.unzipAndStore(allureResults.getInputStream());
        log.info("File saved to file system '{}'", allureResults);
        return UploadResponse.builder().fileName(allureResults.getOriginalFilename()).uuid(path.getFileName().toString()).build();
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public void constraintViolationException(HttpServletResponse response) throws IOException {
        response.sendError(HttpStatus.BAD_REQUEST.value());
    }
}