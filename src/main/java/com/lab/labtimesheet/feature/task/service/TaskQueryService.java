package com.lab.labtimesheet.feature.task.service;

import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.dto.TaskActualMinutesView;
import com.lab.labtimesheet.feature.task.model.dto.TaskCommentView;
import com.lab.labtimesheet.feature.task.model.dto.TaskDailyReportView;
import com.lab.labtimesheet.feature.task.model.dto.TaskDueDateImpactView;
import com.lab.labtimesheet.feature.task.model.dto.TaskHistoryView;
import com.lab.labtimesheet.feature.task.model.dto.TaskMemberWorkView;
import com.lab.labtimesheet.feature.task.model.dto.TaskProjectProgress;
import com.lab.labtimesheet.feature.task.model.dto.TaskRemainingEffortForecastSummary;
import com.lab.labtimesheet.feature.task.model.dto.TaskWorkLogView;
import com.lab.labtimesheet.feature.task.model.entity.Task;
import com.lab.labtimesheet.feature.task.model.entity.TaskComment;
import com.lab.labtimesheet.feature.task.model.entity.TaskRemainingEffortForecast;
import com.lab.labtimesheet.feature.task.model.entity.TaskWorkLog;
import com.lab.labtimesheet.feature.task.repository.TaskCommentRepository;
import com.lab.labtimesheet.feature.task.repository.TaskRemainingEffortForecastRepository;
import com.lab.labtimesheet.feature.task.repository.TaskRepository;
import com.lab.labtimesheet.feature.task.repository.TaskWorkLogRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public Task query boundary used by other features without exposing Task entities or repositories.
 */
@Service
public class TaskQueryService {

    private final TaskRepository tasks;
    private final TaskCommentRepository comments;
    private final TaskWorkLogRepository workLogs;
    private final TaskRemainingEffortForecastRepository forecasts;

    /**
     * Retains the small constructor used by producer tests that do not exercise forecast reads.
     * Spring uses the annotated four-argument constructor for the complete public boundary.
     */
    public TaskQueryService(
            TaskRepository tasks, TaskCommentRepository comments, TaskWorkLogRepository workLogs) {
        this(tasks, comments, workLogs, null);
    }

    /** Spring-injected constructor including the append-only forecast producer. */
    @Autowired
    public TaskQueryService(
            TaskRepository tasks,
            TaskCommentRepository comments,
            TaskWorkLogRepository workLogs,
            TaskRemainingEffortForecastRepository forecasts) {
        this.tasks = tasks;
        this.comments = comments;
        this.workLogs = workLogs;
        this.forecasts = forecasts;
    }

    /**
     * Counts current Tasks assigned outside the supplied active membership set.
     *
     * <p>Only non-deleted Tasks are considered. An empty set means every current Task is invalid
     * and avoids an empty {@code NOT IN} predicate. This method joins an existing transaction; a
     * Project lifecycle caller must acquire and retain the Project write lock before calling it so
     * the assignee guard remains stable through the Project decision and commit.
     *
     * @param projectId Project whose current Task assignments are being validated
     * @param activeMembershipIds current eligible same-Project membership identifiers
     * @return number of current Tasks whose assignee is not in the supplied set
     */
    @Transactional(readOnly = true)
    public long countCurrentTasksAssignedOutside(long projectId, Set<Long> activeMembershipIds) {
        if (activeMembershipIds.isEmpty()) {
            return tasks.countByProjectIdAndDeletedAtIsNull(projectId);
        }
        return tasks.countCurrentTasksAssignedOutside(projectId, Set.copyOf(activeMembershipIds));
    }

    /**
     * Returns status counts and total retained work minutes for one Project.
     *
     * <p>Callers must authorize the Project before invoking this DTO-only boundary. Soft-deleted
     * Tasks are excluded from status counts, while retained work logs remain included in total
     * minutes because historical effort is never rewritten by Task deletion.
     *
     * @param projectId Project whose current progress is requested
     * @return hand-checkable counts, minutes, and empty-denominator semantics
     */
    @Transactional(readOnly = true)
    public TaskProjectProgress projectProgress(long projectId) {
        return tasks.projectProgress(
                projectId,
                TaskStatus.TODO,
                TaskStatus.IN_PROGRESS,
                TaskStatus.BLOCKED,
                TaskStatus.DONE);
    }

