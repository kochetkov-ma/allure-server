package ru.iopump.qa.allure.gui.component;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.PropertyId;
import io.qameta.allure.entity.ExecutorInfo;
import java.io.Closeable;
import java.util.Collections;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import ru.iopump.qa.allure.controller.AllureReportController;
import ru.iopump.qa.allure.gui.dto.GenerateDto;
import ru.iopump.qa.allure.model.ReportGenerateRequest;
import ru.iopump.qa.allure.model.ReportSpec;

@Slf4j
public class ReportGenerateDialog extends Dialog {

    private static final long serialVersionUID = -1711683187592143180L;

    private final AllureReportController allureReportController;

    private final Label info = new Label();
    private final Label error = new Label();
    @Getter
    private final FormPayload payload;
    private final Button generate = new Button("Generate", e -> onClickGenerate());
    private final Button close = new Button("Cancel", e -> onClickCloseAndDiscard());
    private final FormLayout form;

    public ReportGenerateDialog(AllureReportController allureReportController) {
        this.allureReportController = allureReportController;

        info.getStyle().set("color", "green");
        error.getStyle().set("color", "red");

        this.form = new FormLayout();
        this.payload = new FormPayload(form);

        configureForm();
        configureDialog();
    }

    public void addControlButton(final Button externalButton) {
        externalButton.addClickListener(event -> {
            if (isOpened()) {
                onClickCloseAndDiscard();
            } else {
                onClickOpenAndInit();
            }
        });
    }


    private void configureForm() {
        form.add(createButtonsLayout(), 2);
    }

    private HorizontalLayout createButtonsLayout() {
        generate.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        generate.addClickShortcut(Key.ENTER);

        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        close.addClickShortcut(Key.ESCAPE);
        close.addClickListener(e -> onClickCloseAndDiscard());

        return new HorizontalLayout(generate, close);
    }

    private void onClickGenerate() {
        cleanInfo();
        try {
            if (payload.getBinder().validate().isOk()) {
                var res = allureReportController.generateReport(payload.readAndMap()); // Generate
                info.setVisible(true);
                info.setText("Success: " + res);
            }
        } catch (Exception e) { //NOPMD
            error.setVisible(true);
            error.setText("Error: " + e.getLocalizedMessage());
            log.error("Generation error", e);
        } finally {
            payload.reset();
        }
    }

    private void onClickOpenAndInit() {
        open();
        cleanInfo();
    }


    private void onClickCloseAndDiscard() {
        close();
        payload.close();
        cleanInfo();
    }

    private void cleanInfo() {
        info.removeAll();
        error.removeAll();
    }

    private void configureDialog() {
        setCloseOnEsc(true);
        setCloseOnOutsideClick(true);

        payload.getBinder().addStatusChangeListener(e -> generate.setEnabled(payload.getBinder().isValid()));
        var title = new H3("Generate report");
        add(title, form, info, error);
    }

    public static class FormPayload implements Closeable {
        @PropertyId("resultUuid")
        private final TextField resultUuid = new TextField("Result uuid");
        @PropertyId("path")
        private final TextField path = new TextField("Report path");
        @PropertyId("build")
        private final TextField build = new TextField("Build id");
        @PropertyId("deleteResults")
        private final Checkbox deleteResults = new Checkbox("Delete on success");
        @Getter
        private final Binder<GenerateDto> binder = new BeanValidationBinder<>(GenerateDto.class);

        public FormPayload(@NonNull FormLayout form) {
            create();
            visitForm(form);
        }

        public ReportGenerateRequest toReportGenerateRequest(GenerateDto generateDto) {
            var request = new ReportGenerateRequest();
            var spec = new ReportSpec();
            var info = new ExecutorInfo();
            request.setResults(Collections.singletonList(generateDto.getResultUuid()));
            request.setDeleteResults(generateDto.isDeleteResults());

            spec.setPath(new String[] {generateDto.getPath()});
            spec.setExecutorInfo(info.setBuildName(generateDto.getBuild()));

            request.setReportSpec(spec);
            return request;
        }

        public ReportGenerateRequest readAndMap() {
            return toReportGenerateRequest(binder.getBean());
        }

        private void reset() {
            binder.setBean(new GenerateDto());
        }

        private void create() {
            binder.bindInstanceFields(this);
        }

        private void visitForm(final FormLayout form) {
            form.add(resultUuid, path, build, deleteResults);
        }

        @Override
        public void close() {
            binder.removeBean();
        }
    }
}

