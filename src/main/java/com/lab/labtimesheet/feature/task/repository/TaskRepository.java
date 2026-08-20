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
 * JPA persistence, khóa ghi và truy vấn hiện tại cho các dòng Task.
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
     * Finds one Task historically regardless of soft-deletion state.
     *
     * <p>The caller applies the historical-inspection authorization (current Leader or self-Task
     * creator) before exposing the row; soft-deleted Tasks are otherwise hidden.
     *
     * @param id Task identifier
     * @param projectId owning Project identifier
     * @return matching Task including soft-deleted rows, if present in that aggregate
     */
    Optional<Task> findByIdAndProjectId(long id, long projectId);

    /**
     * Lists Tasks for one Project historically regardless of soft-deletion state.
     *
     * <p>The caller applies the historical-inspection authorization before exposing the rows;
     * soft-deleted Tasks are otherwise excluded from normal lists.
     *
     * @param projectId owning Project identifier
     * @return Tasks including soft-deleted rows in deterministic identifier order
     */
    List<Task> findAllByProjectIdOrderById(long projectId);

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
     * Locks every unfinished current Task assigned to one membership within one Project.
     *
     * <p>Used by the exit-transfer boundary, which runs inside the Project feature's locked
     * transaction. {@code DONE} and soft-deleted Tasks are excluded because completed work and
     * historical rows never move.
     *
     * @param projectId owning Project identifier
     * @param assigneeMembershipId membership whose unfinished Tasks are being transferred
     * @return locked non-deleted Tasks in TODO, IN_PROGRESS, or BLOCKED state
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select task
            from Task task
            where task.projectId = :projectId
              and task.assigneeMembershipId = :assigneeMembershipId
              and task.deletedAt is null
              and task.status <> com.lab.labtimesheet.feature.task.model.TaskStatus.DONE
            order by task.id
            """)
    List<Task> findLockedUnfinishedByProjectIdAndAssigneeMembershipId(
            @Param("projectId") long projectId,
            @Param("assigneeMembershipId") long assigneeMembershipId);

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
     * [I2-PRJ-05] Đếm Task hiện tại chưa hoàn thành để quyết định Project có thể chuyển sang
     * trạng thái terminal hay chưa.
     *
     * @param projectId Project sở hữu Task
     * @return số Task chưa xóa có trạng thái khác DONE
     */
    @Query("""
            select count(task)
            from Task task
            where task.projectId = :projectId
              and task.deletedAt is null
              and task.status <> com.lab.labtimesheet.feature.task.model.TaskStatus.DONE
            """)
    long countUnfinishedByProjectId(@Param("projectId") long projectId);

    /**
     * Counts current Tasks in one status within one Project.
     *
     * @param projectId owning Project identifier
     * @param status status to count
     * @return matching non-deleted Task count
     */
    long countByProjectIdAndStatusAndDeletedAtIsNull(long projectId, TaskStatus status);

    /**
     * Groups current Task counts by status for one Project.
     *
     * @param projectId owning Project identifier
     * @return rows of {@code (TaskStatus, long count)} for each non-empty status
     */
    @Query("""
            select task.status, count(task)
            from Task task
            where task.projectId = :projectId
              and task.deletedAt is null
            group by task.status
            """)
    List<Object[]> countByProjectIdGroupedByStatus(@Param("projectId") long projectId);

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
