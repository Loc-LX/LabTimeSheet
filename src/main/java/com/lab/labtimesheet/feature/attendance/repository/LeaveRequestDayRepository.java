package com.lab.labtimesheet.feature.attendance.repository;

import com.lab.labtimesheet.feature.attendance.model.dto.MonthReservation;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestDayEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestDayId;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
     * Aggregates frozen days per quota month excluding one request, so an edit can validate its replacement without
     * counting its own still-reserving days.
     *
     * @param internId owning Intern account identifier
     * @param excludedRequestId request being edited
     * @return one reservation count per quota month that has pending/approved days outside the excluded request
     */
    @Query("""
            select new com.lab.labtimesheet.feature.attendance.model.dto.MonthReservation(
                day.quotaMonth, count(day))
            from LeaveRequestDayEntity day
            join day.request request
            where request.internUserId = :internId
              and request.status in ('PENDING', 'APPROVED')
              and request.id <> :excludedRequestId
            group by day.quotaMonth
            """)
    List<MonthReservation> countReservedByMonthExcluding(
            @Param("internId") long internId,
            @Param("excludedRequestId") long excludedRequestId);

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

    /**
     * Loads an already-persisted request's frozen day entities in ascending date order.
     *
     * @param requestId leave request identifier
     * @return frozen day entities, ascending
     */
    @Query("""
            select day
            from LeaveRequestDayEntity day
            where day.request.id = :requestId
            order by day.id.leaveDate
            """)
    List<LeaveRequestDayEntity> findDaysByRequestId(@Param("requestId") long requestId);

    /**
     * Deletes a request's frozen days so an edit can replace them atomically inside the same transaction.
     *
     * @param requestId leave request identifier
     */
    @Modifying
    @Query("delete from LeaveRequestDayEntity day where day.request.id = :requestId")
    void deleteAllByRequestId(@Param("requestId") long requestId);
}