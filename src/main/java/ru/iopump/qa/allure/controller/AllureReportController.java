package ru.iopump.qa.allure.controller;

import io.qameta.allure.entity.ExecutorInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ru.iopump.qa.allure.entity.ReportEntity;
import ru.iopump.qa.allure.model.ReportGenerateRequest;
import ru.iopump.qa.allure.model.ReportResponse;
import ru.iopump.qa.allure.properties.AllureProperties;
import ru.iopump.qa.allure.service.JpaReportService;
import ru.iopump.qa.allure.service.PathUtil;
import ru.iopump.qa.allure.service.ResultService;
import ru.iopump.qa.util.StreamUtil;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.stream.Collectors;

import static ru.iopump.qa.allure.helper.Util.url;

@SuppressWarnings({"unused", "UnusedReturnValue"})
@RequiredArgsConstructor
@RestController
@Slf4j
@Validated
@RequestMapping(path = "/api/report")
public class AllureReportController {
    final static String CACHE = "reports";
    private static final String ZIP_MIME = "application/zip";
    private static final String ZIP_MIME_X = "application/x-zip-compressed";

    private final JpaReportService reportService;
    private final ResultService resultService;
    private final AllureProperties allureProperties;

    public String baseUrl() {
        return url(allureProperties);
    }

    @Operation(summary = "Get generated allure reports")
    @GetMapping
    public Collection<ReportResponse> getAllReports(@RequestParam(required = false) String path) {
        return StreamUtil.stream(getAllCached())
            .filter(i -> path == null || i.getPath().startsWith(path))
            .collect(Collectors.toUnmodifiableSet());
    }

    @Cacheable(CACHE) // caching results
    public Collection<ReportResponse> getAllCached() {
        return StreamUtil.stream(reportService.getAll())
            .map(entity -> new ReportResponse(
                entity.getUuid(),
                entity.getPath(),
                entity.generateUrl(baseUrl(), allureProperties.reports().dir()),
                entity.generateLatestUrl(baseUrl(), allureProperties.reports().path())
            ))
            .collect(Collectors.toUnmodifiableList());
    }

    @Operation(summary = "Generate report")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @CacheEvict(value = {CACHE, AllureResultController.CACHE}, allEntries = true) // update results cache
    public ReportResponse generateReport(@RequestBody @Valid ReportGenerateRequest reportGenerateRequest) throws IOException {

        final ReportEntity reportEntity = reportService.generate(
            reportGenerateRequest.getReportSpec().getPathsAsPath(),
            reportGenerateRequest.getResultsAsPath(resultService.getStoragePath()),
            reportGenerateRequest.isDeleteResults(),
            reportGenerateRequest.getReportSpec().getExecutorInfo(),
            baseUrl()
        );

        return new ReportResponse(
            reportEntity.getUuid(),
            reportEntity.getPath(),
            reportEntity.generateUrl(baseUrl(), allureProperties.reports().dir()),
            reportEntity.generateLatestUrl(baseUrl(), allureProperties.reports().path())
        );
    }


    @SneakyThrows
    @Operation(summary = "Upload allure-report.zip with generated allure report files")
    @PostMapping(value = "{reportPath}", consumes = {"multipart/form-data"})
    @ResponseStatus(HttpStatus.CREATED)
    @CacheEvict(value = CACHE, allEntries = true) // update results cache
    public ReportResponse uploadReport(
        @PathVariable("reportPath") @NonNull @NotBlank(message = "reportPath must not be blank") String reportPath,
        @Parameter(description = "File as multipart body. File must be an zip archive and not be empty. Nested type is 'application/zip'",
            name = "allureResults",
            example = "allure-result.zip",
            required = true,
            content = @Content(mediaType = "application/zip")
        )
        @RequestParam MultipartFile allureReportArchive) {

        requireNonEmptyZip(allureReportArchive, "allureReportArchive");

        // Unzip and save
        ReportEntity reportEntity = reportService
            .uploadReport(reportPath, allureReportArchive.getInputStream(), new ExecutorInfo(), baseUrl());
        log.info("File saved to file system '{}'", allureReportArchive);

        return new ReportResponse(
            reportEntity.getUuid(),
            reportEntity.getPath(),
            reportEntity.generateUrl(baseUrl(), allureProperties.reports().dir()),
            reportEntity.generateLatestUrl(baseUrl(), allureProperties.reports().path())
        );
    }

    @Operation(summary = "Clear all history reports")
    @DeleteMapping("/history")
    @CacheEvict(value = CACHE, allEntries = true)
    public Collection<ReportResponse> deleteAllHistory() {
        return reportService.clearAllHistory().stream()
            .map(entity -> new ReportResponse(
                entity.getUuid(),
                entity.getPath(),
                entity.generateUrl(baseUrl(), allureProperties.reports().dir()),
                entity.generateLatestUrl(baseUrl(), allureProperties.reports().path())
            ))
            .collect(Collectors.toUnmodifiableList());
    }

    @Operation(summary = "Delete a single report by UUID")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Report deleted"),
        @ApiResponse(responseCode = "404", description = "Report with the given UUID was not found")
    })
    @DeleteMapping("/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @CacheEvict(value = CACHE, allEntries = true)
    public void deleteReport(
        @Parameter(description = "UUID of the report to delete", required = true)
        @PathVariable @NotBlank @Pattern(regexp = PathUtil.UUID_PATTERN) String uuid
    ) throws IOException {
        reportService.deleteByUuid(uuid);
    }

    @Operation(summary = "Delete all reports or older than date in epoch seconds")
    @DeleteMapping
    @CacheEvict(value = CACHE, allEntries = true)
    public Collection<ReportResponse> deleteAll(@RequestParam(required = false) Long seconds) throws IOException {
        Collection<ReportEntity> deleted;
        if (seconds == null) {
            deleted = reportService.deleteAll();
        } else {
            LocalDateTime boundaryDate = LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds), ZoneId.of("UTC"));
            deleted = reportService.deleteAllOlderThanDate(boundaryDate);
        }
        return deleted.stream()
            .map(entity -> new ReportResponse(
                entity.getUuid(),
                entity.getPath(),
                entity.generateUrl(baseUrl(), allureProperties.reports().dir()),
                entity.generateLatestUrl(baseUrl(), allureProperties.reports().path())
            ))
            .collect(Collectors.toUnmodifiableList());
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
