package ru.iopump.qa.allure.config;

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
import ru.iopump.qa.allure.properties.AllureProperties;

import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static ru.iopump.qa.allure.helper.Util.join;

@Configuration
@Slf4j
public class RedirectConfiguration implements WebMvcConfigurer {

    private final String swaggerPath;
    private final AllureProperties cfg;

    public RedirectConfiguration(@Value("${springdoc.swagger-ui.path}") String swaggerPath,
                                 AllureProperties cfg) {
        this.swaggerPath = swaggerPath;
        this.cfg = cfg;
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("swagger", swaggerPath); // Will redirect to UI
        registry.addRedirectViewController("api", swaggerPath);
        registry.addRedirectViewController("/", "ui"); // To Vaadin UI
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer config) {
        config.setUseTrailingSlashMatch(true);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry
                .addResourceHandler(join("/", cfg.reports().dir(), "**"))
                .addResourceLocations("file:" + cfg.reports().dir())
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
