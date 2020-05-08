package ru.iopump.qa.allure.gui.view;

import static ru.iopump.qa.allure.gui.MainLayout.ALLURE_SERVER;
import static ru.iopump.qa.allure.gui.component.Col.prop;

import com.google.common.collect.ImmutableList;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import ru.iopump.qa.allure.controller.AllureReportController;
import ru.iopump.qa.allure.controller.AllureResultController;
import ru.iopump.qa.allure.gui.MainLayout;
import ru.iopump.qa.allure.gui.component.Col;
import ru.iopump.qa.allure.gui.component.FilteredGrid;
import ru.iopump.qa.allure.gui.component.ReportGenerateDialog;
import ru.iopump.qa.allure.gui.component.ResultUploadDialog;
import ru.iopump.qa.allure.gui.dto.GenerateDto;
import ru.iopump.qa.allure.model.ResultResponse;
import ru.iopump.qa.util.StreamUtil;

@Tag("results-view")
@PageTitle("Results | " + ALLURE_SERVER)
@Route(value = "results", layout = MainLayout.class)
@Slf4j
public class ResultsView extends VerticalLayout {
    private static final long serialVersionUID = 5822017036734416962L;

    private static final List<Col<ResultResponse>> COLUMNS = ImmutableList.<Col<ResultResponse>>builder()
        .add(Col.<ResultResponse>with().name("Uuid").value(prop("uuid")).build())
        .add(Col.<ResultResponse>with().name("Created").value(prop("created")).build())
        .add(Col.<ResultResponse>with().name("Size KB").value(prop("size")).type(Col.Type.NUMBER).build())
        .build();

    /* COMPONENTS */
    private final FilteredGrid<ResultResponse> results;
    private final Button generateButton;
    private final ReportGenerateDialog generateDialog;
    private final Button uploadButton;
    private final ResultUploadDialog uploadDialog;

    private final Button deleteSelection;

    public ResultsView(final AllureResultController allureResultController,
                       final AllureReportController allureReportController) {

        this.results = new FilteredGrid<>(
            ResultResponse.class,
            asProvider(allureResultController),
            COLUMNS
        );
        this.generateButton = new Button("Generate report");
        this.uploadButton = new Button("Upload result");

        this.generateDialog = new ReportGenerateDialog(allureReportController);
        this.uploadDialog = new ResultUploadDialog(allureResultController);

        uploadDialog.onClose(event -> results.getGrid().getDataProvider().refreshAll());

        this.deleteSelection = new Button("Delete selection",
            new Icon(VaadinIcon.CLOSE_CIRCLE),
            event -> {
                for (ResultResponse resultResponse : results.getGrid().getSelectedItems()) {
                    String uuid = resultResponse.getUuid();
                    try {
                        allureResultController.deleteResult(uuid);
                        Notification.show("Delete success: " + uuid, 2000, Notification.Position.TOP_START);
                    } catch (Exception e) { //NOPMD
                        Notification.show("Deleting error: " + e.getLocalizedMessage(),
                            5000,
                            Notification.Position.TOP_START);
                        log.error("Deleting error", e);
                    }
                }
                results.getGrid().deselectAll();
                results.getGrid().getDataProvider().refreshAll();
            });
        deleteSelection.addThemeVariants(ButtonVariant.LUMO_ERROR);

        // Add first selected item on open generation dialog or empty bind
        generateDialog.addOpenedChangeListener(event -> {
            StreamUtil.stream(results.getGrid().getSelectedItems()).findFirst()
                .ifPresentOrElse(resultResponse -> generateDialog.getPayload().getBinder()
                        .setBean(new GenerateDto(resultResponse.getUuid(), null, null, false)),
                    () -> generateDialog.getPayload().getBinder().setBean(new GenerateDto())
                );
        });
    }

    //// PRIVATE ////
    private static ListDataProvider<ResultResponse> asProvider(final AllureResultController allureResultController) {
        //noinspection unchecked
        final Collection<ResultResponse> collection = (Collection<ResultResponse>) Proxy
            .newProxyInstance(Thread.currentThread().getContextClassLoader(),
                new Class[] {Collection.class},
                (proxy, method, args) -> method.invoke(allureResultController.getAllResult(), args));

        return new ListDataProvider<>(collection);
    }

    @PostConstruct
    public void postConstruct() {
        generateDialog.addControlButton(generateButton);
        uploadDialog.addControlButton(uploadButton);

        add(new HorizontalLayout(generateButton, uploadButton), generateDialog);
        add(deleteSelection);
        results.addTo(this);
    }
}
