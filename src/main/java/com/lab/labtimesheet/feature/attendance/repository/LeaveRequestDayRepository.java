package com.lab.labtimesheet.feature.attendance.repository;

import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestDayEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestDayId;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data persistence boundary for frozen quota-consuming leave dates. */
public interface LeaveRequestDayRepository extends JpaRepository<LeaveRequestDayEntity, LeaveRequestDayId> {

    /**
     * Counts reserved days in one quota month for pending/approved requests.
     *
     * @param internId owning Intern
     * @param quotaMonth first date of the month
     * @param statuses quota-reserving request states
     * @return reserved allocation count
     */
    @Query("""
            select count(day) from LeaveRequestDayEntity day
            join day.request request
            where request.internUserId = :internId
              and day.quotaMonth = :quotaMonth
              and request.status in :statuses
            """)
    long countReserved(
            @Param("internId") long internId,
            @Param("quotaMonth") LocalDate quotaMonth,
            @Param("statuses") List<String> statuses);

    /**
     * Counts reserved days while excluding one request during an edit.
     *
     * @param internId owning Intern
     * @param quotaMonth first date of the month
     * @param statuses quota-reserving request states
     * @param excludeId request being edited, or {@code null}
     * @return reserved allocation count excluding the edited request
     */
    @Query("""
            select count(day) from LeaveRequestDayEntity day
            join day.request request
            where request.internUserId = :internId
              and day.quotaMonth = :quotaMonth
              and request.status in :statuses
              and (:excludeId is null or request.id <> :excludeId)
            """)
    long countReservedExcluding(
            @Param("internId") long internId,
            @Param("quotaMonth") LocalDate quotaMonth,
            @Param("statuses") List<String> statuses,
            @Param("excludeId") Long excludeId);

    /**
     * Loads a request's allocations in date order for the persistence-free view.
     *
     * @param requestId leave request identifier
     * @return frozen allocation rows
     */
    @Query("select day from LeaveRequestDayEntity day where day.request.id = :requestId order by day.id.leaveDate asc")
    List<LeaveRequestDayEntity> findByRequestIdOrderByLeaveDate(@Param("requestId") long requestId);

    /**
     * Deletes all frozen allocations while a pending request is edited.
     *
     * @param requestId leave request identifier
     */
    void deleteByRequestId(long requestId);
}
