package com.lab.labtimesheet.feature.attendance.repository;

import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestEntity;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data access to the full-day leave request lifecycle owned by Attendance.
 */
public interface LeaveRequestRepository extends JpaRepository<LeaveRequestEntity, Long> {

    /**
     * Loads an Intern's requests newest-first for the prior-requests table.
     *
     * @param internUserId owning Intern account identifier
     * @return requests ordered by newest submission
     */
    List<LeaveRequestEntity> findByInternUserIdOrderByIdDesc(long internUserId);

    /**
     * Loads pending/approved requests whose inclusive date range intersects the given range for one Intern. The
     * PostgreSQL GiST exclusion constraint {@code ex_leave_requests_no_overlap} is the hard gate; this query gives
     * the service a friendly pre-check. Touching dates count as overlap because ranges are inclusive.
     *
     * @param internUserId owning Intern account identifier
     * @param startDate inclusive first candidate date
     * @param endDate inclusive last candidate date
     * @return overlapping pending/approved requests, if any
     */
    @Query("""
            select request from LeaveRequestEntity request
            where request.internUserId = :internUserId
              and request.status in ('PENDING', 'APPROVED')
              and request.startDate <= :endDate
              and request.endDate >= :startDate
            """)
    List<LeaveRequestEntity> findOverlapping(
            @Param("internUserId") long internUserId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Loads every request newest-first for the Mentor decisions page.
     *
     * @return requests ordered by newest submission
     */
    List<LeaveRequestEntity> findAllByOrderByIdDesc();
}