package com.lab.labtimesheet.feature.task.service;

import com.lab.labtimesheet.feature.task.model.entity.Task;
import com.lab.labtimesheet.feature.task.repository.TaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Producer-side bulk Task transfer operation consumed by the Project feature.
 *
 * <p>The Project feature owns the transaction that removes a member or completes a Project and
 * holds the Project write lock before invoking this boundary, so the Task-to-Project lock order is
 * preserved. Unfinished Tasks assigned to a departing membership are pessimistically locked and
 * reassigned to the transfer target (the current Leader, or the newly appointed Leader when the
 * departing member leads) in the same transaction.
 */
@Service
@RequiredArgsConstructor
public class TaskTransferService {

    private final TaskRepository tasks;
    private final Clock clock;

    /**
     * Atomically transfers every unfinished non-deleted Task from one membership to another within
     * the caller's transaction.
     *
     * <p>The caller must already hold the owning Project's write lock. Each transferred Task keeps
     * its creator attribution, status, comments, and work logs; only the current assignment
     * actor/time move to the receiving membership. {@code DONE} Tasks are never transferred.
     *
     * @param projectId owning Project identifier
     * @param fromMembershipId departing membership whose unfinished Tasks move
     * @param toMembershipId receiving membership (current or newly appointed Leader)
     * @return number of Tasks transferred
     */
    @Transactional
    public int transferUnfinishedTasks(long projectId, long fromMembershipId, long toMembershipId) {
        List<Task> unfinished = tasks.findLockedUnfinishedByProjectIdAndAssigneeMembershipId(
                projectId, fromMembershipId);
        if (unfinished.isEmpty()) {
            return 0;
        }
        Instant now = clock.instant();
        unfinished.forEach(task -> task.reassign(toMembershipId, toMembershipId, now));
        tasks.flush();
        return unfinished.size();
    }
}
