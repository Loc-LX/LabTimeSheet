package com.lab.labtimesheet.feature.task.service;

import com.lab.labtimesheet.feature.project.model.dto.ProjectActorView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskContext;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.task.model.TaskProgress;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.dto.TaskMemberHours;
import com.lab.labtimesheet.feature.task.model.dto.TaskProjectWorkSummary;
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
    private final TaskWorkLogRepository workLogs;
    private final ProjectQueryService projects;

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
     * Counts every current non-deleted Task in one Project.
     *
     * <p>Completion gate: a Project is completable only when this count equals
     * {@link #countDoneTasks}. The caller holds its own authorization context and transaction.
     *
     * @param projectId owning Project identifier
     * @return non-deleted Task count
     */
    @Transactional(readOnly = true)
    public long countCurrentTasks(long projectId) {
        return tasks.countByProjectIdAndDeletedAtIsNull(projectId);
    }

    /**
     * Counts current non-deleted Tasks already in {@code DONE} state in one Project.
     *
     * <p>Completion gate: a Project is completable only when every non-deleted Task is DONE, that
     * is, when {@link #countCurrentTasks} equals this count.
     *
     * @param projectId owning Project identifier
     * @return DONE non-deleted Task count
     */
    @Transactional(readOnly = true)
    public long countDoneTasks(long projectId) {
        return tasks.countByProjectIdAndStatusAndDeletedAtIsNull(projectId, TaskStatus.DONE);
    }

    /**
     * Builds the authorized Project/Task work summary for one actor.
     *
     * <p>Status counts, completion percentage (empty when the Project has no non-deleted Tasks, so
     * the view renders {@code N/A}), and total logged minutes are always returned. The per-member
     * hour breakdown is populated only for an Admin, the owning Mentor, or the current Leader;
     * every other visible actor receives the aggregate totals without member detail (AUTH-010,
     * RPT-005). Membership attribution in the breakdown is retained even when the Task was later
     * reassigned, because work logs are permanent history.
     *
     * @param actorEmail authenticated account email
     * @param projectId owning Project identifier
     * @return aggregate summary plus the scoped per-member hour breakdown
     */
    @Transactional(readOnly = true)
    public TaskProjectWorkSummary projectWorkSummary(String actorEmail, long projectId) {
        ProjectActorView actor = projects.authenticatedActor(actorEmail);
        ProjectTaskContext context = projects.taskContext(actor.userId(), projectId);
        TaskProgress progress = progress(context.projectId());
        long totalMinutes = workLogs.sumMinutesByProjectId(projectId);
        boolean perMemberVisible = canSeePerMember(actor, context);
        List<TaskMemberHours> perMember = perMemberVisible
                ? memberHours(projectId)
                : List.of();
        return new TaskProjectWorkSummary(
                progress, progress.completionPercentage(), totalMinutes, perMemberVisible, perMember);
    }

    private TaskProgress progress(long projectId) {
        int todo = 0;
        int inProgress = 0;
        int blocked = 0;
        int done = 0;
        for (Object[] row : tasks.countByProjectIdGroupedByStatus(projectId)) {
            switch ((TaskStatus) row[0]) {
                case TODO -> todo = ((Number) row[1]).intValue();
                case IN_PROGRESS -> inProgress = ((Number) row[1]).intValue();
                case BLOCKED -> blocked = ((Number) row[1]).intValue();
                case DONE -> done = ((Number) row[1]).intValue();
            }
        }
        return new TaskProgress(todo, inProgress, blocked, done);
    }

    private List<TaskMemberHours> memberHours(long projectId) {
        return workLogs.sumMinutesByProjectIdGroupedByMembership(projectId).stream()
                .map(row -> new TaskMemberHours((Long) row[0], (Long) row[1]))
                .toList();
    }

    private boolean canSeePerMember(ProjectActorView actor, ProjectTaskContext context) {
        if ("ADMIN".equals(actor.role())) {
            return true;
        }
        if ("MENTOR".equals(actor.role()) && context.mentorUserId() == actor.userId()) {
            return true;
        }
        return context.currentLeaderMembershipId() != null
                && context.activeMembers().stream()
                        .anyMatch(member -> member.userId() == actor.userId()
                                && member.membershipId() == context.currentLeaderMembershipId());
    }
}
