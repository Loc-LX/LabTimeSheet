package com.lab.labtimesheet.feature.attendance.repository;

import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceCorrectionEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceRecordEntity;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionSummary;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data persistence boundary for missed-checkout correction state. */
public interface AttendanceCorrectionRepository extends JpaRepository<AttendanceCorrectionEntity, Long> {

    /**
     * Lists correction rows visible to one owning Intern without hydrating foreign state.
     *
     * @param internUserId owning Intern account
     * @return newest-first correction summaries
     */
    @Query("""
            select new com.lab.labtimesheet.feature.attendance.model.dto.CorrectionSummary(
                    correction.id, correction.attendanceRecordId, record.internUserId,
                    correction.requestedCheckoutAt, correction.reason, correction.status,
                    correction.submittedAt, correction.decisionDeadline)
            from AttendanceCorrectionEntity correction, AttendanceRecordEntity record
            where correction.attendanceRecordId = record.id
              and record.internUserId = :internUserId
            order by correction.submittedAt desc, correction.id desc
            """)
    List<CorrectionSummary> findSummariesByInternUserId(@Param("internUserId") long internUserId);

    /** @return newest-first correction summaries for an active global Mentor */
    @Query("""
            select new com.lab.labtimesheet.feature.attendance.model.dto.CorrectionSummary(
                    correction.id, correction.attendanceRecordId, record.internUserId,
                    correction.requestedCheckoutAt, correction.reason, correction.status,
                    correction.submittedAt, correction.decisionDeadline)
            from AttendanceCorrectionEntity correction, AttendanceRecordEntity record
            where correction.attendanceRecordId = record.id
            order by correction.submittedAt desc, correction.id desc
            """)
    List<CorrectionSummary> findAllSummaries();

    /** Scalar Intern route used to lock Account rows before locking one correction row. */
    @Query("""
            select record.internUserId
            from AttendanceCorrectionEntity correction, AttendanceRecordEntity record
            where correction.attendanceRecordId = record.id
              and correction.id = :id
            """)
    Optional<Long> findInternUserIdById(@Param("id") long id);

    /** Bounded scalar scheduler route; no correction entity is hydrated before Account locks are held. */
    @Query("""
            select correction.id as correctionId,
                   correction.attendanceRecordId as attendanceRecordId,
                   record.internUserId as internUserId
            from AttendanceCorrectionEntity correction, AttendanceRecordEntity record
            where correction.attendanceRecordId = record.id
              and correction.lockedAt is null
              and correction.decisionDeadline <= :now
            order by correction.id asc
            """)
    List<ExpiredRecipientRoute> findExpiredRecipientRoutes(
            @Param("now") Instant now, Pageable pageable);

    /** Immutable scalar route for one bounded correction-expiry candidate. */
    interface ExpiredRecipientRoute {
        /** @return correction identifier */
        long getCorrectionId();

        /** @return attached raw attendance identifier */
        long getAttendanceRecordId();

        /** @return owning Intern account identifier */
        long getInternUserId();
    }

    /**
     * Finds the sole correction attached to one attendance row.
     *
     * @param attendanceRecordId raw attendance identifier
     * @return existing correction, if submitted
     */
    Optional<AttendanceCorrectionEntity> findByAttendanceRecordId(long attendanceRecordId);

    /**
     * Locks a correction for one atomic Mentor or expiry transition.
     *
     * @param id correction identifier
     * @return locked correction, when present
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select correction from AttendanceCorrectionEntity correction where correction.id = :id")
    Optional<AttendanceCorrectionEntity> findForUpdateById(@Param("id") long id);

    /**
     * Locks all corrections attached to an already loaded attendance history in one bulk read.
     * The caller samples server time only after this lock query returns, then applies request-time expiry
     * without issuing one correction lookup per attendance row.
     *
     * @param attendanceRecordIds raw attendance identifiers already loaded by the history query
     * @return locked corrections ordered by ascending correction identifier, the canonical order shared with expiry
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select correction from AttendanceCorrectionEntity correction
            where correction.attendanceRecordId in :attendanceRecordIds
            order by correction.id asc
            """)
    List<AttendanceCorrectionEntity> findByAttendanceRecordIdInForUpdate(
            @Param("attendanceRecordIds") Collection<Long> attendanceRecordIds);

    /**
     * Lists bounded unlocked corrections whose decision window has expired.
     *
     * @param now authoritative server instant
     * @param pageable database page and limit, applied before materialization
     * @return candidates ordered by ascending correction identifier, the canonical lock order shared with history
     */
    @Query("""
            select correction from AttendanceCorrectionEntity correction
            where correction.lockedAt is null and correction.decisionDeadline <= :now
            order by correction.id asc
            """)
    List<AttendanceCorrectionEntity> findExpiredUnlocked(
            @Param("now") Instant now, Pageable pageable);
}
