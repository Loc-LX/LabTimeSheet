package com.lab.labtimesheet.feature.reporting.service;

import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.account.model.dto.AccountSummary;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.attendance.exception.AttendanceException;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceCurrentState;
import com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService;
import com.lab.labtimesheet.feature.reporting.exception.DashboardAccessDeniedException;
import com.lab.labtimesheet.feature.reporting.model.dto.DashboardView;
import com.lab.labtimesheet.feature.reporting.model.dto.DashboardView.AssignedTask;
import com.lab.labtimesheet.feature.reporting.model.dto.DashboardView.AttendanceState;
import com.lab.labtimesheet.feature.project.model.dto.ProjectDashboardSummary;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.task.model.dto.TaskDashboardView;
import com.lab.labtimesheet.feature.task.service.TaskDashboardService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Composes authorized dashboard projections exclusively from public feature services and DTOs.
 *
 * <p>This service owns no persistence mapping or business-date calculation. Account lifecycle and
 * role are revalidated from persisted identity data, while Project, Task, and Attendance retain
 * ownership of their query scope, ordering, and attendance-policy business date.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final AccountService accounts;
    private final ProjectQueryService projects;
    private final TaskDashboardService tasks;
    private final AttendanceApplicationService attendance;

    /**
     * Creates a reporting coordinator over the concrete feature query boundaries.
     *
     * @param accounts account identity and Admin summary boundary
     * @param projects role-scoped Project summary boundary
     * @param tasks role-scoped Task dashboard boundary
     * @param attendance attendance state boundary using the active policy business date
     */
    public DashboardService(
            AccountService accounts,
            ProjectQueryService projects,
            TaskDashboardService tasks,
            AttendanceApplicationService attendance) {
        this.accounts = accounts;
        this.projects = projects;
        this.tasks = tasks;
        this.attendance = attendance;
    }

    /**
     * Builds system-wide Admin counts after confirming an active persisted Admin identity.
     *
     * <p>Counts are zero when the corresponding feature has no matching records.
     *
     * @param email authenticated account email
     * @return account lifecycle counts and the Admin-visible active Project count
     * @throws DashboardAccessDeniedException when the persisted account is missing, inactive, or
     *     not an Admin
     */
    public DashboardView.Admin admin(String email) {
        AccountIdentity admin = activeAccount(email, GlobalRole.ADMIN);
        AccountSummary accountSummary = accounts.summary();
        ProjectDashboardSummary projectSummary = projects.dashboardSummary(admin.id());
        return new DashboardView.Admin(
                accountSummary.activeAccounts(),
                accountSummary.pendingActivations(),
                accountSummary.activeInternships(),
                projectSummary.activeProjectCount());
    }

    /**
     * Builds the owning-Mentor dashboard after persisted-role revalidation.
     *
     * <p>Project and member counts are scoped by the Project service; blocked Task count is scoped
     * by the Task service. Each empty scope is represented by a zero count.
     *
     * @param email authenticated account email
     * @return Mentor display name and role-scoped Project, member, and blocked-Task counts
     * @throws DashboardAccessDeniedException when the persisted account is missing, inactive, or
     *     not a Mentor
     */
    public DashboardView.Mentor mentor(String email) {
        AccountIdentity mentor = activeAccount(email, GlobalRole.MENTOR);
        ProjectDashboardSummary projectSummary = projects.dashboardSummary(mentor.id());
        TaskDashboardView taskSummary = tasks.dashboard(email);
        return new DashboardView.Mentor(
                mentor.displayName(),
                projectSummary.activeProjectCount(),
                projectSummary.distinctActiveMemberCount(),
                taskSummary.blockedTaskCount());
    }

    /**
     * Builds the eligible Intern dashboard after persisted-role revalidation.
     *
     * <p>The Attendance feature determines today's state from its policy-owned business date. The
     * Task feature owns assigned count and priority ordering; no matching Tasks produce an empty
     * priority list. Attendance ineligibility is converted to the same non-disclosing dashboard
     * denial as other invalid Intern lifecycle states.
     *
     * @param email authenticated account email
     * @return Intern attendance state, scoped counts, and at most the Task service's priority items
     * @throws DashboardAccessDeniedException when the persisted account or internship is not
     *     eligible for the Intern dashboard
     */
    public DashboardView.Intern intern(String email) {
        AccountIdentity intern = activeAccount(email, GlobalRole.INTERN);
        AttendanceCurrentState attendanceState;
        try {
            attendanceState = attendance.currentState(intern.id());
        } catch (AttendanceException exception) {
            throw new DashboardAccessDeniedException("Active Intern account and internship required");
        }
        ProjectDashboardSummary projectSummary = projects.dashboardSummary(intern.id());
        TaskDashboardView taskSummary = tasks.dashboard(email);
        return new DashboardView.Intern(
                intern.displayName(),
                AttendanceState.valueOf(attendanceState.name()),
                projectSummary.activeProjectCount(),
                taskSummary.assignedTaskCount(),
                taskSummary.priorityTasks().stream()
                        .map(task -> new AssignedTask(
                                task.title(), task.projectName(), task.status().name(), task.dueDate()))
                        .toList());
    }

    private AccountIdentity activeAccount(String email, GlobalRole role) {
        AccountIdentity account;
        try {
            account = accounts.requireIdentityByEmail(email);
        } catch (IllegalArgumentException exception) {
            throw new DashboardAccessDeniedException("Active " + role + " account required");
        }
        if (account.status() != AccountStatus.ACTIVE || account.role() != role) {
            throw new DashboardAccessDeniedException("Active " + role + " account required");
        }
        return account;
    }
}
