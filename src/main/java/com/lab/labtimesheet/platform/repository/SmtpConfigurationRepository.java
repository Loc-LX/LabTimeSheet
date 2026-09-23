package com.lab.labtimesheet.platform.repository;

import java.util.List;
import java.util.Optional;

import com.lab.labtimesheet.platform.model.SmtpStatus;
import com.lab.labtimesheet.platform.model.entity.SmtpConfiguration;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** Integration-feature persistence boundary for retained SMTP revisions. */
public interface SmtpConfigurationRepository extends JpaRepository<SmtpConfiguration, Long> {
    /** Finds the single revision in a given lifecycle state. */
    Optional<SmtpConfiguration> findByStatus(SmtpStatus status);

    /** Returns whether a revision exists in a lifecycle state. */
    boolean existsByStatus(SmtpStatus status);

    /** Returns retained revisions newest first for the Admin-only non-secret History projection. */
    List<SmtpConfiguration> findAllByOrderByCreatedAtDescIdDesc();

    /** Returns all revisions currently in the requested lifecycle state. */
    List<SmtpConfiguration> findAllByStatus(SmtpStatus status);

    /**
     * Locks the identified revision in the expected state for atomic activation.
     *
     * @param id SMTP revision identifier
     * @param status required current lifecycle state
     * @return locked revision, if present
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SmtpConfiguration> findWithLockByIdAndStatus(Long id, SmtpStatus status);
}
