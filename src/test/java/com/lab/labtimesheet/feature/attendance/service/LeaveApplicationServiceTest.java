package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.account.model.dto.LockedAccountMutationEligibility;
import com.lab.labtimesheet.feature.account.model.InternshipStatus;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.LeaveStatus;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveRequestCommand;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendancePolicyRepository;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestDayRepository;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestRepository;
import com.lab.labtimesheet.feature.notification.service.NotificationService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

class LeaveApplicationServiceTest {

    @Test
    void rejectsBlankReasonBeforeReadingOrWritingLeaveState() {
        LeaveApplicationService service = new LeaveApplicationService(
                Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC),
                mock(AttendancePolicyRepository.class),
                mock(LeaveRequestRepository.class),
                mock(LeaveRequestDayRepository.class),
                mock(AccountService.class),
                mock(CalendarApplicationService.class),
                mock(TransactionTemplate.class),
                mock(NotificationService.class));

        assertThatThrownBy(() -> service.submit(
                        new AttendanceActor(42L, AttendanceRole.INTERN),
                        new LeaveRequestCommand(
                                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), "  ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expirySamplesClockAfterLockedRowAcquisition() {
        Instant beforeLock = Instant.parse("2026-08-14T00:59:59Z");
        Instant afterLock = Instant.parse("2026-08-14T01:00:00Z");
        AtomicReference<Instant> now = new AtomicReference<>(beforeLock);
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(invocation -> now.get());

        LeaveRequestRepository requests = mock(LeaveRequestRepository.class);
        LeaveRequestRepository.ExpiredRecipientRoute candidate =
                mock(LeaveRequestRepository.ExpiredRecipientRoute.class);
        LeaveRequestEntity locked = mock(LeaveRequestEntity.class);
        when(candidate.getRequestId()).thenReturn(7L);
        when(candidate.getInternUserId()).thenReturn(42L);
        when(locked.status()).thenReturn(LeaveStatus.PENDING);
        when(locked.internUserId()).thenReturn(42L);
        when(locked.firstCountedStartAt()).thenReturn(afterLock);
        when(requests.findExpiredRecipientRoutes(
                        eq(LeaveStatus.PENDING.name()), eq(beforeLock), any(Pageable.class)))
                .thenReturn(List.of(candidate));
        when(requests.findForUpdateById(7L)).thenAnswer(invocation -> {
            now.set(afterLock);
            return Optional.of(locked);
        });
        AccountService accounts = mock(AccountService.class);
        AtomicBoolean accountLocked = new AtomicBoolean();
        when(accounts.lockedAccountMutationEligibility(any())).thenAnswer(invocation -> {
            accountLocked.set(true);
            return List.of(new LockedAccountMutationEligibility(
                    42L,
                    GlobalRole.INTERN,
                    AccountStatus.ACTIVE,
                    java.util.Optional.of(InternshipStatus.ACTIVE)));
        });
        when(accounts.requireIdentityById(42L)).thenAnswer(invocation -> {
            assertThat(accountLocked).as("recipient identity must be read after the Account lock").isTrue();
            return new AccountIdentity(
                    42L, "intern@example.test", "Intern", GlobalRole.INTERN, AccountStatus.ACTIVE);
        });

        LeaveApplicationService service = new LeaveApplicationService(
                clock,
                mock(AttendancePolicyRepository.class),
                requests,
                mock(LeaveRequestDayRepository.class),
                accounts,
                mock(CalendarApplicationService.class),
                mock(TransactionTemplate.class),
                mock(NotificationService.class));

        assertThat(service.expirePending(1)).isEqualTo(1);
    }
}
