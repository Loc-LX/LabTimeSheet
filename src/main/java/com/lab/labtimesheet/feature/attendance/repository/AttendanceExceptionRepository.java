package com.lab.labtimesheet.feature.attendance.repository;

import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceExceptionEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data persistence boundary for the current state of attendance exceptions. */
public interface AttendanceExceptionRepository extends JpaRepository<AttendanceExceptionEntity, Long> {

    /** Selects a bounded, ordered batch of pending submitted requests whose decision window has elapsed. */
    @Query("""
            select exception.id from AttendanceExceptionEntity exception
            where exception.source = 'REQUEST'
              and exception.status = 'PENDING'
              and exception.decisionDeadline <= :now
            order by exception.decisionDeadline asc, exception.id asc
            """)
    List<Long> findOverdueIds(@Param("now") Instant now, Pageable page);

    /** Checks whether an Intern has an unresolved exception request attached to a work date in the month. */
    @Query("""
            select (count(exception) > 0) from AttendanceExceptionEntity exception
            join AttendanceRecordEntity record on record.id = exception.attendanceRecordId
            where record.internUserId = :internUserId
              and record.workDate >= :monthStart
              and record.workDate < :nextMonth
              and exception.source = 'REQUEST'
              and exception.status in ('PENDING', 'OVERDUE')
            """)
    boolean existsUnresolvedRequestForInternAndWorkDate(
            @Param("internUserId") long internUserId,
            @Param("monthStart") LocalDate monthStart,
            @Param("nextMonth") LocalDate nextMonth);

    /** Finds the existing exception for one attendance row and violation kind. */
    Optional<AttendanceExceptionEntity> findByAttendanceRecordIdAndViolationKind(long attendanceRecordId,
            String violationKind);

    /** Locks one exception while a decision is appended and applied. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select exception from AttendanceExceptionEntity exception where exception.id = :id")
    Optional<AttendanceExceptionEntity> findForUpdateById(@Param("id") long id);
}
