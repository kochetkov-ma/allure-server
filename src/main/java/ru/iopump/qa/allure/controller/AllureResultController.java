package ru.iopump.qa.allure.controller;

import com.google.common.base.Preconditions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletResponse;
import javax.validation.ConstraintViolationException;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.iopump.qa.allure.model.ResultResponse;
import ru.iopump.qa.allure.model.UploadResponse;
import ru.iopump.qa.allure.service.ResultService;
import ru.iopump.qa.util.StreamUtil;

@RequiredArgsConstructor
@RestController
@Slf4j
@Validated
@RequestMapping(path = "/api/result")
public class AllureResultController {
    public final static String UUID_PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}";
    final static String CACHE = "results";
    private final ResultService resultService;

    @Operation(summary = "Delete all allure results")
    @DeleteMapping(path = "/result")
    @CacheEvict(value = CACHE, allEntries = true) // clear cache
    public Collection<ResultResponse> deleteAllResults() throws IOException {
        var res = getAllResult();
        resultService.deleteAll();
        return res;
    }

    @Operation(summary = "Get allure result by uuid")
    @GetMapping(path = "/{uuid}")
    public ResultResponse getResult(@PathVariable @NotBlank @Pattern(regexp = UUID_PATTERN) String uuid) throws IOException {
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
            long size;
            try {
                size = Files.walk(p, 1).skip(1).count();
            } catch (IOException e) {
                size = Long.MIN_VALUE;
            }
            return ResultResponse.builder().uuid(p.getFileName().toString()).size(size).build();

        }).collect(Collectors.toUnmodifiableSet());
    }

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
    ) throws IOException {

        final String contentType = allureResults.getContentType();

        // Check Content-Type
        if (StringUtils.isNotBlank(contentType)) {
            Preconditions.checkArgument("application/zip".equals(contentType),
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