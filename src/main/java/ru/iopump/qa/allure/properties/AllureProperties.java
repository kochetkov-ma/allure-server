package ru.iopump.qa.allure.properties;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.core.io.Resource;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

@ConfigurationProperties(prefix = "allure")
@Getter
@Accessors(fluent = true)
@Slf4j
@ToString
public class AllureProperties {

    private final Reports reports;
    private final Upload upload;
    private final String resultsDir;
    private final String dateFormat;
    private final String serverBaseUrl;
    @Nullable
    private final Resource logo;
    private final String title;

    @ConstructorBinding
    public AllureProperties(Reports reports, Upload upload, String resultsDir, String dateFormat, String serverBaseUrl, @Nullable Resource logo, String title) {
        this.reports = defaultIfNull(reports, new Reports());
        this.upload = defaultIfNull(upload, new Upload());
        this.resultsDir = defaultIfNull(resultsDir, "allure/results/");
        this.dateFormat = defaultIfNull(dateFormat, "yy/MM/dd HH:mm:ss");
        this.serverBaseUrl = defaultIfNull(serverBaseUrl, null);
        this.logo = logo;
        this.title = title;
    }

    /**
     * Backward-compatible constructor without upload limits; delegates to defaults.
     * Retained for callers created before {@link Upload} tunables were introduced.
     */
    public AllureProperties(Reports reports, String resultsDir, String dateFormat, String serverBaseUrl, @Nullable Resource logo, String title) {
        this(reports, null, resultsDir, dateFormat, serverBaseUrl, logo, title);
    }

    @PostConstruct
    void init() {
        if (log.isInfoEnabled())
            log.info("[ALLURE SERVER CONFIGURATION] Main AllureProperties parameters: {}", this);
    }

    @Getter
    @ToString
    public static class Reports {

        private final transient Path dirPath;
        private final String dir;
        private final String path;
        private final long historyLevel;

        public Reports() {
            this("allure/reports/", "reports/", 20);
        }

        @ConstructorBinding
        public Reports(String dir,
                       String path,
                       long historyLevel) {

            this.dir = dir;
            this.path = path;
            this.historyLevel = historyLevel;
            this.dirPath = Paths.get(this.dir);
        }
    }

    /**
     * Decompression limits for the allure-results upload intake (zip-bomb / disk-fill DoS guard).
     * The multipart limit caps only the <em>compressed</em> request body, so cumulative decompressed
     * size and entry count must be capped independently during extraction.
     */
    @Getter
    @ToString
    public static class Upload {

        /**
         * ~4 GiB default. Multipart caps compressed input at 100MB and legitimate allure-results
         * (JSON metadata plus already-compressed attachments such as PNG/MP4) rarely expand beyond
         * ~1 GiB, so 4 GiB leaves ample headroom while still aborting zip bombs that expand to tens
         * or hundreds of GB.
         */
        public static final long DEFAULT_MAX_UNCOMPRESSED_BYTES = 4L * 1024 * 1024 * 1024;
        public static final long DEFAULT_MAX_ENTRIES = 100_000L;

        private final long maxUncompressedBytes;
        private final long maxEntries;

        public Upload() {
            this(null, null);
        }

        @ConstructorBinding
        public Upload(@Nullable Long maxUncompressedBytes, @Nullable Long maxEntries) {
            this.maxUncompressedBytes = defaultIfNull(maxUncompressedBytes, DEFAULT_MAX_UNCOMPRESSED_BYTES);
            this.maxEntries = defaultIfNull(maxEntries, DEFAULT_MAX_ENTRIES);
        }
    }
}
