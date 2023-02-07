package ru.iopump.qa.allure.properties;

import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

import javax.annotation.PostConstruct;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

@ConfigurationProperties(prefix = "allure")
@Getter
@Accessors(fluent = true)
@ConstructorBinding
@Slf4j
@ToString
public class AllureProperties {

    private final Reports reports;
    private final String resultsDir;
    private final boolean supportOldFormat;
    private final String dateFormat;
    private final String serverBaseUrl;

    public AllureProperties(Reports reports, String resultsDir, boolean supportOldFormat, String dateFormat, String serverBaseUrl) {
        this.reports = defaultIfNull(reports, new Reports());
        this.resultsDir = defaultIfNull(resultsDir, "allure/results/");
        this.supportOldFormat = defaultIfNull(supportOldFormat, false);
        this.dateFormat = defaultIfNull(dateFormat, "yy/MM/dd HH:mm:ss");
        this.serverBaseUrl = defaultIfNull(serverBaseUrl, null);
    }

    @PostConstruct
    void init() {
        if (log.isInfoEnabled())
            log.info("[ALLURE SERVER CONFIGURATION] Main AllureProperties parameters: " + this);
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
