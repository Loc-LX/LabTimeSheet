package com.lab.labtimesheet.feature.attendance.service;

import com.lab.labtimesheet.feature.internship.service.InternshipService;

import com.lab.labtimesheet.feature.calendar.service.CalendarApplicationService;
import com.lab.labtimesheet.feature.calendar.service.AttendancePolicyTimeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.internship.model.InternshipStatus;
import com.lab.labtimesheet.feature.identity.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.internship.model.dto.InternWorkWindow;
import com.lab.labtimesheet.feature.internship.model.dto.LockedAccountMutationEligibility;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.attendance.exception.LeaveException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.calendar.model.AttendancePolicyFixtures;
import com.lab.labtimesheet.platform.model.GlobalRole;
import com.lab.labtimesheet.feature.attendance.model.LeaveStatus;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveRequestCommand;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestEntity;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestDayRepository;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestDecisionRepository;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionTemplate;

class LeaveApplicationServiceTest {

    @Test
    void rejectsBlankReasonBeforeReadingOrWritingLeaveState() {
        AccountService accounts = mock(AccountService.class);
        when(accounts.requireIdentityById(42L)).thenReturn(activeIntern(42L));
        LeaveApplicationService service = new LeaveApplicationService(
                Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC),
                mock(LeaveRequestRepository.class),
                mock(LeaveRequestDayRepository.class),
                mock(LeaveRequestDecisionRepository.class),
                accounts,
                mock(InternshipService.class),
                mock(CalendarApplicationService.class),
                mock(TransactionTemplate.class),
                mock(NotificationService.class), AttendanceAuthorizationTestPolicy.create(),
                mock(AttendanceExceptionNotificationRecipients.class));

