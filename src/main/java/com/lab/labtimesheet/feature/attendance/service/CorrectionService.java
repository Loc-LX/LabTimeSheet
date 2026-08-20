package com.lab.labtimesheet.feature.attendance.service;

import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.attendance.exception.CorrectionException;
import com.lab.labtimesheet.feature.attendance.exception.CorrectionRejection;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRecord;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionDecisionCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionSubmission;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionSubmissionCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionsOverview;
import com.lab.labtimesheet.feature.attendance.model.dto.MentorCorrectionDecision;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceCorrectionEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceCorrectionEventEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceRecordEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceCorrectionEventRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceCorrectionRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceRecordRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional Attendance boundary for missed-checkout corrections: Intern submission inside the exclusive
 * after-cutoff through scheduled-end-plus-24-hours window, plus the prior-corrections read path. Proposed values
 * are validated against the permanently attached historical policy (COR-001 through COR-003).
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class CorrectionService {

    private static final long TWENTY_FOUR_HOURS = 24L * 60L * 60L;

    private final Clock clock;
    private final AccountService accounts;
    private final AttendanceRecordRepository records;
    private final AttendanceCorrectionRepository corrections;
    private final AttendanceCorrectionEventRepository events;
    private final CorrectionWindowGuard windowGuard;

    /**
     * Submits a missed-checkout correction for the owning Intern in one transaction. The attendance record must
     * have no raw checkout, no existing correction, and the server instant must be strictly after the attached
     * policy's inclusive checkout cutoff and no later than its scheduled-end-plus-24-hours deadline. The proposed
     * checkout is derived from the policy-local work date and time and must be strictly after check-in and not in
     * the future. A concurrent unique conflict on the record is returned as {@link CorrectionRejection#ALREADY_SUBMITTED}.
     *
     * @param internId owning Intern account identifier
     * @param command validated form values
     * @return persisted pending submission with its two deadlines
     */
    @Transactional
    public CorrectionSubmission submit(long internId, CorrectionSubmissionCommand command) {
        LocalDate workDate = requireWorkDate(command);
        LocalTime proposedTime = requireProposedTime(command);
        String reason = requireReason(command.reason());
        if (!accounts.isEligibleIntern(internId)) {
            throw new CorrectionException(CorrectionRejection.INACTIVE_INTERN);
        }
        AttendanceRecordEntity record = records.findByInternUserIdAndWorkDate(internId, workDate)
                .orElseThrow(() -> new CorrectionException(CorrectionRejection.NO_ATTENDANCE_RECORD));
        AttendanceRecord domain = record.toDomain();
        if (domain.checkOutAt() != null) {
            throw new CorrectionException(CorrectionRejection.HAS_RAW_CHECKOUT);
        }
        if (corrections.findByAttendanceRecordId(record.id()).isPresent()) {
            throw new CorrectionException(CorrectionRejection.ALREADY_SUBMITTED);
        }
        Instant now = clock.instant();
        Instant scheduledEnd = ZonedDateTime.of(workDate, domain.policy().scheduledEnd(), domain.policy().zoneId())
                .toInstant();
        Instant cutoff = scheduledEnd.plusSeconds(domain.policy().checkoutGraceMinutes() * 60L);
        if (!now.isAfter(cutoff)) {
            throw new CorrectionException(CorrectionRejection.TOO_EARLY);
        }
        Instant submissionDeadline = scheduledEnd.plusSeconds(TWENTY_FOUR_HOURS);
        if (now.isAfter(submissionDeadline)) {
            throw new CorrectionException(CorrectionRejection.DEADLINE_PASSED);
        }
        Instant proposed = ZonedDateTime.of(workDate, proposedTime, domain.policy().zoneId()).toInstant();
        if (!proposed.isAfter(domain.checkInAt())) {
            throw new CorrectionException(CorrectionRejection.PROPOSED_BEFORE_CHECKIN);
        }
        if (proposed.isAfter(now)) {
            throw new CorrectionException(CorrectionRejection.PROPOSED_IN_FUTURE);
        }
        AttendanceCorrectionEntity persisted;
        try {
            persisted = corrections.save(new AttendanceCorrectionEntity(
                    record,
                    proposed,
                    reason,
                    now,
                    submissionDeadline,
                    now.plusSeconds(TWENTY_FOUR_HOURS)));
            events.save(new AttendanceCorrectionEventEntity(
                    persisted.id(), "SUBMITTED", null, "PENDING", internId, reason, now));
        } catch (DataIntegrityViolationException conflict) {
            throw new CorrectionException(CorrectionRejection.ALREADY_SUBMITTED);
        }
        return submission(persisted);
    }

    /**
     * Renders the Intern correction page: the displayed month and the Intern's prior corrections newest-first.
     *
     * @param internId owning Intern account identifier
     * @param month business month being displayed, first day
     * @return correction page state
     */
    @Transactional(readOnly = true)
    public CorrectionsOverview overview(long internId, LocalDate month) {
        Objects.requireNonNull(month, "month");
        if (!accounts.isEligibleIntern(internId)) {
            throw new CorrectionException(CorrectionRejection.INACTIVE_INTERN);
        }
        return new CorrectionsOverview(
                month,
                corrections.findByInternUserIdOrderByIdDesc(internId).stream()
                        .map(this::submission)
                        .toList());
    }

    private CorrectionSubmission submission(AttendanceCorrectionEntity correction) {
        AttendanceRecord record = correction.attendanceRecord().toDomain();
        return new CorrectionSubmission(
                correction.id(),
                record.workDate(),
                correction.requestedCheckoutAt(),
                record.policy().zoneId(),
                correction.reason(),
                correction.status(),
                correction.submittedAt(),
                correction.submissionDeadline(),
                correction.decisionDeadline());
    }

    /**
     * Approves or rejects a pending correction. The decision-window guard runs first in its own committed
     * transaction, so an expired pending correction is already auto-rejected and locked before this boundary
     * refuses the mutation with {@link CorrectionRejection#LOCKED}; a locked outcome and a passed deadline are
     * likewise rejected without appending any Mentor event. Every accepted decision appends one immutable
     * APPROVED or REJECTED event and persists the deciding Mentor, instant, and note.
     *
     * @param mentorId deciding active Mentor account identifier
     * @param correctionId correction identifier
     * @param command approved/rejected plus optional decision note
     * @return the decided correction with its derived presentation flags
     */
    @Transactional
    public MentorCorrectionDecision decide(
            long mentorId, long correctionId, CorrectionDecisionCommand command) {
        requireActiveMentor(mentorId);
        CorrectionDecisionCommand validated = requireCommand(command);
        Instant now = clock.instant();
        windowGuard.expire(correctionId);
        AttendanceCorrectionEntity correction = requireCorrection(correctionId);
        requireMutable(correction, now);
        requireStatus(correction, "PENDING");
        if (validated.approved()) {
            correction.approve(mentorId, now, validated.decisionNote());
        } else {
            correction.reject(mentorId, now, validated.decisionNote());
        }
        events.save(new AttendanceCorrectionEventEntity(
                correctionId,
                validated.approved() ? "APPROVED" : "REJECTED",
                "PENDING",
                validated.approved() ? "APPROVED" : "REJECTED",
                mentorId,
                validated.decisionNote(),
                now));
        saveAndFlushChecked(correction);
        return decision(correctionId, correction, now);
    }

    /**
     * Reverts a decided correction back to PENDING inside its decision window. The same guard and lock/deadline
     * boundary applies as for {@link #decide(long, long, CorrectionDecisionCommand)}, and the revert appends one
     * immutable REOPENED event before clearing the decided fields.
     *
     * @param mentorId deciding active Mentor account identifier
     * @param correctionId correction identifier
     * @param note optional revert note
     * @return the reverted correction with its derived presentation flags
     */
    @Transactional
    public MentorCorrectionDecision revert(long mentorId, long correctionId, String note) {
        requireActiveMentor(mentorId);
        Instant now = clock.instant();
        windowGuard.expire(correctionId);
        AttendanceCorrectionEntity correction = requireCorrection(correctionId);
        requireMutable(correction, now);
        if (!"APPROVED".equals(correction.status()) && !"REJECTED".equals(correction.status())) {
            throw new CorrectionException(CorrectionRejection.INVALID_STATE);
        }
        String fromStatus = correction.status();
        correction.reopen();
        events.save(new AttendanceCorrectionEventEntity(
                correctionId, "REOPENED", fromStatus, "PENDING", mentorId, note, now));
        saveAndFlushChecked(correction);
        return decision(correctionId, correction, now);
    }

    /**
     * Renders the Mentor corrections page: every correction newest-first with derived compliance, lock, and
     * revertability flags so the page can present the exact allowed actions.
     *
     * @return Mentor-facing correction list
     */
    @Transactional(readOnly = true)
    public List<MentorCorrectionDecision> decisions() {
        Instant now = clock.instant();
        return corrections.findAllByOrderByIdDesc().stream()
                .map(correction -> decision(correction.id(), correction, now))
                .toList();
    }

    private MentorCorrectionDecision decision(
            long correctionId, AttendanceCorrectionEntity correction, Instant now) {
        AttendanceRecord record = correction.attendanceRecord().toDomain();
        Instant scheduledEnd = ZonedDateTime.of(
                        record.workDate(), record.policy().scheduledEnd(), record.policy().zoneId())
                .toInstant();
        boolean decided = !"PENDING".equals(correction.status());
        boolean locked = correction.lockedAt() != null;
        return new MentorCorrectionDecision(
                correctionId,
                record.internId(),
                accounts.requireIdentityById(record.internId()).displayName(),
                record.workDate(),
                correction.requestedCheckoutAt(),
                record.policy().zoneId(),
                correction.status(),
                correction.decisionDeadline(),
                correction.lockedAt(),
                correction.decidedByMentorUserId(),
                correction.decidedAt(),
                correction.decisionNote(),
                record.checkOutAt() != null,
                !correction.requestedCheckoutAt().isBefore(scheduledEnd),
                decided && !locked && !now.isAfter(correction.decisionDeadline()));
    }

    private AttendanceCorrectionEntity requireCorrection(long correctionId) {
        return corrections.findById(correctionId)
                .orElseThrow(() -> new CorrectionException(CorrectionRejection.NOT_FOUND));
    }

    private void requireMutable(AttendanceCorrectionEntity correction, Instant now) {
        if (correction.lockedAt() != null) {
            throw new CorrectionException(CorrectionRejection.LOCKED);
        }
        if (now.isAfter(correction.decisionDeadline())) {
            throw new CorrectionException(CorrectionRejection.DECISION_WINDOW_PASSED);
        }
    }

    private static void requireStatus(AttendanceCorrectionEntity correction, String expected) {
        if (!expected.equals(correction.status())) {
            throw new CorrectionException(CorrectionRejection.INVALID_STATE);
        }
    }

    private void requireActiveMentor(long mentorId) {
        try {
            accounts.requireActiveMentorId(mentorId);
        } catch (IllegalArgumentException exception) {
            throw new CorrectionException(CorrectionRejection.INACTIVE_MENTOR);
        }
    }

    private static CorrectionDecisionCommand requireCommand(CorrectionDecisionCommand command) {
        if (command == null) {
            throw new CorrectionException(CorrectionRejection.INVALID_REQUEST);
        }
        String note = command.decisionNote();
        return new CorrectionDecisionCommand(
                command.approved(), note == null ? null : note.strip());
    }

    private void saveAndFlushChecked(AttendanceCorrectionEntity correction) {
        try {
            corrections.saveAndFlush(correction);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new CorrectionException(CorrectionRejection.CONCURRENT_DECISION);
        }
    }

    private static LocalDate requireWorkDate(CorrectionSubmissionCommand command) {
        if (command == null || command.workDate() == null || command.proposedCheckoutTime() == null) {
            throw new CorrectionException(CorrectionRejection.INVALID_REQUEST);
        }
        return command.workDate();
    }

    private static LocalTime requireProposedTime(CorrectionSubmissionCommand command) {
        return command.proposedCheckoutTime();
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new CorrectionException(CorrectionRejection.INVALID_REQUEST);
        }
        return reason.strip();
    }
}