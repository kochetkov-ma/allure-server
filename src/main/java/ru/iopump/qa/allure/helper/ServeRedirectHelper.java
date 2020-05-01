package ru.iopump.qa.allure.helper;

import com.google.common.collect.Maps;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.RedirectView;
import ru.iopump.qa.allure.AppCfg;

@RequiredArgsConstructor
@Controller
@Slf4j
public class ServeRedirectHelper {
    private final AppCfg cfg;
    private final Map<String, String> redirectReportPaths = Maps.newConcurrentMap();

    @GetMapping("${allure.reports.path}**")
    public View reportPathRedirectToUuid(HttpServletRequest request) {
        final String from = handleFrom(request.getServletPath());
        final String to = redirectReportPaths.get(from);

        if (to != null) {
            log.info("Redirect evaluated: '{}' -> '{}'", from, to);
            return new RedirectView(to, false);
        }

        log.info("Redirect NOT evaluated: '{}'", from);
        throw new RuntimeException();
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
        if (!result.startsWith(cfg.reportsPath())) {
            result = cfg.reportsPath() + result;
        }

        // Remove '/index.html' if exists
        if (result.endsWith("/index.html")) {
            result = StringUtils.removeEnd(result, "/index.html");
        }

        // Must be 'reports/test'
        return result;
    }

    private String handleTo(@NonNull String candidate) {
        String result = candidate;

        // Replace Windows '\'
        result = result.replaceAll("\\\\", "/");

        // Add first '/' if not
        if (!result.startsWith("/")) {
            result = "/" + result;
        }

        // Add '/index.html' if not
        if (!result.endsWith("/index.html")) {
            result = result + "/index.html";
        }

        // Must be '/allure/123456890/index.html'
        return result;
    }
}
