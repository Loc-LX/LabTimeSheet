package com.lab.labtimesheet.feature.attendance.repository;

import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceExceptionDecisionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data persistence boundary for append-only attendance exception decisions. */
public interface AttendanceExceptionDecisionRepository
        extends JpaRepository<AttendanceExceptionDecisionEntity, Long> {

    /** Loads retained decisions in stable occurrence and identifier order. */
    List<AttendanceExceptionDecisionEntity>
            findByAttendanceExceptionIdOrderByOccurredAtAscIdAsc(long attendanceExceptionId);
}
