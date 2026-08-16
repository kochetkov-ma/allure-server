package ru.iopump.qa.allure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import ru.iopump.qa.allure.properties.AllureProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * One-shot retroactive sweep that re-applies Brew.QA branding to every existing report on disk
 * after application startup. Runs once on {@link ApplicationReadyEvent}.
 *
 * <p>Depends directly on the Spring-owned {@link BrandingService} collaborator — not on the
 * concrete {@link ru.iopump.qa.allure.helper.plugin.BrandingPlugin} nor on the reflective
 * external-plugin discovery mechanism — so built-in branding is independent of external-plugin
 * scanning. Idempotent: directories already containing the branding marker are skipped by
 * {@link BrandingService#applyBranding(Path)}.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class BrandingSweepConfiguration {

    private final BrandingService brandingService;
    private final AllureProperties allureProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void sweepOnStartup() {
        Path reportsRoot = allureProperties.reports().dirPath();
        if (!Files.isDirectory(reportsRoot)) {
            log.info("Retroactive branding sweep: reports root {} does not exist — nothing to do", reportsRoot);
            return;
        }
        try (Stream<Path> top = Files.list(reportsRoot)) {
            top.filter(Files::isDirectory).forEach(this::safeApply);
        } catch (IOException e) {
            log.warn("Retroactive branding sweep: failed to enumerate {}", reportsRoot, e);
        }
    }

    private void safeApply(Path reportDirectory) {
        try {
            brandingService.applyBranding(reportDirectory);
        } catch (IOException e) {
            log.warn("Retroactive branding sweep: failed for {}", reportDirectory, e);
        }
    }
}
