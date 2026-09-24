package com.lab.labtimesheet.platform.controller;

import com.lab.labtimesheet.platform.service.SmtpConfigurationService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Supplies the persistent restricted-installation warning state to server-rendered views until a tested SMTP
 * configuration is active.
 */
@ControllerAdvice
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class SmtpWarningAdvice {
    private final SmtpConfigurationService smtp;

    @ModelAttribute("smtpRestricted")
    boolean smtpRestricted(HttpServletRequest request) {
        if (isErrorRequest(request)) {
            return false;
        }
        return !smtp.hasActiveConfiguration();
    }

    private static boolean isErrorRequest(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String errorPath = (contextPath == null ? "" : contextPath) + "/error";
        return request.getDispatcherType() == DispatcherType.ERROR
                || errorPath.equals(request.getRequestURI());
    }
}
