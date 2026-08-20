package com.lab.labtimesheet.feature.attendance.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * JPA persistence model for one missed-checkout correction request bound to its attendance record. The attached
 * historical policy remains reachable through the record, so submission deadlines stay anchored to scheduled end.
 */
@Entity
@Table(name = "attendance_corrections")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceCorrectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "attendance_record_id", nullable = false)
    private AttendanceRecordEntity attendanceRecord;

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
     * Creates a pending correction requesting a proposed checkout for the given attendance record.
     *
     * @param attendanceRecord corrected record with no raw checkout
     * @param requestedCheckoutAt proposed checkout instant on the original work date
     * @param reason non-blank explanation
     * @param submittedAt server submission instant
     * @param submissionDeadline inclusive scheduled-end-plus-24-hours deadline
     * @param decisionDeadline separate 24-hour decision window end
     */
    public AttendanceCorrectionEntity(
            AttendanceRecordEntity attendanceRecord,
            Instant requestedCheckoutAt,
            String reason,
            Instant submittedAt,
            Instant submissionDeadline,
            Instant decisionDeadline) {
        this.attendanceRecord = attendanceRecord;
        this.requestedCheckoutAt = requestedCheckoutAt;
        this.reason = reason;
        this.status = "PENDING";
        this.submittedAt = submittedAt;
        this.submissionDeadline = submissionDeadline;
        this.decisionDeadline = decisionDeadline;
    }

    /**
     * Returns the persisted identifier assigned by the database.
     *
     * @return correction identifier
     */
    public long id() {
        if (id == null) {
            throw new IllegalStateException("Correction has not been persisted");
        }
        return id;
    }

    /**
     * Returns the corrected attendance record with its permanently attached historical policy.
     *
     * @return attendance record entity
     */
    public AttendanceRecordEntity attendanceRecord() {
        return attendanceRecord;
    }

    /**
     * Returns the proposed checkout instant used as effective checkout once approved.
     *
     * @return proposed checkout instant
     */
    public Instant requestedCheckoutAt() {
        return requestedCheckoutAt;
    }

    /**
     * Returns the submitted explanation.
     *
     * @return reason text
     */
    public String reason() {
        return reason;
    }

    /**
     * Returns the current decision state.
     *
     * @return PENDING, APPROVED, or REJECTED
     */
    public String status() {
        return status;
    }

    /**
     * Returns the server submission instant.
     *
     * @return submission instant
     */
    public Instant submittedAt() {
        return submittedAt;
    }

    /**
     * Returns the inclusive submission deadline anchored to scheduled end plus 24 hours.
     *
     * @return submission deadline
     */
    public Instant submissionDeadline() {
        return submissionDeadline;
    }

    /**
     * Returns the separate decision-window end, submitted time plus 24 hours.
     *
     * @return decision deadline
     */
    public Instant decisionDeadline() {
        return decisionDeadline;
    }

    /**
     * Records an approval by an active Mentor. The service layer owns the decision-window guard; this mutator only
     * persists the decided state so the effective-checkout derivation can observe an approved proposal.
     *
     * @param mentorUserId deciding Mentor account identifier
     * @param at server decision instant
     * @param note optional decision note
     */
    public void approve(long mentorUserId, Instant at, String note) {
        this.status = "APPROVED";
        this.decidedByMentorUserId = mentorUserId;
        this.decidedAt = at;
        this.decisionNote = note;
    }

    /**
     * Returns the deciding Mentor account identifier once decided.
     *
     * @return deciding Mentor identifier, or {@code null} while pending
     */
    public Long decidedByMentorUserId() {
        return decidedByMentorUserId;
    }

    /**
     * Returns the server decision instant once decided.
     *
     * @return decision instant, or {@code null} while pending
     */
    public Instant decidedAt() {
        return decidedAt;
    }

    /**
     * Returns the optional Mentor decision note.
     *
     * @return decision note, or {@code null} when none was recorded
     */
    public String decisionNote() {
        return decisionNote;
    }

    /**
     * Returns the instant when the decided correction became locked after its decision window.
     *
     * @return lock instant, or {@code null} while the decision remains mutable
     */
    public Instant lockedAt() {
        return lockedAt;
    }
}