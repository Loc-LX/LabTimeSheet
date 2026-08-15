package com.lab.labtimesheet.feature.task.repository;

import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.entity.Task;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * JPA persistence and current-read queries for Task rows.
 *
 * <p>Normal reads consistently exclude soft-deleted rows. Mutation callers acquire the owning
 * Project lock before requesting the Task row lock so aggregate and Task lock order remains stable.
 */
public interface TaskRepository extends JpaRepository<Task, Long> {

    /**
     * Finds a current Task only when its identifier belongs to the supplied Project.
     *
     * @param id Task identifier
     * @param projectId owning Project identifier
     * @return matching non-deleted Task, if visible in that aggregate
     */
    Optional<Task> findByIdAndProjectIdAndDeletedAtIsNull(long id, long projectId);

    /**
     * Locks one current Task for a mutation after the caller has locked its Project.
     *
     * @param id Task identifier
     * @param projectId owning Project identifier
     * @return matching non-deleted Task under a pessimistic write lock
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Task> findLockedByIdAndProjectIdAndDeletedAtIsNull(long id, long projectId);

    /**
     * Lists current Tasks for one Project in deterministic identifier order.
     *
     * @param projectId owning Project identifier
     * @return non-deleted Tasks
     */
    List<Task> findAllByProjectIdAndDeletedAtIsNullOrderById(long projectId);

    /**
     * Counts all current Tasks in one Project.
     *
     * @param projectId owning Project identifier
     * @return non-deleted Task count
     */
    long countByProjectIdAndDeletedAtIsNull(long projectId);

    /**
     * Counts current Tasks in a status across the supplied Projects.
     *
     * @param projectIds authorized Project identifiers
     * @param status status to count
     * @return matching non-deleted Task count
     */
    long countByProjectIdInAndStatusAndDeletedAtIsNull(List<Long> projectIds, TaskStatus status);

    /**
     * Counts current Tasks assigned through the supplied Project memberships.
     *
     * @param projectIds authorized Project identifiers
     * @param assigneeMembershipIds actor memberships scoped to those Projects
     * @return matching non-deleted Task count
     */
    long countByProjectIdInAndAssigneeMembershipIdInAndDeletedAtIsNull(
            List<Long> projectIds, List<Long> assigneeMembershipIds);

    /**
     * Loads the highest-priority current assignments within authorized Project/membership pairs.
     *
     * <p>Ordering is due date ascending, null due dates last, then Task identifier ascending. The
     * supplied page bounds how many rows are returned.
     *
     * @param projectIds authorized active Project identifiers
     * @param assigneeMembershipIds actor's current memberships in those Projects
     * @param pageable result limit
     * @return ordered non-deleted Tasks
     */
    @Query("""
            select task
            from Task task
            where task.projectId in :projectIds
              and task.assigneeMembershipId in :assigneeMembershipIds
              and task.deletedAt is null
            order by case when task.dueDate is null then 1 else 0 end,
                     task.dueDate,
                     task.id
            """)
    List<Task> findPriorityTasks(
            @Param("projectIds") List<Long> projectIds,
            @Param("assigneeMembershipIds") List<Long> assigneeMembershipIds,
            Pageable pageable);

    /**
     * Counts current Tasks whose assignee is outside a non-empty active-membership set.
     *
     * <p>The Project activation caller is responsible for holding the Project write lock through
     * its decision. Empty membership sets are handled by {@code TaskQueryService} rather than this
     * {@code NOT IN} query.
     *
     * @param projectId locked Project identifier
     * @param activeMembershipIds non-empty active same-Project membership identifiers
     * @return non-deleted Tasks assigned outside the supplied set
     */
    @Query("""
            select count(task)
            from Task task
            where task.projectId = :projectId
              and task.deletedAt is null
              and task.assigneeMembershipId not in :activeMembershipIds
            """)
    long countCurrentTasksAssignedOutside(
            @Param("projectId") long projectId,
            @Param("activeMembershipIds") Set<Long> activeMembershipIds);
}
