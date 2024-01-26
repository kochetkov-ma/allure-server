package ru.iopump.qa.allure.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.security.enable-oauth2:false}")
    private boolean enableOAuth2;

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        if (!enableOAuth2 && basicProperties.enable()) {
            PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
            auth.inMemoryAuthentication()
                .withUser(basicProperties.username())
                .password(encoder.encode(basicProperties.password()))
                .roles("USER", "ADMIN");
        }
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.headers().frameOptions().sameOrigin()
            .and()
            .csrf().disable()
            .requestCache().requestCache(new CustomRequestCache());

        if (enableOAuth2) {
            http
                .oauth2Login()
                .and()
                .authorizeRequests()
                .requestMatchers(SecurityUtils::isFrameworkInternalRequest).permitAll()
                .anyRequest().authenticated();
        } else {
            http
                .authorizeRequests(configurer -> configurer
                    .requestMatchers(SecurityUtils::isFrameworkInternalRequest).permitAll()
                    .anyRequest().authenticated()
                )
                .httpBasic();
        }
    }
}
