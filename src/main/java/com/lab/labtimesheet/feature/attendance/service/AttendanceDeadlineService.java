package com.lab.labtimesheet.feature.attendance.service;

import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceCorrectionEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceCorrectionRepository;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared idempotent deadline transitions and bounded expiry evaluation (I2-ATT-07). The scheduled worker and every
 * request-time access path call the same transition logic, so a late or repeated scheduler invocation, and an
 * access that races with it, produce exactly one final state and one notification set (LEV-010, COR-008, ERR-004).
 * Each single-request method and each bounded batch runs in an isolated committed transaction
 * ({@link Propagation#REQUIRES_NEW}), so the auto-reject survives even when the calling mutation then refuses the
 * action (ERR-003). A notification failure is swallowed so the committed domain transition is never rolled back
 * (NOT-002, ERR-005).
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class AttendanceDeadlineService {

    private final Clock clock;
    private final LeaveRequestRepository leaveRequests;
    private final AttendanceCorrectionRepository corrections;
    private final CorrectionWindowGuard correctionWindowGuard;
    private final AttendanceNotificationClient notifications;

    /**
     * Auto-rejects one pending leave request at/after its first counted start in its own committed transaction.
     *
     * @param requestId leave request identifier
     * @return {@code true} when the request was auto-rejected, {@code false} for a missing/resolved/not-yet-started
     *     request
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expireLeave(long requestId) {
        LeaveRequestEntity request = leaveRequests.findById(requestId).orElse(null);
        return request != null && expireLeaveNow(request);
    }

    /**
     * Auto-rejects a bounded batch of expired pending leaves in one committed transaction.
     *
     * @param limit maximum number of requests to inspect
     * @return number of requests actually auto-rejected
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int expirePendingLeaves(int limit) {
        List<LeaveRequestEntity> batch = leaveRequests
                .findByStatusAndFirstCountedStartAtLessThanEqualOrderByIdAsc(
                        "PENDING", clock.instant(), PageRequest.of(0, limit));
        int expired = 0;
        for (LeaveRequestEntity request : batch) {
            if (expireLeaveNow(request)) {
                expired++;
            }
        }
        return expired;
    }

    /**
     * Auto-rejects a bounded batch of one Intern's expired pending leaves in one committed transaction, so the
     * Intern leave page applies the identical guard without touching other Interns.
     *
     * @param internUserId owning Intern account identifier
     * @param limit maximum number of requests to inspect
     * @return number of requests actually auto-rejected
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int expirePendingLeavesForIntern(long internUserId, int limit) {
        List<LeaveRequestEntity> batch = leaveRequests
                .findByInternUserIdAndStatusAndFirstCountedStartAtLessThanEqualOrderByIdAsc(
                        internUserId, "PENDING", clock.instant(), PageRequest.of(0, limit));
        int expired = 0;
        for (LeaveRequestEntity request : batch) {
            if (expireLeaveNow(request)) {
                expired++;
            }
        }
        return expired;
    }

    private boolean expireLeaveNow(LeaveRequestEntity request) {
        if (!"PENDING".equals(request.status())) {
            return false;
        }
        Instant now = clock.instant();
        if (now.isBefore(request.firstCountedStartAt())) {
            return false;
        }
        request.autoReject(now);
        leaveRequests.saveAndFlush(request);
        try {
            notifications.leaveAutoRejected(request.internUserId(), request.id(), now);
        } catch (RuntimeException exception) {
            // NOT-002/ERR-005: a notification failure must not roll back the committed transition.
        }
        return true;
    }

    /**
     * Expires a bounded batch of unlocked corrections whose decision deadline has passed in one committed
     * transaction, delegating the idempotent transition to {@link CorrectionWindowGuard}.
     *
     * @param limit maximum number of corrections to inspect
     * @return number of corrections actually transitioned
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int expireCorrections(int limit) {
        List<AttendanceCorrectionEntity> batch =
                corrections.findExpiredByDecisionDeadline(clock.instant(), PageRequest.of(0, limit));
        int expired = 0;
        for (AttendanceCorrectionEntity correction : batch) {
            if (correctionWindowGuard.expireNow(correction)) {
                expired++;
            }
        }
        return expired;
    }

    /**
     * Expires a bounded batch of one Intern's unlocked expired corrections in one committed transaction, so the
     * Intern correction page applies the identical guard without touching other Interns.
     *
     * @param internUserId owning Intern account identifier
     * @param limit maximum number of corrections to inspect
     * @return number of corrections actually transitioned
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int expireCorrectionsForIntern(long internUserId, int limit) {
        List<AttendanceCorrectionEntity> batch =
                corrections.findExpiredByInternUserId(internUserId, clock.instant(), PageRequest.of(0, limit));
        int expired = 0;
        for (AttendanceCorrectionEntity correction : batch) {
            if (correctionWindowGuard.expireNow(correction)) {
                expired++;
            }
        }
        return expired;
    }
}