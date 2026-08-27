package com.lab.labtimesheet.feature.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceReportDateContext;
import com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService;
import com.lab.labtimesheet.feature.project.exception.ProjectRuleViolationException;
import com.lab.labtimesheet.feature.project.model.dto.ProjectActorView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMemberView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectSummary;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.reporting.model.dto.DailyProjectWorkReportTask;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.TaskVarianceState;
import com.lab.labtimesheet.feature.task.model.dto.TaskDailyReportView;
import com.lab.labtimesheet.feature.task.model.dto.TaskRemainingEffortForecastSummary;
import com.lab.labtimesheet.feature.task.model.dto.TaskWorkLogView;
import com.lab.labtimesheet.feature.task.service.TaskQueryService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Public-boundary RED/GREEN contract for the Daily Project Work Report dataset. */
class DailyProjectWorkReportServiceTest {

    private static final LocalDate REPORT_DATE = LocalDate.of(2026, 8, 20);
    private static final Instant ASSIGNED_AT = Instant.parse("2026-08-01T00:00:00Z");

    private final ProjectQueryService projects = mock(ProjectQueryService.class);
    private final TaskQueryService taskQueries = mock(TaskQueryService.class);
    private final AttendanceApplicationService attendance = mock(AttendanceApplicationService.class);
    private DailyProjectWorkReportService reports;

    @BeforeEach
    void setUp() {
        reports = new DailyProjectWorkReportService(projects, taskQueries, attendance);
    }

