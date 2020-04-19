package ru.iopump.qa.allure.config;

import static ru.iopump.qa.allure.service.ReportService.REPORT_PATH_DEFAULT;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;

@Configuration
@Slf4j
public class RedirectConfiguration implements WebMvcConfigurer {

    public final String swaggerPath;
    public final String reportsPath;

    public RedirectConfiguration(@Value("${springdoc.swagger-ui.path}") String swaggerPath,
                                 @Value("${allure.reports.dir:" + REPORT_PATH_DEFAULT + "}") String reportsPath) {

        this.swaggerPath = swaggerPath;
        this.reportsPath = reportsPath;
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", swaggerPath); // Will redirect to UI
        registry.addRedirectViewController("/api", swaggerPath);
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer config) {
        config.setUseTrailingSlashMatch(true);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry
            .addResourceHandler("/" + REPORT_PATH_DEFAULT + "**")
            .addResourceLocations("file:" + reportsPath)
            .resourceChain(true)
            .addResolver(new PathResourceResolver() {

                @Override
                public Resource resolveResource(HttpServletRequest request,
                                                @Nonnull String requestPath,
                                                @Nonnull List<? extends Resource> locations,
                                                @Nonnull ResourceResolverChain chain) {
                    return super.resolveResource(request, requestPath, locations, chain);
                }

                @Override
                protected Resource getResource(@Nonnull String resourcePath,
                                               @Nonnull Resource location) throws IOException {
                    var res = super.getResource(resourcePath, location);
                    if (res == null) {
                        return getIndexHtml(resourcePath, location);
                    }
                    return res;

                }
            });
    }

    @SneakyThrows
    private Resource getIndexHtml(@Nonnull String resourcePath,
                                  @Nonnull Resource location) {
        final Path thisResource = location.getFile().toPath().resolve(resourcePath);
        if (Files.exists(thisResource) && Files.isDirectory(thisResource)) {
            return Files.walk(thisResource, 1)
                .skip(1)
                .filter(i -> "index.html".equals(i.getFileName().toString()))
                .map(i -> new FileSystemResource(i.toFile()))
                .findFirst()
                .orElse(null);
        }
        return null;
    }
}
