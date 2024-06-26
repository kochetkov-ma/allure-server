package ru.iopump.qa.allure.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.regex.Pattern;

@Data
@ConfigurationProperties(prefix = "tms")
public class TmsProperties {

    private final String host;
    private final String apiBaseUrl;
    private final String project;
    private final String token;
    private final Pattern issueKeyPattern;
    private final boolean dryRun;
}
