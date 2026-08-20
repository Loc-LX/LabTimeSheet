package com.lab.labtimesheet.feature.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.lab.labtimesheet.feature.project.model.dto.ProjectActorView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectDetail;
import com.lab.labtimesheet.feature.project.model.dto.ProjectSummary;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskContext;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskMemberView;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.task.model.TaskProgress;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.dto.TaskListView;
import com.lab.labtimesheet.feature.task.model.dto.TaskView;
import com.lab.labtimesheet.feature.task.service.TaskService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit contract for Project/Task report filtering over public producer DTOs. */
class ProjectTaskReportServiceTest {

    private final ProjectQueryService projects = mock(ProjectQueryService.class);
    private final TaskService tasks = mock(TaskService.class);
    private ProjectTaskReportService reports;

    @BeforeEach
    void setUp() {
        reports = new ProjectTaskReportService(projects, tasks);
    }

    @Test
    void filtersAuthorizedCurrentTasksByMemberStatusAndDueDate() {
        var project = new ProjectSummary(
                42L, "Portal", "ACTIVE", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        var detail = new ProjectDetail(
                42L, "Portal", "Description", "ACTIVE", project.startDate(), project.endDate(),
                "Mentor", "Leader", false);
        var member = new ProjectTaskMemberView(7L, 3L, "Mai Intern");
        var context = new ProjectTaskContext(
                42L, 2L, "ACTIVE", project.startDate(), project.endDate(), 7L, List.of(member));
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

    private static TaskView task(
            long id, String title, long membershipId, TaskStatus status, LocalDate dueDate) {
        Instant createdAt = Instant.parse("2026-08-01T00:00:00Z");
        return new TaskView(
                id, 42L, membershipId, "Mai Intern", title, null, status, dueDate,
                membershipId, membershipId, createdAt, createdAt);
    }
}
