package com.lab.labtimesheet.platform.model.dto;

import com.lab.labtimesheet.platform.model.SecurityMode;

/**
 * Complete request-local SMTP connection material passed only to the delivery adapter.
 * The cleartext password must never be persisted, logged, or exposed to views.
 *
 * @param host SMTP host
 * @param port SMTP port
 * @param securityMode transport security mode
 * @param username optional authentication username
 * @param password optional decrypted password, scoped to the immediate call
 * @param fromAddress envelope From address
 * @param fromName human-readable From name
 */
public record SmtpConnection(String host, int port, SecurityMode securityMode, String username, String password,
        String fromAddress, String fromName) {
}
