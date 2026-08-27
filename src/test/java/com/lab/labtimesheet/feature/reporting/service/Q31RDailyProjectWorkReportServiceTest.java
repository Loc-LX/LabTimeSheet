package com.lab.labtimesheet.feature.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceReportDateContext;
import com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService;
import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.model.dto.ProjectActorView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMemberView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectSummary;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.TaskVarianceState;
import com.lab.labtimesheet.feature.task.model.dto.TaskDailyReportView;
import com.lab.labtimesheet.feature.task.model.dto.TaskWorkLogView;
import com.lab.labtimesheet.feature.task.service.TaskQueryService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Public Q31-R authorization boundary for current-Leader Daily reporting. */
class Q31RDailyProjectWorkReportServiceTest {

    private static final LocalDate REPORT_DATE = LocalDate.of(2026, 8, 20);

    private final ProjectQueryService projects = mock(ProjectQueryService.class);
    private final TaskQueryService taskQueries = mock(TaskQueryService.class);
    private final AttendanceApplicationService attendance = mock(AttendanceApplicationService.class);
    private DailyProjectWorkReportService reports;

    @BeforeEach
    void setUp() {
        reports = new DailyProjectWorkReportService(projects, taskQueries, attendance);
    }

    @Test
    void currentLeaderGetsOneLockedProjectReportWithMandatoryProjectSelection() {
        ProjectSummary project = project(42L, "Portal", "ACTIVE");
        given(projects.authenticatedActor("leader@example.test"))
                .willReturn(new ProjectActorView(3L, "INTERN"));
        given(projects.currentLeaderProjectForDailyReport(3L, 42L)).willReturn(project);
        given(taskQueries.dailyReport(42L, REPORT_DATE)).willReturn(List.of());
        given(attendance.currentBusinessDate()).willReturn(REPORT_DATE);
        given(attendance.reportDateContext(REPORT_DATE)).willReturn(dayContext());

        var view = reports.build("leader@example.test", 42L, REPORT_DATE);

        assertThat(view.lockedSingleProject()).isTrue();
        assertThat(view.selectedProjectId()).isEqualTo(42L);
        assertThat(view.selectedProjectName()).isEqualTo("Portal");
        assertThat(view.projectOptions()).containsExactly(project);
        assertThat(view.projects()).singleElement()
                .satisfies(row -> assertThat(row.projectId()).isEqualTo(42L));
        verify(projects).currentLeaderProjectForDailyReport(3L, 42L);
    }

    @Test
    void currentLeaderReportSupportsPlannedProjectWithNoRetainedWork() {
        ProjectSummary project = project(42L, "Planned Portal", "PLANNED");
        given(projects.authenticatedActor("leader@example.test"))
                .willReturn(new ProjectActorView(3L, "INTERN"));
        given(projects.currentLeaderProjectForDailyReport(3L, 42L)).willReturn(project);
        given(taskQueries.dailyReport(42L, REPORT_DATE)).willReturn(List.of());
        given(attendance.currentBusinessDate()).willReturn(REPORT_DATE);
        given(attendance.reportDateContext(REPORT_DATE)).willReturn(dayContext());

        var view = reports.build("leader@example.test", 42L, REPORT_DATE);

        assertThat(view.lockedSingleProject()).isTrue();
        assertThat(view.projects()).singleElement().satisfies(row -> {
            assertThat(row.projectId()).isEqualTo(42L);
            assertThat(row.members()).isEmpty();
        });
        assertThat(view.hasRows()).isFalse();
    }

    @Test
    void currentLeaderReportIncludesRetainedWorkFromBeforeCurrentLeadershipTerm() {
        LocalDate historicalDate = LocalDate.of(2025, 12, 31);
        Instant loggedAt = Instant.parse("2026-01-01T08:00:00Z");
        ProjectSummary project = project(42L, "Historical Portal", "ACTIVE");
        TaskWorkLogView log = new TaskWorkLogView(
                101L, 42L, 501L, 7L, historicalDate, 30,
                "work before current Leader", loggedAt, loggedAt);
        TaskDailyReportView task = new TaskDailyReportView(
                501L, 42L, 8L, "Retained history", TaskStatus.IN_PROGRESS,
                null, 30L, TaskVarianceState.NOT_ESTIMATED, null,
                loggedAt, loggedAt, null, List.of(log), null);
        given(projects.authenticatedActor("leader@example.test"))
                .willReturn(new ProjectActorView(3L, "INTERN"));
        given(projects.currentLeaderProjectForDailyReport(3L, 42L)).willReturn(project);
        given(taskQueries.dailyReport(42L, historicalDate)).willReturn(List.of(task));
        given(projects.members(3L, 42L)).willReturn(List.of(new ProjectMemberView(
                7L, 4L, "Historical Author", loggedAt, null, false, 3L, null)));
        given(attendance.currentBusinessDate()).willReturn(REPORT_DATE);
        given(attendance.reportDateContext(historicalDate)).willReturn(
                new AttendanceReportDateContext(
                        historicalDate, false, false, 1L, LocalDate.of(1970, 1, 1),
                        ZoneId.of("Asia/Ho_Chi_Minh")));

        var view = reports.build("leader@example.test", 42L, historicalDate);

        assertThat(view.reportDate()).isEqualTo(historicalDate);
        assertThat(view.overallTotalMinutes()).isEqualTo(30L);
        assertThat(view.projects()).singleElement().satisfies(row ->
                assertThat(row.members().getFirst().tasks().getFirst().logs().getFirst().description())
                        .isEqualTo("work before current Leader"));
        verify(taskQueries).dailyReport(42L, historicalDate);
    }

