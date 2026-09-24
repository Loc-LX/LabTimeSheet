package com.lab.labtimesheet.feature.internship.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.lab.labtimesheet.feature.internship.model.InternshipStatus;
import com.lab.labtimesheet.feature.internship.model.entity.InternProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence boundary for Intern profiles and their lifecycle state. */
public interface InternProfileRepository extends JpaRepository<InternProfile, Long> {
    /** Returns whether an Intern profile has the requested lifecycle state. */
    boolean existsByUserIdAndInternshipStatus(Long userId, InternshipStatus status);

    /** Returns whether an Intern is in the requested state throughout the supplied inclusive date point. */
    boolean existsByUserIdAndInternshipStatusAndInternshipStartDateLessThanEqualAndInternshipEndDateGreaterThanEqual(
            Long userId, InternshipStatus status, LocalDate latestStartDate, LocalDate earliestEndDate);

    /**
     * Returns date-eligible profiles in database Student Code and account-ID order. Account lifecycle filtering is
     * composed over the identity service contract by the internship service.
     */
    @Query("""
            select p from InternProfile p
            where p.internshipStatus = :internshipStatus
              and p.internshipStartDate <= :businessDate
              and p.internshipEndDate >= :businessDate
            order by p.studentCode asc, p.userId asc
            """)
    List<InternProfile> findEligibleProfiles(
            @Param("internshipStatus") InternshipStatus internshipStatus,
            @Param("businessDate") LocalDate businessDate);

    /** Counts Intern profiles in a lifecycle state. */
    long countByInternshipStatus(InternshipStatus status);

    /** Returns due profile IDs in stable account order; the identity intersection is composed by the service. */
    @Query("""
            select p.userId from InternProfile p
            where p.internshipStatus = :internshipStatus
              and p.internshipStartDate <= :businessDate
              and p.internshipEndDate >= :businessDate
            order by p.userId asc
            """)
    List<Long> findDueUserIds(
            @Param("internshipStatus") InternshipStatus internshipStatus,
            @Param("businessDate") LocalDate businessDate);

    /** Returns all profiles in stable account order for the unfiltered Admin directory. */
    @Query("select p from InternProfile p order by p.userId asc")
    List<InternProfile> findAllOrderByUserId();

    /** Returns profiles whose Student Code matches normalized Admin search text. */
    @Query("""
            select p from InternProfile p
            where lower(trim(p.studentCode)) like concat('%', :search, '%')
            order by p.userId asc
            """)
    List<InternProfile> findByStudentCodeFilter(@Param("search") String search);

    /** Returns profiles for a batched account set in stable account order. */
    @Query("select p from InternProfile p where p.userId in :userIds order by p.userId asc")
    List<InternProfile> findByUserIdsOrderByUserId(@Param("userIds") Collection<Long> userIds);

    /** Locks an Intern profile for lifecycle mutation until the current transaction completes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from InternProfile p where p.userId = :userId")
    Optional<InternProfile> findForUpdateByUserId(@Param("userId") Long userId);
}
