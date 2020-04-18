package ru.iopump.qa.allure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class RedirectConfiguration implements WebMvcConfigurer {

    public final String swaggerPath;

    public RedirectConfiguration(@Value("${springdoc.swagger-ui.path}") String swaggerPath) {
        this.swaggerPath = swaggerPath;
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", swaggerPath); // Will redirect to UI
        registry.addRedirectViewController("/api", swaggerPath);
    }
}
