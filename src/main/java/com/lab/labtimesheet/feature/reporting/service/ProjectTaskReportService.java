package com.lab.labtimesheet.feature.reporting.service;

import com.lab.labtimesheet.feature.project.model.dto.ProjectActorView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectDetail;
import com.lab.labtimesheet.feature.project.model.dto.ProjectSummary;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskContext;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskMemberView;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportFilter;
import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportRow;
import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportView;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.dto.TaskListView;
import com.lab.labtimesheet.feature.task.model.dto.TaskView;
import com.lab.labtimesheet.feature.task.service.TaskService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Composes Project and Task public DTO boundaries into one authorization-safe HTML report dataset.
 *
 * <p>The Project service determines visible Projects and current member options; the Task service
 * determines the current readable Task set. Reporting applies only requested status/member/date
 * filters and never imports a foreign repository or entity.</p>
 */
@Service
@RequiredArgsConstructor
public class ProjectTaskReportService {

    private final ProjectQueryService projects;
    private final TaskService tasks;

    /**
     * Builds a filtered current Task dataset for the authenticated actor.
     *
     * @param actorEmail authenticated account email
     * @param projectId optional visible Project selection
     * @param memberMembershipId optional current membership filter
     * @param status optional fixed Task status filter
     * @param dueFrom optional inclusive due-date lower bound
     * @param dueTo optional inclusive due-date upper bound
     * @return authorized report view and redisplay options
     * @throws IllegalArgumentException when due-date bounds are reversed
     */
    @Transactional(readOnly = true)
    public ProjectTaskReportView build(
            String actorEmail,
            Long projectId,
            Long memberMembershipId,
            TaskStatus status,
            LocalDate dueFrom,
            LocalDate dueTo) {
        if (dueFrom != null && dueTo != null && dueFrom.isAfter(dueTo)) {
            throw new IllegalArgumentException("dueFrom must not be after dueTo");
        }
        ProjectActorView actor = projects.authenticatedActor(actorEmail);
        List<ProjectSummary> projectOptions = projects.listVisible(actor.userId());
        ProjectTaskReportFilter filter = new ProjectTaskReportFilter(
                projectId, memberMembershipId, status, dueFrom, dueTo);
        if (projectId == null) {
            return new ProjectTaskReportView(filter, projectOptions, List.of(), List.of(), 0, 0, "N/A");
        }

        ProjectDetail project = projects.detail(actor.userId(), projectId);
        ProjectTaskContext context = projects.taskContext(actor.userId(), projectId);
        TaskListView taskList = tasks.list(actorEmail, projectId);
        List<ProjectTaskReportRow> rows = taskList.tasks().stream()
                .filter(task -> matches(task, filter))
                .map(task -> row(project, task))
                .toList();
        long done = rows.stream().filter(task -> task.status() == TaskStatus.DONE).count();
        return new ProjectTaskReportView(
                filter,
                projectOptions,
                context.activeMembers(),
                rows,
                rows.size(),
                done,
                ProjectTaskReportView.percentage(rows.size(), done));
    }

    private static boolean matches(TaskView task, ProjectTaskReportFilter filter) {
        if (filter.memberMembershipId() != null
                && task.assigneeMembershipId() != filter.memberMembershipId()) {
            return false;
        }
        if (filter.status() != null && task.status() != filter.status()) {
            return false;
        }
        if (filter.dueFrom() != null
                && (task.dueDate() == null || task.dueDate().isBefore(filter.dueFrom()))) {
            return false;
        }
        return filter.dueTo() == null
                || (task.dueDate() != null && !task.dueDate().isAfter(filter.dueTo()));
    }

    private static ProjectTaskReportRow row(ProjectDetail project, TaskView task) {
        return new ProjectTaskReportRow(
                project.id(),
                project.name(),
                task.id(),
                task.title(),
                task.assigneeName(),
                task.assigneeMembershipId(),
                task.status(),
                task.dueDate(),
                task.creatorMembershipId(),
                task.assignerMembershipId(),
                task.assignedAt(),
                task.createdAt());
    }
}
