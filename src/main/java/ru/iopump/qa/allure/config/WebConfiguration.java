package ru.iopump.qa.allure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web layer wiring for the new server-side rendered UI at {@value #APP_PATH_PREFIX}/**.
 * <p>
 * JTE's {@code gg.jte.TemplateEngine} and {@code JteViewResolver} beans are auto-configured by
 * {@code gg.jte:jte-spring-boot-starter-3} ({@code JteAutoConfiguration}), driven by the
 * {@code gg.jte.*} properties in {@code application.yaml}. No manual engine/view-resolver beans
 * are declared here — the starter covers both (confirmed: its bean methods are annotated with
 * {@code @ConditionalOnMissingBean}, so we could override if needed, but defaults are correct).
 * <p>
 * Spring Boot's {@code WebMvcAutoConfiguration} already serves {@code classpath:/static/**} at
 * {@code /**}. The explicit handlers registered here for {@code /css/**}, {@code /js/**} and
 * {@code /img/**} document the contract used by the JTE layouts (B5/B6) and mirror the URL
 * patterns permitted in {@code SecurityConfiguration}, so the two stay in lockstep.
 */
@Slf4j
@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    /** URL prefix for every server-side rendered (JTE) route. Security layer references the same constant. */
    public static final String APP_PATH_PREFIX = "/app";
    public static final String APP_PATH_PATTERN = APP_PATH_PREFIX + "/**";

    /** Static asset URL patterns — intentionally open (permitAll) even when authentication is enabled, so the login page can style itself. */
    public static final String CSS_PATH_PATTERN = "/css/**";
    public static final String JS_PATH_PATTERN = "/js/**";
    public static final String IMG_PATH_PATTERN = "/img/**";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(CSS_PATH_PATTERN).addResourceLocations("classpath:/static/css/");
        registry.addResourceHandler(JS_PATH_PATTERN).addResourceLocations("classpath:/static/js/");
        registry.addResourceHandler(IMG_PATH_PATTERN).addResourceLocations("classpath:/static/img/");
    }
}
