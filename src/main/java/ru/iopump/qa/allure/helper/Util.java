package ru.iopump.qa.allure.helper;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import ru.iopump.qa.allure.properties.AllureProperties;
import ru.iopump.qa.util.Str;
import ru.iopump.qa.util.StreamUtil;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.stream.Collectors;

@SuppressWarnings("RedundantModifiersUtilityClassLombok")
@UtilityClass
public class Util {
    public static String url(AllureProperties allureProperties) {
        if (StringUtils.isBlank(allureProperties.serverBaseUrl())) {
            return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString() + "/";
        } else {
            return allureProperties.serverBaseUrl();
        }
    }

    public static String concatParts(@Nullable String... part) {
        return StreamUtil.stream(part)
                .collect(Collectors.joining("/"))
                .replaceAll("/{2,}", "/");
    }

    public static String join(@Nullable Object... part) {
        return StreamUtil.stream(part)
                .map(Str::toStr)
                .map(s -> StringUtils.strip(s, "/"))
                .collect(Collectors.joining("/"));
    }

    public static String shortUrl(@Nullable String str) {
        var ls = lastSegment(str);
        return ls.length() < 16 ? noFirstSegment(noScheme(str)) : ls;
    }

    public static String noFirstSegment(@Nullable String str) {
        var localString = StringUtils.stripEnd(str, "/");
        return Optional.ofNullable(StringUtils.substringAfter(localString, "/")).orElse(localString);
    }


    public static String lastSegment(@Nullable String str) {
        var localString = StringUtils.stripEnd(str, "/");
        return Optional.ofNullable(StringUtils.substringAfterLast(localString, "/")).orElse(localString);
    }

    public static String noScheme(@Nullable String str) {
        var localString = StringUtils.stripEnd(str, "/");
        return Optional.ofNullable(StringUtils.substringAfter(localString, "//")).orElse(localString);
    }
}
