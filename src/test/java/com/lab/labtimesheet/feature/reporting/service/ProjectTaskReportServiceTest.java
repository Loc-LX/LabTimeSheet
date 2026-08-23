package com.lab.labtimesheet.feature.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.lab.labtimesheet.feature.project.model.dto.ProjectActorView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectDetail;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMemberView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectSummary;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskContext;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskMemberView;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportMemberHours;
import com.lab.labtimesheet.feature.task.model.TaskProgress;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.dto.TaskHistoryView;
import com.lab.labtimesheet.feature.task.model.dto.TaskListView;
import com.lab.labtimesheet.feature.task.model.dto.TaskView;
import com.lab.labtimesheet.feature.task.model.dto.TaskWorkLogView;
import com.lab.labtimesheet.feature.task.service.TaskQueryService;
import com.lab.labtimesheet.feature.task.service.TaskService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit contract for Project/Task report filtering over public producer DTOs. */
class ProjectTaskReportServiceTest {

    private final ProjectQueryService projects = mock(ProjectQueryService.class);
    private final TaskService tasks = mock(TaskService.class);
    private final TaskQueryService taskQueries = mock(TaskQueryService.class);
    private ProjectTaskReportService reports;

    @BeforeEach
    void setUp() {
        reports = new ProjectTaskReportService(projects, tasks, taskQueries);
    }

    @Test
    void filtersAuthorizedCurrentTasksByMemberStatusAndDueDate() {
        var project = new ProjectSummary(
                42L, "Portal", "ACTIVE", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        var detail = new ProjectDetail(
                42L, "Portal", "Description", "ACTIVE", project.startDate(), project.endDate(),
                "Mentor", "Leader", false);
        var member = new ProjectTaskMemberView(
                7L, 3L, "Mai Intern", Instant.parse("2026-08-01T00:00:00Z"));
        var context = new ProjectTaskContext(
                42L, 2L, "ACTIVE", project.startDate(), project.endDate(), 7L, List.of(member), Set.of());
        var done = task(1L, "Finish report", 7L, TaskStatus.DONE, LocalDate.of(2026, 8, 15));
        var blocked = task(2L, "Investigate issue", 7L, TaskStatus.BLOCKED, LocalDate.of(2026, 8, 25));
        var undated = task(3L, "Draft notes", 7L, TaskStatus.TODO, null);
        given(projects.authenticatedActor("mentor@example.test"))
                .willReturn(new ProjectActorView(2L, "MENTOR"));
        given(projects.listVisible(2L)).willReturn(List.of(project));
        given(projects.detail(2L, 42L)).willReturn(detail);
        given(projects.taskContext(2L, 42L)).willReturn(context);
        given(tasks.list("mentor@example.test", 42L))
                .willReturn(new TaskListView(
                        List.of(done, blocked, undated),
                        TaskProgress.from(List.of(done.status(), blocked.status(), undated.status())),
                        false));

        var view = reports.build(
                "mentor@example.test",
                42L,
                7L,
                TaskStatus.DONE,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 20));

        assertThat(view.rows()).hasSize(1);
        assertThat(view.rows().getFirst().title()).isEqualTo("Finish report");
        assertThat(view.totalTasks()).isEqualTo(1);
        assertThat(view.doneTasks()).isEqualTo(1);
        assertThat(view.completionRate()).isEqualTo("100.0%");
        assertThat(view.memberOptions()).containsExactly(member);
    }

    @Test
    void keepsProjectPickerEmptyDatasetAuthorizedWithoutCallingTaskService() {
        given(projects.authenticatedActor("mentor@example.test"))
                .willReturn(new ProjectActorView(2L, "MENTOR"));
        given(projects.listVisible(2L)).willReturn(List.of());

        var view = reports.build("mentor@example.test", null, null, null, null, null);

        assertThat(view.rows()).isEmpty();
        assertThat(view.completionRate()).isEqualTo("N/A");
        assertThat(view.projectOptions()).isEmpty();
    }

