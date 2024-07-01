package ru.iopump.qa.allure.security;

import com.vaadin.flow.server.HandlerHelper;
import com.vaadin.flow.shared.ApplicationConstants;
import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.UtilityClass;

import java.util.stream.Stream;

@UtilityClass
public class SecurityUtils {

    boolean isFrameworkInternalRequest(HttpServletRequest request) {
        final String parameterValue = request.getParameter(ApplicationConstants.REQUEST_TYPE_PARAMETER);
        return parameterValue != null
                && Stream.of(HandlerHelper.RequestType.values())
                .anyMatch(r -> r.getIdentifier().equals(parameterValue));
    }
}