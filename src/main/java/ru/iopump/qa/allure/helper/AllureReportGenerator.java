package ru.iopump.qa.allure.helper;

import io.qameta.allure.Aggregator2;
import io.qameta.allure.ConfigurationBuilder;
import io.qameta.allure.Extension;
import io.qameta.allure.ReportGenerator;
import io.qameta.allure.ReportStorage;
import io.qameta.allure.core.Configuration;
import io.qameta.allure.core.LaunchResults;
import io.qameta.allure.core.Plugin;
import io.qameta.allure.plugin.DefaultPluginLoader;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import ru.iopump.qa.allure.helper.plugin.AllureServerPlugin;
import ru.iopump.qa.allure.properties.AllureProperties;
import ru.iopump.qa.allure.properties.TmsProperties;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@Slf4j
public final class AllureReportGenerator {

    private final Collection<AllureServerPlugin> listeners;
    private final AllureProperties allureProperties;
    private final TmsProperties tmsProperties;
    private final ReportGenerator delegate;
    private final BeanFactory beanFactory;
    private final AggregatorGrabber aggregatorGrabber = new AggregatorGrabber();
    private final Extension ciExecutor = new ExecutorCiPlugin();

    public AllureReportGenerator(@NonNull Collection<AllureServerPlugin> listeners, AllureProperties allureProperties, TmsProperties tmsProperties, BeanFactory beanFactory) {
        this.listeners = listeners;
        this.allureProperties = allureProperties;
        this.tmsProperties = tmsProperties;
        this.beanFactory = beanFactory;
        this.delegate = new ReportGenerator(configuration());
    }

    private Configuration configuration() {
        return ConfigurationBuilder
            .bundled()
            .withPlugins(loadPlugins())
            .withExtensions(List.of(aggregatorGrabber, ciExecutor))
            .build();
    }

    ///// PRIVATE /////

    private static String pluginRelPath(String string) {
        return StringUtils.substringAfterLast(string, "plugins/");
    }

    @SneakyThrows
    private static List<Plugin> loadPlugins() {
        final Path pluginsDirectory = Optional.ofNullable(System.getProperty("allure.plugins.directory"))
            .map(Paths::get)
            .filter(Files::isDirectory)
            .orElseGet(AllureReportGenerator::extractDefaultPlugin);

        log.info("Found plugins directory {}", pluginsDirectory);
        final DefaultPluginLoader loader = new DefaultPluginLoader();
        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return Files.list(pluginsDirectory)
            .filter(Files::isDirectory)
            .map(pluginDir -> loader.loadPlugin(classLoader, pluginDir))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .peek(p -> log.info("Found {} plugin", p.getConfig()))
            .collect(Collectors.toList());
    }

    private static Path extractDefaultPlugin() {
        try {
            final Resource[] resources = new PathMatchingResourcePatternResolver().getResources("/plugins/**");
            final Path to = Paths.get("allure/plugins");
            for (Resource resource : resources) {
                Path targetItem = to.resolve(pluginRelPath(resource.getURL().getPath()));
                if (resource.exists() && resource.isReadable()) {
                    FileUtils.copyInputStreamToFile(resource.getInputStream(), targetItem.toFile());
                }
            }
            return to;
        } catch (Exception exception) {
            throw new IllegalStateException("Error default plugins loading from resources '/plugins/**'", exception);
        }
    }

    public Path generate(Path outputDirectory, List<Path> resultsDirectories, String reportUrl) {
        var ctx = new PluginContext(reportUrl);

        var effectiveListeners = listeners.stream()
            .filter(it -> {
                if (it.isEnabled(ctx))
                    return true;
                log.info("[PLUGIN] Plugin '{} : {}' is disabled", it.getName(), it.getClass().getName());
                return false;
            })
            .toList();

        effectiveListeners.parallelStream()
            .forEach(listener -> evaluateListener(() -> listener.onGenerationStart(resultsDirectories, ctx), listener.getName(), "before generation"));

        final Collection<LaunchResults> launchesResults;
        synchronized (aggregatorGrabber) {
            delegate.generate(outputDirectory, resultsDirectories);
            launchesResults = aggregatorGrabber.launchesResults();
        }

        effectiveListeners.parallelStream()
            .forEach(listener -> evaluateListener(() -> listener.onGenerationFinish(outputDirectory, launchesResults, ctx), listener.getName(), "after generation"));

        return outputDirectory;
    }

    @Getter
    @RequiredArgsConstructor
    private class PluginContext implements AllureServerPlugin.Context {

        private final String reportUrl;

        @Override
        public AllureProperties getAllureProperties() {
            return allureProperties;
        }

        @Override
        public TmsProperties tmsProperties() {
            return tmsProperties;
        }

        @Override
        public BeanFactory beanFactory() {
            return beanFactory;
        }
    }

    private static void evaluateListener(Runnable runnable, String name, String stage) {
        try {
            runnable.run();
            log.info("Listener '{}' {} executed successfully", name, stage);
        } catch (Exception ex) {
            log.error("Error in listener '{}'", name, ex);
        }
    }

    private static class AggregatorGrabber implements Aggregator2 {

        private Collection<LaunchResults> launchesResults = Collections.emptyList();

        private Collection<LaunchResults> launchesResults() {
            return launchesResults.stream().toList();
        }

        @Override
        public void aggregate(Configuration configuration, List<LaunchResults> launchesResults, ReportStorage storage) {
            this.launchesResults = launchesResults;
        }
    }
}
