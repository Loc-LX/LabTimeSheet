package com.lab.labtimesheet.feature.attendance.repository;

import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceExceptionEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data persistence boundary for the current state of attendance exceptions. */
public interface AttendanceExceptionRepository extends JpaRepository<AttendanceExceptionEntity, Long> {

    /** Finds the existing exception for one attendance row and violation kind. */
    Optional<AttendanceExceptionEntity> findByAttendanceRecordIdAndViolationKind(long attendanceRecordId,
            String violationKind);

    /** Locks one exception while a decision is appended and applied. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select exception from AttendanceExceptionEntity exception where exception.id = :id")
    Optional<AttendanceExceptionEntity> findForUpdateById(@Param("id") long id);
}
