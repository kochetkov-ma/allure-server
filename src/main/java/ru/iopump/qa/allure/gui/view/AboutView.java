package ru.iopump.qa.allure.gui.view;

import static ru.iopump.qa.allure.gui.MainLayout.ALLURE_SERVER;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import ru.iopump.qa.allure.gui.MainLayout;
import ru.iopump.qa.util.ResourceUtil;

@Tag("about-view")
@PageTitle("About | " + ALLURE_SERVER)
@Route(value = "about", layout = MainLayout.class)
@Slf4j
public class AboutView extends VerticalLayout {
    public static final String FONT_FAMILY = "font-family";
    public static final String GERMANIA_ONE = "Cambria";
    public static final String APP_VERSION = getVersionOrDefault("1.0.0");

    private static final long serialVersionUID = 5122017036734476962L;

    public AboutView() {
        var description = new Div(
            new Paragraph("Version: " + APP_VERSION),
            new Paragraph("This is a server to save and aggregate Allure Report results"),
            new Paragraph(new Anchor("https://docs.qameta.io/allure/", "Allure Reporting Framework")),
            new Paragraph("Allure Reports are very popular QA Reporting tool in Russia. " +
                "But it hasn't any free EE servers, just plugins like Jenkins Plugin. " +
                "This solution can be the only place to save, generate and provide Allure Reports in an organization. " +
                "Run your tests, make zip archive with the allure results and upload to the Allure Server by API or Web-UI")
        );

        var author = new Paragraph("Author: Maxim Kochetkov, Russia, Moscow");
        var mailTo = new Anchor("mailto:kochetkov-ma@yandex.ru", "Email: kochetkov-ma@yandex.ru");

        var mainLayout = new VerticalLayout(description, author, mailTo);
        mainLayout.getStyle().set(FONT_FAMILY, GERMANIA_ONE);
        add(mainLayout);
    }

    public static String getVersionOrDefault(@NonNull String defaultVersion) {
        try (final InputStream inputStream = ResourceUtil.getResourceAsStream("version.info")) {
            var version = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            log.info("App version from version.info = " + version);
            return StringUtils.isBlank(version) ? defaultVersion : version;
        } catch (IOException e) {
            log.error("Version error", e);
            return defaultVersion;
        }
    }
}
