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
 * within-window corrections are left untouched, so repeated scheduler invocations are idempotent.
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class CorrectionWindowGuard {

    private final Clock clock;
    private final AttendanceCorrectionRepository corrections;
    private final AttendanceCorrectionEventRepository events;

    /**
     * Applies the decision-window expiry for one correction in an isolated, always-committed transaction.
     *
     * @param correctionId correction identifier
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expire(long correctionId) {
        AttendanceCorrectionEntity correction = corrections.findById(correctionId).orElse(null);
        if (correction == null || correction.lockedAt() != null) {
            return;
        }
        Instant now = clock.instant();
        if (!now.isAfter(correction.decisionDeadline())) {
            return;
        }
        if ("PENDING".equals(correction.status())) {
            correction.autoReject(now);
            events.save(new AttendanceCorrectionEventEntity(
                    correctionId,
                    "AUTO_REJECTED",
                    "PENDING",
                    "REJECTED",
                    null,
                    "Pending decision expired at the decision-window deadline",
                    now));
        } else {
            correction.lock(now);
            events.save(new AttendanceCorrectionEventEntity(
                    correctionId,
                    "LOCKED",
                    correction.status(),
                    correction.status(),
                    null,
                    "Decision window passed; the decided outcome is locked",
                    now));
        }
        corrections.saveAndFlush(correction);
    }
}