    @Test
    void mentorDailyReportGroupsSelectedLogsByHistoricalAuthorAndRetainsPlanningFacts() {
        ProjectSummary project = new ProjectSummary(
                42L, "Portal", "ACTIVE", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        TaskWorkLogView first = workLog(101L, 7L, "repeatable description", 45);
        TaskWorkLogView second = workLog(102L, 7L, "repeatable description", 45);
        TaskDailyReportView task = new TaskDailyReportView(
                501L,
                42L,
                8L,
                "Prepare handover",
                TaskStatus.IN_PROGRESS,
                480,
                150L,
                TaskVarianceState.PENDING,
                null,
                ASSIGNED_AT,
                ASSIGNED_AT,
                null,
                List.of(first, second),
                new TaskRemainingEffortForecastSummary(120, 90L, ASSIGNED_AT));
        given(projects.authenticatedActor("mentor@example.test"))
                .willReturn(new ProjectActorView(2L, "MENTOR"));
        given(projects.listAllVisibleForReport(2L)).willReturn(List.of(project));
        given(projects.members(2L, 42L)).willReturn(List.of(
                new ProjectMemberView(
                        7L, 3L, "Mai Intern", Instant.parse("2026-07-01T00:00:00Z"),
                        Instant.parse("2026-08-10T00:00:00Z"), false, 2L, 2L),
                new ProjectMemberView(
                        8L, 4L, "Nhi Intern", ASSIGNED_AT, null, false, 2L, null)));
        given(taskQueries.dailyReport(42L, REPORT_DATE)).willReturn(List.of(task));
        given(attendance.currentBusinessDate()).willReturn(REPORT_DATE);
        given(attendance.reportDateContext(REPORT_DATE)).willReturn(
                new AttendanceReportDateContext(
                        REPORT_DATE, true, false, 1L, LocalDate.of(1970, 1, 1),
                        ZoneId.of("Asia/Ho_Chi_Minh")));

        var view = reports.build("mentor@example.test", null, REPORT_DATE);

        assertThat(view.reportDate()).isEqualTo(REPORT_DATE);
        assertThat(view.dayContext().configuredWorkday()).isTrue();
        assertThat(view.projects()).hasSize(1);
        assertThat(view.overallTotalMinutes()).isEqualTo(90L);

        var reportProject = view.projects().getFirst();
        assertThat(reportProject.projectId()).isEqualTo(42L);
        assertThat(reportProject.totalMinutes()).isEqualTo(90L);
        assertThat(reportProject.members()).hasSize(1);

        var author = reportProject.members().getFirst();
        assertThat(author.membershipId()).isEqualTo(7L);
        assertThat(author.displayName()).isEqualTo("Mai Intern");
        assertThat(author.totalMinutes()).isEqualTo(90L);
        assertThat(author.tasks()).singleElement().satisfies((DailyProjectWorkReportTask reportTask) -> {
            assertThat(reportTask.taskId()).isEqualTo(501L);
            assertThat(reportTask.currentAssigneeMembershipId()).isEqualTo(8L);
            assertThat(reportTask.status()).isEqualTo(TaskStatus.IN_PROGRESS);
            assertThat(reportTask.selectedDateMinutes()).isEqualTo(90L);
            assertThat(reportTask.lifetimeActualMinutes()).isEqualTo(150L);
            assertThat(reportTask.estimatedMinutes()).isEqualTo(480);
            assertThat(reportTask.varianceState()).isEqualTo(TaskVarianceState.PENDING);
            assertThat(reportTask.latestForecast().remainingMinutes()).isEqualTo(120);
            assertThat(reportTask.latestForecast().forecastTotalMinutes()).isEqualTo(210L);
            assertThat(reportTask.logs()).extracting(log -> log.description())
                    .containsExactly("repeatable description", "repeatable description");
        });
    }

    @Test
    void mentorAllProjectsOmitsEmptyProjectButKeepsGlobalDayOffContextAndLogs() {
        ProjectSummary withWork = project(42L, "Portal");
        ProjectSummary withoutWork = project(43L, "Empty");
        TaskDailyReportView task = new TaskDailyReportView(
                501L, 42L, 8L, "Prepare handover", TaskStatus.DONE, null, 90L,
                TaskVarianceState.NOT_ESTIMATED, null, ASSIGNED_AT, ASSIGNED_AT, null,
                List.of(workLog(101L, 7L, "worked on day off", 90)), null);
        given(projects.authenticatedActor("mentor@example.test"))
                .willReturn(new ProjectActorView(1L, "MENTOR"));
        given(projects.listAllVisibleForReport(1L)).willReturn(List.of(withWork, withoutWork));
        given(projects.members(1L, 42L)).willReturn(List.of(member(7L, "Mai Intern")));
        given(projects.members(1L, 43L))
                .willThrow(new ProjectRuleViolationException("Project has no current Leader"));
        given(taskQueries.dailyReport(42L, REPORT_DATE)).willReturn(List.of(task));
        given(taskQueries.dailyReport(43L, REPORT_DATE)).willReturn(List.of());
        given(attendance.currentBusinessDate()).willReturn(REPORT_DATE);
        given(attendance.reportDateContext(REPORT_DATE)).willReturn(
                new AttendanceReportDateContext(
                        REPORT_DATE, false, true, 2L, LocalDate.of(2026, 1, 1),
                        ZoneId.of("Asia/Ho_Chi_Minh")));

        var view = reports.build("mentor@example.test", null, REPORT_DATE);

        assertThat(view.projects()).extracting(reportProject -> reportProject.projectId())
                .containsExactly(42L);
        assertThat(view.overallTotalMinutes()).isEqualTo(90L);
        assertThat(view.dayContext().label()).isEqualTo("Global day off");
        verify(taskQueries).dailyReport(42L, REPORT_DATE);
        verify(taskQueries).dailyReport(43L, REPORT_DATE);
        verify(projects, never()).members(1L, 43L);
    }

    @Test
    void selectedAuthorizedEmptyProjectHasAnExplicitEmptyDataset() {
        ProjectSummary project = project(42L, "Portal");
        given(projects.authenticatedActor("mentor@example.test"))
                .willReturn(new ProjectActorView(2L, "MENTOR"));
        given(projects.listAllVisibleForReport(2L)).willReturn(List.of(project));
        given(projects.members(2L, 42L))
                .willThrow(new ProjectRuleViolationException("Project has no current Leader"));
        given(taskQueries.dailyReport(42L, REPORT_DATE)).willReturn(List.of());
        given(attendance.currentBusinessDate()).willReturn(REPORT_DATE);
        given(attendance.reportDateContext(REPORT_DATE)).willReturn(
                new AttendanceReportDateContext(
                        REPORT_DATE, true, false, 1L, LocalDate.of(1970, 1, 1),
                        ZoneId.of("Asia/Ho_Chi_Minh")));

        var view = reports.build("mentor@example.test", 42L, REPORT_DATE);

        assertThat(view.selectedProject()).isTrue();
        assertThat(view.selectedProjectName()).isEqualTo("Portal");
        assertThat(view.projects()).hasSize(1);
        assertThat(view.projects().getFirst().members()).isEmpty();
        assertThat(view.hasRows()).isFalse();
        assertThat(view.emptyTitle()).contains("Project");
        verify(projects, never()).members(2L, 42L);
    }

    @Test
    void deniesLeaderAndUnauthorizedProjectBeforeTaskDatasetConstruction() {
        given(projects.authenticatedActor("leader@example.test"))
                .willReturn(new ProjectActorView(3L, "INTERN"));
        assertThatThrownBy(() -> reports.build("leader@example.test", null, REPORT_DATE))
                .isInstanceOf(com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException.class);
        verify(projects, never()).listAllVisibleForReport(3L);
        verifyNoTaskReads();

        ProjectSummary owned = project(42L, "Portal");
        given(projects.authenticatedActor("mentor@example.test"))
                .willReturn(new ProjectActorView(2L, "MENTOR"));
        given(projects.listAllVisibleForReport(2L)).willReturn(List.of(owned));
        assertThatThrownBy(() -> reports.build("mentor@example.test", 999L, REPORT_DATE))
                .isInstanceOf(com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException.class);
        verifyNoTaskReads();
    }

    @Test
    void rejectsFutureDateBeforeAttendanceContextOrTaskRead() {
        given(projects.authenticatedActor("mentor@example.test"))
                .willReturn(new ProjectActorView(2L, "MENTOR"));
        given(projects.listAllVisibleForReport(2L)).willReturn(List.of());
        given(attendance.currentBusinessDate()).willReturn(REPORT_DATE);

        assertThatThrownBy(() -> reports.build(
                "mentor@example.test", null, REPORT_DATE.plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Report date must not be in the future");
        verify(attendance, never()).reportDateContext(REPORT_DATE.plusDays(1));
        verifyNoTaskReads();
    }

    @Test
    void acceptsPastDateOutsideProjectIntervalAndPolicyNonWorkdayWithoutFilteringLogs() {
        LocalDate pastDate = LocalDate.of(2025, 12, 31);
        ProjectSummary project = project(42L, "Portal");
        TaskDailyReportView task = new TaskDailyReportView(
                501L, 42L, 8L, "Historical work", TaskStatus.IN_PROGRESS, null, 30L,
                TaskVarianceState.NOT_ESTIMATED, null, ASSIGNED_AT, ASSIGNED_AT, null,
                List.of(workLog(101L, 7L, "year-end work", pastDate, 30)), null);
        given(projects.authenticatedActor("mentor@example.test"))
                .willReturn(new ProjectActorView(2L, "MENTOR"));
        given(projects.listAllVisibleForReport(2L)).willReturn(List.of(project));
        given(projects.members(2L, 42L)).willReturn(List.of(member(7L, "Mai Intern")));
        given(taskQueries.dailyReport(42L, pastDate)).willReturn(List.of(task));
        given(attendance.currentBusinessDate()).willReturn(REPORT_DATE);
        given(attendance.reportDateContext(pastDate)).willReturn(
                new AttendanceReportDateContext(
                        pastDate, false, false, 3L, LocalDate.of(2025, 1, 1),
                        ZoneId.of("Asia/Ho_Chi_Minh")));

        var view = reports.build("mentor@example.test", 42L, pastDate);

        assertThat(view.reportDate()).isEqualTo(pastDate);
        assertThat(view.dayContext().label()).isEqualTo("Policy non-workday");
        assertThat(view.projects()).hasSize(1);
        assertThat(view.overallTotalMinutes()).isEqualTo(30L);
    }

    @Test
    void defaultsMissingReportDateToAttendanceBusinessDate() {
        given(projects.authenticatedActor("mentor@example.test"))
                .willReturn(new ProjectActorView(2L, "MENTOR"));
        given(projects.listAllVisibleForReport(2L)).willReturn(List.of());
        given(attendance.currentBusinessDate()).willReturn(REPORT_DATE);
        given(attendance.reportDateContext(REPORT_DATE)).willReturn(
                new AttendanceReportDateContext(
                        REPORT_DATE, true, false, 1L, LocalDate.of(1970, 1, 1),
                        ZoneId.of("Asia/Ho_Chi_Minh")));

        var view = reports.build("mentor@example.test", null, null);

        assertThat(view.reportDate()).isEqualTo(REPORT_DATE);
        assertThat(view.dayContext().policyZoneId().getId()).isEqualTo("Asia/Ho_Chi_Minh");
    }

    private void verifyNoTaskReads() {
        verify(taskQueries, never()).dailyReport(42L, REPORT_DATE);
        verify(taskQueries, never()).dailyReport(999L, REPORT_DATE);
    }

    private static ProjectSummary project(long id, String name) {
        return new ProjectSummary(
                id, name, "ACTIVE", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
    }

    private static ProjectMemberView member(long membershipId, String displayName) {
        return new ProjectMemberView(
                membershipId, membershipId + 100L, displayName, ASSIGNED_AT, null, false, 2L, null);
    }

    private static TaskWorkLogView workLog(long id, long membershipId, String note, int minutes) {
        return workLog(id, membershipId, note, REPORT_DATE, minutes);
    }

    private static TaskWorkLogView workLog(
            long id, long membershipId, String note, LocalDate date, int minutes) {
        Instant createdAt = Instant.parse("2026-08-20T08:00:00Z");
        return new TaskWorkLogView(
                id, 42L, 501L, membershipId, date, minutes, note, createdAt, createdAt);
    }
}
