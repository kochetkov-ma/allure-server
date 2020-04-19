package ru.iopump.qa.allure.controller;

import static ru.iopump.qa.allure.service.PathUtil.str;
import static ru.iopump.qa.allure.service.ReportService.REPORT_PATH_DEFAULT;

import io.swagger.v3.oas.annotations.Operation;
import java.io.IOException;
import java.util.Collection;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.ConstraintViolationException;
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
import ru.iopump.qa.allure.model.ReportGenerateRequest;
import ru.iopump.qa.allure.model.ReportResponse;
import ru.iopump.qa.allure.service.ReportService;
import ru.iopump.qa.allure.service.ResultService;
import ru.iopump.qa.util.StreamUtil;

@RequiredArgsConstructor
@RestController
@Slf4j
@Validated
@RequestMapping(path = "/api/report")
public class AllureReportController {
    final static String CACHE = "reports";
    private final ReportService reportService;
    private final ResultService resultService;

    private static String baseUrl(HttpServletRequest request) {
        return String.format("%s://%s:%d/%s", request.getScheme(), request.getServerName(), request.getServerPort(), REPORT_PATH_DEFAULT);
    }

    @Operation(summary = "Get generated allure reports")
    @GetMapping
    public Collection<ReportResponse> getAllReports(@RequestParam(required = false) String path,
                                                    HttpServletRequest request) throws IOException {
        return StreamUtil.stream(getAllCached(request))
            .filter(i -> path == null || i.getPath().startsWith(path))
            .collect(Collectors.toUnmodifiableSet());
    }

    @Cacheable(CACHE) // caching results
    public Collection<ReportResponse> getAllCached(HttpServletRequest request) throws IOException {
        return StreamUtil.stream(reportService.getAll())
            .map(path -> new ReportResponse(str(path), baseUrl(request)))
            .collect(Collectors.toUnmodifiableSet());
    }

    @Operation(summary = "Generate report")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @CacheEvict(value = {CACHE, AllureResultController.CACHE}, allEntries = true) // update results cache
    public ReportResponse generateReport(@RequestBody ReportGenerateRequest reportGenerateRequest,
                                         HttpServletRequest request
    ) throws IOException {
        return new ReportResponse(
            str(reportService.generate(
                reportGenerateRequest.getReportSpec().getPathsAsPath(),
                reportGenerateRequest.getResultsAsPath(resultService.getStoragePath()),
                reportGenerateRequest.isDeleteResults(),
                reportGenerateRequest.getReportSpec().getExecutorInfo())), baseUrl(request)
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public void constraintViolationException(HttpServletResponse response) throws IOException {
        response.sendError(HttpStatus.BAD_REQUEST.value());
    }

    @Operation(summary = "Delete all reports")
    @DeleteMapping
    @CacheEvict(value = CACHE, allEntries = true)
    public Collection<ReportResponse> deleteAll(HttpServletRequest request) throws IOException {
        return reportService.deleteAll().stream()
            .map(p -> new ReportResponse(str(p), baseUrl(request)))
            .collect(Collectors.toUnmodifiableList());
    }
}