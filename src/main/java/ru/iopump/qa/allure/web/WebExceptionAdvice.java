package ru.iopump.qa.allure.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@ControllerAdvice(basePackageClasses = {
    ResultsWebController.class,
    ReportsWebController.class,
    AboutWebController.class
})
public class WebExceptionAdvice {

    private static final String FLASH_LEVEL = "level";
    private static final String FLASH_MESSAGE = "message";
    private static final String LEVEL_ERROR = "error";

    @ExceptionHandler(ConstraintViolationException.class)
    public String handleConstraintViolation(ConstraintViolationException ex,
                                            HttpServletRequest request,
                                            RedirectAttributes flash) {
        final Map<String, String> violations = ex.getConstraintViolations() == null
            ? Map.of()
            : ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                    WebExceptionAdvice::shortPath,
                    ConstraintViolation::getMessage,
                    (left, right) -> left,
                    LinkedHashMap::new
                ));
        final String summary = violations.entrySet().stream()
            .map(e -> e.getKey() + ": " + e.getValue())
            .collect(Collectors.joining("; "));
        final String message = summary.isEmpty()
            ? "Request rejected: constraint violation"
            : "Request rejected: " + summary;
        log.warn("400 {} {} - constraint violations: {}", request.getMethod(), request.getRequestURI(), violations);
        flash.addFlashAttribute("flash", Map.of(FLASH_LEVEL, LEVEL_ERROR, FLASH_MESSAGE, message));
        return "redirect:" + redirectTargetFor(request);
    }

    private static String redirectTargetFor(HttpServletRequest request) {
        final String uri = request.getRequestURI();
        if (uri.startsWith("/app/results")) {
            return "/app/results";
        }
        if (uri.startsWith("/app/reports")) {
            return "/app/reports";
        }
        return "/app/reports";
    }

    private static String shortPath(ConstraintViolation<?> violation) {
        final String raw = violation.getPropertyPath() == null ? "" : violation.getPropertyPath().toString();
        if (raw.isEmpty()) {
            return "<root>";
        }
        final String[] parts = raw.split("\\.");
        return parts.length == 0 ? raw : parts[parts.length - 1];
    }
}
