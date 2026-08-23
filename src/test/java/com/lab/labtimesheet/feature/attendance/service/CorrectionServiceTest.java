package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.attendance.exception.CorrectionException;
import com.lab.labtimesheet.feature.attendance.exception.CorrectionRejection;
import com.lab.labtimesheet.feature.attendance.model.AttendancePolicyFixtures;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRecord;
import com.lab.labtimesheet.feature.attendance.model.AttendanceViolations;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionSubmission;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionSubmissionCommand;
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
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CorrectionServiceTest {

    private static final long INTERN_ID = 42L;
    private static final long RECORD_ID = 77L;
    private static final long SAVED_CORRECTION_ID = 88L;
    private static final LocalDate WORKDAY = LocalDate.of(2026, 8, 14);
    private static final Instant CHECK_IN = Instant.parse("2026-08-14T01:30:00Z");
    private static final Instant CUTOFF = Instant.parse("2026-08-14T09:00:00Z");
    private static final Instant SUBMISSION_DEADLINE = Instant.parse("2026-08-15T08:30:00Z");

    private final AccountService accounts = mock(AccountService.class);
    private final AttendanceRecordRepository records = mock(AttendanceRecordRepository.class);
    private final AttendanceCorrectionRepository corrections = mock(AttendanceCorrectionRepository.class);
    private final AttendanceCorrectionEventRepository events = mock(AttendanceCorrectionEventRepository.class);
    private final CorrectionWindowGuard windowGuard = mock(CorrectionWindowGuard.class);
      private final AttendanceDeadlineService deadlines = mock(AttendanceDeadlineService.class);

    private AttendanceRecordEntity record;
    private AttendanceCorrectionEntity saved;

    @BeforeEach
    void setUp() {
        when(accounts.isEligibleIntern(INTERN_ID)).thenReturn(true);
        record = mock(AttendanceRecordEntity.class);
        when(record.id()).thenReturn(RECORD_ID);
        when(record.toDomain()).thenReturn(new AttendanceRecord(
                INTERN_ID, WORKDAY, AttendancePolicyFixtures.seeded(1L), CHECK_IN, null));
        when(records.findByInternUserIdAndWorkDate(INTERN_ID, WORKDAY)).thenReturn(Optional.of(record));
        when(corrections.findByAttendanceRecordId(RECORD_ID)).thenReturn(Optional.empty());

        saved = mock(AttendanceCorrectionEntity.class);
        when(saved.id()).thenReturn(SAVED_CORRECTION_ID);
        when(saved.attendanceRecord()).thenReturn(record);
        when(saved.requestedCheckoutAt()).thenReturn(Instant.parse("2026-08-14T08:45:00Z"));
        when(saved.reason()).thenReturn("Forgot to check out");
        when(saved.status()).thenReturn("PENDING");
        when(saved.submittedAt()).thenReturn(Instant.parse("2026-08-14T09:00:01Z"));
        when(saved.submissionDeadline()).thenReturn(SUBMISSION_DEADLINE);
        when(saved.decisionDeadline()).thenReturn(Instant.parse("2026-08-15T09:00:01Z"));
        when(corrections.save(any(AttendanceCorrectionEntity.class))).thenReturn(saved);
    }

    @Test
    void acceptsJustAfterCheckoutCutoffAndPersistsDeadlinesAndSubmittedEvent() {
        Instant now = Instant.parse("2026-08-14T09:00:01Z");
        CorrectionService service = serviceAt(now);

        CorrectionSubmission submission = service.submit(
                INTERN_ID, command(LocalTime.of(15, 45), "Forgot to check out"));

        assertThat(submission.id()).isEqualTo(SAVED_CORRECTION_ID);
        assertThat(submission.status()).isEqualTo("PENDING");
        assertThat(submission.proposedCheckoutAt()).isEqualTo(Instant.parse("2026-08-14T08:45:00Z"));

        ArgumentCaptor<AttendanceCorrectionEntity> correctionCaptor =
                ArgumentCaptor.forClass(AttendanceCorrectionEntity.class);
        verify(corrections).save(correctionCaptor.capture());
        AttendanceCorrectionEntity persisted = correctionCaptor.getValue();
        assertThat(persisted.requestedCheckoutAt()).isEqualTo(Instant.parse("2026-08-14T08:45:00Z"));
        assertThat(persisted.status()).isEqualTo("PENDING");
        assertThat(persisted.submittedAt()).isEqualTo(now);
        assertThat(persisted.submissionDeadline()).isEqualTo(SUBMISSION_DEADLINE);
        assertThat(persisted.decisionDeadline()).isEqualTo(now.plusSeconds(24 * 60 * 60));

        ArgumentCaptor<AttendanceCorrectionEventEntity> eventCaptor =
                ArgumentCaptor.forClass(AttendanceCorrectionEventEntity.class);
        verify(events).save(eventCaptor.capture());
        AttendanceCorrectionEventEntity event = eventCaptor.getValue();
        assertThat(event.eventType()).isEqualTo("SUBMITTED");
        assertThat(event.fromStatus()).isNull();
        assertThat(event.toStatus()).isEqualTo("PENDING");
        assertThat(event.actorUserId()).isEqualTo(INTERN_ID);
        assertThat(event.correctionId()).isEqualTo(SAVED_CORRECTION_ID);
        assertThat(event.occurredAt()).isEqualTo(now);
    }

    @Test
    void rejectsBeforeAndAtInclusiveCheckoutCutoff() {
        assertRejectedAt(Instant.parse("2026-08-14T09:00:00Z"), CorrectionRejection.TOO_EARLY);
        assertRejectedAt(Instant.parse("2026-08-14T08:59:59.999Z"), CorrectionRejection.TOO_EARLY);
    }

    @Test
    void acceptsAtInclusiveSubmissionDeadlineAndRejectsTheFirstLaterInstant() {
        assertAcceptedAt(Instant.parse("2026-08-15T08:30:00Z"));
        assertRejectedAt(Instant.parse("2026-08-15T08:30:00.001Z"), CorrectionRejection.DEADLINE_PASSED);
    }

    @Test
    void rejectsWhenTheRecordAlreadyHasARawCheckout() {
        when(record.toDomain()).thenReturn(new AttendanceRecord(
                INTERN_ID, WORKDAY, AttendancePolicyFixtures.seeded(1L), CHECK_IN, Instant.parse("2026-08-14T08:00:00Z")));

        assertRejectedAt(Instant.parse("2026-08-14T09:00:01Z"), CorrectionRejection.HAS_RAW_CHECKOUT);
    }

    @Test
    void rejectsWhenNoAttendanceRecordExistsForTheDate() {
        when(records.findByInternUserIdAndWorkDate(INTERN_ID, WORKDAY)).thenReturn(Optional.empty());

        assertRejectedAt(Instant.parse("2026-08-14T09:00:01Z"), CorrectionRejection.NO_ATTENDANCE_RECORD);
    }

    @Test
    void rejectsWhenACorrectionAlreadyExistsForTheRecord() {
        when(corrections.findByAttendanceRecordId(RECORD_ID))
                .thenReturn(Optional.of(mock(AttendanceCorrectionEntity.class)));

        assertRejectedAt(Instant.parse("2026-08-14T09:00:01Z"), CorrectionRejection.ALREADY_SUBMITTED);
        verify(corrections, never()).save(any());
    }

    @Test
    void rejectsProposedCheckoutNotAfterCheckIn() {
        assertRejectedAt(
                Instant.parse("2026-08-14T09:00:01Z"),
                command(LocalTime.of(8, 30), "At check-in time"),
                CorrectionRejection.PROPOSED_BEFORE_CHECKIN);
    }

    @Test
    void rejectsProposedCheckoutInTheFutureAtSubmission() {
        assertRejectedAt(
                Instant.parse("2026-08-14T09:00:01Z"),
                command(LocalTime.of(23, 0), "Future time"),
                CorrectionRejection.PROPOSED_IN_FUTURE);
    }

    @Test
    void rejectsBlankReasonAndMissingCommandFields() {
        assertRejectedAt(
                Instant.parse("2026-08-14T09:00:01Z"),
                command(LocalTime.of(15, 45), "   "),
                CorrectionRejection.INVALID_REQUEST);
        assertThatThrownBy(() -> serviceAt(Instant.parse("2026-08-14T09:00:01Z")).submit(INTERN_ID, null))
                .isInstanceOfSatisfying(CorrectionException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(CorrectionRejection.INVALID_REQUEST));
        assertRejectedAt(
                Instant.parse("2026-08-14T09:00:01Z"),
                new CorrectionSubmissionCommand(null, LocalTime.of(15, 45), "Missing date"),
                CorrectionRejection.INVALID_REQUEST);
        assertRejectedAt(
                Instant.parse("2026-08-14T09:00:01Z"),
                new CorrectionSubmissionCommand(WORKDAY, null, "Missing time"),
                CorrectionRejection.INVALID_REQUEST);
        verify(corrections, never()).save(any());
    }

    @Test
    void rejectsSubmissionFromInactiveIntern() {
        when(accounts.isEligibleIntern(INTERN_ID)).thenReturn(false);

        assertRejectedAt(Instant.parse("2026-08-14T09:00:01Z"), CorrectionRejection.INACTIVE_INTERN);
        verify(records, never()).findByInternUserIdAndWorkDate(anyLong(), any());
    }

    @Test
    void approvedProposedCheckoutBecomesEffectiveBeforeScheduledEnd() {
        AttendanceRecord missing = new AttendanceRecord(
                INTERN_ID, WORKDAY, AttendancePolicyFixtures.seeded(1L), CHECK_IN, null);
        Instant proposed = Instant.parse("2026-08-14T08:00:00Z");

        AttendanceViolations violations = missing.violations(
                Instant.parse("2026-08-14T10:00:00Z"), proposed);

        assertThat(violations.missingCheckout()).isFalse();
        assertThat(violations.earlyDeparture()).isTrue();
        assertThat(violations.late()).isFalse();
    }

    @Test
    void approvedProposedCheckoutAtScheduledEndClearsMissingWithoutEarlyDeparture() {
        AttendanceRecord missing = new AttendanceRecord(
                INTERN_ID, WORKDAY, AttendancePolicyFixtures.seeded(1L), CHECK_IN, null);
        Instant proposed = Instant.parse("2026-08-14T08:30:00Z");

        AttendanceViolations violations = missing.violations(
                Instant.parse("2026-08-14T10:00:00Z"), proposed);

        assertThat(violations.missingCheckout()).isFalse();
        assertThat(violations.earlyDeparture()).isFalse();
    }

    @Test
    void withoutEffectiveCheckoutTheDayRemainsMissingOnly() {
        AttendanceRecord missing = new AttendanceRecord(
                INTERN_ID, WORKDAY, AttendancePolicyFixtures.seeded(1L), CHECK_IN, null);

        AttendanceViolations violations = missing.violations(
                Instant.parse("2026-08-14T10:00:00Z"), null);

        assertThat(violations.missingCheckout()).isTrue();
        assertThat(violations.earlyDeparture()).isFalse();
    }

    private void assertAcceptedAt(Instant now) {
        CorrectionSubmission submission = serviceAt(now).submit(
                INTERN_ID, command(LocalTime.of(15, 45), "Boundary accepted"));
        assertThat(submission.status()).isEqualTo("PENDING");
    }

    private void assertRejectedAt(Instant now, CorrectionRejection rejection) {
        assertRejectedAt(now, command(LocalTime.of(15, 45), "Should fail"), rejection);
    }

    private void assertRejectedAt(
            Instant now, CorrectionSubmissionCommand command, CorrectionRejection rejection) {
        assertThatThrownBy(() -> serviceAt(now).submit(INTERN_ID, command))
                .isInstanceOfSatisfying(CorrectionException.class,
                        exception -> assertThat(exception.rejection()).isEqualTo(rejection));
    }

    private static CorrectionSubmissionCommand command(LocalTime proposedCheckoutTime, String reason) {
        return new CorrectionSubmissionCommand(WORKDAY, proposedCheckoutTime, reason);
    }

    private CorrectionService serviceAt(Instant now) {
return new CorrectionService(
                  Clock.fixed(now, ZoneOffset.UTC),
                  accounts,
                  records,
                  corrections,
                  events,
                  windowGuard,
                  deadlines);
    }
}