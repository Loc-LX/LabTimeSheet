package com.lab.labtimesheet.feature.task.repository;

import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.dto.TaskProjectProgress;
import com.lab.labtimesheet.feature.task.model.entity.Task;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
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
     * Lists current Tasks whose stored due date matches a later calendar impact date.
     *
     * @param dueDate exact local date being previewed
     * @return non-deleted Tasks ordered by Project and Task identifier
     */
    List<Task> findAllByDueDateAndDeletedAtIsNullOrderByProjectIdAscIdAsc(LocalDate dueDate);

    /**
     * Counts all current Tasks in one Project.
     *
     * @param projectId owning Project identifier
     * @return non-deleted Task count
     */
    long countByProjectIdAndDeletedAtIsNull(long projectId);

    /**
     * Reads status counts and retained work minutes from one aggregate query.
     *
     * <p>The aggregate produces one row even when a Project has no current Tasks. The correlated
     * work-log sum is evaluated within the same database snapshot as the Task counts, avoiding
     * mixed READ COMMITTED observations during concurrent status or log changes.
     *
     * @param projectId owning Project identifier
     * @param todo TODO status value
     * @param inProgress IN_PROGRESS status value
     * @param blocked BLOCKED status value
     * @param done DONE status value
     * @return current non-deleted counts and retained total minutes
     */
    @Query("""
            select new com.lab.labtimesheet.feature.task.model.dto.TaskProjectProgress(
                coalesce(sum(case when task.status = :todo then 1 else 0 end), 0),
                coalesce(sum(case when task.status = :inProgress then 1 else 0 end), 0),
                coalesce(sum(case when task.status = :blocked then 1 else 0 end), 0),
                coalesce(sum(case when task.status = :done then 1 else 0 end), 0),
                coalesce((select sum(log.minutes)
                          from TaskWorkLog log
                          where log.projectId = :projectId), 0))
            from Task task
            where task.projectId = :projectId
              and task.deletedAt is null
            """)
    TaskProjectProgress projectProgress(
            @Param("projectId") long projectId,
            @Param("todo") TaskStatus todo,
            @Param("inProgress") TaskStatus inProgress,
            @Param("blocked") TaskStatus blocked,
            @Param("done") TaskStatus done);

    /**
     * Counts unfinished current Tasks assigned to one membership.
     *
     * @param projectId owning Project identifier
     * @param assigneeMembershipId current assignee membership identifier
     * @param statuses unfinished status set
     * @return matching non-deleted Task count
     */
    long countByProjectIdAndAssigneeMembershipIdAndStatusInAndDeletedAtIsNull(
            long projectId, long assigneeMembershipId, Set<TaskStatus> statuses);

    /**
     * Counts unfinished current Tasks with at least one retained work log.
     *
     * <p>The correlated existence check keeps the worked/unworked distinction in the same
     * Project-scoped query used by the direct-removal guard. The caller holds the Project write
     * lock while making the decision, so a concurrent work-log append cannot pass the guard after
     * this count is observed.</p>
     *
     * @param projectId owning Project identifier
     * @param assigneeMembershipId current source assignee membership identifier
     * @param statuses unfinished status set
     * @return worked unfinished non-deleted Task count
     */
    @Query("""
            select count(task)
            from Task task
            where task.projectId = :projectId
              and task.assigneeMembershipId = :assigneeMembershipId
              and task.status in :statuses
              and task.deletedAt is null
              and exists (select log.id
                          from TaskWorkLog log
                          where log.projectId = task.projectId
                            and log.taskId = task.id)
            """)
    long countWorkedUnfinishedByProjectIdAndAssigneeMembershipIdAndStatusInAndDeletedAtIsNull(
            @Param("projectId") long projectId,
            @Param("assigneeMembershipId") long assigneeMembershipId,
            @Param("statuses") Set<TaskStatus> statuses);

    /**
     * Lists unfinished current Task IDs in stable order for a guarded all-or-nothing transfer.
     *
     * @param projectId owning Project identifier
     * @param assigneeMembershipId source assignee membership identifier
     * @param statuses unfinished status set
     * @return matching non-deleted Task identifiers ordered ascending
     */
    @Query("""
            select task.id
            from Task task
            where task.projectId = :projectId
              and task.assigneeMembershipId = :assigneeMembershipId
              and task.status in :statuses
              and task.deletedAt is null
            order by task.id
            """)
    List<Long> findIdsByProjectIdAndAssigneeMembershipIdAndStatusInAndDeletedAtIsNullOrderById(
            @Param("projectId") long projectId,
            @Param("assigneeMembershipId") long assigneeMembershipId,
            @Param("statuses") Set<TaskStatus> statuses);

    /**
     * Lists all retained Task rows, including soft-deleted history, by stable identifier order.
     *
     * @param projectId owning Project identifier
     * @return retained Task rows
     */
    List<Task> findAllByProjectIdOrderById(long projectId);

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
