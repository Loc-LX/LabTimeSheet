package com.lab.labtimesheet.feature.task.service;

import com.lab.labtimesheet.feature.task.repository.TaskRepository;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public Task query boundary used by other features without exposing Task entities or repositories.
 */
@Service
public class TaskQueryService {

    private final TaskRepository tasks;

    /**
     * Creates the cross-feature Task query service.
     *
     * @param tasks Task persistence boundary
     */
    public TaskQueryService(TaskRepository tasks) {
        this.tasks = tasks;
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
}
