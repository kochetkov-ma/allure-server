package ru.iopump.qa.allure.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.iopump.qa.allure.entity.ApiTokenEntity;
import ru.iopump.qa.allure.entity.UserEntity;
import ru.iopump.qa.allure.entity.UserRole;
import ru.iopump.qa.allure.security.CurrentUserProvider;
import ru.iopump.qa.allure.service.ApiTokenService;
import ru.iopump.qa.allure.service.TokenLimitExceededException;
import ru.iopump.qa.allure.service.TokenPolicy;
import ru.iopump.qa.allure.web.dto.CreateTokenForm;
import ru.iopump.qa.allure.web.dto.TokenRow;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-rendered profile page at {@code /app/profile}. Shows the current user
 * block and a table of personal API tokens, plus a dialog for creating new ones.
 * <p>
 * The shared guest account is strictly READ-ONLY and CANNOT manage API tokens.
 * Only persisted, non-guest users may create or revoke tokens; every guest
 * principal — whether the seeded (persisted) guest or the anonymous (unpersisted)
 * fallback — is rejected with 403 on token mutations and rendered without token
 * controls. This mirrors the authoritative authorization rule enforced in
 * {@code SecurityConfiguration} (see {@link TokenPolicy} for per-role caps).
 */
@Controller
@RequestMapping("/app/profile")
@RequiredArgsConstructor
@Validated
@Slf4j
public class ProfileWebController {

    private static final String VIEW_INDEX = "profile/index";
    private static final String REDIRECT_INDEX = "redirect:/app/profile";
    private static final String FLASH_JUST_CREATED = "justCreatedToken";
    private static final String FLASH_KEY = "flash";

    private final ApiTokenService apiTokenService;
    private final CurrentUserProvider currentUserProvider;
    private final TokenPolicy tokenPolicy;

    @GetMapping
    public String index(Model model) {
        final UserEntity currentUser = currentUserProvider.current();
        populate(model, currentUser);
        model.addAttribute("title", "Profile");
        model.addAttribute("activeNav", "profile");
        return VIEW_INDEX;
    }

    @PostMapping("/tokens")
    public String createToken(@Valid @ModelAttribute CreateTokenForm form,
                              RedirectAttributes flash) {
        final UserEntity currentUser = currentUserProvider.current();
        requireTokenManagementAllowed(currentUser);
        try {
            final ApiTokenService.TokenIssueResult issued =
                apiTokenService.createToken(currentUser, form.name(), form.ttl());
            flash.addFlashAttribute(FLASH_JUST_CREATED, Map.of(
                "id", issued.entityId().toString(),
                "name", form.name(),
                "plain", issued.plainToken()
            ));
            flash.addFlashAttribute(FLASH_KEY, toastMap("success",
                "Token '" + form.name() + "' created. Copy it now — it will not be shown again."));
            log.info("User '{}' created API token '{}'", currentUser.getUsername(), form.name());
        } catch (IllegalArgumentException ex) {
            log.warn("Token creation rejected for '{}': {}", currentUser.getUsername(), ex.getMessage());
            flash.addFlashAttribute(FLASH_KEY, toastMap("error", ex.getMessage()));
        }
        return REDIRECT_INDEX;
    }

    /**
     * Revoke a token. The template renders a standard POST form with a hidden
     * {@code _method=delete}; Spring's HiddenHttpMethodFilter rewrites the verb.
     */
    @DeleteMapping("/tokens/{id}")
    public String revokeToken(@PathVariable("id") UUID id,
                              RedirectAttributes flash) {
        final UserEntity currentUser = currentUserProvider.current();
        requireTokenManagementAllowed(currentUser);
        final boolean revoked = apiTokenService.revoke(currentUser, id);
        if (revoked) {
            flash.addFlashAttribute(FLASH_KEY, toastMap("success", "Token revoked"));
            log.info("User '{}' revoked API token '{}'", currentUser.getUsername(), id);
        } else {
            flash.addFlashAttribute(FLASH_KEY, toastMap("error", "Token not found or already revoked"));
        }
        return REDIRECT_INDEX;
    }

    ///// PRIVATE /////

    private void populate(Model model, UserEntity currentUser) {
        final Instant now = Instant.now();
        final UserRole role = currentUser == null ? UserRole.GUEST : currentUser.getRole();
        final boolean canManageTokens = currentUser != null
            && currentUser.getId() != null
            && role != UserRole.GUEST;
        final List<TokenRow> rows;
        final long activeCount;
        if (canManageTokens) {
            final List<ApiTokenEntity> tokens = apiTokenService.listAll(currentUser);
            rows = tokens.stream().map(t -> TokenRow.from(t, now)).toList();
            activeCount = rows.stream().filter(r -> "active".equals(r.status())).count();
        } else {
            rows = List.of();
            activeCount = 0L;
        }
        model.addAttribute("tokens", rows);
        model.addAttribute("expirations", CreateTokenForm.TokenExpiration.values());
        model.addAttribute("activeTokenCount", activeCount);
        model.addAttribute("maxActiveTokens", tokenPolicy.maxActiveTokens(role));
    }

    @ExceptionHandler(TokenLimitExceededException.class)
    public String handleTokenLimitExceeded(TokenLimitExceededException ex, RedirectAttributes flash) {
        log.warn("Token limit exceeded: role={} current={} limit={}",
            ex.getRole(), ex.getCurrentActive(), ex.getLimit());
        flash.addFlashAttribute(FLASH_KEY, toastMap("error", ex.getMessage()));
        return REDIRECT_INDEX;
    }

    private static void requireTokenManagementAllowed(UserEntity currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Authentication required to manage API tokens");
        }
        if (currentUser.getRole() == UserRole.GUEST) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Guest account is read-only and cannot manage API tokens");
        }
    }

    private static Map<String, String> toastMap(String level, String message) {
        final Map<String, String> map = new LinkedHashMap<>();
        map.put("level", level);
        map.put("message", message == null ? "" : message);
        return map;
    }
}
