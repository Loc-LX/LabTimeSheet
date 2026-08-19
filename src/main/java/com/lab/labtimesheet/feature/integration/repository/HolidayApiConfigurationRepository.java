package com.lab.labtimesheet.feature.integration.repository;

import com.lab.labtimesheet.feature.integration.model.HolidayApiStatus;
import com.lab.labtimesheet.feature.integration.model.entity.HolidayApiConfiguration;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** Integration-feature persistence boundary for retained HolidayAPI revisions. */
public interface HolidayApiConfigurationRepository extends JpaRepository<HolidayApiConfiguration, Long> {
    /** Finds the single revision in a given lifecycle state. */
    Optional<HolidayApiConfiguration> findByStatus(HolidayApiStatus status);

    /** Returns whether a revision exists in a lifecycle state. */
    boolean existsByStatus(HolidayApiStatus status);

    /** Locks the identified draft/active revision for atomic lifecycle mutation. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<HolidayApiConfiguration> findWithLockByIdAndStatus(Long id, HolidayApiStatus status);
}
