package com.lab.labtimesheet.feature.integration.model.dto;

import com.lab.labtimesheet.feature.integration.model.SecurityMode;

/**
 * Admin SMTP draft command. Its optional cleartext password is request-local and is encrypted by the service before
 * persistence.
 *
 * @param host SMTP host
 * @param port SMTP port
 * @param securityMode transport security mode
 * @param username optional authentication username
 * @param password optional cleartext password for immediate encryption
 * @param fromAddress envelope From address
 * @param fromName human-readable From name
 */
public record SmtpDraft(String host, int port, SecurityMode securityMode, String username, String password,
        String fromAddress, String fromName) {
}
