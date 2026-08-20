package com.lab.labtimesheet.feature.task.repository;

import com.lab.labtimesheet.feature.task.model.entity.TaskWorkLog;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** JPA persistence boundary for dated Task work logs and their author corrections. */
public interface TaskWorkLogRepository extends JpaRepository<TaskWorkLog, Long> {

    /**
     * Loads one work log within its owning Task under a pessimistic write lock. The caller's
     * transaction retains the lock through commit or rollback, serializing concurrent corrections.
     *
     * @param id work log identifier
     * @param taskId owning Task identifier
     * @param projectId owning Project identifier
     * @return the locked log, or empty when identifiers do not match a log
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select log from TaskWorkLog log
            where log.id = :id and log.taskId = :taskId and log.projectId = :projectId
            """)
    Optional<TaskWorkLog> findLockedByIdAndTaskIdAndProjectId(
            @Param("id") long id, @Param("taskId") long taskId, @Param("projectId") long projectId);

    /**
     * Lists a Task's effort history deterministically for authorized detail views.
     *
     * @param taskId owning Task identifier
     * @param projectId owning Project identifier
     * @return logs ordered by work date then identifier
     */
    List<TaskWorkLog> findAllByTaskIdAndProjectIdOrderByWorkDateAscIdAsc(long taskId, long projectId);

    /**
     * Sums one Intern's logged minutes across all of their memberships on a single work date.
     * The membership identifiers come from the Project feature boundary, so the result spans
     * every Project without this feature querying foreign tables.
     *
     * @param membershipIds every membership interval of the Intern
     * @param workDate shared local business date
     * @return combined minutes, or zero when no log exists for the date
     */
    @Query("""
            select coalesce(sum(log.minutes), 0)
            from TaskWorkLog log
            where log.membershipId in :membershipIds and log.workDate = :workDate
            """)
    int sumMinutesByMembershipIdsAndWorkDate(
            @Param("membershipIds") java.util.Collection<Long> membershipIds, @Param("workDate") LocalDate workDate);

    /**
     * Sums all logged minutes across one Project.
     *
     * <p>Consumed by the Project feature's progress/report boundaries. The result is the total
     * effort ever logged in the Project regardless of later Task soft-deletion, because work logs
     * are permanent history.
     *
     * @param projectId owning Project identifier
     * @return total minutes, or zero when nothing was logged
     */
    @Query("""
            select coalesce(sum(log.minutes), 0)
            from TaskWorkLog log
            where log.projectId = :projectId
            """)
    long sumMinutesByProjectId(@Param("projectId") long projectId);

    /**
     * Sums logged minutes per membership within one Project.
     *
     * <p>Consumed by the Project feature's per-member hour boundary. Membership attribution is
     * retained even when the Task was later reassigned, and work logs are permanent history.
     *
     * @param projectId owning Project identifier
     * @return rows of {@code (membershipId, long minutes)} ordered by membership identifier
     */
    @Query("""
            select log.membershipId, coalesce(sum(log.minutes), 0)
            from TaskWorkLog log
            where log.projectId = :projectId
            group by log.membershipId
            order by log.membershipId
            """)
    List<Object[]> sumMinutesByProjectIdGroupedByMembership(@Param("projectId") long projectId);
}
