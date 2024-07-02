package ru.iopump.qa.allure.service;

import jakarta.annotation.Nullable;
import lombok.experimental.UtilityClass;

import java.nio.file.Path;

@SuppressWarnings("RedundantModifiersUtilityClassLombok")
@UtilityClass
public class PathUtil {

    public final static String UUID_PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}";

    public static String str(@Nullable Path path) {
        if (path == null) {
            return "";
        }
        return path.toString().replaceAll("\\\\", "/");
    }
}
