package ru.iopump.qa.allure.helper.plugin;

import io.qameta.allure.core.LaunchResults;
import lombok.extern.slf4j.Slf4j;
import ru.iopump.qa.allure.config.BrandingService;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Applies Brew.QA branding (favicon, sidebar logo, home link) to every generated Allure report.
 * Runs during generation via {@link #onGenerationFinish(Path, Collection, Context)}, delegating the
 * actual file patching to the Spring-owned {@link BrandingService} obtained from the plugin
 * {@link Context}. A retroactive startup sweep for reports already on disk is performed by
 * {@link ru.iopump.qa.allure.config.BrandingSweepConfiguration}.
 *
 * <p>This plugin is discovered via {@code ReflectionUtil.createImplementations} (no Spring DI), so
 * it cannot constructor-inject collaborators; the {@link BrandingService} is resolved lazily from
 * {@link Context#beanFactory()} at generation time. Idempotent: re-running on an already-branded
 * report is a no-op (marker file check in {@link BrandingService}).
 */
@Slf4j
public class BrandingPlugin implements AllureServerPlugin {

    @Override
    public void onGenerationStart(Collection<Path> resultsDirectories, Context context) {
        // no-op
    }

    @Override
    public void onGenerationFinish(Path reportDirectory, Collection<LaunchResults> launchResults, Context context) {
        try {
            context.beanFactory().getBean(BrandingService.class).applyBranding(reportDirectory);
        } catch (IOException e) {
            log.warn("{}: failed to apply Brew.QA branding to {}", getName(), reportDirectory, e);
        }
    }

    @Override
    public String getName() {
        return "Brew.QA Branding Plugin";
    }
}
