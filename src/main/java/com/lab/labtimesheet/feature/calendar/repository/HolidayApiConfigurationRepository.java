package com.lab.labtimesheet.feature.calendar.repository;

import java.util.List;
import java.util.Optional;

import com.lab.labtimesheet.feature.calendar.model.HolidayApiStatus;
import com.lab.labtimesheet.feature.calendar.model.entity.HolidayApiConfiguration;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** Integration-feature persistence boundary for retained HolidayAPI revisions. */
public interface HolidayApiConfigurationRepository extends JpaRepository<HolidayApiConfiguration, Long> {
    /** Finds the single revision in a given lifecycle state. */
    Optional<HolidayApiConfiguration> findByStatus(HolidayApiStatus status);

    /** Returns whether a revision exists in a lifecycle state. */
    boolean existsByStatus(HolidayApiStatus status);

    /** Returns retained revisions newest first for the non-secret History projection. */
    List<HolidayApiConfiguration> findAllByOrderByCreatedAtDescIdDesc();

    /** Returns all revisions currently in the requested lifecycle state. */
    List<HolidayApiConfiguration> findAllByStatus(HolidayApiStatus status);

    /** Locks a draft for atomic replacement of the active revision. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<HolidayApiConfiguration> findWithLockByIdAndStatus(Long id, HolidayApiStatus status);
}
