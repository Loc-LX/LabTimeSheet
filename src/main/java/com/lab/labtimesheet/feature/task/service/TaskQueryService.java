package com.lab.labtimesheet.feature.task.service;

import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.dto.TaskCommentView;
import com.lab.labtimesheet.feature.task.model.dto.TaskHistoryView;
import com.lab.labtimesheet.feature.task.model.dto.TaskMemberWorkView;
import com.lab.labtimesheet.feature.task.model.dto.TaskProjectProgress;
import com.lab.labtimesheet.feature.task.model.dto.TaskWorkLogView;
import com.lab.labtimesheet.feature.task.model.entity.Task;
import com.lab.labtimesheet.feature.task.model.entity.TaskComment;
import com.lab.labtimesheet.feature.task.model.entity.TaskWorkLog;
import com.lab.labtimesheet.feature.task.repository.TaskCommentRepository;
import com.lab.labtimesheet.feature.task.repository.TaskRepository;
import com.lab.labtimesheet.feature.task.repository.TaskWorkLogRepository;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public Task query boundary used by other features without exposing Task entities or repositories.
 */
@Service
@RequiredArgsConstructor
public class TaskQueryService {

    private final TaskRepository tasks;
    private final TaskCommentRepository comments;
    private final TaskWorkLogRepository workLogs;

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
                        .toList());
    }

    private static TaskCommentView commentView(TaskComment comment) {
        return new TaskCommentView(
                comment.getId(), comment.getTaskId(), comment.getAuthorUserId(),
                comment.getBody(), comment.getCreatedAt());
    }

    private static TaskWorkLogView workLogView(TaskWorkLog log) {
        return new TaskWorkLogView(
                log.getId(), log.getProjectId(), log.getTaskId(), log.getMembershipId(),
                log.getWorkDate(), log.getMinutes(), log.getNote(), log.getCreatedAt(), log.getUpdatedAt());
    }
}
