package com.lab.labtimesheet.feature.attendance.repository;

import com.lab.labtimesheet.feature.attendance.model.entity.AttendancePolicyEntity;
import java.util.List;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data access to the effective-dated policy timeline owned by Attendance.
 */
public interface AttendancePolicyRepository extends JpaRepository<AttendancePolicyEntity, Long> {

    /**
     * Loads the complete timeline in effective-date order for deterministic local-date resolution.
     *
     * @return ascending policy versions, including the 1970 seed
     */
    List<AttendancePolicyEntity> findAllByOrderByEffectiveFromAsc();

    /**
     * Finds the unique policy scheduled for one first-of-month effective date.
     *
     * @param effectiveFrom first effective local date
     * @return matching policy, when scheduled
     */
    Optional<AttendancePolicyEntity> findByEffectiveFrom(LocalDate effectiveFrom);

    /**
     * Locks the future version row while an Admin replaces its not-yet-effective values.
     *
     * @param effectiveFrom effective date of the version to lock
     * @return the matching policy, when a future version already exists
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select policy from AttendancePolicyEntity policy where policy.effectiveFrom = :effectiveFrom")
    Optional<AttendancePolicyEntity> findForUpdateByEffectiveFrom(@Param("effectiveFrom") LocalDate effectiveFrom);

    /**
     * Locks one attached policy row while a leave request reserves its monthly quota.
     *
     * @param id policy version identifier
     * @return locked policy, when present
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select policy from AttendancePolicyEntity policy where policy.id = :id")
    Optional<AttendancePolicyEntity> findForUpdateById(@Param("id") long id);
}
