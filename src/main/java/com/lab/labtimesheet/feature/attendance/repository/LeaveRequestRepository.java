package com.lab.labtimesheet.feature.attendance.repository;

import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestEntity;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data persistence boundary for full-day leave request state. */
public interface LeaveRequestRepository extends JpaRepository<LeaveRequestEntity, Long> {

    /**
     * Locks one request for an atomic decision, edit, cancellation, or deadline transition.
     *
     * @param id leave request identifier
     * @return locked request, when present
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from LeaveRequestEntity request where request.id = :id")
    Optional<LeaveRequestEntity> findForUpdateById(@Param("id") long id);

    /**
     * Finds an Intern's inclusive active range overlaps, excluding one request during edit.
     *
     * @param internId owning Intern
     * @param startDate proposed inclusive start
     * @param endDate proposed inclusive end
     * @param statuses active request states
     * @param excludeId request being edited, or {@code null}
     * @return overlapping requests ordered by range
     */
    @Query("""
            select request from LeaveRequestEntity request
            where request.internUserId = :internId
              and request.status in :statuses
              and request.startDate <= :endDate
              and request.endDate >= :startDate
              and (:excludeId is null or request.id <> :excludeId)
            order by request.startDate asc, request.id asc
            """)
    List<LeaveRequestEntity> findOverlaps(
            @Param("internId") long internId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("statuses") List<String> statuses,
            @Param("excludeId") Long excludeId);

    /**
     * Lists bounded pending requests whose first counted start has passed.
     *
     * @param status pending state name
     * @param boundary inclusive first-counted-start boundary
     * @param pageable database page and limit, applied before materialization
     * @return expired requests ordered by identifier
     */
    List<LeaveRequestEntity> findByStatusAndFirstCountedStartAtLessThanEqualOrderByIdAsc(
            String status, java.time.Instant boundary, Pageable pageable);
}
