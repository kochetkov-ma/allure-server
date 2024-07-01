package ru.iopump.qa.allure.gui.view;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.html.IFrame;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.servlet.ServletContext;
import lombok.extern.slf4j.Slf4j;
import ru.iopump.qa.allure.gui.MainLayout;

import java.io.Serial;

import static ru.iopump.qa.allure.gui.MainLayout.ALLURE_SERVER;
import static ru.iopump.qa.allure.helper.Util.concatParts;

@Tag("swagger-view")
@PageTitle("Swagger | " + ALLURE_SERVER)
@Route(value = "swagger", layout = MainLayout.class)
@Slf4j
public class SwaggerView extends VerticalLayout {

    @Serial
    private static final long serialVersionUID = 5822077036734476962L;

    public SwaggerView(ServletContext context) {
        var frame = new IFrame(concatParts(context.getContextPath(), "swagger"));
        frame.setSizeFull();
        add(frame);
    }
}
