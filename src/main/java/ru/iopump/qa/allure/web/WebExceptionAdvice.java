package ru.iopump.qa.allure.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@ControllerAdvice(basePackageClasses = {
    ResultsWebController.class,
    ReportsWebController.class,
    AboutWebController.class,
    ProfileWebController.class,
    AdminUsersController.class,
    AdminSettingsController.class,
    PasswordChangeController.class
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

    /**
     * Bean-validation failure on a {@code @Valid @ModelAttribute} form that has no adjacent
     * {@code BindingResult} parameter — Spring raises {@code MethodArgumentNotValidException}
     * (a {@link BindException} subtype). Without this handler the request would render a raw
     * container 400 (whitelabel is excluded). Translate the field errors into the same
     * flash-toast redirect every other web error path uses.
     */
    @ExceptionHandler(BindException.class)
    public String handleBindException(BindException ex,
                                      HttpServletRequest request,
                                      RedirectAttributes flash) {
        final String summary = ex.getBindingResult().getAllErrors().stream()
            .map(WebExceptionAdvice::formatBindingError)
            .collect(Collectors.joining("; "));
        final String message = summary.isEmpty()
            ? "Form rejected: validation error"
            : "Form rejected: " + summary;
        log.warn("400 {} {} - form binding errors: {}", request.getMethod(), request.getRequestURI(), summary);
        flash.addFlashAttribute("flash", Map.of(FLASH_LEVEL, LEVEL_ERROR, FLASH_MESSAGE, message));
        return "redirect:" + redirectTargetFor(request);
    }

    /**
     * Domain not-found raised from a web-layer service (e.g.
     * {@code UserManagementService} mutating a user id that no longer resolves to a
     * persisted row — a stale grid action or double-submit). The exception carries a
     * human-safe message, so we surface it directly as a flash error and redirect back to
     * the originating page instead of a whitelabel 500.
     *
     * <p>Scoped to the typed {@link ru.iopump.qa.allure.service.UserNotFoundException} on
     * purpose: a genuine, unexpected {@link IllegalArgumentException} (a real programming
     * bug) is intentionally NOT caught here so it propagates to a loud 500 rather than being
     * masked as a friendly toast that may leak an internal message.
     */
    @ExceptionHandler(ru.iopump.qa.allure.service.UserNotFoundException.class)
    public String handleUserNotFound(ru.iopump.qa.allure.service.UserNotFoundException ex,
                                     HttpServletRequest request,
                                     RedirectAttributes flash) {
        final String message = ex.getMessage() == null || ex.getMessage().isBlank()
            ? "The requested user could not be found."
            : ex.getMessage();
        log.warn("404 {} {} - user not found: {}", request.getMethod(), request.getRequestURI(), message);
        flash.addFlashAttribute("flash", Map.of(FLASH_LEVEL, LEVEL_ERROR, FLASH_MESSAGE, message));
        return "redirect:" + redirectTargetFor(request);
    }

    /**
     * A request parameter could not be converted to the declared type — e.g. the bulk-delete
     * {@code @RequestParam("uuids") List<UUID>} receiving a malformed (non-UUID) token from a
     * tampered or scripted POST. Spring raises {@link MethodArgumentTypeMismatchException},
     * which is a {@code TypeMismatchException} and therefore NOT covered by the
     * {@link BindException} handler above. Translate it to the same flash-toast redirect every
     * other web error path uses instead of a raw container 400. Covers both reports and
     * results bulk-delete. We deliberately do NOT echo the raw value/message to avoid leaking
     * internal detail.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public String handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                     HttpServletRequest request,
                                     RedirectAttributes flash) {
        final String parameter = ex.getName() == null ? "request parameter" : ex.getName();
        final String message = "Request rejected: invalid value for '" + parameter + "'.";
        log.warn("400 {} {} - type mismatch on parameter '{}'", request.getMethod(), request.getRequestURI(), parameter);
        flash.addFlashAttribute("flash", Map.of(FLASH_LEVEL, LEVEL_ERROR, FLASH_MESSAGE, message));
        return "redirect:" + redirectTargetFor(request);
    }

    private static String formatBindingError(ObjectError error) {
        final String field = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
        final String detail = error.getDefaultMessage() == null ? error.getCode() : error.getDefaultMessage();
        return field + ": " + detail;
    }

    private static String redirectTargetFor(HttpServletRequest request) {
        final String uri = request.getRequestURI();
        if (uri.startsWith("/app/results")) {
            return "/app/results";
        }
        if (uri.startsWith("/app/reports")) {
            return "/app/reports";
        }
        if (uri.startsWith("/app/profile/password")) {
            return "/app/profile/password";
        }
        if (uri.startsWith("/app/profile")) {
            return "/app/profile";
        }
        if (uri.startsWith("/app/admin/users")) {
            return "/app/admin/users";
        }
        if (uri.startsWith("/app/admin/settings")) {
            return "/app/admin/settings";
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
