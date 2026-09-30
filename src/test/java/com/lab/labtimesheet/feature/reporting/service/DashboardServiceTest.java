package com.lab.labtimesheet.feature.reporting.service;

import com.lab.labtimesheet.feature.internship.service.InternshipService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.inOrder;

import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.identity.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.identity.model.dto.AccountSummary;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.attendance.exception.AttendanceException;
import com.lab.labtimesheet.feature.attendance.exception.AttendanceRejection;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceCurrentState;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveBalance;
import com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService;
import com.lab.labtimesheet.feature.attendance.service.LeaveApplicationService;
import com.lab.labtimesheet.feature.calendar.service.CalendarApplicationService;
import com.lab.labtimesheet.feature.project.model.dto.ProjectDashboardSummary;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.reporting.exception.DashboardAccessDeniedException;
import com.lab.labtimesheet.feature.reporting.model.dto.DashboardView;
import com.lab.labtimesheet.feature.project.model.TaskStatus;
import com.lab.labtimesheet.feature.project.model.dto.TaskDashboardView;
import com.lab.labtimesheet.feature.project.model.dto.TaskPriorityView;
import com.lab.labtimesheet.feature.project.service.TaskDashboardService;
import com.lab.labtimesheet.platform.model.GlobalRole;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DashboardServiceTest {

    private final AccountService accounts = mock(AccountService.class);
    private final InternshipService internships = mock(InternshipService.class);
    private final ProjectQueryService projects = mock(ProjectQueryService.class);
    private final TaskDashboardService tasks = mock(TaskDashboardService.class);
    private final AttendanceApplicationService attendance = mock(AttendanceApplicationService.class);
    private final CalendarApplicationService calendar = mock(CalendarApplicationService.class);
    private final LeaveApplicationService leave = mock(LeaveApplicationService.class);
    private DashboardService dashboards;

    @BeforeEach
    void setUp() {
        dashboards = new DashboardService(accounts, internships, projects, tasks, attendance, calendar, leave);
    }

    /**
     * Protects {@code LEV-004}, {@code UI-019}, and {@code AC-LEV-006}: the Intern dashboard projection requires a
     * non-null leave balance. Observable break: null balance is accepted. Expected: NullPointerException thrown.
     */
    @Test
    void internDashboardRequiresNonNullLeaveBalance() {
        assertThatThrownBy(() -> new DashboardView.Intern(
                "Intern", DashboardView.AttendanceState.NOT_CHECKED_IN, 1, 0, List.of(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("leaveBalance");
    }

    @Test
    void adminDashboardContainsOnlyAccountLifecycleSummaries() {
        given(accounts.requireIdentityByEmail("admin@example.test"))
                .willReturn(identity(1L, "Admin", GlobalRole.ADMIN, AccountStatus.ACTIVE));
        given(internships.summary()).willReturn(new AccountSummary(8, 2, 3));

        assertThat(dashboards.admin("admin@example.test"))
                .isEqualTo(new DashboardView.Admin(8, 2, 3));

        verifyNoInteractions(projects, tasks, attendance);
    }

    @Test
    void mentorDashboardCombinesOwnedProjectAndTaskSummaries() {
        given(accounts.requireIdentityByEmail("mentor@example.test"))
                .willReturn(identity(2L, "Minh Mentor", GlobalRole.MENTOR, AccountStatus.ACTIVE));
        given(projects.dashboardSummary(2L)).willReturn(new ProjectDashboardSummary(3, 7));
        given(tasks.dashboard("mentor@example.test")).willReturn(new TaskDashboardView(5, 0, List.of()));

        assertThat(dashboards.mentor("mentor@example.test"))
                .isEqualTo(new DashboardView.Mentor("Minh Mentor", 3, 7, 5));

        verifyNoInteractions(attendance);
    }

    /**
     * Protects {@code LEV-004}, {@code UI-019}, and {@code AC-LEV-006}: after confirming the Intern's active attendance
     * state, the dashboard selects the policy business month and asks Leave for its balance. Observable break: it
     * displays a browser/system month or reads balance before eligibility. August's fixture value is 2/3/1.
     */
    @Test
    void internDashboardCombinesAttendanceProjectAndTaskViews() {
        var dueDate = LocalDate.of(2026, 8, 20);
        given(accounts.requireIdentityByEmail("intern@example.test"))
                .willReturn(identity(3L, "Mai Intern", GlobalRole.INTERN, AccountStatus.ACTIVE));
        given(attendance.currentState(3L)).willReturn(AttendanceCurrentState.CHECKED_IN);
        given(calendar.currentBusinessDate()).willReturn(LocalDate.of(2026, 8, 21));
        given(leave.balance(new AttendanceActor(3L, GlobalRole.INTERN), YearMonth.of(2026, 8)))
                .willReturn(new LeaveBalance(YearMonth.of(2026, 8), 2, 3));
        given(projects.dashboardSummary(3L)).willReturn(new ProjectDashboardSummary(2, 0));
        given(tasks.dashboard("intern@example.test")).willReturn(new TaskDashboardView(
                0, 6, List.of(new TaskPriorityView("Draft report", "Portal", TaskStatus.IN_PROGRESS, dueDate))));

        assertThat(dashboards.intern("intern@example.test"))
                .isEqualTo(new DashboardView.Intern(
                        "Mai Intern",
                        DashboardView.AttendanceState.CHECKED_IN,
                        2,
                        6,
                        List.of(new DashboardView.AssignedTask("Draft report", "Portal", "IN_PROGRESS", dueDate)),
                        new LeaveBalance(YearMonth.of(2026, 8), 2, 3)));

        var order = inOrder(attendance, calendar, leave);
        order.verify(attendance).currentState(3L);
        order.verify(calendar).currentBusinessDate();
        order.verify(leave).balance(new AttendanceActor(3L, GlobalRole.INTERN), YearMonth.of(2026, 8));
    }

    @Test
    void roleAndActiveStatusComeFromTheAccountServiceRatherThanGrantedAuthorities() {
        given(accounts.requireIdentityByEmail("intern@example.test"))
                .willReturn(identity(3L, "Mai Intern", GlobalRole.INTERN, AccountStatus.ACTIVE));
        given(accounts.requireIdentityByEmail("locked@example.test"))
                .willReturn(identity(4L, "Locked Mentor", GlobalRole.MENTOR, AccountStatus.LOCKED));

        assertThatThrownBy(() -> dashboards.admin("intern@example.test"))
                .isInstanceOf(DashboardAccessDeniedException.class);
        assertThatThrownBy(() -> dashboards.mentor("locked@example.test"))
                .isInstanceOf(DashboardAccessDeniedException.class);

        verifyNoInteractions(projects, tasks, attendance);
    }

    @Test
    void missingAccountIsReportedAsDashboardAccessDenied() {
        given(accounts.requireIdentityByEmail("missing@example.test"))
                .willThrow(new IllegalArgumentException("Account not found"));

        assertThatThrownBy(() -> dashboards.intern("missing@example.test"))
                .isInstanceOf(DashboardAccessDeniedException.class);

        verifyNoInteractions(projects, tasks, attendance);
    }

    @Test
    void ineligibleInternIsReportedAsDashboardAccessDenied() {
        given(accounts.requireIdentityByEmail("intern@example.test"))
                .willReturn(identity(3L, "Mai Intern", GlobalRole.INTERN, AccountStatus.ACTIVE));
        given(attendance.currentState(3L))
                .willThrow(new AttendanceException(AttendanceRejection.INACTIVE_INTERN));

        assertThatThrownBy(() -> dashboards.intern("intern@example.test"))
                .isInstanceOf(DashboardAccessDeniedException.class);

        verifyNoInteractions(projects, tasks);
    }

    private static AccountIdentity identity(
            long id, String displayName, GlobalRole role, AccountStatus status) {
        return new AccountIdentity(id, role.name().toLowerCase() + "@example.test", displayName, role, status);
    }
}
