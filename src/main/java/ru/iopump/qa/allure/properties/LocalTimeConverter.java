package ru.iopump.qa.allure.properties;

import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Component
@ConfigurationPropertiesBinding
public class LocalTimeConverter implements Converter<String, LocalTime> {

    @Override
    public LocalTime convert(@NonNull String source) {
        return LocalTime.parse(source, DateTimeFormatter.ofPattern("HH[:mm][:ss]"));
    }
}