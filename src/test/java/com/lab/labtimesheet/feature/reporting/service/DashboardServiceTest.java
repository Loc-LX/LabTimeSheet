package com.lab.labtimesheet.feature.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.identity.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.identity.model.dto.AccountSummary;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.attendance.exception.AttendanceException;
import com.lab.labtimesheet.feature.attendance.exception.AttendanceRejection;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceCurrentState;
import com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService;
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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DashboardServiceTest {

    private final AccountService accounts = mock(AccountService.class);
    private final ProjectQueryService projects = mock(ProjectQueryService.class);
    private final TaskDashboardService tasks = mock(TaskDashboardService.class);
    private final AttendanceApplicationService attendance = mock(AttendanceApplicationService.class);
    private DashboardService dashboards;

    @BeforeEach
    void setUp() {
        dashboards = new DashboardService(accounts, projects, tasks, attendance);
    }

    @Test
    void adminDashboardContainsOnlyAccountLifecycleSummaries() {
        given(accounts.requireIdentityByEmail("admin@example.test"))
                .willReturn(identity(1L, "Admin", GlobalRole.ADMIN, AccountStatus.ACTIVE));
        given(accounts.summary()).willReturn(new AccountSummary(8, 2, 3));

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

    @Test
    void internDashboardCombinesAttendanceProjectAndTaskViews() {
        var dueDate = LocalDate.of(2026, 8, 20);
        given(accounts.requireIdentityByEmail("intern@example.test"))
                .willReturn(identity(3L, "Mai Intern", GlobalRole.INTERN, AccountStatus.ACTIVE));
        given(attendance.currentState(3L)).willReturn(AttendanceCurrentState.CHECKED_IN);
        given(projects.dashboardSummary(3L)).willReturn(new ProjectDashboardSummary(2, 0));
        given(tasks.dashboard("intern@example.test")).willReturn(new TaskDashboardView(
                0, 6, List.of(new TaskPriorityView("Draft report", "Portal", TaskStatus.IN_PROGRESS, dueDate))));

        assertThat(dashboards.intern("intern@example.test"))
                .isEqualTo(new DashboardView.Intern(
                        "Mai Intern",
                        DashboardView.AttendanceState.CHECKED_IN,
                        2,
                        6,
                        List.of(new DashboardView.AssignedTask("Draft report", "Portal", "IN_PROGRESS", dueDate))));
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
