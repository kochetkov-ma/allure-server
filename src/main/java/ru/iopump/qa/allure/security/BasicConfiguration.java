package ru.iopump.qa.allure.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import ru.iopump.qa.allure.properties.BasicProperties;

@SuppressWarnings("deprecation")
@Configuration
@EnableWebSecurity
@Slf4j
public class BasicConfiguration extends WebSecurityConfigurerAdapter {

    private final BasicProperties basicProperties;
    private final boolean enableOAuth2;
    private final boolean enableBasicAuth;
    private final boolean enableAnyAuth;

    BasicConfiguration(BasicProperties basicProperties, @Value("${app.security.enable-oauth2:false}") boolean enableOAuth2) {
        super();

        this.basicProperties = basicProperties;
        this.enableBasicAuth = basicProperties.enable();
        this.enableOAuth2 = enableOAuth2;
        this.enableAnyAuth = enableBasicAuth || enableOAuth2;

        log.info("[ALLURE SERVER SECURITY] Basic Auth: {} | OAuth2: {}", enableBasicAuth, enableOAuth2);
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        if (enableBasicAuth)
            auth.inMemoryAuthentication()
                    .withUser(basicProperties.username())
                    .password(PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(basicProperties.password()))
                    .roles("USER", "ADMIN");
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .headers().frameOptions().sameOrigin()
                .and()
                .csrf().disable()
                .requestCache().requestCache(new CustomRequestCache());

        if (enableAnyAuth)
            http
                    .authorizeRequests()
                    .requestMatchers(SecurityUtils::isFrameworkInternalRequest).permitAll()
                    .anyRequest().authenticated();

        if (enableOAuth2)
            http
                    .oauth2Login();

        if (enableBasicAuth)
            http
                    .httpBasic();
    }
}
