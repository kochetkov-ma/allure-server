package ru.iopump.qa.allure.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Static "About" page.
 * <p>Version data is sourced from the classpath resource {@code version.info}, written by the
 * {@code classes.doLast} block in {@code build.gradle}. No Spring Boot {@code BuildProperties}
 * dependency is required, avoiding a duplicate version source.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public final class AboutWebController {

    private static final String VERSION_RESOURCE = "version.info";
    private static final String DEFAULT_VERSION = "dev";

    @GetMapping("/app/about")
    public String about(Model model) {
        model.addAttribute("title", "About");
        model.addAttribute("activeNav", "about");
        model.addAttribute("version", readVersion());
        model.addAttribute("buildTime", readBuildTime());
        return "about/index";
    }

    private String readVersion() {
        final ClassPathResource resource = new ClassPathResource(VERSION_RESOURCE);
        if (!resource.exists()) {
            log.debug("Classpath resource '{}' not found — falling back to '{}'", VERSION_RESOURCE, DEFAULT_VERSION);
            return DEFAULT_VERSION;
        }
        try (InputStream inputStream = resource.getInputStream()) {
            final String version = IOUtils.toString(inputStream, StandardCharsets.UTF_8).trim();
            return StringUtils.isBlank(version) ? DEFAULT_VERSION : version;
        } catch (IOException e) {
            log.warn("Failed to read '{}' from classpath: {}", VERSION_RESOURCE, e.getMessage());
            return DEFAULT_VERSION;
        }
    }

    private Instant readBuildTime() {
        final ClassPathResource resource = new ClassPathResource(VERSION_RESOURCE);
        if (!resource.exists()) {
            return null;
        }
        try {
            final long lastModified = resource.lastModified();
            return lastModified > 0L ? Instant.ofEpochMilli(lastModified) : null;
        } catch (IOException e) {
            log.debug("Cannot read lastModified for '{}': {}", VERSION_RESOURCE, e.getMessage());
            return null;
        }
    }
}
