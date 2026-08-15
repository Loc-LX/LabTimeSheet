package com.lab.labtimesheet.feature.attendance.repository;

import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceRecordEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data access to raw attendance rows and attached historical policy versions.
 */
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecordEntity, Long> {

    /**
     * Finds the unique row protected by the database's Intern/work-date constraint.
     *
     * @param internUserId Intern account identifier
     * @param workDate policy-local work date
     * @return row when the Intern has checked in on that date
     */
    Optional<AttendanceRecordEntity> findByInternUserIdAndWorkDate(long internUserId, LocalDate workDate);

    /**
     * Loads an Intern's inclusive history newest-first; each entity carries its attached policy.
     *
     * @param internUserId Intern account identifier
     * @param from inclusive first local date
     * @param to inclusive last local date
     * @return matching attendance rows newest-first
     */
    List<AttendanceRecordEntity> findByInternUserIdAndWorkDateBetweenOrderByWorkDateDesc(
            long internUserId, LocalDate from, LocalDate to);
}
