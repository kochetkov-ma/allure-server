package ru.iopump.qa.allure.helper;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import ru.iopump.qa.allure.AppCfg;

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
}
