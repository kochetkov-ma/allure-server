package ru.iopump.qa.allure.service;

import java.nio.file.Path;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;

@SuppressWarnings("RedundantModifiersUtilityClassLombok")
@UtilityClass
public class PathUtil {

    public static String str(@Nullable Path path) {
        if (path == null) {
            return "";
        }
        return path.toString().replaceAll("\\\\", "/");
    }
}
