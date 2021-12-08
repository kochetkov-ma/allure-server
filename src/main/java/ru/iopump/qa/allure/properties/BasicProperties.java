package ru.iopump.qa.allure.properties;

import lombok.Getter;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

@ConfigurationProperties(prefix = "basic.auth")
@Getter
@Accessors(fluent = true)
@ConstructorBinding
public class BasicProperties {

    private final String username;
    private final String password;
    private final boolean enable;

    public BasicProperties(String username, String password, boolean enable) {
        this.username = defaultIfNull(username, "admin");
        this.password = defaultIfNull(password, "admin");
        this.enable = defaultIfNull(enable, false);
    }
}
