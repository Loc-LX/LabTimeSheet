package com.lab.labtimesheet.feature.integration.controller;

import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
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
    boolean smtpRestricted() {
        return !smtp.hasActiveConfiguration();
    }
}
