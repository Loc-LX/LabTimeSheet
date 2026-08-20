package com.lab.labtimesheet.feature.attendance.service;

import java.time.Instant;

/**
 * Contract for requesting attendance-owned in-app notifications, owned by the attendance feature. The platform
 * feature supplies the tested {@code NotificationService} transport and recipient deduplication; attendance calls
 * this port only when an automatic deadline transition actually occurs, so a late or repeated scheduler/request
 * invocation can never duplicate a notification (ERR-004). A missing or failing platform implementation must not
 * roll back the already-committed domain transition (NOT-002, ERR-005), which is why implementations are expected
 * to be best-effort and the default fallback is a no-op.
 */
public interface AttendanceNotificationClient {

    /**
     * Requests the in-app notification for a pending leave request auto-rejected at its first counted start.
     *
     * @param recipientUserId owning Intern account identifier
     * @param requestId leave request identifier
     * @param at server transition instant
     */
    void leaveAutoRejected(long recipientUserId, long requestId, Instant at);

    /**
     * Requests the in-app notification for a pending correction auto-rejected and locked at its decision deadline.
     *
     * @param recipientUserId owning Intern account identifier
     * @param correctionId correction identifier
     * @param at server transition instant
     */
    void correctionAutoRejected(long recipientUserId, long correctionId, Instant at);

    /**
     * Requests the in-app notification for a decided correction outcome locked after its decision window.
     *
     * @param recipientUserId owning Intern account identifier
     * @param correctionId correction identifier
     * @param at server transition instant
     */
    void correctionLocked(long recipientUserId, long correctionId, Instant at);
}