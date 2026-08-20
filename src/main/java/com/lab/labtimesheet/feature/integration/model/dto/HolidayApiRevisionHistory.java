package com.lab.labtimesheet.feature.integration.model.dto;

import java.time.Instant;

import com.lab.labtimesheet.feature.integration.model.HolidayApiStatus;

/**
 * Immutable non-secret HolidayAPI revision projection for Admin History.
 * Ciphertext, nonce, API key, and master-key material are intentionally absent.
 *
 * @param id retained revision identifier
 * @param status lifecycle state
 * @param countryCode fixed integration country
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
public record HolidayApiRevisionHistory(
        Long id,
        HolidayApiStatus status,
        String countryCode,
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
