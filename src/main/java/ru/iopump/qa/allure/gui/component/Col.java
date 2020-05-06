package ru.iopump.qa.allure.gui.component;

import com.vaadin.flow.function.ValueProvider;
import lombok.Builder;
import lombok.Value;
import org.joor.Reflect;

@Builder(builderMethodName = "with")
@Value
public class Col<ROW_OBJECT> {
    String name;
    @Builder.Default
    boolean sortable = true;
    ValueProvider<ROW_OBJECT, ?> value;
    @Builder.Default
    Type type = Type.TEXT;

    public static <ROW_OBJECT> ValueProvider<ROW_OBJECT, ?> prop(String propName) {
        return row -> Reflect.on(row).field(propName).get();
    }

    public enum Type {
        TEXT, LINK, NUMBER
    }
}
