package com.lab.labtimesheet.feature.attendance.model.entity;

import com.lab.labtimesheet.feature.attendance.model.CorrectionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** JPA mapping for one missed-checkout correction and its separate decision deadline. */
@Entity
@Table(name = "attendance_corrections")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceCorrectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attendance_record_id", nullable = false)
    private long attendanceRecordId;

    @Column(name = "requested_checkout_at", nullable = false)
    private Instant requestedCheckoutAt;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    private String status;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "submission_deadline", nullable = false)
    private Instant submissionDeadline;

    @Column(name = "decision_deadline", nullable = false)
    private Instant decisionDeadline;

    @Column(name = "decided_by_mentor_user_id")
    private Long decidedByMentorUserId;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note")
    private String decisionNote;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Version
    private long version;

    /**
     * Creates a pending correction request before its submitted event is appended.
     *
     * @param attendanceRecordId raw attendance row with missing checkout
     * @param requestedCheckoutAt proposed effective checkout instant
     * @param reason normalized Intern explanation
     * @param submittedAt server submission timestamp
     * @param submissionDeadline inclusive scheduled-end-plus-24-hour deadline
     * @param decisionDeadline separate submitted-plus-24-hour decision deadline
     */
    public AttendanceCorrectionEntity(
            long attendanceRecordId,
            Instant requestedCheckoutAt,
            String reason,
            Instant submittedAt,
            Instant submissionDeadline,
            Instant decisionDeadline) {
        this.attendanceRecordId = attendanceRecordId;
        this.requestedCheckoutAt = requestedCheckoutAt;
        this.reason = reason;
        this.status = CorrectionStatus.PENDING.name();
        this.submittedAt = submittedAt;
        this.submissionDeadline = submissionDeadline;
        this.decisionDeadline = decisionDeadline;
    }

    /**
     * Returns persisted correction identifier.
     *
     * @return database identifier
     */
    public long id() {
        if (id == null) {
            throw new IllegalStateException("Correction has not been persisted");
        }
        return id;
    }

    /**
     * Returns attached attendance row identifier.
     *
     * @return raw attendance identifier
     */
    public long attendanceRecordId() {
        return attendanceRecordId;
    }

    /**
     * Returns proposed raw-server-equivalent checkout instant.
     *
     * @return proposed checkout instant
     */
    public Instant requestedCheckoutAt() {
        return requestedCheckoutAt;
    }

    /**
     * Returns normalized submission reason.
     *
     * @return non-blank reason
     */
    public String reason() {
        return reason;
    }

    /**
     * Returns current correction state.
     *
     * @return pending, approved, or rejected state
     */
    public CorrectionStatus status() {
        return CorrectionStatus.valueOf(status);
    }

    /**
     * Returns submission instant.
     *
     * @return server submission timestamp
     */
    public Instant submittedAt() {
        return submittedAt;
    }

    /**
     * Returns inclusive submission deadline.
     *
     * @return scheduled-end-plus-24-hour boundary
     */
    public Instant submissionDeadline() {
        return submissionDeadline;
    }

    /**
     * Returns exclusive decision-window expiry instant.
     *
     * @return submitted-plus-24-hour boundary
     */
    public Instant decisionDeadline() {
        return decisionDeadline;
    }

    /**
     * Returns Mentor decision actor.
     *
     * @return deciding user identifier, or {@code null} while pending/auto-rejected
     */
    public Long decidedByMentorUserId() {
        return decidedByMentorUserId;
    }

    /**
     * Returns Mentor decision instant.
     *
     * @return decision timestamp, or {@code null} while pending
     */
    public Instant decidedAt() {
        return decidedAt;
    }

    /**
     * Returns optional final lock timestamp.
     *
     * @return lock timestamp after the decision window, or {@code null}
     */
    public Instant lockedAt() {
        return lockedAt;
    }

    /**
     * Applies an approval transition without changing the raw attendance row.
     *
     * @param mentorUserId active Mentor actor
     * @param now server decision timestamp
     * @param note optional decision note
     */
    public void approve(long mentorUserId, Instant now, String note) {
        requireUnlocked();
        if (status() != CorrectionStatus.PENDING) {
            throw new IllegalStateException("Only pending correction can be approved");
        }
        status = CorrectionStatus.APPROVED.name();
        decidedByMentorUserId = mentorUserId;
        decidedAt = now;
        decisionNote = note;
    }

    /**
     * Applies a rejection transition without changing the raw attendance row.
     *
     * @param mentorUserId active Mentor actor
     * @param now server decision timestamp
     * @param note optional decision note
     */
    public void reject(long mentorUserId, Instant now, String note) {
        requireUnlocked();
        if (status() != CorrectionStatus.PENDING) {
            throw new IllegalStateException("Only pending correction can be rejected");
        }
        status = CorrectionStatus.REJECTED.name();
        decidedByMentorUserId = mentorUserId;
        decidedAt = now;
        decisionNote = note;
    }

    /**
     * Reopens a decision to pending while the separate decision window remains open.
     *
     * @param now server transition timestamp used for the surrounding event
     */
    public void reopen(Instant now) {
        requireUnlocked();
        if (status() == CorrectionStatus.PENDING) {
            throw new IllegalStateException("Pending correction is already open");
        }
        status = CorrectionStatus.PENDING.name();
        decidedByMentorUserId = null;
        decidedAt = null;
        decisionNote = null;
    }

    /**
     * Automatically rejects a still-pending correction when the decision deadline expires.
     *
     * @param now server expiry timestamp
     */
    public void autoReject(Instant now) {
        requireUnlocked();
        if (status() != CorrectionStatus.PENDING) {
            return;
        }
        status = CorrectionStatus.REJECTED.name();
        decidedAt = now;
        decisionNote = "Decision window expired";
    }

    /**
     * Locks an approved/rejected correction after its decision deadline.
     *
     * @param now server lock timestamp
     */
    public void lock(Instant now) {
        if (lockedAt == null) {
            lockedAt = now;
        }
    }

    private void requireUnlocked() {
        if (lockedAt != null) {
            throw new IllegalStateException("Correction is locked");
        }
    }
}
