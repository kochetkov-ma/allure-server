package ru.iopump.qa.allure.gui.view;

import com.google.common.collect.ImmutableList;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import ru.iopump.qa.allure.controller.AllureReportController;
import ru.iopump.qa.allure.entity.ReportEntity;
import ru.iopump.qa.allure.gui.DateTimeResolver;
import ru.iopump.qa.allure.gui.MainLayout;
import ru.iopump.qa.allure.gui.component.Col;
import ru.iopump.qa.allure.gui.component.FilteredGrid;
import ru.iopump.qa.allure.gui.component.ResultUploadDialog;
import ru.iopump.qa.allure.properties.AllureProperties;
import ru.iopump.qa.allure.service.JpaReportService;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static ru.iopump.qa.allure.gui.MainLayout.ALLURE_SERVER;
import static ru.iopump.qa.allure.gui.component.Col.Type.LINK;
import static ru.iopump.qa.allure.gui.component.Col.Type.NUMBER;
import static ru.iopump.qa.allure.gui.component.Col.prop;
import static ru.iopump.qa.allure.gui.component.ResultUploadDialog.toMultiPartFile;
import static ru.iopump.qa.allure.helper.Util.url;

@Tag("reports-view")
@PageTitle("Reports | " + ALLURE_SERVER)
@Route(value = "", layout = MainLayout.class)
@Slf4j
public class ReportsView extends VerticalLayout {
    private static final long serialVersionUID = 5822017036734476962L;
    private final DateTimeResolver dateTimeResolver;
    private final AllureProperties allureProperties;

    /* COMPONENTS */
    private final FilteredGrid<ReportEntity> reports;
    private final Button deleteSelection;
    private final ResultUploadDialog uploadDialog;
    private final Button uploadButton;

    public ReportsView(final JpaReportService jpaReportService,
                       final AllureReportController allureReportController,
                       final DateTimeResolver dateTimeResolver,
                       final AllureProperties allureProperties,
                       final MultipartProperties multipartProperties) {
        this.dateTimeResolver = dateTimeResolver;
        this.allureProperties = allureProperties;
        this.dateTimeResolver.retrieve();

        this.uploadDialog = new ResultUploadDialog(
            (buffer) -> allureReportController.uploadReport("manual_uploaded", toMultiPartFile(buffer)),
            (int) multipartProperties.getMaxFileSize().toBytes(),
            "report"
        );

        this.reports = new FilteredGrid<>(
            asProvider(jpaReportService),
            cols()
        );
        this.uploadButton = new Button("Upload report");
        this.deleteSelection = new Button("Delete selection",
            new Icon(VaadinIcon.CLOSE_CIRCLE),
            event -> {
                for (ReportEntity reportEntity : reports.getGrid().getSelectedItems()) {
                    UUID uuid = reportEntity.getUuid();
                    try {
                        jpaReportService.internalDeleteByUUID(uuid);
                        Notification.show("Delete success: " + uuid, 2000, Notification.Position.TOP_START);
                    } catch (Exception e) {
                        Notification.show("Deleting error: " + e.getLocalizedMessage(),
                            5000,
                            Notification.Position.TOP_START);
                        log.error("Deleting error", e);
                    }
                }
                reports.getGrid().deselectAll();
                reports.getGrid().getDataProvider().refreshAll();
            });
        deleteSelection.addThemeVariants(ButtonVariant.LUMO_ERROR);

        this.dateTimeResolver.onClientReady(() -> reports.getGrid().getDataProvider().refreshAll());

        uploadDialog.onClose(event -> reports.getGrid().getDataProvider().refreshAll());
    }

    private static ListDataProvider<ReportEntity> asProvider(final JpaReportService jpaReportService) {
        //noinspection unchecked
        final Collection<ReportEntity> collection = (Collection<ReportEntity>) Proxy
            .newProxyInstance(Thread.currentThread().getContextClassLoader(),
                new Class[]{Collection.class},
                (proxy, method, args) -> method.invoke(jpaReportService.getAll(), args));

        return new ListDataProvider<>(collection);
    }

    //// PRIVATE ////
    private List<Col<ReportEntity>> cols() {
        return ImmutableList.<Col<ReportEntity>>builder()
            .add(Col.<ReportEntity>with().name("Uuid").value(prop("uuid")).build())
            .add(
                Col.<ReportEntity>with()
                    .name("Created")
                    .value(e -> dateTimeResolver.printDate(e.getCreatedDateTime()))
                    .build()
            )
            .add(Col.<ReportEntity>with().name("Url").value(this::displayUrl).type(LINK).build())
            .add(Col.<ReportEntity>with().name("Path").value(prop("path")).build())
            .add(Col.<ReportEntity>with().name("Active").value(prop("active")).build())
            .add(Col.<ReportEntity>with().name("Size KB").value(prop("size")).type(NUMBER).build())
            .add(Col.<ReportEntity>with().name("Build").value(this::buildUrl).type(LINK).build())
            .build();
    }

    private String buildUrl(ReportEntity e) {
        return e.getBuildUrl();
    }

    private String displayUrl(ReportEntity e) {
        if (e.isActive()) {
            return e.generateLatestUrl(url(allureProperties), allureProperties.reports().path());
        } else {
            return e.generateUrl(url(allureProperties), allureProperties.reports().dir());
        }
    }

    @PostConstruct
    public void postConstruct() {
        add(deleteSelection, uploadButton);
        uploadDialog.addControlButton(uploadButton);
        reports.addTo(this);
    }
}
