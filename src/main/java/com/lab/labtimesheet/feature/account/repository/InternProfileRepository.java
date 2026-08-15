package com.lab.labtimesheet.feature.account.repository;

import java.time.LocalDate;

import com.lab.labtimesheet.feature.account.model.InternshipStatus;
import com.lab.labtimesheet.feature.account.model.entity.InternProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Account-feature persistence boundary for Intern lifecycle and eligibility. */
public interface InternProfileRepository extends JpaRepository<InternProfile, Long> {
    /** Returns whether an Intern profile has the requested lifecycle state. */
    boolean existsByUserIdAndInternshipStatus(Long userId, InternshipStatus status);

    /** Returns whether an Intern is in the requested state throughout the supplied inclusive date point. */
    boolean existsByUserIdAndInternshipStatusAndInternshipStartDateLessThanEqualAndInternshipEndDateGreaterThanEqual(
            Long userId, InternshipStatus status, LocalDate latestStartDate, LocalDate earliestEndDate);

    /** Counts Intern profiles in a lifecycle state. */
    long countByInternshipStatus(InternshipStatus status);

    /**
     * Locks an Intern profile for lifecycle mutation until the current transaction completes.
     *
     * @param userId owning account identifier
     * @return locked profile, if present
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from InternProfile p where p.userId = :userId")
    java.util.Optional<InternProfile> findForUpdateByUserId(@Param("userId") Long userId);
}
