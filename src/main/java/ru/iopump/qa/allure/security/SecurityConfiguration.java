package ru.iopump.qa.allure.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import ru.iopump.qa.allure.properties.BasicProperties;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
@Slf4j
public class SecurityConfiguration {

    private final BasicProperties basicProperties;
    private final boolean enableOAuth2;
    private final boolean enableBasicAuth;
    private final boolean enableAnyAuth;

    public SecurityConfiguration(BasicProperties basicProperties, @Value("${app.security.enable-oauth2:false}") boolean enableOAuth2) {
        this.basicProperties = basicProperties;
        this.enableBasicAuth = basicProperties.enable();
        this.enableOAuth2 = enableOAuth2;
        this.enableAnyAuth = enableBasicAuth || enableOAuth2;

        log.info("[ALLURE SERVER SECURITY] Basic Auth: {} | OAuth2: {}", enableBasicAuth, enableOAuth2);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .headers(it -> it.frameOptions(FrameOptionsConfig::sameOrigin))
            .csrf(AbstractHttpConfigurer::disable)
            .requestCache(it -> it.requestCache(new CustomRequestCache()));

        if (enableAnyAuth)
            http
                .authorizeHttpRequests(it -> it
                    .requestMatchers(SecurityUtils::isFrameworkInternalRequest).permitAll()
                    .anyRequest().authenticated());

        if (enableOAuth2)
            http
                .oauth2Login(withDefaults());

        if (enableBasicAuth)
            http
                .httpBasic(withDefaults());

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        UserDetails user = User.withUsername(basicProperties.username())
            .password(encoder.encode(basicProperties.password()))
            .roles("USER", "ADMIN")
            .build();
        return new InMemoryUserDetailsManager(user);
    }
}
