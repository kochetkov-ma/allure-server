package ru.iopump.qa.allure.gui;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.HighlightConditions;
import com.vaadin.flow.router.RouterLink;
import ru.iopump.qa.allure.gui.view.ReportsView;
import ru.iopump.qa.allure.gui.view.ResultsView;
import ru.iopump.qa.allure.gui.view.SwaggerView;

public class MainLayout extends AppLayout {

    public static final String ALLURE_SERVER = "Allure Server";

    private static final long serialVersionUID = 2881152775131362224L;

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
        // var swagger = new Anchor(baseUrl + "/swagger", "Swagger");
        var swagger = new RouterLink("Swagger", SwaggerView.class);
        results.setHighlightCondition(HighlightConditions.sameLocation());

        Tabs tabs = new Tabs(new Tab(reports), new Tab(results), new Tab(swagger));
        tabs.setOrientation(Tabs.Orientation.VERTICAL);

        addToDrawer(tabs);
    }
}