    @Test
    void missingLeaderProjectIdIsDeniedBeforeAttendanceOrTaskReads() {
        given(projects.authenticatedActor("leader@example.test"))
                .willReturn(new ProjectActorView(3L, "INTERN"));

        assertThatThrownBy(() -> reports.build("leader@example.test", null, REPORT_DATE))
                .isInstanceOf(ProjectAccessDeniedException.class);

        verify(projects, never()).currentLeaderProjectForDailyReport(3L, 42L);
        verifyNoDownstreamReads();
    }

    @Test
    void adminIsDeniedBeforeProjectReportReadsOrAttendanceContext() {
        given(projects.authenticatedActor("admin@example.test"))
                .willReturn(new ProjectActorView(1L, "ADMIN"));

        assertThatThrownBy(() -> reports.build("admin@example.test", null, REPORT_DATE))
                .isInstanceOf(ProjectAccessDeniedException.class);

        verify(projects, never()).listAllVisibleForReport(1L);
        verifyNoDownstreamReads();
    }

    @Test
    void producerDenialForFormerLeaderNeverReachesAttendanceOrTasks() {
        given(projects.authenticatedActor("former-leader@example.test"))
                .willReturn(new ProjectActorView(3L, "INTERN"));
        given(projects.currentLeaderProjectForDailyReport(3L, 42L))
                .willThrow(new ProjectAccessDeniedException());

        assertThatThrownBy(() -> reports.build("former-leader@example.test", 42L, REPORT_DATE))
                .isInstanceOf(ProjectAccessDeniedException.class);

        verify(projects).currentLeaderProjectForDailyReport(3L, 42L);
        verifyNoDownstreamReads();
    }

    @Test
    void ordinaryCompletedAndGuessedLeaderProjectVariantsAreDeniedBeforeDownstreamReads() {
        given(projects.authenticatedActor("ordinary-intern@example.test"))
                .willReturn(new ProjectActorView(4L, "INTERN"));
        given(projects.authenticatedActor("other-project-leader@example.test"))
                .willReturn(new ProjectActorView(7L, "INTERN"));
        given(projects.authenticatedActor("completed-project-leader@example.test"))
                .willReturn(new ProjectActorView(5L, "INTERN"));
        given(projects.authenticatedActor("guessed-project-leader@example.test"))
                .willReturn(new ProjectActorView(6L, "INTERN"));
        given(projects.currentLeaderProjectForDailyReport(4L, 42L))
                .willThrow(new ProjectAccessDeniedException());
        given(projects.currentLeaderProjectForDailyReport(7L, 42L))
                .willThrow(new ProjectAccessDeniedException());
        given(projects.currentLeaderProjectForDailyReport(5L, 42L))
                .willThrow(new ProjectAccessDeniedException());
        given(projects.currentLeaderProjectForDailyReport(6L, 999L))
                .willThrow(new ProjectAccessDeniedException());

        assertThatThrownBy(() -> reports.build("ordinary-intern@example.test", 42L, REPORT_DATE))
                .isInstanceOf(ProjectAccessDeniedException.class);
        assertThatThrownBy(() -> reports.build(
                "other-project-leader@example.test", 42L, REPORT_DATE))
                .isInstanceOf(ProjectAccessDeniedException.class);
        assertThatThrownBy(() -> reports.build(
                "completed-project-leader@example.test", 42L, REPORT_DATE))
                .isInstanceOf(ProjectAccessDeniedException.class);
        assertThatThrownBy(() -> reports.build(
                "guessed-project-leader@example.test", 999L, REPORT_DATE))
                .isInstanceOf(ProjectAccessDeniedException.class);

        verify(projects).currentLeaderProjectForDailyReport(4L, 42L);
        verify(projects).currentLeaderProjectForDailyReport(7L, 42L);
        verify(projects).currentLeaderProjectForDailyReport(5L, 42L);
        verify(projects).currentLeaderProjectForDailyReport(6L, 999L);
        verifyNoDownstreamReads();
    }

    private void verifyNoDownstreamReads() {
        verify(attendance, never()).currentBusinessDate();
        verify(attendance, never()).reportDateContext(REPORT_DATE);
        verify(taskQueries, never()).dailyReport(42L, REPORT_DATE);
    }

    private static ProjectSummary project(long id, String name, String status) {
        return new ProjectSummary(
                id, name, status, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
    }

    private static AttendanceReportDateContext dayContext() {
        return new AttendanceReportDateContext(
                REPORT_DATE, true, false, 1L, LocalDate.of(1970, 1, 1),
                ZoneId.of("Asia/Ho_Chi_Minh"));
    }
}
