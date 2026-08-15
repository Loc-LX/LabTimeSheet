package com.lab.labtimesheet.feature.integration.controller;

import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Supplies the persistent restricted-installation warning state to server-rendered views until a tested SMTP
 * configuration is active.
 */
@ControllerAdvice
class SmtpWarningAdvice {
    private final SmtpConfigurationService smtp;

    SmtpWarningAdvice(SmtpConfigurationService smtp) {
        this.smtp = smtp;
    }

    @ModelAttribute("smtpRestricted")
    boolean smtpRestricted() {
        return !smtp.hasActiveConfiguration();
    }
}
