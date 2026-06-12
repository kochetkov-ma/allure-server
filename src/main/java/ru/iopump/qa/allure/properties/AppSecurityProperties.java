package ru.iopump.qa.allure.properties;

import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

/**
 * App-level security toggles. Only the initial bootstrap default for
 * {@code requireApiAuth} is sourced from yaml — once the system-settings row is
 * written (on first start), the database is authoritative and this property is
 * ignored.
 */
@ConfigurationProperties(prefix = "app.security")
@Getter
@Accessors(fluent = true)
@ToString
public class AppSecurityProperties {

    private final boolean requireApiAuth;
    private final boolean enableOauth2;

    @ConstructorBinding
    public AppSecurityProperties(Boolean requireApiAuth, Boolean enableOauth2) {
        this.requireApiAuth = defaultIfNull(requireApiAuth, false);
        this.enableOauth2 = defaultIfNull(enableOauth2, false);
    }
}
