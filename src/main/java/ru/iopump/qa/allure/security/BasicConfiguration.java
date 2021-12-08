package ru.iopump.qa.allure.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.iopump.qa.allure.properties.BasicProperties;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class BasicConfiguration extends WebSecurityConfigurerAdapter {

    private final BasicProperties basicProperties;

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        PasswordEncoder encoder =
            PasswordEncoderFactories.createDelegatingPasswordEncoder();
        auth
                .inMemoryAuthentication()
                .withUser(basicProperties.username())
                .password(encoder.encode(basicProperties.password()))
            .roles("USER", "ADMIN");
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        var spec = http
            .headers().frameOptions().sameOrigin()
            .and()
            .csrf().disable()
            .requestCache().requestCache(new CustomRequestCache());
        if (basicProperties.enable()) {
            spec.and().authorizeRequests()
                    .requestMatchers(SecurityUtils::isFrameworkInternalRequest).permitAll()
                    .anyRequest().authenticated()
                    .and().httpBasic();
        }
    }
}