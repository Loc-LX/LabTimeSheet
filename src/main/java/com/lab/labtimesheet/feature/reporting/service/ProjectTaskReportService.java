package com.lab.labtimesheet.feature.reporting.service;

import com.lab.labtimesheet.feature.project.model.dto.ProjectActorView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectDetail;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMemberView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectSummary;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskContext;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskMemberView;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportFilter;
import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportMemberHours;
import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportRow;
import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportView;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.dto.TaskHistoryView;
import com.lab.labtimesheet.feature.task.model.dto.TaskListView;
import com.lab.labtimesheet.feature.task.model.dto.TaskView;
import com.lab.labtimesheet.feature.task.model.dto.TaskWorkLogView;
import com.lab.labtimesheet.feature.task.service.TaskQueryService;
import com.lab.labtimesheet.feature.task.service.TaskService;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
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
    private final TaskQueryService taskQueries;

    /**
     * Builds a current Task dataset using the existing due-date filter contract.
     *
     * @param actorEmail authenticated account email
     * @param projectId optional visible Project selection
     * @param memberMembershipId optional current membership filter
     * @param status optional fixed Task status filter
     * @param dueFrom inclusive due-date lower bound
     * @param dueTo inclusive due-date upper bound
     * @return authorized report view
     */
    @Transactional(readOnly = true)
    public ProjectTaskReportView build(
            String actorEmail,
            Long projectId,
            Long memberMembershipId,
            TaskStatus status,
            LocalDate dueFrom,
            LocalDate dueTo) {
        return build(actorEmail, projectId, memberMembershipId, status, dueFrom, dueTo, null, null);
    }

    /**
     * Builds a filtered current Task dataset and retained work summary for the authenticated actor.
     *
     * <p>Due-date and work-date bounds are independent. A work-date filter keeps a current Task
     * only when it has at least one retained work log in the requested inclusive range, and row
     * minutes include only logs in that range. Visible owning Mentors and current Leaders receive
     * member-hour rows; ordinary members receive aggregate totals and no member filter options
     * even when they guess a membership identifier. Admins have no operational Project/Task-report
     * scope and are rejected before listing Projects or reading Task data.</p>
     *
     * @param actorEmail authenticated account email
     * @param projectId optional visible Project selection
     * @param memberMembershipId optional current membership filter
     * @param status optional fixed Task status filter
     * @param dueFrom inclusive due-date lower bound
     * @param dueTo inclusive due-date upper bound
     * @param workFrom inclusive work-date lower bound
     * @param workTo inclusive work-date upper bound
     * @return authorized report view and redisplay options
     * @throws AccessDeniedException when the persisted actor has no operational report scope
     * @throws IllegalArgumentException when either date range is reversed
     */
    @Transactional(readOnly = true)
    public ProjectTaskReportView build(
            String actorEmail,
            Long projectId,
            Long memberMembershipId,
            TaskStatus status,
            LocalDate dueFrom,
            LocalDate dueTo,
            LocalDate workFrom,
            LocalDate workTo) {
        ProjectActorView actor = projects.authenticatedActor(actorEmail);
        requireReportAccess(actor);
        validateRange(dueFrom, dueTo, "dueFrom", "dueTo");
        validateRange(workFrom, workTo, "workFrom", "workTo");

        List<ProjectSummary> projectOptions = projects.listVisible(actor.userId());
        if (projectId == null) {
            return new ProjectTaskReportView(
                    new ProjectTaskReportFilter(null, null, status, dueFrom, dueTo, workFrom, workTo),
                    projectOptions,
                    List.of(),
                    List.of(),
                    0,
                    0,
                    ProjectTaskReportView.percentage(0, 0),
                    emptyStatusCounts(),
                    0,
                    false,
                    List.of());
        }

        ProjectDetail project = projects.detail(actor.userId(), projectId);
        ProjectTaskContext context = projects.taskContext(actor.userId(), projectId);
        boolean detailedMemberHours = canViewMemberHours(actor, context);
        Long effectiveMemberFilter = detailedMemberHours ? memberMembershipId : null;
        ProjectTaskReportFilter filter = new ProjectTaskReportFilter(
                projectId, effectiveMemberFilter, status, dueFrom, dueTo, workFrom, workTo);
        TaskListView taskList = tasks.list(actorEmail, projectId);
        Map<Long, TaskHistoryView> historyByTask = taskQueries.history(projectId).stream()
                .collect(java.util.stream.Collectors.toMap(TaskHistoryView::id, history -> history));
        List<FilteredTask> filtered = taskList.tasks().stream()
                .filter(task -> matches(task, filter, historyByTask.get(task.id())))
                .map(task -> filteredTask(task, historyByTask.get(task.id()), filter))
                .toList();
        List<ProjectTaskReportRow> rows = filtered.stream()
                .map(task -> row(project, task.task(), task.loggedMinutes()))
                .toList();
        long done = rows.stream().filter(task -> task.status() == TaskStatus.DONE).count();
        Map<TaskStatus, Long> statusCounts = statusCounts(rows);
        List<ProjectTaskReportMemberHours> memberHours = detailedMemberHours
                ? memberHours(actor.userId(), projectId, context, filtered, filter)
                : List.of();
        return new ProjectTaskReportView(
                filter,
                projectOptions,
                detailedMemberHours ? context.activeMembers() : List.of(),
                rows,
                rows.size(),
                done,
                ProjectTaskReportView.percentage(rows.size(), done),
                statusCounts,
                rows.stream().mapToLong(ProjectTaskReportRow::loggedMinutes).sum(),
                detailedMemberHours,
                memberHours);
    }

    private static void validateRange(LocalDate from, LocalDate to, String fromName, String toName) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException(fromName + " must not be after " + toName);
        }
    }

    private static boolean canViewMemberHours(ProjectActorView actor, ProjectTaskContext context) {
        if ("MENTOR".equals(actor.role())) {
            return true;
        }
        return "INTERN".equals(actor.role())
                && context.currentLeaderMembershipId() != null
                && context.activeMembers().stream().anyMatch(member ->
                        member.membershipId() == context.currentLeaderMembershipId()
                                && member.userId() == actor.userId());
    }

    private static void requireReportAccess(ProjectActorView actor) {
        if (actor == null || (!"MENTOR".equals(actor.role()) && !"INTERN".equals(actor.role()))) {
            throw new AccessDeniedException("Admins may not access Project and Task reports");
        }
    }

    private static boolean matches(
            TaskView task, ProjectTaskReportFilter filter, TaskHistoryView history) {
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
        if (filter.dueTo() != null
                && (task.dueDate() == null || task.dueDate().isAfter(filter.dueTo()))) {
            return false;
        }
        return (filter.workFrom() == null && filter.workTo() == null)
                || history != null && history.workLogs().stream().anyMatch(log -> inWorkRange(log, filter));
    }

    private static FilteredTask filteredTask(
            TaskView task, TaskHistoryView history, ProjectTaskReportFilter filter) {
        List<TaskWorkLogView> logs = history == null ? List.of() : history.workLogs();
        long loggedMinutes = logs.stream()
                .filter(log -> inWorkRange(log, filter))
                .mapToLong(TaskWorkLogView::minutes)
                .sum();
        return new FilteredTask(task, logs, loggedMinutes);
    }

    private static boolean inWorkRange(TaskWorkLogView log, ProjectTaskReportFilter filter) {
        if (filter.workFrom() != null && log.workDate().isBefore(filter.workFrom())) {
            return false;
        }
        return filter.workTo() == null || !log.workDate().isAfter(filter.workTo());
    }

    private static Map<TaskStatus, Long> statusCounts(List<ProjectTaskReportRow> rows) {
        Map<TaskStatus, Long> counts = emptyStatusCounts();
        rows.forEach(row -> counts.compute(row.status(), (status, count) -> count + 1));
        return counts;
    }

    private static Map<TaskStatus, Long> emptyStatusCounts() {
        Map<TaskStatus, Long> counts = new EnumMap<>(TaskStatus.class);
        for (TaskStatus status : TaskStatus.values()) {
            counts.put(status, 0L);
        }
        return counts;
    }

    private List<ProjectTaskReportMemberHours> memberHours(
            long actorUserId,
            long projectId,
            ProjectTaskContext context,
            List<FilteredTask> filtered,
            ProjectTaskReportFilter filter) {
        Map<Long, Long> minutesByMembership = new HashMap<>();
        filtered.forEach(task -> task.logs().stream()
                .filter(log -> inWorkRange(log, filter))
                .forEach(log -> minutesByMembership.merge(
                        log.membershipId(), (long) log.minutes(), Long::sum)));
        List<ProjectMemberView> members = projects.members(actorUserId, projectId);
        return members.stream()
                .map(member -> new ProjectTaskReportMemberHours(
                        member.membershipId(),
                        member.displayName(),
                        minutesByMembership.getOrDefault(member.membershipId(), 0L)))
                .toList();
    }

    private static ProjectTaskReportRow row(
            ProjectDetail project, TaskView task, long loggedMinutes) {
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
                task.createdAt(),
                loggedMinutes);
    }

    private record FilteredTask(TaskView task, List<TaskWorkLogView> logs, long loggedMinutes) {
        private FilteredTask {
            logs = List.copyOf(logs);
        }
    }
}
