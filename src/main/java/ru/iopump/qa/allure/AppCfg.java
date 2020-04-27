package ru.iopump.qa.allure;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Primary
@Configuration
@ConfigurationProperties
@ConstructorBinding
@Data
@Accessors(fluent = true)
public class AppCfg {
    @Value("${allure.results.dir}")
    String resultsDir;
    @Value("${allure.reports.dir}")
    String reportsDir;
    @Value("${allure.reports.path}")
    String reportsPath;
    @Value("${springdoc.swagger-ui.path}")
    String swaggerPath;
    @Value("${allure.reports.history.level}")
    long maxReportHistoryLevel;
}
