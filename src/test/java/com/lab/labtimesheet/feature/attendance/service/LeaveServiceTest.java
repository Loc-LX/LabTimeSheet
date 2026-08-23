package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.attendance.exception.LeaveException;
import com.lab.labtimesheet.feature.attendance.exception.LeaveRejection;
import com.lab.labtimesheet.feature.attendance.model.AttendancePolicy;
import com.lab.labtimesheet.feature.attendance.model.AttendancePolicyFixtures;
import com.lab.labtimesheet.feature.attendance.model.dto.CountedLeaveDay;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveOverview;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveSubmission;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveSubmissionCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.MonthReservation;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendancePolicyEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendancePolicyRepository;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestDayRepository;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LeaveServiceTest {

    private static final long INTERN_ID = 42L;
    private static final long SAVED_REQUEST_ID = 99L;
    private static final LocalDate INTERNSHIP_START = LocalDate.of(2026, 8, 1);
    private static final LocalDate INTERNSHIP_END = LocalDate.of(2026, 12, 31);
    private static final Instant NOW = Instant.parse("2026-08-14T02:00:00Z");

    private final AttendancePolicyRepository policies = mock(AttendancePolicyRepository.class);
    private final AccountService accounts = mock(AccountService.class);
    private final CalendarApplicationService calendar = mock(CalendarApplicationService.class);
    private final LeaveRequestRepository requests = mock(LeaveRequestRepository.class);
    private final LeaveRequestDayRepository days = mock(LeaveRequestDayRepository.class);
    private final AttendanceDeadlineService deadlines = mock(AttendanceDeadlineService.class);
    private LeaveService leave;

    @BeforeEach
    void setUp() {
        AttendancePolicyEntity policyEntity = mock(AttendancePolicyEntity.class);
        when(policyEntity.toDomain()).thenReturn(AttendancePolicyFixtures.seeded(1L));
        when(policies.findAllByOrderByEffectiveFromAsc()).thenReturn(List.of(policyEntity));
        when(policies.getReferenceById(1L)).thenReturn(mock(AttendancePolicyEntity.class));
        when(accounts.isEligibleIntern(INTERN_ID)).thenReturn(true);
        when(accounts.lockActiveInternship(INTERN_ID))
                .thenReturn(new AccountService.InternshipWindow(INTERNSHIP_START, INTERNSHIP_END));

        when(calendar.isGlobalDayOff(any())).thenReturn(false);
        when(days.countReservedByMonth(INTERN_ID)).thenReturn(List.of());

        LeaveRequestEntity saved = mock(LeaveRequestEntity.class);
        when(saved.id()).thenReturn(SAVED_REQUEST_ID);
        when(requests.save(any(LeaveRequestEntity.class))).thenReturn(saved);

        leave = new LeaveService(
                Clock.fixed(NOW, ZoneOffset.UTC),
                accounts,
                policies,
                calendar,
                requests,
                days,
                deadlines);
    }

    @Test
    void materializesOnlyEligibleWorkdaysAcrossMonthsWithPerDateSnapshot() {
        when(calendar.isGlobalDayOff(LocalDate.of(2026, 9, 2))).thenReturn(true);

        LeaveSubmission submission = leave.submit(
                INTERN_ID, new LeaveSubmissionCommand(
                        LocalDate.of(2026, 8, 31),
                        LocalDate.of(2026, 9, 4),
                        "Family trip"));

        assertThat(submission.requestId()).isEqualTo(SAVED_REQUEST_ID);
        assertThat(submission.status()).isEqualTo("PENDING");
        assertThat(submission.countedDays())
                .extracting(CountedLeaveDay::date, CountedLeaveDay::quotaMonth, CountedLeaveDay::monthlyQuotaSnapshot)
                .containsExactly(
                        tuple(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 1), 3),
                        tuple(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), 3),
                        tuple(LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 1), 3),
                        tuple(LocalDate.of(2026, 9, 4), LocalDate.of(2026, 9, 1), 3));

        ArgumentCaptor<LeaveRequestEntity> requestCaptor = ArgumentCaptor.forClass(LeaveRequestEntity.class);
        verify(requests).save(requestCaptor.capture());
        LeaveRequestEntity persisted = requestCaptor.getValue();
        assertThat(persisted.startDate()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(persisted.endDate()).isEqualTo(LocalDate.of(2026, 9, 4));
        assertThat(persisted.reason()).isEqualTo("Family trip");
        assertThat(persisted.status()).isEqualTo("PENDING");
        assertThat(persisted.submittedAt()).isEqualTo(NOW);
        assertThat(persisted.firstCountedStartAt()).isEqualTo(Instant.parse("2026-08-31T01:30:00Z"));

        verify(days, times(4)).save(any());
    }

    @Test
    void rejectsRangeWithNoEligibleWorkday() {
        assertThatThrownBy(() -> leave.submit(
                INTERN_ID, new LeaveSubmissionCommand(
                        LocalDate.of(2026, 8, 15),
                        LocalDate.of(2026, 8, 16),
                        "Weekend only")))
                .isInstanceOfSatisfying(LeaveException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(LeaveRejection.NO_COUNTED_DAYS));
        verify(requests, never()).save(any());
    }

    @Test
    void rejectsInvertedRangeAndBlankReason() {
        assertRejected(new LeaveSubmissionCommand(
                LocalDate.of(2026, 9, 4), LocalDate.of(2026, 9, 1), "Inverted"), LeaveRejection.INVALID_REQUEST);
        assertRejected(new LeaveSubmissionCommand(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4), "   "), LeaveRejection.INVALID_REQUEST);
        assertRejected(new LeaveSubmissionCommand(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4), null), LeaveRejection.INVALID_REQUEST);
    }

    @Test
    void rejectsWhenReservedPlusCandidateExceedsMonthlySnapshot() {
        when(days.countReservedByMonth(INTERN_ID)).thenReturn(List.of(
                new MonthReservation(LocalDate.of(2026, 8, 1), 2L)));

        assertThatThrownBy(() -> leave.submit(
                INTERN_ID, new LeaveSubmissionCommand(
                        LocalDate.of(2026, 8, 17),
                        LocalDate.of(2026, 8, 18),
                        "Already near the limit")))
                .isInstanceOfSatisfying(LeaveException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(LeaveRejection.QUOTA_EXCEEDED));
        verify(requests, never()).save(any());
    }

    @Test
    void acceptsSeparateMonthQuotaWhenEachAffectedMonthFits() {
        when(days.countReservedByMonth(INTERN_ID)).thenReturn(List.of(
                new MonthReservation(LocalDate.of(2026, 8, 1), 2L)));

        LeaveSubmission submission = leave.submit(
                INTERN_ID, new LeaveSubmissionCommand(
                        LocalDate.of(2026, 8, 31),
                        LocalDate.of(2026, 9, 1),
                        "Spans the month boundary"));

        assertThat(submission.countedDays())
                .extracting(CountedLeaveDay::date, CountedLeaveDay::quotaMonth)
                .containsExactly(
                        tuple(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 1)),
                        tuple(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1)));
    }

    @Test
    void serializesReservationOnTheInternProfileRow() {
        leave.submit(
                INTERN_ID, new LeaveSubmissionCommand(
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 2),
                        "Concurrency guard"));

        verify(accounts).lockActiveInternship(INTERN_ID);
    }

    @Test
    void rejectsSubmissionFromInactiveInternBeforeLocking() {
        when(accounts.isEligibleIntern(INTERN_ID)).thenReturn(false);

        assertThatThrownBy(() -> leave.submit(
                INTERN_ID, new LeaveSubmissionCommand(
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 2),
                        "Inactive")))
                .isInstanceOfSatisfying(LeaveException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(LeaveRejection.INACTIVE_INTERN));
        verify(accounts, never()).lockActiveInternship(anyLong());
        verify(requests, never()).save(any());
    }

    @Test
    void overviewReportsQuotaReservedAvailableAndPriorCountedDays() {
        when(days.countReservedByMonth(INTERN_ID)).thenReturn(List.of(
                new MonthReservation(LocalDate.of(2026, 8, 1), 1L)));

        LeaveRequestEntity prior = mock(LeaveRequestEntity.class);
        when(prior.id()).thenReturn(5L);
        when(prior.startDate()).thenReturn(LocalDate.of(2026, 8, 10));
        when(prior.endDate()).thenReturn(LocalDate.of(2026, 8, 10));
        when(prior.status()).thenReturn("PENDING");
        when(prior.reason()).thenReturn("Clinic visit");
        when(prior.submittedAt()).thenReturn(NOW);
        when(prior.firstCountedStartAt()).thenReturn(Instant.parse("2026-08-10T01:30:00Z"));
        when(requests.findByInternUserIdOrderByIdDesc(INTERN_ID)).thenReturn(List.of(prior));
        when(days.findLeaveDatesByRequestId(5L)).thenReturn(List.of(LocalDate.of(2026, 8, 10)));

        LeaveOverview overview = leave.overview(INTERN_ID, LocalDate.of(2026, 8, 1));

        assertThat(overview.month()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(overview.quota()).isEqualTo(3);
        assertThat(overview.reserved()).isEqualTo(1);
        assertThat(overview.available()).isEqualTo(2);
        assertThat(overview.requests()).hasSize(1);
        assertThat(overview.requests().getFirst().id()).isEqualTo(5L);
        assertThat(overview.requests().getFirst().status()).isEqualTo("PENDING");
        assertThat(overview.requests().getFirst().countedDays()).containsExactly(LocalDate.of(2026, 8, 10));
    }

    private void assertRejected(LeaveSubmissionCommand command, LeaveRejection rejection) {
        assertThatThrownBy(() -> leave.submit(INTERN_ID, command))
                .isInstanceOfSatisfying(LeaveException.class,
                        exception -> assertThat(exception.rejection()).isEqualTo(rejection));
    }
}
