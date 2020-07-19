package ru.iopump.qa.allure.gui.view;

import static ru.iopump.qa.allure.gui.MainLayout.ALLURE_SERVER;
import static ru.iopump.qa.allure.gui.component.Col.Type.LINK;
import static ru.iopump.qa.allure.gui.component.Col.Type.NUMBER;
import static ru.iopump.qa.allure.gui.component.Col.prop;
import static ru.iopump.qa.allure.helper.Util.url;

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
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import ru.iopump.qa.allure.AppCfg;
import ru.iopump.qa.allure.entity.ReportEntity;
import ru.iopump.qa.allure.gui.DateTimeResolver;
import ru.iopump.qa.allure.gui.MainLayout;
import ru.iopump.qa.allure.gui.component.Col;
import ru.iopump.qa.allure.gui.component.FilteredGrid;
import ru.iopump.qa.allure.service.JpaReportService;

@Tag("reports-view")
@PageTitle("Reports | " + ALLURE_SERVER)
@Route(value = "", layout = MainLayout.class)
@Slf4j
public class ReportsView extends VerticalLayout {
    private static final long serialVersionUID = 5822017036734476962L;
    private final DateTimeResolver dateTimeResolver;
    private final AppCfg appCfg;

    /* COMPONENTS */
    private final FilteredGrid<ReportEntity> reports;
    private final Button deleteSelection;

    public ReportsView(final JpaReportService jpaReportService,
                       final DateTimeResolver dateTimeResolver,
                       final AppCfg appCfg) {
        this.dateTimeResolver = dateTimeResolver;
        this.appCfg = appCfg;
        this.dateTimeResolver.retrieve();

        this.reports = new FilteredGrid<>(
            asProvider(jpaReportService),
            cols()
        );
        this.deleteSelection = new Button("Delete selection",
            new Icon(VaadinIcon.CLOSE_CIRCLE),
            event -> {
                for (ReportEntity reportEntity : reports.getGrid().getSelectedItems()) {
                    UUID uuid = reportEntity.getUuid();
                    try {
                        jpaReportService.internalDeleteByUUID(uuid);
                        Notification.show("Delete success: " + uuid, 2000, Notification.Position.TOP_START);
                    } catch (Exception e) { //NOPMD
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
            .add(Col.<ReportEntity>with().name("Url").value(e -> e.generateUrl(url(appCfg))).type(LINK).build())
            .add(Col.<ReportEntity>with().name("Path").value(prop("path")).build())
            .add(Col.<ReportEntity>with().name("Active").value(prop("active")).build())
            .add(Col.<ReportEntity>with().name("Size KB").value(prop("size")).type(NUMBER).build())
            .build();
    }

    private static ListDataProvider<ReportEntity> asProvider(final JpaReportService jpaReportService) {
        //noinspection unchecked
        final Collection<ReportEntity> collection = (Collection<ReportEntity>) Proxy
            .newProxyInstance(Thread.currentThread().getContextClassLoader(),
                new Class[] {Collection.class},
                (proxy, method, args) -> method.invoke(jpaReportService.getAll(), args));

        return new ListDataProvider<>(collection);
    }

    @PostConstruct
    public void postConstruct() {
        add(deleteSelection);
        reports.addTo(this);
    }
}
