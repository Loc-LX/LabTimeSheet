package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verify;

import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceQueryRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceRecordRepository;
import com.lab.labtimesheet.feature.calendar.service.AttendancePolicyTimeline;
import com.lab.labtimesheet.feature.calendar.service.CalendarApplicationService;
import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.identity.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.internship.service.InternshipService;
import com.lab.labtimesheet.platform.authorization.AuthorizationCatalogue;
import com.lab.labtimesheet.platform.authorization.AuthorizationPolicy;
import com.lab.labtimesheet.platform.authorization.WithdrawnAuthorizationCatalogues;
import com.lab.labtimesheet.platform.model.GlobalRole;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class AttendanceReportQueryServiceAuthorizationTest {

    private static final LocalDate FROM = LocalDate.of(2026, 8, 1);
    private static final LocalDate TO = LocalDate.of(2026, 8, 31);

    private final AccountService accounts = mock(AccountService.class);
    private final InternshipService internships = mock(InternshipService.class);
    private final AttendanceRecordRepository records = mock(AttendanceRecordRepository.class);
    private final AttendanceQueryRepository queries = mock(AttendanceQueryRepository.class);
    private final CalendarApplicationService calendar = mock(CalendarApplicationService.class);
    private final AttendanceCorrectionApplicationService corrections = mock(AttendanceCorrectionApplicationService.class);
    private final AuthorizationPolicy authorizationPolicy =
            new AuthorizationPolicy(AuthorizationCatalogue.loadDefault());
    private final AttendanceReportQueryService reports = new AttendanceReportQueryService(
            Clock.systemUTC(), accounts, internships, records, queries, calendar, corrections, authorizationPolicy);

    @Test
    void inactivePersistedAdminIsDeniedByPolicyAfterTargetValidation() {
        given(accounts.requireIdentityById(1L)).willReturn(new AccountIdentity(
                1L, "admin@example.test", "Admin", GlobalRole.ADMIN, AccountStatus.PENDING_ACTIVATION));
        given(accounts.requireIdentityById(7L)).willReturn(new AccountIdentity(
                7L, "intern@example.test", "Intern", GlobalRole.INTERN, AccountStatus.ACTIVE));

        assertThatThrownBy(() -> reports.query(
                new AttendanceActor(1L, GlobalRole.ADMIN), 7L, FROM, TO))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("An active Mentor or Admin is required");

        verify(accounts).requireIdentityById(1L);
        verify(accounts).requireIdentityById(7L);
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

    /**
     * Protects {@code AUTH-012} and {@code RPT-004}. Observable break: an active Intern could read another
     * Intern's report if the empty actor scope were not refused; §5.2 grants an active member only own history.
     */
    @Test
    void activeInternCannotReadAnotherInternAttendanceThroughTheRealPolicy() {
        given(accounts.requireIdentityById(1L)).willReturn(new AccountIdentity(
                1L, "intern-one@example.test", "Intern One", GlobalRole.INTERN, AccountStatus.ACTIVE));
        given(accounts.requireIdentityById(7L)).willReturn(new AccountIdentity(
                7L, "intern-two@example.test", "Intern Two", GlobalRole.INTERN, AccountStatus.ACTIVE));
        given(internships.historicalInternReportingWindow(7L)).willReturn(Optional.empty());
        given(records.findByInternUserIdAndWorkDateBetweenOrderByWorkDateAsc(7L, FROM, TO)).willReturn(List.of());
        given(calendar.policiesByVersionIds(Set.of())).willReturn(Map.of());
        given(corrections.prepareHistory(List.of())).willReturn(Map.of());
        given(queries.findApprovedLeaveDates(7L, FROM, TO)).willReturn(List.of());
        given(calendar.historyBetween(FROM, TO)).willReturn(List.of());
        given(calendar.policyTimeline()).willReturn(mock(AttendancePolicyTimeline.class));

        assertThatThrownBy(() -> reports.query(
                new AttendanceActor(1L, GlobalRole.INTERN), 7L, FROM, TO))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Interns may view only their own attendance");

        verify(accounts).requireIdentityById(1L);
        verify(accounts).requireIdentityById(7L);
        verifyNoMoreInteractions(accounts);
        verifyNoInteractions(records, queries, calendar, corrections);
    }

    /**
     * Protects {@code AUTH-012}, {@code AC-AUTH-011}, and {@code D1}. Observable break: an Admin whose
     * ATTENDANCE_REPORT cell is withdrawn could open the unscoped detail selector; the Attendance-owned policy
     * boundary must deny that request before target options are read.
     */
    @Test
    void withdrawnAttendanceReportPolicyDeniesAdminDetailSelection() {
        given(accounts.requireIdentityById(1L)).willReturn(new AccountIdentity(
                1L, "admin@example.test", "Admin", GlobalRole.ADMIN, AccountStatus.ACTIVE));
        AttendanceReportQueryService withdrawnReports = reports(new AuthorizationPolicy(
                WithdrawnAuthorizationCatalogues.attendanceReportAdminWithdrawn()));

        assertThatThrownBy(() -> withdrawnReports.requireDetailSelectionAccess(
                new AttendanceActor(1L, GlobalRole.ADMIN)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("An active Mentor or Admin is required");

        verify(accounts).requireIdentityById(1L);
        verifyNoMoreInteractions(accounts);
        verifyNoInteractions(records, queries, calendar, corrections);
    }

    /** Protects {@code AUTH-012} and {@code RPT-004}: a granted active Mentor must retain the unscoped selector. */
    @Test
    void withdrawnAttendanceReportPolicyRetainsActiveMentorDetailSelection() {
        given(accounts.requireIdentityById(2L)).willReturn(new AccountIdentity(
                2L, "mentor@example.test", "Mentor", GlobalRole.MENTOR, AccountStatus.ACTIVE));
        AttendanceReportQueryService withdrawnReports = reports(new AuthorizationPolicy(
                WithdrawnAuthorizationCatalogues.attendanceReportAdminWithdrawn()));

        assertDoesNotThrow(() -> withdrawnReports.requireDetailSelectionAccess(
                new AttendanceActor(2L, GlobalRole.MENTOR)));

        verify(accounts).requireIdentityById(2L);
        verifyNoMoreInteractions(accounts);
        verifyNoInteractions(records, queries, calendar, corrections);
    }

    private AttendanceReportQueryService reports(AuthorizationPolicy policy) {
        return new AttendanceReportQueryService(
                Clock.systemUTC(), accounts, internships, records, queries, calendar, corrections, policy);
    }
}
