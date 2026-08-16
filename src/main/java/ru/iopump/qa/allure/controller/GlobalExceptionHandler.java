package ru.iopump.qa.allure.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Central REST exception handler producing RFC 7807 {@link ProblemDetail} responses
 * with {@code application/problem+json} content type. Replaces per-controller
 * {@code @ExceptionHandler} methods to provide a single source of truth for
 * HTTP error translation across the API.
 */
@Slf4j
@RestControllerAdvice(basePackageClasses = {AllureReportController.class, AllureResultController.class})
public class GlobalExceptionHandler {

    private static final String TITLE_BAD_REQUEST = "Bad Request";
    private static final String TITLE_PAYLOAD_TOO_LARGE = "Payload Too Large";
    private static final String TITLE_INTERNAL_SERVER_ERROR = "Internal Server Error";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        final Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        final String detail = fieldErrors.isEmpty()
            ? "Request body validation failed"
            : "Request body validation failed: " + fieldErrors;
        final ProblemDetail problem = buildProblem(HttpStatus.BAD_REQUEST, TITLE_BAD_REQUEST, detail, request);
        problem.setProperty("errors", fieldErrors);
        log.warn("400 {} {} - validation errors: {}", request.getMethod(), request.getRequestURI(), fieldErrors);
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        final Map<String, String> violations = ex.getConstraintViolations() == null
            ? Map.of()
            : ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                    GlobalExceptionHandler::propertyPath,
                    ConstraintViolation::getMessage,
                    (left, right) -> left,
                    LinkedHashMap::new
                ));
        final String detail = violations.isEmpty()
            ? "Constraint violation"
            : "Constraint violation: " + violations;
        final ProblemDetail problem = buildProblem(HttpStatus.BAD_REQUEST, TITLE_BAD_REQUEST, detail, request);
        problem.setProperty("errors", violations);
        log.warn("400 {} {} - constraint violations: {}", request.getMethod(), request.getRequestURI(), violations);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        final String detail = "Malformed or unreadable request body";
        final ProblemDetail problem = buildProblem(HttpStatus.BAD_REQUEST, TITLE_BAD_REQUEST, detail, request);
        log.warn("400 {} {} - not readable: {}", request.getMethod(), request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return problem;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        final String detail = "Uploaded file exceeds the maximum allowed size";
        final ProblemDetail problem = buildProblem(HttpStatus.PAYLOAD_TOO_LARGE, TITLE_PAYLOAD_TOO_LARGE, detail, request);
        log.warn("413 {} {} - upload too large: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        final String message = ex.getMessage() == null ? "Illegal argument" : ex.getMessage();
        final ProblemDetail problem = buildProblem(HttpStatus.BAD_REQUEST, TITLE_BAD_REQUEST, message, request);
        log.warn("400 {} {} - illegal argument: {}", request.getMethod(), request.getRequestURI(), message);
        return problem;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        final HttpStatusCode statusCode = ex.getStatusCode();
        final String reason = ex.getReason() == null ? statusCode.toString() : ex.getReason();
        final String title = HttpStatus.valueOf(statusCode.value()).getReasonPhrase();
        final ProblemDetail problem = buildProblem(statusCode, title, reason, request);
        if (statusCode.is5xxServerError()) {
            log.error("{} {} {} - response status exception: {}", statusCode.value(), request.getMethod(), request.getRequestURI(), reason, ex);
        } else {
            log.warn("{} {} {} - response status exception: {}", statusCode.value(), request.getMethod(), request.getRequestURI(), reason);
        }
        return problem;
    }

    @ExceptionHandler(ErrorResponseException.class)
    public ProblemDetail handleErrorResponse(ErrorResponseException ex, HttpServletRequest request) {
        final HttpStatusCode statusCode = ex.getStatusCode();
        final String title = HttpStatus.valueOf(statusCode.value()).getReasonPhrase();
        final String detail = ex.getBody().getDetail() == null ? title : ex.getBody().getDetail();
        final ProblemDetail problem = buildProblem(statusCode, title, detail, request);
        if (statusCode.is5xxServerError()) {
            log.error("{} {} {} - error response: {}", statusCode.value(), request.getMethod(), request.getRequestURI(), detail, ex);
        } else {
            log.warn("{} {} {} - error response: {}", statusCode.value(), request.getMethod(), request.getRequestURI(), detail);
        }
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("500 {} {} - unhandled exception", request.getMethod(), request.getRequestURI(), ex);
        return buildProblem(
            HttpStatus.INTERNAL_SERVER_ERROR,
            TITLE_INTERNAL_SERVER_ERROR,
            "Unexpected server error. See server logs for details.",
            request
        );
    }

    private static ProblemDetail buildProblem(HttpStatusCode status, String title, String detail, HttpServletRequest request) {
        final ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    private static String propertyPath(ConstraintViolation<?> violation) {
        final String raw = violation.getPropertyPath() == null ? "" : violation.getPropertyPath().toString();
        if (raw.isEmpty()) {
            return "<root>";
        }
        final List<String> parts = List.of(raw.split("\\."));
        return parts.isEmpty() ? raw : parts.get(parts.size() - 1);
    }
}
