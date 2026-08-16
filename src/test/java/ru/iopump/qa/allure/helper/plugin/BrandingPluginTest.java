package ru.iopump.qa.allure.helper.plugin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.BeanFactory;
import ru.iopump.qa.allure.config.BrandingService;
import ru.iopump.qa.allure.helper.plugin.AllureServerPlugin.Context;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BrandingPlugin")
class BrandingPluginTest {

    private static final String EXPECTED_PLUGIN_NAME = "Brew.QA Branding Plugin";

    private static final String ORIGINAL_INDEX = """
        <!DOCTYPE html>
        <html><head>
            <title>Allure Report</title>
        </head><body><div id="content"></div></body></html>
        """;

    @Mock
    private BeanFactory beanFactory;

    @Mock
    private Context context;

    private final BrandingPlugin plugin = new BrandingPlugin();

    @Test
    @DisplayName("should resolve BrandingService from the context bean factory and brand the report on finish")
    void should_delegate_to_branding_service_on_finish(@TempDir Path reportDir) throws Exception {
        // GIVEN a generated report directory and a BrandingService available via the context bean factory
        Files.writeString(reportDir.resolve("index.html"), ORIGINAL_INDEX, StandardCharsets.UTF_8);
        when(context.beanFactory()).thenReturn(beanFactory);
        when(beanFactory.getBean(BrandingService.class)).thenReturn(new BrandingService());

        // WHEN generation finishes
        plugin.onGenerationFinish(reportDir, List.of(), context);

        // THEN the service patched index.html and emitted the branding marker asset
        verify(beanFactory).getBean(BrandingService.class);
        assertThat(Files.exists(reportDir.resolve("brew-brand.css")))
            .as("BrandingService applied branding through the plugin (marker asset present)").isTrue();
        String patched = Files.readString(reportDir.resolve("index.html"), StandardCharsets.UTF_8);
        assertThat(patched)
            .as("index.html was patched with branding stylesheet link")
            .contains("<link rel=\"stylesheet\" href=\"brew-brand.css\">");
    }

    @Test
    @DisplayName("should do nothing on generation start")
    void should_be_no_op_on_start(@TempDir Path resultsDir) {
        // GIVEN results directories and a context

        // WHEN generation starts
        plugin.onGenerationStart(List.of(resultsDir), context);

        // THEN the context bean factory is never consulted (no branding work on start)
        verifyNoInteractions(context);
    }

    @Test
    @DisplayName("should expose the stable plugin name")
    void should_expose_plugin_name() {
        // GIVEN the branding plugin

        // WHEN the name is requested / THEN it matches the documented SPI identity
        assertThat(plugin.getName())
            .as("stable plugin name used in logs and discovery")
            .isEqualTo(EXPECTED_PLUGIN_NAME);
    }
}
