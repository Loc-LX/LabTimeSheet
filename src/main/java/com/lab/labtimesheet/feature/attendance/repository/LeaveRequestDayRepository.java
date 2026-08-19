package com.lab.labtimesheet.feature.attendance.repository;

import com.lab.labtimesheet.feature.attendance.model.dto.MonthReservation;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestDayEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestDayId;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data access to frozen quota-consuming leave days.
 */
public interface LeaveRequestDayRepository extends JpaRepository<LeaveRequestDayEntity, LeaveRequestDayId> {

    /**
     * Aggregates frozen days per quota month for an Intern, counting only requests that currently reserve quota.
     * REJECTED and CANCELLED requests release their days because they are excluded from the status filter.
     *
     * @param internId owning Intern account identifier
     * @return one reservation count per quota month that has pending or approved days
     */
    @Query("""
            select new com.lab.labtimesheet.feature.attendance.model.dto.MonthReservation(
                day.quotaMonth, count(day))
            from LeaveRequestDayEntity day
            join day.request request
            where request.internUserId = :internId and request.status in ('PENDING', 'APPROVED')
            group by day.quotaMonth
            """)
    List<MonthReservation> countReservedByMonth(@Param("internId") long internId);

    /**
     * Loads an already-persisted request's frozen dates in ascending order.
     *
     * @param requestId leave request identifier
     * @return counted dates, ascending
     */
    @Query("""
            select day.id.leaveDate
            from LeaveRequestDayEntity day
            where day.request.id = :requestId
            order by day.id.leaveDate
            """)
    List<LocalDate> findLeaveDatesByRequestId(@Param("requestId") long requestId);
}