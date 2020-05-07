package ru.iopump.qa.allure.gui.view;

import static ru.iopump.qa.allure.gui.MainLayout.ALLURE_SERVER;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.html.IFrame;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import lombok.extern.slf4j.Slf4j;
import ru.iopump.qa.allure.gui.MainLayout;

@Tag("swagger-view")
@PageTitle("Swagger | " + ALLURE_SERVER)
@Route(value = "swagger", layout = MainLayout.class)
@Slf4j
public class SwaggerView extends VerticalLayout {
    private static final long serialVersionUID = 5822077036734476962L;

    public SwaggerView() {
        var frame = new IFrame("/swagger");
        frame.setSizeFull();
        add(frame);
    }
}
