package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendancePolicy;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.CorrectionStatus;
import com.lab.labtimesheet.feature.attendance.model.LeaveStatus;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionSummary;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveBalance;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveRequestSummary;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendancePolicyEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceCorrectionRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendancePolicyRepository;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestDayRepository;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

/** Unit contracts for separate Leave and Correction read-model ordering and balance values. */
class AttendanceReadModelServiceTest {

    @Test
    void leaveQueuePlacesActionableRequestsBeforeRetainedHistory() {
        LeaveRequestRepository requests = mock(LeaveRequestRepository.class);
        LeaveRequestEntity approved = mock(LeaveRequestEntity.class);
        LeaveRequestEntity pending = mock(LeaveRequestEntity.class);
        when(approved.id()).thenReturn(2L);
        when(approved.internUserId()).thenReturn(7L);
        when(approved.status()).thenReturn(LeaveStatus.APPROVED);
        when(pending.id()).thenReturn(1L);
        when(pending.internUserId()).thenReturn(7L);
        when(pending.status()).thenReturn(LeaveStatus.PENDING);
        when(requests.findByInternUserIdOrderBySubmittedAtDescIdDesc(7L))
                .thenReturn(List.of(approved, pending));
        AccountService accounts = mock(AccountService.class);
        when(accounts.requireIdentityById(7L)).thenReturn(internIdentity());

        LeaveApplicationService service = new LeaveApplicationService(
                Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC),
                mock(AttendancePolicyRepository.class), requests, mock(LeaveRequestDayRepository.class),
                accounts, mock(CalendarApplicationService.class), mock(TransactionTemplate.class),
                mock(com.lab.labtimesheet.feature.notification.service.NotificationService.class));

        assertThat(service.list(new AttendanceActor(7L, AttendanceRole.INTERN)))
                .extracting(LeaveRequestSummary::status)
                .containsExactly(LeaveStatus.PENDING, LeaveStatus.APPROVED);
    }

    @Test
    void monthlyBalanceUsesReservedStatusesAndCurrentPolicyQuota() {
        AttendancePolicyEntity seed = mock(AttendancePolicyEntity.class);
        AttendancePolicy policy = new AttendancePolicy(
                1L, LocalDate.of(1970, 1, 1), ZoneId.of("Asia/Ho_Chi_Minh"),
                LocalTime.of(8, 30), LocalTime.of(15, 30), 30, 30, 3,
                BigDecimal.valueOf(0.25), Set.of(
                        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
        when(seed.toDomain()).thenReturn(policy);
        AttendancePolicyRepository policies = mock(AttendancePolicyRepository.class);
        when(policies.findAllByOrderByEffectiveFromAsc()).thenReturn(List.of(seed));
        LeaveRequestDayRepository days = mock(LeaveRequestDayRepository.class);
        when(days.countReserved(7L, LocalDate.of(2026, 8, 1),
                List.of(LeaveStatus.PENDING.name(), LeaveStatus.APPROVED.name()))).thenReturn(2L);
        AccountService accounts = mock(AccountService.class);
        when(accounts.requireIdentityById(7L)).thenReturn(internIdentity());

        LeaveApplicationService service = new LeaveApplicationService(
                Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC), policies,
                mock(LeaveRequestRepository.class), days, accounts, mock(CalendarApplicationService.class),
                mock(TransactionTemplate.class),
                mock(com.lab.labtimesheet.feature.notification.service.NotificationService.class));

        LeaveBalance balance = service.balance(
                new AttendanceActor(7L, AttendanceRole.INTERN), YearMonth.of(2026, 8));

        assertThat(balance.quotaMonth()).isEqualTo(YearMonth.of(2026, 8));
        assertThat(balance.reservedDays()).isEqualTo(2);
        assertThat(balance.quotaDays()).isEqualTo(3);
        assertThat(balance.remainingDays()).isEqualTo(1);
    }

    @Test
    void correctionQueuePlacesPendingRequestsBeforeDecisionHistory() {
        AttendanceCorrectionRepository corrections = mock(AttendanceCorrectionRepository.class);
        when(corrections.findSummariesByInternUserId(7L)).thenReturn(List.of(
                new CorrectionSummary(2L, 12L, 7L, Instant.parse("2026-08-20T09:00:00Z"),
                        "Approved", CorrectionStatus.APPROVED.name(), Instant.parse("2026-08-20T10:00:00Z"),
                        Instant.parse("2026-08-21T10:00:00Z")),
                new CorrectionSummary(1L, 11L, 7L, Instant.parse("2026-08-21T09:00:00Z"),
                        "Pending", CorrectionStatus.PENDING.name(), Instant.parse("2026-08-21T10:00:00Z"),
                        Instant.parse("2026-08-22T10:00:00Z"))));
        AccountService accounts = mock(AccountService.class);
        when(accounts.requireIdentityById(7L)).thenReturn(internIdentity());

        AttendanceCorrectionApplicationService service = new AttendanceCorrectionApplicationService(
                Clock.fixed(Instant.parse("2026-08-21T00:00:00Z"), ZoneOffset.UTC),
                mock(com.lab.labtimesheet.feature.attendance.repository.AttendanceRecordRepository.class),
                corrections,
                mock(com.lab.labtimesheet.feature.attendance.repository.AttendanceCorrectionEventRepository.class),
                accounts, mock(TransactionTemplate.class),
                mock(com.lab.labtimesheet.feature.notification.service.NotificationService.class));

        assertThat(service.list(new AttendanceActor(7L, AttendanceRole.INTERN)))
                .extracting(CorrectionSummary::status)
                .containsExactly(CorrectionStatus.PENDING.name(), CorrectionStatus.APPROVED.name());
    }

    private static AccountIdentity internIdentity() {
        return new AccountIdentity(7L, "intern@example.test", "Intern", GlobalRole.INTERN, AccountStatus.ACTIVE);
    }
}
