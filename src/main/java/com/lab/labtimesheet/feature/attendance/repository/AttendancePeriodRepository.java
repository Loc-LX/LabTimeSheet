package com.lab.labtimesheet.feature.attendance.repository;

import com.lab.labtimesheet.feature.attendance.model.entity.AttendancePeriodEntity;
import java.time.LocalDate;
import org.springframework.data.repository.Repository;

/** Read-only query boundary for monthly Attendance period guards. */
public interface AttendancePeriodRepository extends Repository<AttendancePeriodEntity, Long> {

    /** Reports whether an Intern's month has already been finalized. */
    boolean existsByInternUserIdAndPeriodMonthAndStatus(long internUserId, LocalDate periodMonth, String status);
}
