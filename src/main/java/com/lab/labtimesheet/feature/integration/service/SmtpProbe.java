package com.lab.labtimesheet.feature.integration.service;

import com.lab.labtimesheet.feature.integration.model.dto.SmtpConnection;

/** External SMTP adapter boundary used by setup tests and application email delivery. */
@FunctionalInterface
public interface SmtpProbe {
    /**
     * Sends one immediate plain-text message using the supplied request-local connection values.
     *
     * @param connection complete SMTP connection material
     * @param recipient destination email address
     * @param subject message subject
     * @param body message body
     */
    void send(SmtpConnection connection, String recipient, String subject, String body);
}
