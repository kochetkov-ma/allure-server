package ru.iopump.qa.allure.gui.component;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.web.multipart.MultipartFile;
import ru.iopump.qa.allure.controller.AllureResultController;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import static ru.iopump.qa.util.Str.format;

@Slf4j
public class ResultUploadDialog extends Dialog { //NOPMD

    private static final long serialVersionUID = -4958469225519042248L;
    private final MemoryBuffer buffer;
    private final Upload upload;
    private final Div infoContainer;
    private final Button close = new Button("Ok", e -> onClickCloseAndDiscard());

    public ResultUploadDialog(AllureResultController allureResultController, int maxFileSizeBytes) {
        this.buffer = new MemoryBuffer();
        this.infoContainer = new Div();

        this.upload = new Upload(buffer);
        upload.setMaxFiles(1);
        upload.setDropLabel(new Label("Upload allure results as Zip archive (.zip)"));
        upload.setAcceptedFileTypes(".zip");
        upload.setMaxFileSize(maxFileSizeBytes);

        upload.addSucceededListener(event -> {
            try {
                var uploadResponse = allureResultController.uploadResults(toMultiPartFile(buffer));
                show(info(format(
                    "File '{}- {} bytes' loaded: {}",
                    event.getFileName(), event.getContentLength(), uploadResponse
                    )), false
                );
            } catch (Exception ex) { //NOPMD
                show(error("Internal error: " + ex.getLocalizedMessage()), true);
                log.error("Uploading error", ex);
            }

        });

        upload.addFileRejectedListener(event -> {
            show(error("Reject: " + event.getErrorMessage()), true);
            if (log.isWarnEnabled()) {
                log.warn("Uploading rejected: " + event.getErrorMessage());
            }
        });
        configureDialog();
    }

    public void onClose(ComponentEventListener<DialogCloseActionEvent> listener) {
        addDialogCloseActionListener(listener);
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

    private void configureDialog() {
        setCloseOnEsc(true);
        setCloseOnOutsideClick(true);
        addDialogCloseActionListener(event -> {
            cleanInfo();
            close();
        });

        var title = new H3("Upload result");
        add(title, new VerticalLayout(upload, infoContainer, close));
    }

    private void onClickOpenAndInit() {
        cleanInfo();
        open();
    }

    private void onClickCloseAndDiscard() {
        super.fireEvent(new Dialog.DialogCloseActionEvent(this, true));
    }

    private MultipartFile toMultiPartFile(MemoryBuffer memoryBuffer) {
        return new MultipartFile() {

            @Override
            @Nonnull
            public String getName() {
                return memoryBuffer.getFileName();
            }

            @Override
            public String getOriginalFilename() {
                return memoryBuffer.getFileName();
            }

            @Override
            public String getContentType() {
                return memoryBuffer.getFileData().getMimeType();
            }

            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public long getSize() {
                try {
                    return getBytes().length;
                } catch (IOException e) {
                    return 0;
                }
            }

            @Override
            @Nonnull
            public byte[] getBytes() throws IOException {
                return IOUtils.toByteArray(getInputStream());
            }

            @Override
            @Nonnull
            public InputStream getInputStream() {
                return memoryBuffer.getInputStream();
            }

            @Override
            public void transferTo(@Nonnull File destination) {
                throw new UnsupportedOperationException("transferTo");
            }
        };
    }

    private Component info(String text) {
        var p = new Paragraph();
        p.getElement().setText(text);
        p.getElement().getStyle().set("color", "green");
        return p;
    }

    private Component error(String text) {
        var p = new Paragraph();
        p.getElement().setText(text);
        p.getElement().getStyle().set("color", "red");
        return p;
    }

    private void cleanInfo() {
        infoContainer.removeAll();
        upload.getElement().getStyle().remove("background");
    }

    private void show(Component component, boolean error) {
        cleanInfo();
        if (error) {
            upload.getElement().getStyle().set("background", "pink");
        }
        infoContainer.add(component);

    }
}

