package ru.iopump.qa.allure.security;

import com.vaadin.flow.server.ServletHelper;
import com.vaadin.flow.shared.ApplicationConstants;
import lombok.experimental.UtilityClass;

import javax.servlet.http.HttpServletRequest;
import java.util.stream.Stream;

@UtilityClass
public class SecurityUtils {

    boolean isFrameworkInternalRequest(HttpServletRequest request) {
        final String parameterValue = request.getParameter(ApplicationConstants.REQUEST_TYPE_PARAMETER);
        //noinspection deprecation
        return parameterValue != null
                && Stream.of(ServletHelper.RequestType.values())
                .anyMatch(r -> r.getIdentifier().equals(parameterValue));
    }
}