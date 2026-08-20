package com.lab.labtimesheet.feature.attendance.service;

import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.attendance.exception.CorrectionException;
import com.lab.labtimesheet.feature.attendance.exception.CorrectionRejection;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRecord;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionSubmission;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionSubmissionCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionsOverview;
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
import java.util.Objects;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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