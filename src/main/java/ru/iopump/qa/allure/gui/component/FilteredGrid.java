package ru.iopump.qa.allure.gui.component;

import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.HeaderRow;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.Getter;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import ru.iopump.qa.util.Str;

public class FilteredGrid<T> {
    private final static String GRID_CLASS = "report-grid";

    private final ListDataProvider<T> dataProvider;
    @Getter
    private final Grid<T> grid;
    private final List<Col<T>> columnSpecList;

    public FilteredGrid(
        @NonNull final Class<T> rowClass,
        @NonNull final ListDataProvider<T> dataProvider,
        @NonNull final List<Col<T>> columnSpecList
    ) {
        this.dataProvider = dataProvider;
        this.grid = new Grid<>(rowClass, false);
        this.columnSpecList = columnSpecList;

        baseConfigurationGrid();
        filterConfiguration();
    }

    public FilteredGrid<T> addTo(HasComponents parent) {
        parent.add(grid);
        return this;
    }

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

        filterField.addValueChangeListener(event -> dataProvider.addFilter(
            row -> {
                var value = spec.getValue().apply(row);
                return StringUtils.containsIgnoreCase(Str.toStr(value), filterField.getValue());
            }));
        filterField.setValueChangeMode(ValueChangeMode.LAZY);
        filterField.setClearButtonVisible(true);
        filterField.setPlaceholder("Filter contains ...");
        // filterField.setSizeFull();

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
        cols.stream().findFirst().ifPresent(c -> c.setFooter("Count: " + dataProvider.getItems().size()));

    }

    protected Grid.Column<T> addColumn(Col<T> columnSpec) {
        final Grid.Column<T> column;

        switch (columnSpec.getType()) {
            case LINK:
                column = grid.addColumn(linkRenderer(columnSpec));
                break;
            case NUMBER:
                column = grid.addColumn(linkRenderer(columnSpec));
                long amount = dataProvider.getItems().stream()
                    .mapToLong(item -> Long.parseLong(Str.toStr(columnSpec.getValue().apply(item))))
                    .sum();
                column.setFooter("Total: " + amount);
                break;
            default:
                column = grid.addColumn(columnSpec.getValue());
                break;
        }

        column.setKey(columnSpec.getName())
            .setHeader(columnSpec.getName())
            .setAutoWidth(true)
            .setSortable(columnSpec.isSortable());
        return column;
    }

    private Renderer<T> linkRenderer(Col<T> columnSpec) {
        return new ComponentRenderer<>(row -> {
            var link = Str.toStr(columnSpec.getValue().apply(row));
            return new Anchor(link, link);
        });
    }
}