        assertThatThrownBy(() -> service.submit(
                        new AttendanceActor(42L, GlobalRole.INTERN),
                        new LeaveRequestCommand(
                                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), "  ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Protects §5.2 `Submit own leave, correction, or exception request`. Observable break: a
     * deactivated Intern is treated as ACTIVE and can read the leave balance; the expected result
     * is an authorization denial before any calendar or leave data is read.
     */
    @Test
    void deactivatedInternCannotReadLeaveBalance() {
        AccountService accounts = mock(AccountService.class);
        when(accounts.requireIdentityById(42L)).thenReturn(new AccountIdentity(
                42L, "intern@example.test", "Intern", GlobalRole.INTERN, AccountStatus.DEACTIVATED));
        CalendarApplicationService calendar = mock(CalendarApplicationService.class);
        when(calendar.policyTimeline()).thenReturn(new AttendancePolicyTimeline(
                List.of(AttendancePolicyFixtures.seeded(1L))));
        LeaveApplicationService service = new LeaveApplicationService(
                Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC),
                mock(LeaveRequestRepository.class),
                mock(LeaveRequestDayRepository.class),
                mock(LeaveRequestDecisionRepository.class),
                accounts,
                mock(InternshipService.class),
                calendar,
                mock(TransactionTemplate.class),
                mock(NotificationService.class), AttendanceAuthorizationTestPolicy.create(),
                mock(AttendanceExceptionNotificationRecipients.class));

        assertThatThrownBy(() -> service.balance(
                        new AttendanceActor(42L, GlobalRole.INTERN), java.time.YearMonth.of(2026, 8)))
                .isInstanceOf(AccessDeniedException.class);

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
        InternshipService internships = mock(InternshipService.class);
        AtomicBoolean accountLocked = new AtomicBoolean();
        when(internships.lockedAccountMutationEligibility(any())).thenAnswer(invocation -> {
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
                requests,
                mock(LeaveRequestDayRepository.class),
                mock(LeaveRequestDecisionRepository.class),
                accounts,
                internships,
                mock(CalendarApplicationService.class),
                mock(TransactionTemplate.class),
                mock(NotificationService.class), AttendanceAuthorizationTestPolicy.create(),
                mock(AttendanceExceptionNotificationRecipients.class));

        service.expirePending(1);
        verify(locked).autoReject(afterLock);
    }

    /**
     * Protects {@code LEV-009}. Observable break: the same-day boundary is made inclusive, or is
     * anchored to midnight rather than to scheduled start, so an Intern can file leave for a day
     * whose working hours have already begun.
     *
     * <p>Expected values derived from {@code LEV-009} and {@code ATT-002}, not from the
     * implementation. The seeded policy starts the workday at 08:30 in {@code Asia/Ho_Chi_Minh},
     * which is {@code 01:30Z}. Friday 14 August 2026 is a configured workday. {@code LEV-009}
     * accepts a submission whose first counted date is that day only while the clock is strictly
     * before 08:30 local, so {@code 2026-08-14T01:29:59Z} is accepted and
     * {@code 2026-08-14T01:30:00Z} is refused. The rule states both halves in words.
     *
     * <p>One second separates the two clocks, so an implementation that rounded to the day, or
     * that used {@code isAfter} where the rule says at or after, would pass one half and fail the
     * other.
     */
    @Test
    void sameDayLeaveIsAcceptedBeforeScheduledStartAndRefusedFromItOnwards() {
        LocalDate workday = LocalDate.of(2026, 8, 14);
        Instant oneSecondBeforeScheduledStart = Instant.parse("2026-08-14T01:29:59Z");
        Instant exactlyScheduledStart = Instant.parse("2026-08-14T01:30:00Z");

        LeaveRequestRepository acceptingRequests = mock(LeaveRequestRepository.class);
        when(acceptingRequests.saveAndFlush(any(LeaveRequestEntity.class)))
                .thenAnswer(invocation -> {
                    LeaveRequestEntity persisted =
                            org.mockito.Mockito.spy(invocation.<LeaveRequestEntity>getArgument(0));
                    org.mockito.Mockito.doReturn(1L).when(persisted).id();
                    return persisted;
                });

        submitSameDayLeave(oneSecondBeforeScheduledStart, workday, acceptingRequests);
        org.mockito.Mockito.verify(acceptingRequests).saveAndFlush(any(LeaveRequestEntity.class));

        LeaveRequestRepository refusingRequests = mock(LeaveRequestRepository.class);
        assertThatThrownBy(() -> submitSameDayLeave(exactlyScheduledStart, workday, refusingRequests))
                .isInstanceOf(LeaveException.class)
                .hasMessageContaining("first counted start");
        org.mockito.Mockito.verify(refusingRequests, org.mockito.Mockito.never())
                .saveAndFlush(any(LeaveRequestEntity.class));
    }

    /**
     * Protects the second half of {@code LEV-008}, which reserves the leave decision for an active
     * Mentor and withholds it from an Admin. Observable break: the guard is loosened to any
     * privileged role, and an Admin starts approving and rejecting leave, which also moves quota
     * because an approval reserves it.
     *
     * <p>{@code GlobalRole} carries `ADMIN`, so an Admin can reach these methods and the
     * refusal has to be deliberate rather than a consequence of the type. The test asserts the
     * refusal on both decisions and, separately, that nothing was read on the way to it: the guard
     * runs before the request is looked up, so an Admin holding a guessed identifier learns neither
     * whether it exists nor whose it is. That second assertion is what would fail if the guard were
     * moved below the lookup while still refusing.
    /**
     * Protects {@code AUTH-012}, {@code LEV-008}, {@code ATT-024}: an Admin cannot decide leave
     * and is refused by the authorization policy inside the decision transaction.
     * Observable break: policy allows Admin to approve or reject leave. Expected: AccessDeniedException.
     */
    @Test
    void anAdminCannotDecideLeaveAndIsRefusedByPolicy() {
        LeaveRequestRepository requests = mock(LeaveRequestRepository.class);
        when(requests.findInternUserIdById(77L)).thenReturn(Optional.of(42L));
        LeaveRequestDayRepository days = mock(LeaveRequestDayRepository.class);
        AccountService accounts = mock(AccountService.class);
        when(accounts.requireIdentityById(42L)).thenReturn(activeIntern(42L));
        InternshipService internships = mock(InternshipService.class);
        when(internships.lockedAccountMutationEligibility(any())).thenReturn(List.of(
                new LockedAccountMutationEligibility(9L, GlobalRole.ADMIN, AccountStatus.ACTIVE, Optional.empty()),
                new LockedAccountMutationEligibility(42L, GlobalRole.INTERN, AccountStatus.ACTIVE, Optional.of(InternshipStatus.ACTIVE))));

        org.springframework.transaction.PlatformTransactionManager tm = mock(org.springframework.transaction.PlatformTransactionManager.class);
        TransactionTemplate txTemplate = mock(TransactionTemplate.class);
        when(txTemplate.getTransactionManager()).thenReturn(tm);
        when(tm.getTransaction(any())).thenReturn(mock(org.springframework.transaction.TransactionStatus.class));

        LeaveApplicationService service = new LeaveApplicationService(
                Clock.fixed(Instant.parse("2026-08-14T01:00:00Z"), ZoneOffset.UTC),
                requests,
                days,
                mock(LeaveRequestDecisionRepository.class),
                accounts,
                internships,
                mock(CalendarApplicationService.class),
                txTemplate,
                mock(NotificationService.class), AttendanceAuthorizationTestPolicy.create(),
                mock(AttendanceExceptionNotificationRecipients.class));
        AttendanceActor admin = new AttendanceActor(9L, GlobalRole.ADMIN);

        assertThatThrownBy(() -> service.approve(admin, 77L))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.reject(admin, 77L))
                .isInstanceOf(AccessDeniedException.class);
    }

    /**
     * Submits a one-day leave request for {@code workday} with the clock fixed at {@code now},
     * against the seeded policy, an Intern eligible across that date, and no global day off.
     *
     * @param now instant the service reads as the current time
     * @param workday the single requested date, which is also the first counted date
     * @param requests leave repository, so a caller can assert whether the row was written
     */
    private void submitSameDayLeave(Instant now, LocalDate workday, LeaveRequestRepository requests) {
        AccountService accounts = mock(AccountService.class);
        when(accounts.requireIdentityById(42L)).thenReturn(activeIntern(42L));
        InternshipService internships = mock(InternshipService.class);
        when(accounts.activeGlobalMentorIdentities()).thenReturn(List.of());
        when(internships.lockedInternWorkWindow(eq(42L), any(LocalDate.class)))
                .thenReturn(new InternWorkWindow(
                        42L,
                        workday,
                        workday.minusMonths(1),
                        workday.plusMonths(1),
                        AccountStatus.ACTIVE,
                        InternshipStatus.ACTIVE));

        CalendarApplicationService calendar = mock(CalendarApplicationService.class);
        when(calendar.isGlobalDayOff(any(LocalDate.class))).thenReturn(false);
        when(calendar.policyTimeline()).thenReturn(new AttendancePolicyTimeline(
                List.of(AttendancePolicyFixtures.seeded(1L))));

        new LeaveApplicationService(
                        Clock.fixed(now, ZoneOffset.UTC),
                        requests,
                        mock(LeaveRequestDayRepository.class),
                        mock(LeaveRequestDecisionRepository.class),
                        accounts,
                        internships,
                        calendar,
                        mock(TransactionTemplate.class),
                        mock(NotificationService.class), AttendanceAuthorizationTestPolicy.create(),
                mock(AttendanceExceptionNotificationRecipients.class))
                .submit(
                        new AttendanceActor(42L, GlobalRole.INTERN),
                        new LeaveRequestCommand(workday, workday, "Family matter"));
    }

    private static AccountIdentity activeIntern(long userId) {
        return new AccountIdentity(
                userId, "intern@example.test", "Intern", GlobalRole.INTERN, AccountStatus.ACTIVE);
    }
}