    @Test
    void includesRetainedWorkMinutesAndMemberHoursForAnAuthorizedMentor() {
        var project = new ProjectSummary(
                42L, "Portal", "ACTIVE", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        var detail = new ProjectDetail(
                42L, "Portal", "Description", "ACTIVE", project.startDate(), project.endDate(),
                "Mentor", "Leader", false);
        var leader = new ProjectTaskMemberView(
                7L, 3L, "Mai Intern", Instant.parse("2026-08-01T00:00:00Z"));
        var context = new ProjectTaskContext(
                42L, 2L, "ACTIVE", project.startDate(), project.endDate(), 7L, List.of(leader), Set.of());
        var done = task(1L, "Finish report", 7L, TaskStatus.DONE, LocalDate.of(2026, 8, 15));
        var blocked = task(2L, "Investigate issue", 7L, TaskStatus.BLOCKED, LocalDate.of(2026, 8, 25));
        given(projects.authenticatedActor("mentor@example.test"))
                .willReturn(new ProjectActorView(2L, "MENTOR"));
        given(projects.listVisible(2L)).willReturn(List.of(project));
        given(projects.detail(2L, 42L)).willReturn(detail);
        given(projects.taskContext(2L, 42L)).willReturn(context);
        given(projects.members(2L, 42L)).willReturn(List.of(
                new ProjectMemberView(
                        7L, 3L, "Mai Intern", Instant.parse("2026-08-01T00:00:00Z"), null, true, 2L, null),
                new ProjectMemberView(
                        8L, 4L, "Nhi Intern", Instant.parse("2026-08-02T00:00:00Z"), null, false, 2L, null)));
        given(tasks.list("mentor@example.test", 42L))
                .willReturn(new TaskListView(
                        List.of(done, blocked),
                        TaskProgress.from(List.of(done.status(), blocked.status())),
                        false));
        given(taskQueries.history(42L)).willReturn(List.of(
                history(done, List.of(
                        workLog(101L, 1L, 7L, LocalDate.of(2026, 8, 10), 60),
                        workLog(102L, 1L, 7L, LocalDate.of(2026, 8, 21), 15))),
                history(blocked, List.of(
                        workLog(103L, 2L, 8L, LocalDate.of(2026, 8, 12), 30),
                        workLog(104L, 2L, 7L, LocalDate.of(2026, 8, 20), 45)))));

        var view = reports.build(
                "mentor@example.test",
                42L,
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 20));

        assertThat(view.rows()).extracting(row -> row.loggedMinutes()).containsExactly(60L, 75L);
        assertThat(view.totalLoggedMinutes()).isEqualTo(135L);
        assertThat(view.statusCounts()).containsEntry(TaskStatus.DONE, 1L)
                .containsEntry(TaskStatus.BLOCKED, 1L);
        assertThat(view.detailedMemberHours()).isTrue();
        assertThat(view.memberHours()).containsExactly(
                new ProjectTaskReportMemberHours(7L, "Mai Intern", 105L),
                new ProjectTaskReportMemberHours(8L, "Nhi Intern", 30L));
    }

