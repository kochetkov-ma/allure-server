package ru.iopump.qa.allure.helper;

import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import ru.iopump.qa.allure.AppCfg;
import ru.iopump.qa.util.Str;
import ru.iopump.qa.util.StreamUtil;

@SuppressWarnings("RedundantModifiersUtilityClassLombok")
@UtilityClass
public class Util {
    public static String url(AppCfg appCfg) {
        if (StringUtils.isBlank(appCfg.reportHost())) {
            return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString() + "/";
        } else {
            return appCfg.reportHost();
        }
    }

    public static String join(@Nullable Object... part) {
        return StreamUtil.stream(part)
            .map(Str::toStr)
            .map(s -> StringUtils.strip(s, "/"))
            .collect(Collectors.joining("/"));
    }

    public static String lastSegment(@Nullable String str) {
        var localString = StringUtils.stripEnd(str, "/");
        return Optional.ofNullable(StringUtils.substringAfterLast(localString, "/")).orElse(localString);
    }
}
