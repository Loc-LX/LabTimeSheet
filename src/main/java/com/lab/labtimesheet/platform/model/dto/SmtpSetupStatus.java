package com.lab.labtimesheet.platform.model.dto;

import com.lab.labtimesheet.platform.model.SecurityMode;

/**
 * Non-secret snapshot used by Admin setup views. No encrypted or cleartext credential material crosses this
 * service boundary.
 *
 * @param active whether a tested SMTP configuration is active
 * @param draftId editable draft identifier, or {@code null} when no draft exists
 * @param tested whether the current draft most recently passed its connection test
 * @param host draft host
 * @param port draft port
 * @param securityMode draft transport security
 * @param username draft username, or {@code null}
 * @param fromAddress draft sender address
 * @param fromName draft sender display name
 */
public record SmtpSetupStatus(boolean active, Long draftId, boolean tested, String host, int port,
        SecurityMode securityMode, String username, String fromAddress, String fromName) {
}
