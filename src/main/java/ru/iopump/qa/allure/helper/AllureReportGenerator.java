package ru.iopump.qa.allure.helper; //NOPMD

import io.qameta.allure.ConfigurationBuilder;
import io.qameta.allure.ReportGenerator;
import io.qameta.allure.allure1.Allure1Plugin;
import io.qameta.allure.allure2.Allure2Plugin;
import io.qameta.allure.category.CategoriesPlugin;
import io.qameta.allure.category.CategoriesTrendPlugin;
import io.qameta.allure.context.FreemarkerContext;
import io.qameta.allure.context.JacksonContext;
import io.qameta.allure.context.MarkdownContext;
import io.qameta.allure.context.RandomUidContext;
import io.qameta.allure.core.AttachmentsPlugin;
import io.qameta.allure.core.Configuration;
import io.qameta.allure.core.MarkdownDescriptionsPlugin;
import io.qameta.allure.core.Plugin;
import io.qameta.allure.core.ReportWebPlugin;
import io.qameta.allure.core.TestsResultsPlugin;
import io.qameta.allure.duration.DurationPlugin;
import io.qameta.allure.duration.DurationTrendPlugin;
import io.qameta.allure.environment.Allure1EnvironmentPlugin;
import io.qameta.allure.history.HistoryPlugin;
import io.qameta.allure.history.HistoryTrendPlugin;
import io.qameta.allure.launch.LaunchPlugin;
import io.qameta.allure.mail.MailPlugin;
import io.qameta.allure.owner.OwnerPlugin;
import io.qameta.allure.plugin.DefaultPluginLoader;
import io.qameta.allure.retry.RetryPlugin;
import io.qameta.allure.retry.RetryTrendPlugin;
import io.qameta.allure.severity.SeverityPlugin;
import io.qameta.allure.status.StatusChartPlugin;
import io.qameta.allure.suites.SuitesPlugin;
import io.qameta.allure.summary.SummaryPlugin;
import io.qameta.allure.tags.TagsPlugin;
import io.qameta.allure.timeline.TimelinePlugin;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public final class AllureReportGenerator {

    private final ReportGenerator delegate;

    public AllureReportGenerator() {
        this.delegate = new ReportGenerator(configuration());
    }

    private static Configuration configuration() {
        return new ConfigurationBuilder()
            .fromPlugins(loadPlugins())
            .fromExtensions(
                Arrays.asList(
                    new JacksonContext(),
                    new MarkdownContext(),
                    new FreemarkerContext(),
                    new RandomUidContext(),
                    new MarkdownDescriptionsPlugin(),
                    new RetryPlugin(),
                    new RetryTrendPlugin(),
                    new TagsPlugin(),
                    new SeverityPlugin(),
                    new OwnerPlugin(),
                    new HistoryPlugin(),
                    new HistoryTrendPlugin(),
                    new CategoriesPlugin(),
                    new CategoriesTrendPlugin(),
                    new DurationPlugin(),
                    new DurationTrendPlugin(),
                    new StatusChartPlugin(),
                    new TimelinePlugin(),
                    new SuitesPlugin(),
                    new ReportWebPlugin(),
                    new TestsResultsPlugin(),
                    new AttachmentsPlugin(),
                    new MailPlugin(),
                    new SummaryPlugin(),
                    new ExecutorCiPlugin(),
                    new LaunchPlugin(),
                    new Allure2Plugin(),
                    new Allure1EnvironmentPlugin(),
                    new Allure1Plugin()
                )
            ).build();
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
        } catch (Exception exception) { //NOPMD
            throw new IllegalStateException("Error default plugins loading from resources '/plugins/**'", exception);
        }
    }

    public Path generate(Path outputDirectory, List<Path> resultsDirectories) throws IOException {
        delegate.generate(outputDirectory, resultsDirectories);
        return outputDirectory;
    }
}
