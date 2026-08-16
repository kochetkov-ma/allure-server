package ru.iopump.qa.allure.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.iopump.qa.allure.entity.UserEntity;
import ru.iopump.qa.allure.security.CurrentUserProvider;
import ru.iopump.qa.allure.service.PasswordChangeService;
import ru.iopump.qa.allure.service.WeakPasswordException;
import ru.iopump.qa.allure.web.dto.PasswordChangeForm;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Self-service password change at {@code /app/profile/password}. When reached
 * with {@code ?forced=true} (by {@code ForcePasswordChangeFilter}) the template
 * surfaces an alert explaining the rotation requirement.
 */
@Controller
@RequestMapping("/app/profile/password")
@RequiredArgsConstructor
@Slf4j
public class PasswordChangeController {

    private static final String VIEW_INDEX = "profile/password";
    private static final String REDIRECT_SELF = "redirect:/app/profile/password";
    private static final String REDIRECT_SELF_FORCED = "redirect:/app/profile/password?forced=true";
    private static final String REDIRECT_REPORTS = "redirect:/app/reports";
    private static final String FLASH_KEY = "flash";
    private static final String FORCED_PARAM = "forced";

    private final PasswordChangeService passwordChangeService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public String index(@RequestParam(name = "forced", defaultValue = "false") boolean forced,
                        Model model) {
        model.addAttribute("forced", forced);
        model.addAttribute("title", "Change Password");
        model.addAttribute("activeNav", "profile");
        return VIEW_INDEX;
    }

    @PostMapping
    public String submit(@Valid @ModelAttribute PasswordChangeForm form,
                         BindingResult binding,
                         @RequestParam(name = FORCED_PARAM, defaultValue = "false") boolean forced,
                         RedirectAttributes flash) {
        final UserEntity actor = currentUserProvider.current();
        if (actor == null || actor.getId() == null) {
            flash.addFlashAttribute(FLASH_KEY, toastMap("error",
                "You must be signed in to change a password."));
            return selfRedirect(forced);
        }
        // A blank/invalid field (@NotBlank) is handled here — not by the global WebExceptionAdvice —
        // so the rotation banner (?forced=true) is preserved using the same authoritative forced
        // signal (request param OR persisted passwordTemporary) every other error path in this
        // controller uses. Routing it through the advice would drop ?forced and briefly hide the banner.
        if (binding.hasErrors()) {
            flash.addFlashAttribute(FLASH_KEY, toastMap("error",
                "Form rejected: " + firstFieldError(binding)));
            return selfRedirect(isForced(forced, actor));
        }
        if (!form.confirmed()) {
            flash.addFlashAttribute(FLASH_KEY, toastMap("error",
                "New password and confirmation do not match."));
            return selfRedirect(isForced(forced, actor));
        }
        passwordChangeService.change(actor.getId(), form.currentPassword(), form.newPassword());
        flash.addFlashAttribute(FLASH_KEY, toastMap("success", "Password changed."));
        return REDIRECT_REPORTS;
    }

    @ExceptionHandler(BadCredentialsException.class)
    public String handleBadCredentials(BadCredentialsException ex,
                                       HttpServletRequest request,
                                       RedirectAttributes flash) {
        flash.addFlashAttribute(FLASH_KEY, toastMap("error", ex.getMessage()));
        return selfRedirect(isForced(request));
    }

    @ExceptionHandler(WeakPasswordException.class)
    public String handleWeakPassword(WeakPasswordException ex,
                                     HttpServletRequest request,
                                     RedirectAttributes flash) {
        flash.addFlashAttribute(FLASH_KEY, toastMap("error", ex.getMessage()));
        return selfRedirect(isForced(request));
    }

    /**
     * Keep the {@code ?forced=true} framing on every error redirect so the rotation-required
     * banner persists until the change actually succeeds. A failed forced change must not
     * silently drop the user back to the ordinary change page.
     */
    private static String selfRedirect(boolean forced) {
        return forced ? REDIRECT_SELF_FORCED : REDIRECT_SELF;
    }

    /**
     * A change is forced when the request carried {@code ?forced=true} OR the persisted actor
     * still holds a temporary (admin-issued) password. The persisted flag is authoritative —
     * it survives even when the submitting form omits the query parameter, so the rotation
     * banner persists across a failed mandatory change without a template round-trip.
     */
    private static boolean isForced(boolean forcedParam, UserEntity actor) {
        return forcedParam || (actor != null && actor.isPasswordTemporary());
    }

    private boolean isForced(HttpServletRequest request) {
        final boolean forcedParam = Boolean.parseBoolean(request.getParameter(FORCED_PARAM));
        final UserEntity actor = currentUserProvider.current();
        return isForced(forcedParam, actor);
    }

    /**
     * Render the first binding error as {@code field: message} for the flash toast. Mirrors
     * {@code WebExceptionAdvice#formatBindingError} so the user-facing wording is identical
     * whether the validation error is caught here or (for other forms) by the global advice.
     */
    private static String firstFieldError(BindingResult binding) {
        return binding.getAllErrors().stream()
            .findFirst()
            .map(PasswordChangeController::formatBindingError)
            .orElse("validation error");
    }

    private static String formatBindingError(ObjectError error) {
        final String field = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
        final String detail = error.getDefaultMessage() == null ? error.getCode() : error.getDefaultMessage();
        return field + ": " + detail;
    }

    private static Map<String, String> toastMap(String level, String message) {
        final Map<String, String> map = new LinkedHashMap<>();
        map.put("level", level);
        map.put("message", message == null ? "" : message);
        return map;
    }
}
