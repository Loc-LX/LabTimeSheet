package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.attendance.exception.CorrectionException;
import com.lab.labtimesheet.feature.attendance.exception.CorrectionRejection;
import com.lab.labtimesheet.feature.attendance.model.AttendancePolicyFixtures;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRecord;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionDecisionCommand;
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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class CorrectionDecisionServiceTest {

    private static final long MENTOR_ID = 101L;
    private static final long INTERN_ID = 42L;
    private static final long RECORD_ID = 77L;
    private static final long CORRECTION_ID = 88L;
    private static final LocalDate WORKDAY = LocalDate.of(2026, 8, 14);
    private static final Instant CHECK_IN = Instant.parse("2026-08-14T01:30:00Z");
    private static final Instant PROPOSED = Instant.parse("2026-08-14T08:45:00Z");
    private static final Instant DECISION_DEADLINE = Instant.parse("2026-08-15T09:00:01Z");
    private static final Instant INSIDE_WINDOW = Instant.parse("2026-08-15T08:00:00Z");

    private final AccountService accounts = mock(AccountService.class);
    private final AttendanceRecordRepository records = mock(AttendanceRecordRepository.class);
    private final AttendanceCorrectionRepository corrections = mock(AttendanceCorrectionRepository.class);
    private final AttendanceCorrectionEventRepository events = mock(AttendanceCorrectionEventRepository.class);
    private final CorrectionWindowGuard windowGuard = mock(CorrectionWindowGuard.class);

    private AttendanceRecordEntity record;
    private AttendanceCorrectionEntity correction;

    @BeforeEach
    void setUp() {
        when(accounts.requireActiveMentorId(MENTOR_ID)).thenReturn(MENTOR_ID);
        when(accounts.requireIdentityById(INTERN_ID)).thenReturn(new AccountIdentity(
                INTERN_ID, "intern@example.test", "Intern", GlobalRole.INTERN, AccountStatus.ACTIVE));
        record = mock(AttendanceRecordEntity.class);
        when(record.id()).thenReturn(RECORD_ID);
        when(record.toDomain()).thenReturn(new AttendanceRecord(
                INTERN_ID, WORKDAY, AttendancePolicyFixtures.seeded(1L), CHECK_IN, null));
        correction = new AttendanceCorrectionEntity(
                record,
                PROPOSED,
                "Forgot to check out",
                Instant.parse("2026-08-14T09:00:01Z"),
                Instant.parse("2026-08-15T08:30:00Z"),
                DECISION_DEADLINE);
        when(corrections.findById(CORRECTION_ID)).thenReturn(Optional.of(correction));
    }

    @Test
    void approvesPendingCorrectionInsideWindowPersistingStateAndApprovedEvent() {
        CorrectionDecisionCommand command = new CorrectionDecisionCommand(true, "Verified in person");

        MentorCorrectionDecision decision =
                serviceAt(INSIDE_WINDOW).decide(MENTOR_ID, CORRECTION_ID, command);

        assertThat(decision.status()).isEqualTo("APPROVED");
        assertThat(decision.decidedByMentorUserId()).isEqualTo(MENTOR_ID);
        assertThat(decision.decidedAt()).isEqualTo(INSIDE_WINDOW);
        assertThat(decision.decisionNote()).isEqualTo("Verified in person");
        assertThat(decision.internDisplayName()).isEqualTo("Intern");
        verify(windowGuard).expire(CORRECTION_ID);
        verify(corrections).saveAndFlush(correction);
        assertEvent("APPROVED", "PENDING", "APPROVED", MENTOR_ID, "Verified in person");
    }

    @Test
    void rejectsPendingCorrectionInsideWindowPersistingRejectedEvent() {
        CorrectionDecisionCommand command = new CorrectionDecisionCommand(false, "No supporting evidence");

        MentorCorrectionDecision decision =
                serviceAt(INSIDE_WINDOW).decide(MENTOR_ID, CORRECTION_ID, command);

        assertThat(decision.status()).isEqualTo("REJECTED");
        verify(corrections).saveAndFlush(correction);
        assertEvent("REJECTED", "PENDING", "REJECTED", MENTOR_ID, "No supporting evidence");
    }

    @Test
    void revertsDecidedCorrectionInsideWindowBackToPending() {
        correction.approve(MENTOR_ID, Instant.parse("2026-08-14T12:00:00Z"), "Initially approved");

        MentorCorrectionDecision decision =
                serviceAt(INSIDE_WINDOW).revert(MENTOR_ID, CORRECTION_ID, "Reopened after review");

        assertThat(decision.status()).isEqualTo("PENDING");
        assertThat(decision.decidedByMentorUserId()).isNull();
        assertThat(decision.decidedAt()).isNull();
        assertThat(decision.decisionNote()).isNull();
        verify(windowGuard).expire(CORRECTION_ID);
        verify(corrections).saveAndFlush(correction);
        assertEvent("REOPENED", "APPROVED", "PENDING", MENTOR_ID, "Reopened after review");
    }

    @Test
    void rejectsDecisionWhenCorrectionMissing() {
        when(corrections.findById(CORRECTION_ID)).thenReturn(Optional.empty());

        assertRejected(() -> serviceAt(INSIDE_WINDOW).decide(
                MENTOR_ID, CORRECTION_ID, new CorrectionDecisionCommand(true, "Note")),
                CorrectionRejection.NOT_FOUND);
        verify(windowGuard).expire(CORRECTION_ID);
        verify(events, never()).save(any());
        verify(corrections, never()).saveAndFlush(any());
    }

    @Test
    void rejectsDecisionWhenMentorInactive() {
        when(accounts.requireActiveMentorId(MENTOR_ID))
                .thenThrow(new IllegalArgumentException("An active Mentor is required"));

        assertRejected(() -> serviceAt(INSIDE_WINDOW).decide(
                MENTOR_ID, CORRECTION_ID, new CorrectionDecisionCommand(true, "Note")),
                CorrectionRejection.INACTIVE_MENTOR);
        verify(windowGuard, never()).expire(CORRECTION_ID);
        verify(events, never()).save(any());
        verify(corrections, never()).saveAndFlush(any());
    }

    @Test
    void rejectsDecisionWhenLocked() {
        correction.lock(Instant.parse("2026-08-15T09:00:02Z"));

        assertRejected(() -> serviceAt(INSIDE_WINDOW).decide(
                MENTOR_ID, CORRECTION_ID, new CorrectionDecisionCommand(true, "Note")),
                CorrectionRejection.LOCKED);
        verify(events, never()).save(any());
        verify(corrections, never()).saveAndFlush(any());
    }

    @Test
    void rejectsDecisionAfterDecisionWindowPassed() {
        assertRejected(() -> serviceAt(Instant.parse("2026-08-15T09:00:01.001Z")).decide(
                MENTOR_ID, CORRECTION_ID, new CorrectionDecisionCommand(true, "Note")),
                CorrectionRejection.DECISION_WINDOW_PASSED);
        verify(events, never()).save(any());
        verify(corrections, never()).saveAndFlush(any());
    }

    @Test
    void rejectsDecidingAnAlreadyDecidedCorrection() {
        correction.approve(MENTOR_ID, Instant.parse("2026-08-14T12:00:00Z"), "Already approved");

        assertRejected(() -> serviceAt(INSIDE_WINDOW).decide(
                MENTOR_ID, CORRECTION_ID, new CorrectionDecisionCommand(true, "Again")),
                CorrectionRejection.INVALID_STATE);
        verify(corrections, never()).saveAndFlush(any());
    }

    @Test
    void rejectsRevertingAPendingCorrection() {
        assertRejected(() -> serviceAt(INSIDE_WINDOW).revert(MENTOR_ID, CORRECTION_ID, "No decision yet"),
                CorrectionRejection.INVALID_STATE);
        verify(corrections, never()).saveAndFlush(any());
    }

    @Test
    void rejectsNullDecisionCommand() {
        assertRejected(() -> serviceAt(INSIDE_WINDOW).decide(MENTOR_ID, CORRECTION_ID, null),
                CorrectionRejection.INVALID_REQUEST);
        verify(windowGuard, never()).expire(CORRECTION_ID);
        verify(events, never()).save(any());
        verify(corrections, never()).saveAndFlush(any());
    }

    @Test
    void concurrentDecisionIsReportedAsStableConflict() {
        when(corrections.saveAndFlush(any(AttendanceCorrectionEntity.class))).thenThrow(
                new ObjectOptimisticLockingFailureException(AttendanceCorrectionEntity.class, CORRECTION_ID));

        assertRejected(() -> serviceAt(INSIDE_WINDOW).decide(
                MENTOR_ID, CORRECTION_ID, new CorrectionDecisionCommand(true, "Note")),
                CorrectionRejection.CONCURRENT_DECISION);
    }

    @Test
    void decisionsListAllCorrectionsNewestFirstWithDerivedFlags() {
        AttendanceCorrectionEntity pending = mock(AttendanceCorrectionEntity.class);
        when(pending.id()).thenReturn(101L);
        when(pending.attendanceRecord()).thenReturn(record);
        when(pending.requestedCheckoutAt()).thenReturn(Instant.parse("2026-08-14T08:45:00Z"));
        when(pending.status()).thenReturn("PENDING");
        when(pending.decisionDeadline()).thenReturn(DECISION_DEADLINE);
        when(pending.lockedAt()).thenReturn(null);

        AttendanceCorrectionEntity approved = mock(AttendanceCorrectionEntity.class);
        when(approved.id()).thenReturn(100L);
        when(approved.attendanceRecord()).thenReturn(record);
        when(approved.requestedCheckoutAt()).thenReturn(Instant.parse("2026-08-14T08:00:00Z"));
        when(approved.status()).thenReturn("APPROVED");
        when(approved.decisionDeadline()).thenReturn(DECISION_DEADLINE);
        when(approved.lockedAt()).thenReturn(null);

        AttendanceCorrectionEntity locked = mock(AttendanceCorrectionEntity.class);
        when(locked.id()).thenReturn(99L);
        when(locked.attendanceRecord()).thenReturn(record);
        when(locked.requestedCheckoutAt()).thenReturn(Instant.parse("2026-08-14T08:45:00Z"));
        when(locked.status()).thenReturn("REJECTED");
        when(locked.decisionDeadline()).thenReturn(DECISION_DEADLINE);
        when(locked.lockedAt()).thenReturn(Instant.parse("2026-08-15T09:00:02Z"));

        when(corrections.findAllByOrderByIdDesc()).thenReturn(List.of(pending, approved, locked));

        List<MentorCorrectionDecision> decisions = serviceAt(INSIDE_WINDOW).decisions();

        assertThat(decisions).hasSize(3);
        assertThat(decisions).extracting(MentorCorrectionDecision::id).containsExactly(101L, 100L, 99L);

        MentorCorrectionDecision first = decisions.getFirst();
        assertThat(first.internUserId()).isEqualTo(INTERN_ID);
        assertThat(first.internDisplayName()).isEqualTo("Intern");
        assertThat(first.workDate()).isEqualTo(WORKDAY);
        assertThat(first.proposedCheckoutAt()).isEqualTo(Instant.parse("2026-08-14T08:45:00Z"));
        assertThat(first.zoneId()).isEqualTo(ZoneId.of("Asia/Ho_Chi_Minh"));
        assertThat(first.status()).isEqualTo("PENDING");
        assertThat(first.decisionDeadline()).isEqualTo(DECISION_DEADLINE);
        assertThat(first.lockedAt()).isNull();
        assertThat(first.rawCheckoutPresent()).isFalse();
        assertThat(first.approvedCompliant()).isTrue();
        assertThat(first.revertable()).isFalse();

        MentorCorrectionDecision middle = decisions.get(1);
        assertThat(middle.status()).isEqualTo("APPROVED");
        assertThat(middle.approvedCompliant()).isFalse();
        assertThat(middle.revertable()).isTrue();

        MentorCorrectionDecision last = decisions.get(2);
        assertThat(last.status()).isEqualTo("REJECTED");
        assertThat(last.lockedAt()).isNotNull();
        assertThat(last.approvedCompliant()).isTrue();
        assertThat(last.revertable()).isFalse();
    }

    private void assertEvent(
            String eventType, String fromStatus, String toStatus, Long actorUserId, String note) {
        ArgumentCaptor<AttendanceCorrectionEventEntity> eventCaptor =
                ArgumentCaptor.forClass(AttendanceCorrectionEventEntity.class);
        verify(events).save(eventCaptor.capture());
        AttendanceCorrectionEventEntity event = eventCaptor.getValue();
        assertThat(event.eventType()).isEqualTo(eventType);
        assertThat(event.fromStatus()).isEqualTo(fromStatus);
        assertThat(event.toStatus()).isEqualTo(toStatus);
        assertThat(event.actorUserId()).isEqualTo(actorUserId);
        assertThat(event.correctionId()).isEqualTo(CORRECTION_ID);
        assertThat(event.note()).isEqualTo(note);
        assertThat(event.occurredAt()).isEqualTo(INSIDE_WINDOW);
    }

    private void assertRejected(Runnable operation, CorrectionRejection rejection) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(CorrectionException.class,
                        exception -> assertThat(exception.rejection()).isEqualTo(rejection));
    }

    private CorrectionService serviceAt(Instant now) {
        return new CorrectionService(
                Clock.fixed(now, ZoneOffset.UTC),
                accounts,
                records,
                corrections,
                events,
                windowGuard);
    }
}