    /**
     * Returns retained work totals by Project membership.
     *
     * @param projectId Project whose member work is requested
     * @return deterministic membership totals, empty when no work is logged
     */
    @Transactional(readOnly = true)
    public List<TaskMemberWorkView> memberWork(long projectId) {
        return List.copyOf(workLogs.sumMinutesByProjectGroupedByMembership(projectId));
    }

    /**
     * Returns current Task facts affected by a later global day-off decision.
     *
     * <p>The stored due date is never changed here. Attendance owns the calendar decision and may
     * use this DTO-only boundary to show an impact preview to the authorized Project Leader.</p>
     *
     * @param dueDate exact local date being previewed
     * @return current matching Tasks in deterministic Project/Task order
     * @throws IllegalArgumentException when the impact date is missing
     */
    @Transactional(readOnly = true)
    public List<TaskDueDateImpactView> dueDateImpacts(LocalDate dueDate) {
        if (dueDate == null) {
            throw new IllegalArgumentException("Due date is required");
        }
        return tasks.findAllByDueDateAndDeletedAtIsNullOrderByProjectIdAscIdAsc(dueDate).stream()
                .map(task -> new TaskDueDateImpactView(
                        task.getId(),
                        task.getProjectId(),
                        task.getTitle(),
                        task.getDueDate(),
                        task.getStatus(),
                        task.getAssigneeMembershipId()))
                .toList();
    }

    /**
     * Returns retained Task, comment, and work-log rows for Project History.
     *
     * <p>Authorization is deliberately owned by the Project caller. This method reports only
     * persisted facts and never fabricates assignment, status, or edit events.
     *
     * @param projectId Project whose retained history is requested
     * @return stable Task history ordered by Task identifier
     */
    @Transactional(readOnly = true)
    public List<TaskHistoryView> history(long projectId) {
        return tasks.findAllByProjectIdOrderById(projectId).stream()
                .map(task -> history(task, projectId))
                .toList();
    }

    /**
     * Returns retained Task work and planning facts for one authorized Project.
     *
     * <p>The report consumer supplies authorization through {@code ProjectQueryService} before
     * calling this DTO-only boundary. All Task rows, including soft-deleted rows, are retained;
     * selected-date logs are loaded in one Project/date query and lifetime actual effort in one
     * grouped aggregate. A single selected-Task forecast read supplies the latest applicable
     * current-assignment snapshot for every selected Task, avoiding per-Task repository calls.</p>
     *
     * @param projectId owning Project identifier already authorized by the caller
     * @param reportDate selected local report date
     * @return stable Task rows with retained logs, lifetime effort, variance, and latest forecast
     */
    @Transactional(readOnly = true)
    public List<TaskDailyReportView> dailyReport(long projectId, LocalDate reportDate) {
        if (reportDate == null) {
            throw new IllegalArgumentException("Report date is required");
        }
        Map<Long, List<TaskWorkLogView>> logsByTask = workLogs
                .findAllByProjectIdAndWorkDateOrderByTaskIdAscIdAsc(projectId, reportDate)
                .stream()
                .map(TaskQueryService::workLogView)
                .collect(Collectors.groupingBy(
                        TaskWorkLogView::taskId,
                        java.util.LinkedHashMap::new,
                        Collectors.toList()));
        if (logsByTask.isEmpty()) {
            return List.of();
        }
        Set<Long> selectedTaskIds = Set.copyOf(logsByTask.keySet());
        Map<Long, Long> actualMinutesByTask = workLogs
                .sumMinutesByProjectGroupedByTask(projectId, selectedTaskIds)
                .stream()
                .collect(Collectors.toMap(
                        TaskActualMinutesView::taskId,
                        TaskActualMinutesView::totalMinutes,
                        Long::sum,
                        LinkedHashMap::new));
        Map<Long, List<TaskRemainingEffortForecast>> forecastsByTask = forecasts == null
                ? Map.of()
                : forecasts.findAllByProjectIdAndTaskIdInOrderByTaskIdAscAssignmentStartedAtAscCreatedAtAscIdAsc(
                                projectId, selectedTaskIds)
                        .stream()
                        .collect(Collectors.groupingBy(
                                TaskRemainingEffortForecast::getTaskId,
                                java.util.LinkedHashMap::new,
                                Collectors.toList()));
        return tasks.findAllByProjectIdAndIdInOrderById(projectId, selectedTaskIds).stream()
                .map(task -> dailyReport(
                        task,
                        actualMinutesByTask.getOrDefault(task.getId(), 0L),
                        logsByTask.getOrDefault(task.getId(), List.of()),
                        forecastsByTask.getOrDefault(task.getId(), List.of())))
                .toList();
    }

