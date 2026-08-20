package com.lab.labtimesheet.feature.attendance.repository;

import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceCorrectionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data access to missed-checkout correction requests and their attached attendance records.
 */
public interface AttendanceCorrectionRepository extends JpaRepository<AttendanceCorrectionEntity, Long> {

    /**
     * Loads the unique correction for an attendance record, if any. The database unique constraint guarantees at
     * most one row, so this doubles as the duplicate-submission pre-check.
     *
     * @param attendanceRecordId corrected attendance record identifier
     * @return the single correction for the record, if present
     */
    Optional<AttendanceCorrectionEntity> findByAttendanceRecordId(long attendanceRecordId);

    /**
     * Loads an Intern's corrections newest-first by joining their attendance records.
     *
     * @param internUserId owning Intern account identifier
     * @return corrections ordered by newest submission
     */
    @Query("""
            select correction from AttendanceCorrectionEntity correction
            where correction.attendanceRecord.internUserId = :internUserId
            order by correction.id desc
            """)
    List<AttendanceCorrectionEntity> findByInternUserIdOrderByIdDesc(
            @Param("internUserId") long internUserId);
}