    @Test
    void withholdsMemberBreakdownAndGuessedMembershipFilterFromAnOrdinaryIntern() {
        var project = new ProjectSummary(
                42L, "Portal", "ACTIVE", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        var detail = new ProjectDetail(
                42L, "Portal", "Description", "ACTIVE", project.startDate(), project.endDate(),
                "Mentor", "Leader", false);
        var leader = new ProjectTaskMemberView(
                7L, 3L, "Mai Intern", Instant.parse("2026-08-01T00:00:00Z"));
        var context = new ProjectTaskContext(
                42L, 2L, "ACTIVE", project.startDate(), project.endDate(), 7L, List.of(leader), Set.of());
        var task = task(1L, "Finish report", 7L, TaskStatus.DONE, LocalDate.of(2026, 8, 15));
        given(projects.authenticatedActor("intern@example.test"))
                .willReturn(new ProjectActorView(4L, "INTERN"));
        given(projects.listVisible(4L)).willReturn(List.of(project));
        given(projects.detail(4L, 42L)).willReturn(detail);
        given(projects.taskContext(4L, 42L)).willReturn(context);
        given(tasks.list("intern@example.test", 42L))
                .willReturn(new TaskListView(List.of(task), TaskProgress.from(List.of(task.status())), false));

        var view = reports.build("intern@example.test", 42L, 7L, null, null, null);

        assertThat(view.filter().memberMembershipId()).isNull();
        assertThat(view.memberOptions()).isEmpty();
        assertThat(view.detailedMemberHours()).isFalse();
        assertThat(view.memberHours()).isEmpty();
    }

    @Test
    void currentLeaderKeepsMemberFilterAndReceivesMemberBreakdown() {
        var project = new ProjectSummary(
                42L, "Portal", "ACTIVE", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        var detail = new ProjectDetail(
                42L, "Portal", "Description", "ACTIVE", project.startDate(), project.endDate(),
                "Mentor", "Mai Intern", false);
        var leader = new ProjectTaskMemberView(
                7L, 3L, "Mai Intern", Instant.parse("2026-08-01T00:00:00Z"));
        var member = new ProjectTaskMemberView(
                8L, 4L, "Nhi Intern", Instant.parse("2026-08-02T00:00:00Z"));
        var context = new ProjectTaskContext(
                42L, 2L, "ACTIVE", project.startDate(), project.endDate(), 7L,
                List.of(leader, member), Set.of());
        var task = task(1L, "Finish report", 8L, TaskStatus.DONE, LocalDate.of(2026, 8, 15));
        given(projects.authenticatedActor("leader@example.test"))
                .willReturn(new ProjectActorView(3L, "INTERN"));
        given(projects.listVisible(3L)).willReturn(List.of(project));
        given(projects.detail(3L, 42L)).willReturn(detail);
        given(projects.taskContext(3L, 42L)).willReturn(context);
        given(projects.members(3L, 42L)).willReturn(List.of(
                new ProjectMemberView(
                        7L, 3L, "Mai Intern", Instant.parse("2026-08-01T00:00:00Z"), null, true, 2L, null),
                new ProjectMemberView(
                        8L, 4L, "Nhi Intern", Instant.parse("2026-08-02T00:00:00Z"), null, false, 2L, null)));
        given(tasks.list("leader@example.test", 42L))
                .willReturn(new TaskListView(List.of(task), TaskProgress.from(List.of(task.status())), false));

        var view = reports.build("leader@example.test", 42L, 8L, null, null, null);

        assertThat(view.filter().memberMembershipId()).isEqualTo(8L);
        assertThat(view.detailedMemberHours()).isTrue();
        assertThat(view.memberOptions()).containsExactly(leader, member);
        assertThat(view.memberHours()).containsExactly(
                new ProjectTaskReportMemberHours(7L, "Mai Intern", 0L),
                new ProjectTaskReportMemberHours(8L, "Nhi Intern", 0L));
    }

    private static TaskHistoryView history(TaskView task, List<TaskWorkLogView> workLogs) {
        return new TaskHistoryView(
                task.id(), task.projectId(), task.assigneeMembershipId(), task.title(), task.description(),
                task.status(), task.dueDate(), task.creatorMembershipId(), task.assignerMembershipId(),
                task.assignedAt(), task.createdAt(), task.createdAt(), null, null, List.of(), workLogs);
    }

    private static TaskWorkLogView workLog(
            long id, long taskId, long membershipId, LocalDate workDate, int minutes) {
        Instant timestamp = Instant.parse("2026-08-21T00:00:00Z");
        return new TaskWorkLogView(
                id, 42L, taskId, membershipId, workDate, minutes, "retained effort", timestamp, timestamp);
    }

    private static TaskView task(
            long id, String title, long membershipId, TaskStatus status, LocalDate dueDate) {
        Instant createdAt = Instant.parse("2026-08-01T00:00:00Z");
        return new TaskView(
                id, 42L, membershipId, "Mai Intern", title, null, status, dueDate,
                membershipId, membershipId, createdAt, createdAt);
    }
}
