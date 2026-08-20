package com.lab.labtimesheet.feature.task.repository;

import com.lab.labtimesheet.feature.task.model.dto.TaskMemberWorkView;
import com.lab.labtimesheet.feature.task.model.entity.TaskWorkLog;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
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
     * Sums all dated work in one Project, treating an empty set as zero.
     *
     * @param projectId owning Project identifier
     * @return total logged minutes
     */
    @Query("select coalesce(sum(log.minutes), 0) from TaskWorkLog log where log.projectId = :projectId")
    long sumMinutesByProjectId(@Param("projectId") long projectId);

    /**
     * Returns hand-checkable per-membership effort totals for one Project.
     *
     * @param projectId owning Project identifier
     * @return stable membership-order totals
     */
    @Query("""
            select new com.lab.labtimesheet.feature.task.model.dto.TaskMemberWorkView(
                log.membershipId, sum(log.minutes))
            from TaskWorkLog log
            where log.projectId = :projectId
            group by log.membershipId
            order by log.membershipId
            """)
    List<TaskMemberWorkView> sumMinutesByProjectGroupedByMembership(@Param("projectId") long projectId);
}
