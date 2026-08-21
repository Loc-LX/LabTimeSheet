package com.lab.labtimesheet.feature.task.service;

import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.repository.TaskRepository;
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
     * Counts current unfinished Tasks assigned through any retained membership of one Intern.
     *
     * @param membershipIds retained Project membership identifiers
     * @return non-deleted Task count whose status is not {@code DONE}
     */
    @Transactional(readOnly = true)
    public long countUnfinishedTasksForMemberships(Set<Long> membershipIds) {
        if (membershipIds.isEmpty()) {
            return 0L;
        }
        return tasks.countByAssigneeMembershipIdInAndStatusNotAndDeletedAtIsNull(
                Set.copyOf(membershipIds), TaskStatus.DONE);
    }
}
