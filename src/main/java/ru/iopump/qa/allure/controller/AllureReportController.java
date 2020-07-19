package ru.iopump.qa.allure.controller;

import static ru.iopump.qa.allure.helper.Util.url;

import io.swagger.v3.oas.annotations.Operation;
import java.io.IOException;
import java.util.Collection;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletResponse;
import javax.validation.ConstraintViolationException;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.iopump.qa.allure.AppCfg;
import ru.iopump.qa.allure.entity.ReportEntity;
import ru.iopump.qa.allure.model.ReportGenerateRequest;
import ru.iopump.qa.allure.model.ReportResponse;
import ru.iopump.qa.allure.service.JpaReportService;
import ru.iopump.qa.allure.service.ResultService;
import ru.iopump.qa.util.StreamUtil;

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
    private final AppCfg appCfg;

    public String baseUrl() {
        return url(appCfg);
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
            .map(entity -> new ReportResponse(entity.getUuid(), entity.getPath(), entity.generateUrl(baseUrl())))
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

        return new ReportResponse(reportEntity.getUuid(), reportEntity.getPath(), reportEntity.generateUrl(baseUrl()));
    }

    @Operation(summary = "Clear all history reports")
    @DeleteMapping("/history")
    @CacheEvict(value = CACHE, allEntries = true)
    public Collection<ReportResponse> deleteAllHistory() {
        return reportService.clearAllHistory().stream()
            .map(entity -> new ReportResponse(entity.getUuid(), entity.getPath(), entity.generateUrl(baseUrl())))
            .collect(Collectors.toUnmodifiableList());
    }

    @Operation(summary = "Delete all reports")
    @DeleteMapping
    @CacheEvict(value = CACHE, allEntries = true)
    public Collection<ReportResponse> deleteAll() throws IOException {
        return reportService.deleteAll().stream()
            .map(entity -> new ReportResponse(entity.getUuid(), entity.getPath(), entity.generateUrl(baseUrl())))
            .collect(Collectors.toUnmodifiableList());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    private void constraintViolationException(HttpServletResponse response) throws IOException { //NOPMD
        response.sendError(HttpStatus.BAD_REQUEST.value());
    }
}