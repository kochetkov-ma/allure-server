package ru.iopump.qa.allure.gui;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.HighlightConditions;
import com.vaadin.flow.router.RouterLink;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import ru.iopump.qa.allure.gui.view.ReportsView;
import ru.iopump.qa.allure.gui.view.ResultsView;

public class MainLayout extends AppLayout {

    public static final String ALLURE_SERVER = "Allure Server";

    private static final long serialVersionUID = 2881152775131362224L;

    private final String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();

    public MainLayout() {
        createHeader();
        createDrawer();
    }

    private void createHeader() {
        var logo = new H3(ALLURE_SERVER);
        logo.addClassName("logo");

        var header = new HorizontalLayout(new DrawerToggle(), logo);
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.setWidth("100%");
        header.addClassName("header");

        addToNavbar(header);
    }

    private void createDrawer() {
        var reports = new RouterLink("Reports", ReportsView.class);
        reports.setHighlightCondition(HighlightConditions.sameLocation());
        var results = new RouterLink("Results", ResultsView.class);
        results.setHighlightCondition(HighlightConditions.sameLocation());
        var swagger = new Anchor(baseUrl + "/swagger", "Swagger");

        var verticalMenu = new VerticalLayout(swagger, results, reports);
        addToDrawer(verticalMenu);
    }
}
