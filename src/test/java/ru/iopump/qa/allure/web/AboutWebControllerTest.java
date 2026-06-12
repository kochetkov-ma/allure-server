package ru.iopump.qa.allure.web;

import gg.jte.springframework.boot.autoconfigure.JteAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import java.time.Instant;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link AboutWebController} — both the present and absent
 * {@link BuildProperties} branches. The controller is a thin model adapter, so a direct
 * unit test with a mocked {@link ObjectProvider} exercises every branch without standing
 * up an MVC slice (the rendered {@code about/index} template is verified separately by the
 * JTE smoke tests).
 */
class AboutWebControllerTest {

    private static final String DEFAULT_VERSION = "dev";
    private static final String BUILD_VERSION = "1.7.0";
    private static final String GIT_COMMIT = "abc1234";

    @Test
    @DisplayName("should expose 'dev' version and null build metadata when no BuildProperties bean is available")
    void about_withoutBuildProperties_exposesDevDefaults() {
        // GIVEN — no BuildProperties available
        @SuppressWarnings("unchecked")
        final ObjectProvider<BuildProperties> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        final AboutWebController controller = new AboutWebController(provider);
        final Model model = new ConcurrentModel();

        // WHEN — handling GET /app/about
        final String view = controller.about(model);

        // THEN — the about view name is returned with dev defaults and null metadata
        assertThat(view)
            .as("about handler must render the about/index view")
            .isEqualTo("about/index");
        assertThat(model.getAttribute("version"))
            .as("version must fall back to 'dev' when BuildProperties is absent")
            .isEqualTo(DEFAULT_VERSION);
        assertThat(model.getAttribute("buildTime"))
            .as("buildTime must be null when BuildProperties is absent")
            .isNull();
        assertThat(model.getAttribute("gitCommit"))
            .as("gitCommit must be null when BuildProperties is absent")
            .isNull();
    }

    @Test
    @DisplayName("should expose version, build time and git commit from BuildProperties when the bean is available")
    void about_withBuildProperties_exposesBuildMetadata() {
        // GIVEN — a populated BuildProperties bean
        final Instant builtAt = Instant.parse("2026-06-11T10:15:30Z");
        final Properties props = new Properties();
        props.setProperty("version", BUILD_VERSION);
        props.setProperty("time", builtAt.toString());
        props.setProperty("git.commit", GIT_COMMIT);
        final BuildProperties buildProperties = new BuildProperties(props);

        @SuppressWarnings("unchecked")
        final ObjectProvider<BuildProperties> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(buildProperties);
        final AboutWebController controller = new AboutWebController(provider);
        final Model model = new ConcurrentModel();

        // WHEN — handling GET /app/about
        final String view = controller.about(model);

        // THEN — model carries the build metadata
        assertThat(view)
            .as("about handler must render the about/index view")
            .isEqualTo("about/index");
        assertThat(model.getAttribute("version"))
            .as("version must come from BuildProperties when present")
            .isEqualTo(BUILD_VERSION);
        assertThat(model.getAttribute("buildTime"))
            .as("buildTime must be the BuildProperties build instant")
            .isEqualTo(builtAt);
        assertThat(model.getAttribute("gitCommit"))
            .as("gitCommit must come from the git.commit build property")
            .isEqualTo(GIT_COMMIT);
    }
}
