package com.lab.labtimesheet.feature.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.lab.labtimesheet.feature.project.model.dto.ProjectActorView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectSummary;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskContext;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskMemberView;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.entity.Task;
import com.lab.labtimesheet.feature.task.repository.TaskRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class TaskDashboardServiceTest {

    @Mock
    private TaskRepository tasks;

    @Mock
    private ProjectQueryService projects;

    @InjectMocks
    private TaskDashboardService dashboardService;

    @Test
    void mentorDashboardCountsBlockedTasksOnlyInOwnedActiveProjects() {
        given(projects.authenticatedActor("mentor@example.test"))
                .willReturn(new ProjectActorView(3L, "MENTOR"));
        given(projects.listVisible(3L)).willReturn(List.of(
                summary(10L, "Active", "ACTIVE"),
                summary(11L, "Planned", "PLANNED")));
        given(tasks.countByProjectIdInAndStatusAndDeletedAtIsNull(List.of(10L), TaskStatus.BLOCKED))
                .willReturn(4L);

        var dashboard = dashboardService.dashboard("mentor@example.test");

        assertThat(dashboard.blockedTaskCount()).isEqualTo(4L);
        assertThat(dashboard.assignedTaskCount()).isZero();
        assertThat(dashboard.priorityTasks()).isEmpty();
    }

    @Test
    void internDashboardExcludesFormerMembershipsAndReturnsFiveDueDatePriorities() {
        given(projects.authenticatedActor("intern@example.test"))
                .willReturn(new ProjectActorView(5L, "INTERN"));
        given(projects.listVisible(5L)).willReturn(List.of(
                summary(10L, "Current", "ACTIVE"),
                summary(11L, "Former", "ACTIVE"),
                summary(12L, "Completed", "COMPLETED")));
        given(projects.taskContext(5L, 10L)).willReturn(context(
                10L, List.of(new ProjectTaskMemberView(
                        70L, 5L, "Intern", Instant.parse("2026-08-15T00:00:00Z")))));
        given(projects.taskContext(5L, 11L)).willReturn(context(11L, List.of()));
        given(tasks.countByProjectIdInAndAssigneeMembershipIdInAndDeletedAtIsNull(
                        List.of(10L), List.of(70L)))
                .willReturn(6L);
        var priority = new Task(
                10L,
                70L,
                "Due first",
                null,
                LocalDate.of(2026, 8, 16),
                70L,
                Instant.parse("2026-08-15T00:00:00Z"));
        given(tasks.findPriorityTasks(
                        org.mockito.ArgumentMatchers.eq(List.of(10L)),
                        org.mockito.ArgumentMatchers.eq(List.of(70L)),
                        org.mockito.ArgumentMatchers.any(Pageable.class)))
                .willReturn(List.of(priority));

        var dashboard = dashboardService.dashboard("intern@example.test");

        assertThat(dashboard.blockedTaskCount()).isZero();
        assertThat(dashboard.assignedTaskCount()).isEqualTo(6L);
        assertThat(dashboard.priorityTasks()).singleElement().satisfies(task -> {
            assertThat(task.title()).isEqualTo("Due first");
            assertThat(task.projectName()).isEqualTo("Current");
            assertThat(task.status()).isEqualTo(TaskStatus.TODO);
            assertThat(task.dueDate()).isEqualTo(LocalDate.of(2026, 8, 16));
        });
    }

    private static ProjectSummary summary(long id, String name, String status) {
        return new ProjectSummary(
                id, name, status, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
    }

    private static ProjectTaskContext context(long id, List<ProjectTaskMemberView> members) {
        return new ProjectTaskContext(
                id,
                3L,
                "ACTIVE",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                null,
                members,
                java.util.Set.of());
    }
}
