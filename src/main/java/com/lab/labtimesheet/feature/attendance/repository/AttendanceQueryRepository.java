package com.lab.labtimesheet.feature.attendance.repository;

import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestDayEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestDayId;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Narrow Attendance read repository for date-specific leave eligibility facts.
 */
public interface AttendanceQueryRepository extends Repository<LeaveRequestDayEntity, LeaveRequestDayId> {

    /**
     * Checks whether an approved request owns a frozen allocation for the exact date.
     * Request range membership alone is intentionally insufficient because non-workdays and holidays are excluded
     * when leave is materialized.
     *
     * @param internId Intern account identifier
     * @param workDate exact policy-local date being evaluated
     * @return {@code true} only for an approved frozen allocation
     */
    @Query("""
            select count(day) > 0
            from LeaveRequestDayEntity day
            join day.request request
            where request.internUserId = :internId and request.status = 'APPROVED'
              and day.id.leaveDate = :workDate
            """)
    boolean hasApprovedLeave(
            @Param("internId") long internId, @Param("workDate") LocalDate workDate);

    /**
     * Loads frozen approved leave allocations for an inclusive report range in date order.
     * The allocation row, rather than a request's broad requested range, is authoritative because it excludes
     * policy off-days and preserves the policy snapshot materialized when the request was submitted.
     *
     * @param internId target Intern account identifier
     * @param from inclusive first local date
     * @param to inclusive last local date
     * @return approved allocated dates in ascending order
     */
    @Query("""
            select day.id.leaveDate
            from LeaveRequestDayEntity day
            join day.request request
            where request.internUserId = :internId
              and request.status = 'APPROVED'
              and day.id.leaveDate between :from and :to
            order by day.id.leaveDate asc
            """)
    List<LocalDate> findApprovedLeaveDates(
            @Param("internId") long internId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
