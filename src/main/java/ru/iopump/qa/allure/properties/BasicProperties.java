package ru.iopump.qa.allure.properties;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

@ConfigurationProperties(prefix = "basic.auth")
@Getter
@Accessors(fluent = true)

@Slf4j
@ToString(exclude = "password")
public class BasicProperties {

    private final String username;
    private final String password;
    private final boolean enable;

    @ConstructorBinding
    public BasicProperties(String username, String password, boolean enable) {
        this.username = defaultIfNull(username, "admin");
        this.password = defaultIfNull(password, "admin");
        this.enable = defaultIfNull(enable, false);
    }

    @PostConstruct
    void init() {
        if (log.isInfoEnabled())
            log.info("[ALLURE SERVER CONFIGURATION] Authorization parameters: " + this);
    }
}
