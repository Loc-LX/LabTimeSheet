package com.lab.labtimesheet.feature.attendance.service;

import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceCorrectionEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceCorrectionEventEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceCorrectionEventRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceCorrectionRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decision-window expiry guard for corrections. {@link #expire(long)} runs in its own committed transaction
 * ({@link Propagation#REQUIRES_NEW}) so the auto-transition survives even when the calling decide/revert
 * transaction then rejects the mutation with {@code DECISION_WINDOW_PASSED} or {@code LOCKED}. A still-pending
 * correction past its inclusive decision deadline is auto-rejected and locked with an immutable AUTO_REJECTED
 * event; an already-decided correction past the deadline is locked with a LOCKED event. Missing, locked, and
 * within-window corrections are left untouched, so repeated scheduler/request invocations are idempotent and
 * fire at most one notification per actual transition (ERR-004). The {@code AttendanceDeadlineService} bounded
 * worker calls {@link #expireNow(AttendanceCorrectionEntity)} so scheduler and request-time paths share exactly
 * the same idempotent transition logic (COR-008).
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class CorrectionWindowGuard {

    private final Clock clock;
    private final AttendanceCorrectionRepository corrections;
    private final AttendanceCorrectionEventRepository events;
    private final AttendanceNotificationClient notifications;

    /**
     * Applies the decision-window expiry for one correction in an isolated, always-committed transaction.
     *
     * @param correctionId correction identifier
     * @return {@code true} when a transition was actually applied, {@code false} for a missing/locked/within-window
     *     correction or a repeated invocation of an already-resolved one
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expire(long correctionId) {
        AttendanceCorrectionEntity correction = corrections.findById(correctionId).orElse(null);
        return correction != null && expireNow(correction);
    }

    /**
     * Applies the idempotent transition for an already-loaded correction inside the caller's transaction. Reused by
     * the bounded {@code AttendanceDeadlineService} worker so a late scheduler run and a request-time access follow
     * the identical deadline evaluation.
     *
     * @param correction loaded correction candidate
     * @return {@code true} when a transition was actually applied
     */
    boolean expireNow(AttendanceCorrectionEntity correction) {
        if (correction.lockedAt() != null) {
            return false;
        }
        Instant now = clock.instant();
        if (!now.isAfter(correction.decisionDeadline())) {
            return false;
        }
        boolean autoRejected = "PENDING".equals(correction.status());
        if (autoRejected) {
            correction.autoReject(now);
            events.save(new AttendanceCorrectionEventEntity(
                    correction.id(),
                    "AUTO_REJECTED",
                    "PENDING",
                    "REJECTED",
                    null,
                    "Pending decision expired at the decision-window deadline",
                    now));
        } else {
            correction.lock(now);
            events.save(new AttendanceCorrectionEventEntity(
                    correction.id(),
                    "LOCKED",
                    correction.status(),
                    correction.status(),
                    null,
                    "Decision window passed; the decided outcome is locked",
                    now));
        }
        corrections.saveAndFlush(correction);
        long recipient = correction.attendanceRecord().toDomain().internId();
        try {
            if (autoRejected) {
                notifications.correctionAutoRejected(recipient, correction.id(), now);
            } else {
                notifications.correctionLocked(recipient, correction.id(), now);
            }
        } catch (RuntimeException exception) {
            // NOT-002/ERR-005: a notification failure must not roll back the committed transition.
        }
        return true;
    }
}