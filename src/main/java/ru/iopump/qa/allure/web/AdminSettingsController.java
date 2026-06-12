package ru.iopump.qa.allure.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.iopump.qa.allure.entity.UserEntity;
import ru.iopump.qa.allure.security.CurrentUserProvider;
import ru.iopump.qa.allure.service.SystemSettingsService;
import ru.iopump.qa.allure.web.dto.SystemSettingsView;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin-only runtime settings UI at {@code /app/admin/settings}. Currently
 * exposes the {@code requireApiAuth} toggle — when on, {@code /api/**} requires
 * authentication (Basic or X-API-Token); when off, anonymous API traffic is
 * treated as guest (transitional default for backward compatibility).
 */
@Controller
@RequestMapping("/app/admin/settings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminSettingsController {

    private static final String VIEW_INDEX = "admin/settings/index";
    private static final String REDIRECT_INDEX = "redirect:/app/admin/settings";
    private static final String FLASH_KEY = "flash";

    private final SystemSettingsService systemSettingsService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public String index(Model model) {
        model.addAttribute("settings", SystemSettingsView.from(systemSettingsService.current()));
        model.addAttribute("title", "System Settings");
        model.addAttribute("activeNav", "admin-settings");
        return VIEW_INDEX;
    }

    @PostMapping("/require-api-auth")
    public String updateRequireApiAuth(@RequestParam(name = "requireApiAuth", defaultValue = "false") boolean requireApiAuth,
                                       RedirectAttributes flash) {
        final UserEntity actor = currentUserProvider.current();
        systemSettingsService.updateRequireApiAuth(requireApiAuth, actor.getUsername());
        final String message = requireApiAuth
            ? "API authentication is now REQUIRED. Anonymous /api/** requests will receive 401."
            : "API authentication is now OPTIONAL. Anonymous /api/** requests are accepted as guest.";
        flash.addFlashAttribute(FLASH_KEY, toastMap("success", message));
        return REDIRECT_INDEX;
    }

    private static Map<String, String> toastMap(String level, String message) {
        final Map<String, String> map = new LinkedHashMap<>();
        map.put("level", level);
        map.put("message", message == null ? "" : message);
        return map;
    }
}
