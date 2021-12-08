package ru.iopump.qa.allure.controller; //NOPMD

import com.google.common.base.Preconditions;
import io.qameta.allure.entity.ExecutorInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.iopump.qa.allure.entity.ReportEntity;
import ru.iopump.qa.allure.model.ReportGenerateRequest;
import ru.iopump.qa.allure.model.ReportResponse;
import ru.iopump.qa.allure.properties.AllureProperties;
import ru.iopump.qa.allure.service.JpaReportService;
import ru.iopump.qa.allure.service.ResultService;
import ru.iopump.qa.util.StreamUtil;

import javax.servlet.http.HttpServletResponse;
import javax.validation.ConstraintViolationException;
import javax.validation.Valid;
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
            @PathVariable("reportPath") @NonNull String reportPath,
            @Parameter(description = "File as multipart body. File must be an zip archive and not be empty. Nested type is 'application/zip'",
                    name = "allureResults",
                    example = "allure-result.zip",
                    required = true,
                    content = @Content(mediaType = "application/zip")
            )
            @RequestParam MultipartFile allureReportArchive) {

        final String contentType = allureReportArchive.getContentType();

        // Check Content-Type
        if (StringUtils.isNotBlank(contentType)) {
            Preconditions.checkArgument(StringUtils.equalsAny(contentType, "application/zip", "application/x-zip-compressed"),
                    "Content-Type must be '%s' but '%s'", "application/zip", contentType);
        }

        // Check Extension
        if (allureReportArchive.getOriginalFilename() != null) {
            Preconditions.checkArgument(allureReportArchive.getOriginalFilename().endsWith(".zip"),
                    "File must have '.zip' extension but '%s'", allureReportArchive.getOriginalFilename());
        }

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

    @ExceptionHandler(ConstraintViolationException.class)
    private void constraintViolationException(HttpServletResponse response) throws IOException { //NOPMD
        response.sendError(HttpStatus.BAD_REQUEST.value());
    }
}
