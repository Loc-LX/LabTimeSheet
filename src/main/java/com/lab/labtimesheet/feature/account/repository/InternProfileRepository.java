package com.lab.labtimesheet.feature.account.repository;

import java.time.LocalDate;
import java.util.List;

import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.InternshipStatus;
import com.lab.labtimesheet.feature.account.model.dto.EligibleInternOption;
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

    /**
     * Projects account-owned non-secret selection data for Interns eligible on one inclusive business date.
     * Results are ordered by display name, student code, then user ID for deterministic form rendering.
     *
     * @param globalRole required immutable Intern role
     * @param accountStatus required active account state
     * @param internshipStatus required active internship state
     * @param businessDate date that must fall within the inclusive internship range
     * @return eligible Intern selection projections without duplicate profile rows
     */
    @Query("""
            select new com.lab.labtimesheet.feature.account.model.dto.EligibleInternOption(
                u.id, u.displayName, p.studentCode, p.internshipStartDate, p.internshipEndDate)
            from InternProfile p
            join AppUser u on u.id = p.userId
            where u.globalRole = :globalRole
              and u.accountStatus = :accountStatus
              and p.internshipStatus = :internshipStatus
              and p.internshipStartDate <= :businessDate
              and p.internshipEndDate >= :businessDate
            order by u.displayName asc, p.studentCode asc, u.id asc
            """)
    List<EligibleInternOption> findEligibleInternOptions(
            @Param("globalRole") GlobalRole globalRole,
            @Param("accountStatus") AccountStatus accountStatus,
            @Param("internshipStatus") InternshipStatus internshipStatus,
            @Param("businessDate") LocalDate businessDate);

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
