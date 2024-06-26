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
    private final String resultsDir;
    private final String dateFormat;
    private final String serverBaseUrl;
    @Nullable
    private final Resource logo;
    private final String title;

    @ConstructorBinding
    public AllureProperties(Reports reports, String resultsDir, String dateFormat, String serverBaseUrl, @Nullable Resource logo, String title) {
        this.reports = defaultIfNull(reports, new Reports());
        this.resultsDir = defaultIfNull(resultsDir, "allure/results/");
        this.dateFormat = defaultIfNull(dateFormat, "yy/MM/dd HH:mm:ss");
        this.serverBaseUrl = defaultIfNull(serverBaseUrl, null);
        this.logo = logo;
        this.title = title;
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
}
