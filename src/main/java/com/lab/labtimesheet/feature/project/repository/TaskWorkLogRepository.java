package com.lab.labtimesheet.feature.project.repository;

import com.lab.labtimesheet.feature.project.model.dto.TaskActualMinutesView;
import com.lab.labtimesheet.feature.project.model.dto.TaskMemberWorkView;
import com.lab.labtimesheet.feature.project.model.dto.TaskWorkLogCandidate;
import com.lab.labtimesheet.feature.project.model.entity.TaskWorkLog;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** JPA persistence boundary for dated Task work logs and corrections. */
public interface TaskWorkLogRepository extends JpaRepository<TaskWorkLog, Long> {

    /**
     * Locks one retained work log inside its Project/Task scope for author correction.
     *
     * @param id work-log identifier
     * @param projectId owning Project identifier
     * @return locked matching work log, if present
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TaskWorkLog> findLockedByIdAndProjectId(long id, long projectId);

    /**
     * Lists one Task's retained work history in date and identifier order.
     *
     * @param taskId owning Task identifier
     * @param projectId owning Project identifier
     * @return retained logs
     */
    List<TaskWorkLog> findAllByTaskIdAndProjectIdOrderByWorkDateAscIdAsc(long taskId, long projectId);

    /**
     * Lists only the selected local date's retained logs for a Project in stable Task/log order.
     *
     * <p>Daily reporting uses this Project/date query instead of loading each Task's complete
     * history. The returned entity rows are immediately converted to the public work-log DTO.</p>
     *
     * @param projectId owning Project identifier
     * @param workDate selected local report date
     * @return retained logs created for the selected date
     */
    List<TaskWorkLog> findAllByProjectIdAndWorkDateOrderByTaskIdAscIdAsc(
            long projectId, LocalDate workDate);

    /**
     * Reads a narrow Project-scoped work-log projection without taking a row lock.
     *
     * <p>Correction uses this immutable, non-managed projection only to discover the date needed
     * before acquiring the Account profile lock. It then loads the entity for the first time with
     * {@link #findLockedByIdAndProjectId(long, long)} and re-checks every projection field before
     * authorizing or changing the row.</p>
     *
     * @param id work-log identifier
     * @param projectId owning Project identifier
     * @return immutable candidate projection when it belongs to the Project
     */
    @Query("""
            select new com.lab.labtimesheet.feature.project.model.dto.TaskWorkLogCandidate(
                log.id, log.projectId, log.taskId, log.membershipId, log.workDate)
            from TaskWorkLog log
            where log.id = :id
              and log.projectId = :projectId
            """)
    Optional<TaskWorkLogCandidate> findCandidateByIdAndProjectId(
            @Param("id") long id,
            @Param("projectId") long projectId);

    /**
     * Sums one Intern's dated effort across authoritative retained membership intervals.
     *
     * <p>The Task service derives the complete interval identifier set through the Project DTO boundary
     * while holding the mutation locks; this repository query only executes the aggregate over that
     * reviewed set and never resolves Project or Account persistence.</p>
     *
     * @param membershipIds current and historical membership identifiers authorized by Project for the Intern/date
     * @param workDate local business date whose combined effort is read
     * @return combined minutes, or zero when no supplied membership has a log on the date
     */
    @Query("""
            select coalesce(sum(log.minutes), 0)
            from TaskWorkLog log
            where log.membershipId in :membershipIds
              and log.workDate = :workDate
            """)
    long sumMinutesByMembershipIdsAndWorkDate(
            @Param("membershipIds") Set<Long> membershipIds,
            @Param("workDate") LocalDate workDate);

    /**
     * Sums all dated work in one Project, treating an empty set as zero.
     *
     * @param projectId owning Project identifier
     * @return total logged minutes
     */
    @Query("select coalesce(sum(log.minutes), 0) from TaskWorkLog log where log.projectId = :projectId")
    long sumMinutesByProjectId(@Param("projectId") long projectId);

    @Query("select coalesce(sum(log.minutes), 0) from TaskWorkLog log where log.taskId = :taskId and log.projectId = :projectId")
    long sumMinutesByTaskIdAndProjectId(@Param("taskId") long taskId, @Param("projectId") long projectId);

    /**
     * Aggregates lifetime retained effort for every Task in one Project in one database query.
     *
     * @param projectId owning Project identifier
     * @param taskIds selected-date Task identifiers whose lifetime totals are required
     * @return one immutable total per Task that has retained work
     */
    @Query("""
            select new com.lab.labtimesheet.feature.project.model.dto.TaskActualMinutesView(
                log.taskId, coalesce(sum(log.minutes), 0))
            from TaskWorkLog log
            where log.projectId = :projectId
              and log.taskId in :taskIds
            group by log.taskId
            order by log.taskId
            """)
    List<TaskActualMinutesView> sumMinutesByProjectGroupedByTask(
            @Param("projectId") long projectId,
            @Param("taskIds") Set<Long> taskIds);

    @Query("select count(log) > 0 from TaskWorkLog log where log.taskId = :taskId and log.projectId = :projectId")
    boolean existsByTaskIdAndProjectId(@Param("taskId") long taskId, @Param("projectId") long projectId);

    /** Detects incoming-assignee work created at or after a forecast assignment instant. */
    @Query("select count(log) > 0 from TaskWorkLog log where log.taskId = :taskId and log.projectId = :projectId and log.membershipId = :membershipId and log.createdAt >= :createdAt")
    boolean existsByTaskIdAndProjectIdAndMembershipIdAndCreatedAtGreaterThanEqual(
            @Param("taskId") long taskId, @Param("projectId") long projectId,
            @Param("membershipId") long membershipId, @Param("createdAt") Instant createdAt);

    /**
     * Returns hand-checkable per-membership effort totals for one Project.
     *
     * @param projectId owning Project identifier
     * @return stable membership-order totals
     */
    @Query("""
            select new com.lab.labtimesheet.feature.project.model.dto.TaskMemberWorkView(
                log.membershipId, sum(log.minutes))
            from TaskWorkLog log
            where log.projectId = :projectId
            group by log.membershipId
            order by log.membershipId
            """)
    List<TaskMemberWorkView> sumMinutesByProjectGroupedByMembership(@Param("projectId") long projectId);
}
