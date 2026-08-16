package ru.iopump.qa.allure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password encoder wiring. BCrypt with strength 10 is the Spring default and
 * strikes a reasonable CPU/security trade-off for an on-prem CI server.
 */
@Configuration
public class PasswordConfiguration {

    private static final int BCRYPT_STRENGTH = 10;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }
}
