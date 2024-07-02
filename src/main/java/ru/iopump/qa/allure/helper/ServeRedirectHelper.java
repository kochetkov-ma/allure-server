package ru.iopump.qa.allure.helper;

import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.RedirectView;
import ru.iopump.qa.allure.properties.AllureProperties;

import java.util.Map;

import static ru.iopump.qa.allure.helper.Util.concatParts;

@RequiredArgsConstructor
@Controller
@Slf4j
public class ServeRedirectHelper {
    public static final String INDEX_HTML = "/index.html";
    public static final char CHAR = '/';
    private final AllureProperties cfg;
    private final Map<String, String> redirectReportPaths = Maps.newConcurrentMap();

    @GetMapping("${allure.reports.path}**")
    public View reportPathRedirectToUuid(HttpServletRequest request) {
        final String from = handleFrom(request.getServletPath());
        final String to = redirectReportPaths.get(from);

        if (to != null) {
            String redirectTo = concatParts(request.getServletContext().getContextPath(), to);
            log.info("Redirect evaluated: '{}' -> '{}'", from, redirectTo);
            return new RedirectView(redirectTo, false);
        }

        log.info("Redirect NOT evaluated: '{}'", from);
        throw new RuntimeException("Redirect NOT evaluated " + from);
    }

    public void mapRequestTo(String urlPath, String staticResourcePath) {

        final String from = handleFrom(urlPath);
        final String to = handleTo(staticResourcePath);

        log.info("Redirect spec has been added: '{}' -> '{}'", from, to);
        redirectReportPaths.put(from, to);
    }

    //// PRIVATE ////

    private String handleFrom(@NonNull String candidate) {
        String result = candidate;

        // Replace Windows '\'
        result = result.replaceAll("\\\\", "/");

        // Remove all '/'
        result = StringUtils.strip(result, "/");

        // Add base url if not
        if (!result.startsWith(cfg.reports().path())) {
            result = cfg.reports().path() + result;
        }

        // Remove '/index.html' if exists
        if (result.endsWith(INDEX_HTML)) {
            result = StringUtils.removeEnd(result, INDEX_HTML);
        }

        // Must be 'reports/test'
        return result;
    }

    private String handleTo(@NonNull String candidate) {
        String result = candidate;

        // Replace Windows '\'
        result = result.replaceAll("\\\\", "/");

        // Add first '/' if not
        if (result.charAt(0) != CHAR) {
            result = CHAR + result;
        }

        // Add '/index.html' if not
        if (!result.endsWith(INDEX_HTML)) {
            result = result + INDEX_HTML;
        }

        // Must be '/allure/123456890/index.html'
        return result;
    }
}
