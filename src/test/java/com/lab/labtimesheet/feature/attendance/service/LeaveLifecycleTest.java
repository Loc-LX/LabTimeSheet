package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.attendance.exception.LeaveException;
import com.lab.labtimesheet.feature.attendance.exception.LeaveRejection;
import com.lab.labtimesheet.feature.attendance.model.AttendancePolicyFixtures;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveDecisionCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveSubmission;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveSubmissionCommand;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendancePolicyEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveEntityFixtures;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendancePolicyRepository;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestDayRepository;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LeaveLifecycleTest {

    private static final long INTERN_ID = 42L;
    private static final long OTHER_INTERN_ID = 43L;
    private static final long MENTOR_ID = 7L;
    private static final long REQUEST_ID = 99L;
    private static final long OTHER_REQUEST_ID = 88L;
    private static final LocalDate INTERNSHIP_START = LocalDate.of(2026, 8, 1);
    private static final LocalDate INTERNSHIP_END = LocalDate.of(2026, 12, 31);
    private static final Instant NOW = Instant.parse("2026-08-14T02:00:00Z");
    private static final Instant FIRST_START = Instant.parse("2026-09-01T01:30:00Z");

    private final AttendancePolicyRepository policies = mock(AttendancePolicyRepository.class);
    private final AccountService accounts = mock(AccountService.class);
    private final CalendarApplicationService calendar = mock(CalendarApplicationService.class);
    private final LeaveRequestRepository requests = mock(LeaveRequestRepository.class);
    private final LeaveRequestDayRepository days = mock(LeaveRequestDayRepository.class);

    @BeforeEach
    void setUp() {
        AttendancePolicyEntity policyEntity = mock(AttendancePolicyEntity.class);
        when(policyEntity.toDomain()).thenReturn(AttendancePolicyFixtures.seeded(1L));
        when(policies.findAllByOrderByEffectiveFromAsc()).thenReturn(List.of(policyEntity));
        when(policies.getReferenceById(1L)).thenReturn(mock(AttendancePolicyEntity.class));
        when(accounts.isEligibleIntern(INTERN_ID)).thenReturn(true);
        when(accounts.requireActiveMentorId(MENTOR_ID)).thenReturn(MENTOR_ID);
        when(accounts.lockActiveInternship(INTERN_ID))
                .thenReturn(new AccountService.InternshipWindow(INTERNSHIP_START, INTERNSHIP_END));
        when(calendar.isGlobalDayOff(any())).thenReturn(false);
        when(days.countReservedByMonth(INTERN_ID)).thenReturn(List.of());
        when(days.countReservedByMonthExcluding(INTERN_ID, REQUEST_ID)).thenReturn(List.of());
        when(days.findLeaveDatesByRequestId(REQUEST_ID)).thenReturn(List.of());
        when(days.findDaysByRequestId(REQUEST_ID)).thenReturn(List.of());
        when(requests.findOverlapping(anyLong(), any(), any())).thenReturn(List.of());
        LeaveRequestEntity pending = pendingRequest();
        when(requests.findById(REQUEST_ID)).thenReturn(Optional.of(pending));
    }

    private LeaveService leave(Instant now) {
        return new LeaveService(
                Clock.fixed(now, ZoneOffset.UTC),
                accounts,
                policies,
                calendar,
                requests,
                days);
    }

    private LeaveRequestEntity pendingRequest() {
        LeaveRequestEntity pending = LeaveRequestEntity.pending(
                INTERN_ID,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 3),
                "Family trip",
                NOW,
                FIRST_START);
        LeaveRequestEntity spied = spy(pending);
        doReturn(REQUEST_ID).when(spied).id();
        return spied;
    }

    @Test
    void submitIsAcceptedJustBeforeFirstCountedBoundaryAndRejectedExactlyAtIt() {
        LeaveRequestEntity saved = mock(LeaveRequestEntity.class);
        when(saved.id()).thenReturn(REQUEST_ID);
        when(requests.save(any(LeaveRequestEntity.class))).thenReturn(saved);

        LeaveSubmission accepted = leave(Instant.parse("2026-08-31T01:29:59Z"))
                .submit(INTERN_ID, new LeaveSubmissionCommand(
                        LocalDate.of(2026, 8, 31),
                        LocalDate.of(2026, 8, 31),
                        "Same day before 08:30"));
        assertThat(accepted.requestId()).isEqualTo(REQUEST_ID);
        assertThat(accepted.status()).isEqualTo("PENDING");

        assertThatThrownBy(() -> leave(Instant.parse("2026-08-31T01:30:00Z"))
                .submit(INTERN_ID, new LeaveSubmissionCommand(
                        LocalDate.of(2026, 8, 31),
                        LocalDate.of(2026, 8, 31),
                        "Same day at 08:30")))
                .isInstanceOfSatisfying(LeaveException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(LeaveRejection.BOUNDARY_PASSED));
    }

    @Test
    void submitRejectsWhenInclusiveRangeOverlapsPendingOrApprovedRequest() {
        LeaveRequestEntity existing = pendingRequest();
        when(requests.findOverlapping(INTERN_ID, LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 4)))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> leave(NOW)
                .submit(INTERN_ID, new LeaveSubmissionCommand(
                        LocalDate.of(2026, 9, 2),
                        LocalDate.of(2026, 9, 4),
                        "Overlaps existing")))
                .isInstanceOfSatisfying(LeaveException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(LeaveRejection.OVERLAPS_PENDING));
        verify(requests, never()).save(any());
    }

    @Test
    void approveMarksPendingRequestApprovedWithMentorAndNote() {
        LeaveSubmission submission = leave(NOW).decide(
                MENTOR_ID, REQUEST_ID, new LeaveDecisionCommand(true, "Looks fine"));

        assertThat(submission.status()).isEqualTo("APPROVED");
        ArgumentCaptor<LeaveRequestEntity> captor = ArgumentCaptor.forClass(LeaveRequestEntity.class);
        verify(requests).saveAndFlush(captor.capture());
        LeaveRequestEntity persisted = captor.getValue();
        assertThat(persisted.status()).isEqualTo("APPROVED");
        assertThat(persisted.decidedByMentorUserId()).isEqualTo(MENTOR_ID);
        assertThat(persisted.decidedAt()).isEqualTo(NOW);
        assertThat(persisted.decisionNote()).isEqualTo("Looks fine");
    }

    @Test
    void rejectMarksPendingRequestRejectedAndReleasesReservation() {
        LeaveSubmission submission = leave(NOW).decide(
                MENTOR_ID, REQUEST_ID, new LeaveDecisionCommand(false, "Unavailable"));

        assertThat(submission.status()).isEqualTo("REJECTED");
        ArgumentCaptor<LeaveRequestEntity> captor = ArgumentCaptor.forClass(LeaveRequestEntity.class);
        verify(requests).saveAndFlush(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("REJECTED");
        assertThat(captor.getValue().decisionNote()).isEqualTo("Unavailable");
        assertThat(captor.getValue().decidedByMentorUserId()).isEqualTo(MENTOR_ID);
    }

    @Test
    void decideRejectsWhenBoundaryAlreadyPassed() {
        assertThatThrownBy(() -> leave(Instant.parse("2026-09-01T01:30:00Z"))
                .decide(MENTOR_ID, REQUEST_ID, new LeaveDecisionCommand(true, null)))
                .isInstanceOfSatisfying(LeaveException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(LeaveRejection.BOUNDARY_PASSED));
        verify(requests, never()).saveAndFlush(any());
    }

    @Test
    void decideRejectsWhenRequestIsNotPending() {
        LeaveRequestEntity approved = LeaveEntityFixtures.approvedRequest(
                INTERN_ID,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 3),
                NOW,
                FIRST_START,
                MENTOR_ID,
                NOW);
        when(requests.findById(REQUEST_ID)).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> leave(NOW)
                .decide(MENTOR_ID, REQUEST_ID, new LeaveDecisionCommand(true, null)))
                .isInstanceOfSatisfying(LeaveException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(LeaveRejection.INVALID_STATE));
        verify(requests, never()).saveAndFlush(any());
    }

    @Test
    void decideRejectsWhenRequestDoesNotExist() {
        when(requests.findById(REQUEST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leave(NOW)
                .decide(MENTOR_ID, REQUEST_ID, new LeaveDecisionCommand(true, null)))
                .isInstanceOfSatisfying(LeaveException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(LeaveRejection.NOT_FOUND));
    }

    @Test
    void decideRejectsInactiveMentor() {
        when(accounts.requireActiveMentorId(MENTOR_ID))
                .thenThrow(new IllegalArgumentException("An active Mentor is required"));

        assertThatThrownBy(() -> leave(NOW)
                .decide(MENTOR_ID, REQUEST_ID, new LeaveDecisionCommand(true, null)))
                .isInstanceOfSatisfying(LeaveException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(LeaveRejection.INACTIVE_MENTOR));
        verify(requests, never()).saveAndFlush(any());
    }

    @Test
    void cancelMarksPendingRequestCancelledBeforeBoundary() {
        LeaveSubmission submission = leave(NOW).cancel(INTERN_ID, REQUEST_ID);

        assertThat(submission.status()).isEqualTo("CANCELLED");
        ArgumentCaptor<LeaveRequestEntity> captor = ArgumentCaptor.forClass(LeaveRequestEntity.class);
        verify(requests).saveAndFlush(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("CANCELLED");
        assertThat(captor.getValue().cancelledAt()).isEqualTo(NOW);
    }

    @Test
    void cancelRejectsWhenBoundaryAlreadyPassed() {
        assertThatThrownBy(() -> leave(Instant.parse("2026-09-01T01:30:00Z")).cancel(INTERN_ID, REQUEST_ID))
                .isInstanceOfSatisfying(LeaveException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(LeaveRejection.BOUNDARY_PASSED));
        verify(requests, never()).saveAndFlush(any());
    }

    @Test
    void cancelRejectsWhenRequestBelongsToAnotherIntern() {
        LeaveRequestEntity otherInternRequest = pendingRequestFor(OTHER_INTERN_ID);
        when(requests.findById(REQUEST_ID)).thenReturn(Optional.of(otherInternRequest));

        assertThatThrownBy(() -> leave(NOW).cancel(INTERN_ID, REQUEST_ID))
                .isInstanceOfSatisfying(LeaveException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(LeaveRejection.NOT_OWNER));
        verify(requests, never()).saveAndFlush(any());
    }

    @Test
    void cancelRejectsWhenRequestAlreadyResolved() {
        LeaveRequestEntity rejected = pendingRequest();
        rejected.reject(MENTOR_ID, NOW, "Unavailable");
        when(requests.findById(REQUEST_ID)).thenReturn(Optional.of(rejected));

        assertThatThrownBy(() -> leave(NOW).cancel(INTERN_ID, REQUEST_ID))
                .isInstanceOfSatisfying(LeaveException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(LeaveRejection.INVALID_STATE));
        verify(requests, never()).saveAndFlush(any());
    }

    @Test
    void editReplacesRangeReasonAndBoundaryAndRevalidatesQuota() {
        LeaveSubmission submission = leave(NOW).edit(
                INTERN_ID,
                REQUEST_ID,
                new LeaveSubmissionCommand(
                        LocalDate.of(2026, 9, 4),
                        LocalDate.of(2026, 9, 4),
                        "Rescheduled"));

        assertThat(submission.status()).isEqualTo("PENDING");
        verify(days).deleteAllByRequestId(REQUEST_ID);
        verify(days, times(1)).save(any());
        ArgumentCaptor<LeaveRequestEntity> captor = ArgumentCaptor.forClass(LeaveRequestEntity.class);
        verify(requests).saveAndFlush(captor.capture());
        LeaveRequestEntity persisted = captor.getValue();
        assertThat(persisted.startDate()).isEqualTo(LocalDate.of(2026, 9, 4));
        assertThat(persisted.endDate()).isEqualTo(LocalDate.of(2026, 9, 4));
        assertThat(persisted.reason()).isEqualTo("Rescheduled");
        assertThat(persisted.submittedAt()).isEqualTo(NOW);
        assertThat(persisted.firstCountedStartAt()).isEqualTo(Instant.parse("2026-09-04T01:30:00Z"));
    }

    @Test
    void editRejectsWhenBoundaryAlreadyPassed() {
        assertThatThrownBy(() -> leave(Instant.parse("2026-09-01T01:30:00Z"))
                .edit(INTERN_ID, REQUEST_ID, new LeaveSubmissionCommand(
                        LocalDate.of(2026, 9, 4),
                        LocalDate.of(2026, 9, 4),
                        "Too late")))
                .isInstanceOfSatisfying(LeaveException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(LeaveRejection.BOUNDARY_PASSED));
        verify(days, never()).deleteAllByRequestId(anyLong());
    }

    @Test
    void editRejectsWhenNewRangeOverlapsAnotherRequestBeforeMutating() {
        LeaveRequestEntity other = spy(LeaveEntityFixtures.approvedRequest(
                INTERN_ID,
                LocalDate.of(2026, 9, 10),
                LocalDate.of(2026, 9, 12),
                NOW,
                Instant.parse("2026-09-10T01:30:00Z"),
                MENTOR_ID,
                NOW));
        doReturn(OTHER_REQUEST_ID).when(other).id();
        when(requests.findOverlapping(INTERN_ID, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 14)))
                .thenReturn(List.of(other));

        assertThatThrownBy(() -> leave(NOW)
                .edit(INTERN_ID, REQUEST_ID, new LeaveSubmissionCommand(
                        LocalDate.of(2026, 9, 10),
                        LocalDate.of(2026, 9, 14),
                        "Now overlaps")))
                .isInstanceOfSatisfying(LeaveException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(LeaveRejection.OVERLAPS_PENDING));
        verify(days, never()).deleteAllByRequestId(anyLong());
        verify(requests, never()).saveAndFlush(any());
    }

    @Test
    void editRejectsWhenRequestBelongsToAnotherIntern() {
        LeaveRequestEntity otherInternRequest = pendingRequestFor(OTHER_INTERN_ID);
        when(requests.findById(REQUEST_ID)).thenReturn(Optional.of(otherInternRequest));

        assertThatThrownBy(() -> leave(NOW)
                .edit(INTERN_ID, REQUEST_ID, new LeaveSubmissionCommand(
                        LocalDate.of(2026, 9, 4),
                        LocalDate.of(2026, 9, 4),
                        "Not mine")))
                .isInstanceOfSatisfying(LeaveException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(LeaveRejection.NOT_OWNER));
    }

    private LeaveRequestEntity pendingRequestFor(long internUserId) {
        LeaveRequestEntity pending = LeaveRequestEntity.pending(
                internUserId,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 3),
                "Family trip",
                NOW,
                FIRST_START);
        LeaveRequestEntity spied = spy(pending);
        doReturn(REQUEST_ID).when(spied).id();
        return spied;
    }
}