package ru.iopump.qa.allure.gui.component;

import com.google.common.collect.Maps;
import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.grid.HeaderRow;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.function.ValueProvider;
import lombok.Getter;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import ru.iopump.qa.util.Str;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static ru.iopump.qa.allure.helper.Util.shortUrl;

public class FilteredGrid<T> {
    public static final String FONT_FAMILY = "font-family";
    public static final String GERMANIA_ONE = "Germania One";
    private final static String GRID_CLASS = "report-grid";
    private final ListDataProvider<T> dataProvider;
    @Getter
    private final Grid<T> grid;
    private final List<Col<T>> columnSpecList;
    private final Map<Grid.Column<T>, Supplier<String>> dynamicFooter = Maps.newHashMap();

    public FilteredGrid(
        @NonNull final ListDataProvider<T> dataProvider,
        @NonNull final List<Col<T>> columnSpecList
    ) {
        this.dataProvider = dataProvider;
        this.grid = new Grid<>();
        this.columnSpecList = columnSpecList;

        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        baseConfigurationGrid();
        filterConfiguration();

        updateFooters(); // Init footers
        dataProvider.addDataProviderListener(event -> updateFooters()); // Update footers on change
    }

    public FilteredGrid<T> addTo(HasComponents parent) {
        parent.add(grid);
        return this;
    }

    protected Grid.Column<T> addColumn(Col<T> columnSpec) {
        final Grid.Column<T> column;

        switch (columnSpec.getType()) {
            case LINK:
                column = grid.addColumn(link(columnSpec));
                break;
            case NUMBER:
                column = grid.addColumn(text(columnSpec));
                final Supplier<String> footer = () -> {
                    long amount = dataProvider.fetch(new Query<>(dataProvider.getFilter()))
                        .mapToLong(item -> Long.parseLong(Str.toStr(columnSpec.getValue().apply(item))))
                        .sum();
                    return "Total: " + amount;
                };
                dynamicFooter.put(column, footer);
                break;
            default:
                column = grid.addColumn(text(columnSpec));
                break;
        }

        column.setKey(columnSpec.getName())
            .setHeader(columnSpec.getName())
            .setAutoWidth(true)
            .setSortable(columnSpec.isSortable());
        //noinspection unchecked,rawtypes
        column.setComparator((ValueProvider) columnSpec.getValue());
        return column;
    }

    //region Private methods
    //// PRIVATE ////
    private void filterConfiguration() {
        var headerCells = grid.appendHeaderRow().getCells();

        IntStream.range(0, headerCells.size())
            .forEach(index -> {
                final Col<T> spec = columnSpecList.get(index);
                HeaderRow.HeaderCell headerCell = headerCells.get(index);
                addFilter(spec, headerCell);
            });
    }

    private void addFilter(Col<T> spec, HeaderRow.HeaderCell headerCell) {
        final TextField filterField = new TextField();
        filterField.addValueChangeListener(event -> {
            dataProvider.addFilter(
                row -> {
                    var value = spec.getValue().apply(row);
                    return StringUtils.containsIgnoreCase(Str.toStr(value), filterField.getValue());
                });
            updateFooters();
        });
        filterField.setValueChangeMode(ValueChangeMode.LAZY);
        filterField.setValueChangeTimeout(1000);
        filterField.setClearButtonVisible(true);
        filterField.setPlaceholder("Filter contains ...");

        headerCell.setComponent(filterField);
    }

    private void baseConfigurationGrid() {
        grid.addClassName(GRID_CLASS);
        grid.setDataProvider(dataProvider);
        grid.removeAllColumns();
        grid.setHeightByRows(true);
        grid.setSelectionMode(Grid.SelectionMode.MULTI);

        final List<Grid.Column<T>> cols = columnSpecList.stream()
            .map(this::addColumn)
            .collect(Collectors.toUnmodifiableList());
        cols.stream().findFirst()
            .ifPresent(c -> dynamicFooter.put(c, () -> "Count: " + dataProvider
                .size(new Query<>(dataProvider.getFilter())))
            );
    }

    private Renderer<T> text(Col<T> columnSpec) {
        return new ComponentRenderer<>(row -> {
            var value = Str.toStr(columnSpec.getValue().apply(row));
            var res = new Span(value);
            res.getStyle().set(FONT_FAMILY, GERMANIA_ONE);
            return res;
        });
    }

    private Renderer<T> link(Col<T> columnSpec) {
        return new ComponentRenderer<>(row -> {
            var link = Str.toStr(columnSpec.getValue().apply(row));
            var res = new Anchor(link, shortUrl(link));
            res.setTarget("_blank");
            res.getStyle().set(FONT_FAMILY, GERMANIA_ONE);
            return res;
        });
    }

    private void updateFooters() {
        dynamicFooter.forEach((col, sup) -> col.setFooter(sup.get()));
    }
//endregion
}
