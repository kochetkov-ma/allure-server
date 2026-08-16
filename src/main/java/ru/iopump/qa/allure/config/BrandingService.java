package ru.iopump.qa.allure.config;

import com.google.common.base.Preconditions;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;

/**
 * Spring-owned, stateless collaborator that applies Brew.QA branding (favicon, sidebar logo, home
 * link) to a single generated Allure report directory.
 *
 * <p>This is the single source of truth for branding logic. It is injected directly into
 * {@link ru.iopump.qa.allure.helper.plugin.BrandingPlugin} (per-generation hook) and
 * {@link BrandingSweepConfiguration} (retroactive startup sweep), so neither collaborator has to
 * reach for the concrete plugin type or the reflective external-plugin discovery mechanism.
 *
 * <p>Idempotent: re-running on an already-branded report is a no-op (marker file check).
 */
@Slf4j
@Component
public final class BrandingService {

    private static final String MARKER_CSS = "brew-brand.css";
    private static final String BRAND_JS = "brew-brand.js";
    private static final String FAVICON_SVG = "favicon.svg";
    private static final String INDEX_HTML = "index.html";
    private static final String CLASSPATH_ICON = "static/icon.svg";
    private static final String CLASSPATH_CSS = "brew-brand/brew-brand.css";
    private static final String CLASSPATH_JS = "brew-brand/brew-brand.js";

    private static final String HEAD_INJECTION = """
        <link rel="icon" href="favicon.svg" type="image/svg+xml">
        <link rel="stylesheet" href="brew-brand.css">
        <script defer src="brew-brand.js"></script>
        </head>""";

    /**
     * Applies branding to a single report directory. Idempotent: if the marker file is already
     * present the directory is left untouched. Directories that are not a report root (no
     * {@code index.html}) are skipped silently.
     *
     * @param reportDirectory the report root directory; must not be {@code null}
     * @throws IOException if a branding asset cannot be copied or {@code index.html} cannot be patched
     */
    public void applyBranding(@NonNull Path reportDirectory) throws IOException {
        Preconditions.checkNotNull(reportDirectory, "reportDirectory");

        Path indexHtml = reportDirectory.resolve(INDEX_HTML);
        if (!Files.isRegularFile(indexHtml)) {
            // not a report root (e.g. 'history' / nested uuid dir) — skip silently
            return;
        }
        Path marker = reportDirectory.resolve(MARKER_CSS);
        if (Files.exists(marker)) {
            return;
        }

        copyResource(CLASSPATH_ICON, reportDirectory.resolve(FAVICON_SVG));
        copyResource(CLASSPATH_CSS, reportDirectory.resolve(MARKER_CSS));
        copyResource(CLASSPATH_JS, reportDirectory.resolve(BRAND_JS));

        String html = Files.readString(indexHtml, StandardCharsets.UTF_8);
        if (!html.contains(MARKER_CSS) && html.contains("</head>")) {
            String patched = html.replaceFirst("(?i)</head>", Matcher.quoteReplacement(HEAD_INJECTION));
            Files.writeString(indexHtml, patched, StandardCharsets.UTF_8);
        }
        log.info("Brew.QA branding applied to {}", reportDirectory);
    }

    private static void copyResource(String classpathLocation, Path target) throws IOException {
        Resource resource = new ClassPathResource(classpathLocation);
        if (!resource.exists()) {
            throw new IOException("Classpath resource not found: " + classpathLocation);
        }
        Files.createDirectories(target.getParent());
        try (InputStream in = resource.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
