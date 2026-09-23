package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.identity.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.platform.model.GlobalRole;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceQueryRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceRecordRepository;
import com.lab.labtimesheet.platform.model.GlobalRole;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class AttendanceReportQueryServiceAuthorizationTest {

    private static final LocalDate FROM = LocalDate.of(2026, 8, 1);
    private static final LocalDate TO = LocalDate.of(2026, 8, 31);

    private final AccountService accounts = mock(AccountService.class);
    private final AttendanceRecordRepository records = mock(AttendanceRecordRepository.class);
    private final AttendanceQueryRepository queries = mock(AttendanceQueryRepository.class);
    private final CalendarApplicationService calendar = mock(CalendarApplicationService.class);
    private final AttendanceCorrectionApplicationService corrections = mock(AttendanceCorrectionApplicationService.class);
    private final AttendanceReportQueryService reports = new AttendanceReportQueryService(
            Clock.systemUTC(), accounts, records, queries, calendar, corrections);

    @Test
    void inactivePersistedAdminIsDeniedBeforeTargetOrAttendanceReads() {
        given(accounts.requireIdentityById(1L)).willReturn(new AccountIdentity(
                1L, "admin@example.test", "Admin", GlobalRole.ADMIN, AccountStatus.PENDING_ACTIVATION));

        assertThatThrownBy(() -> reports.query(
                new AttendanceActor(1L, GlobalRole.ADMIN), 7L, FROM, TO))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("An active Mentor or Admin is required");

        verify(accounts).requireIdentityById(1L);
        verify(accounts, never()).requireIdentityById(7L);
        verifyNoMoreInteractions(accounts);
        verifyNoInteractions(records, queries, calendar, corrections);
    }

    @Test
    void adminActorWithMismatchedPersistedRoleIsDeniedBeforeTargetOrAttendanceReads() {
        given(accounts.requireIdentityById(1L)).willReturn(new AccountIdentity(
                1L, "admin@example.test", "Admin", GlobalRole.MENTOR, AccountStatus.ACTIVE));

        assertThatThrownBy(() -> reports.query(
                new AttendanceActor(1L, GlobalRole.ADMIN), 7L, FROM, TO))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Attendance actor role does not match the account");

        verify(accounts).requireIdentityById(1L);
        verify(accounts, never()).requireIdentityById(7L);
        verifyNoMoreInteractions(accounts);
        verifyNoInteractions(records, queries, calendar, corrections);
    }
}