    private TaskDailyReportView dailyReport(
            Task task,
            long lifetimeActualMinutes,
            List<TaskWorkLogView> logs,
            List<TaskRemainingEffortForecast> forecastRows) {
        return TaskDailyReportView.of(
                task.getId(), task.getProjectId(), task.getAssigneeMembershipId(), task.getTitle(),
                task.getStatus(), task.getEstimatedMinutes(), lifetimeActualMinutes, task.getAssignedAt(),
                task.getCreatedAt(), task.getDeletedAt(), logs, latestForecast(task, forecastRows));
    }

    private static TaskRemainingEffortForecastSummary latestForecast(
            Task task, List<TaskRemainingEffortForecast> rows) {
        Set<Long> supersededIds = rows.stream()
                .map(TaskRemainingEffortForecast::getSupersedesForecastId)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
        return rows.stream()
                .filter(forecast -> forecast.getIncomingMembershipId() == task.getAssigneeMembershipId())
                .filter(forecast -> Objects.equals(forecast.getAssignmentStartedAt(), task.getAssignedAt()))
                .filter(forecast -> forecast.getId() == null || !supersededIds.contains(forecast.getId()))
                .max(Comparator.comparing(TaskRemainingEffortForecast::getCreatedAt)
                        .thenComparing(forecast -> forecast.getId() == null ? 0L : forecast.getId()))
                .map(forecast -> new TaskRemainingEffortForecastSummary(
                        forecast.getRemainingMinutes(), forecast.getActualMinutesSnapshot(),
                        forecast.getAssignmentStartedAt()))
                .orElse(null);
    }

    private TaskHistoryView history(Task task, long projectId) {
        return new TaskHistoryView(
                task.getId(),
                task.getProjectId(),
                task.getAssigneeMembershipId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getDueDate(),
                task.getCreatorMembershipId(),
                task.getAssignerMembershipId(),
                task.getAssignedAt(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getDeletedAt(),
                task.getDeletedByMembershipId(),
                comments.findAllByTaskIdOrderByCreatedAtAscIdAsc(task.getId()).stream()
                        .map(TaskQueryService::commentView)
                        .toList(),
                workLogs.findAllByTaskIdAndProjectIdOrderByWorkDateAscIdAsc(task.getId(), projectId).stream()
                        .map(TaskQueryService::workLogView)
                        .toList(),
                task.getVersion());
    }

    private static TaskCommentView commentView(TaskComment comment) {
        return new TaskCommentView(
                comment.getId(), comment.getTaskId(), comment.getAuthorUserId(),
                comment.getBody(), comment.getCreatedAt());
    }

    private static TaskWorkLogView workLogView(TaskWorkLog log) {
        return new TaskWorkLogView(
                log.getId(), log.getProjectId(), log.getTaskId(), log.getMembershipId(),
                log.getWorkDate(), log.getMinutes(), log.getNote(), log.getCreatedAt(), log.getUpdatedAt(),
                log.getVersion());
    }
}
