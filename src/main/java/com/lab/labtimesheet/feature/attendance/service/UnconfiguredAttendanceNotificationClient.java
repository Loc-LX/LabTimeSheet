package com.lab.labtimesheet.feature.attendance.service;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Default attendance notification client used when the platform feature has not yet supplied the tested
 * {@code NotificationService}. It is a deliberate no-op: attendance deadline transitions must commit even when no
 * notification transport exists (NOT-002, ERR-005). Registered by
 * {@link AttendanceNotificationClientConfiguration}; the platform implementation replaces this bean once
 * {@code I2-PLAT-06} lands, without changing any attendance behavior.
 */
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class UnconfiguredAttendanceNotificationClient implements AttendanceNotificationClient {

    @Override
    public void leaveAutoRejected(long recipientUserId, long requestId, Instant at) {
        // No-op fallback; the platform notification service is not configured on this branch yet.
    }

    @Override
    public void correctionAutoRejected(long recipientUserId, long correctionId, Instant at) {
        // No-op fallback; the platform notification service is not configured on this branch yet.
    }

    @Override
    public void correctionLocked(long recipientUserId, long correctionId, Instant at) {
        // No-op fallback; the platform notification service is not configured on this branch yet.
    }
}