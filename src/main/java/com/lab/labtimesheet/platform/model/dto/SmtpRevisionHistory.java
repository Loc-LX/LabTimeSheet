package com.lab.labtimesheet.platform.model.dto;

import java.time.Instant;

import com.lab.labtimesheet.platform.model.SecurityMode;
import com.lab.labtimesheet.platform.model.SmtpStatus;

/**
 * Non-secret, immutable projection of one retained SMTP revision for Admin History.
 * Credential ciphertext, nonces, passwords, and transient delivery values are intentionally absent.
 *
 * @param id retained revision identifier
 * @param status lifecycle state
 * @param host configured SMTP host
 * @param port configured SMTP port
 * @param securityMode configured transport security
 * @param username configured login name, or {@code null}
 * @param fromAddress configured sender address
 * @param fromName configured sender display name
 * @param testedAt successful test time, or {@code null}
 * @param testedByUserId Admin who completed the test, or {@code null}
 * @param activatedAt activation time, or {@code null}
 * @param activatedByUserId Admin who activated the revision, or {@code null}
 * @param retiredAt retirement time, or {@code null}
 * @param retiredByUserId Admin who retired the revision, or {@code null}
 * @param createdByUserId Admin who created the revision
 * @param createdAt revision creation time
 * @param updatedAt last lifecycle update time
 */
public record SmtpRevisionHistory(
        Long id,
        SmtpStatus status,
        String host,
        int port,
        SecurityMode securityMode,
        String username,
        String fromAddress,
        String fromName,
        Instant testedAt,
        Long testedByUserId,
        Instant activatedAt,
        Long activatedByUserId,
        Instant retiredAt,
        Long retiredByUserId,
        Long createdByUserId,
        Instant createdAt,
        Instant updatedAt) {
}
