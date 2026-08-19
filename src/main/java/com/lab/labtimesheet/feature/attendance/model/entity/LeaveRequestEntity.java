package com.lab.labtimesheet.feature.attendance.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Minimal Attendance-owned JPA mapping of leave request state used when evaluating frozen leave-day allocations.
 */
@Entity
@Table(name = "leave_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaveRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "intern_user_id", nullable = false)
    private long internUserId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    private String status;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "first_counted_start_at", nullable = false)
    private Instant firstCountedStartAt;

    @Column(name = "decided_by_mentor_user_id")
    private Long decidedByMentorUserId;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note")
    private String decisionNote;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Version
    private long version;

    LeaveRequestEntity(
            long internUserId,
            LocalDate startDate,
            LocalDate endDate,
            String reason,
            Instant submittedAt,
            Instant firstCountedStartAt,
            long decidedByMentorUserId,
            Instant decidedAt) {
        this.internUserId = internUserId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
        this.status = "APPROVED";
        this.submittedAt = submittedAt;
        this.firstCountedStartAt = firstCountedStartAt;
        this.decidedByMentorUserId = decidedByMentorUserId;
        this.decidedAt = decidedAt;
    }

    private LeaveRequestEntity(
            long internUserId,
            LocalDate startDate,
            LocalDate endDate,
            String reason,
            Instant submittedAt,
            Instant firstCountedStartAt) {
        this.internUserId = internUserId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
        this.status = "PENDING";
        this.submittedAt = submittedAt;
        this.firstCountedStartAt = firstCountedStartAt;
    }

    /**
     * Creates a pending leave request reserving quota from submission until a Mentor decision.
     *
     * @param internUserId owning Intern account identifier
     * @param startDate inclusive first local date
     * @param endDate inclusive last local date
     * @param reason non-blank human-readable reason
     * @param submittedAt server submission instant
     * @param firstCountedStartAt scheduled start of the first counted workday in its policy timezone
     * @return new pending request
     */
    public static LeaveRequestEntity pending(
            long internUserId,
            LocalDate startDate,
            LocalDate endDate,
            String reason,
            Instant submittedAt,
            Instant firstCountedStartAt) {
        return new LeaveRequestEntity(internUserId, startDate, endDate, reason, submittedAt, firstCountedStartAt);
    }

    /**
     * Returns the persisted identifier assigned by the database.
     *
     * @return request identifier
     */
    public long id() {
        if (id == null) {
            throw new IllegalStateException("Leave request has not been persisted");
        }
        return id;
    }

    /**
     * Returns the owning Intern account identifier.
     *
     * @return owning Intern identifier
     */
    public long internUserId() {
        return internUserId;
    }

    /**
     * Returns the inclusive first local date.
     *
     * @return inclusive start date
     */
    public LocalDate startDate() {
        return startDate;
    }

    /**
     * Returns the inclusive last local date.
     *
     * @return inclusive end date
     */
    public LocalDate endDate() {
        return endDate;
    }

    /**
     * Returns the submitted reason.
     *
     * @return reason text
     */
    public String reason() {
        return reason;
    }

    /**
     * Returns the current decision state.
     *
     * @return PENDING, APPROVED, REJECTED, or CANCELLED
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
     * Returns the scheduled start of the first counted workday, the decision/cancellation boundary.
     *
     * @return first counted start instant
     */
    public Instant firstCountedStartAt() {
        return firstCountedStartAt;
    }

    /**
     * Marks the request approved by an active Mentor before the first counted start.
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
     * Marks the request rejected by an active Mentor before the first counted start.
     *
     * @param mentorUserId deciding Mentor account identifier
     * @param at server decision instant
     * @param note optional decision note
     */
    public void reject(long mentorUserId, Instant at, String note) {
        this.status = "REJECTED";
        this.decidedByMentorUserId = mentorUserId;
        this.decidedAt = at;
        this.decisionNote = note;
    }

    /**
     * Cancels a pending or approved request before the first counted start, releasing its reservation.
     *
     * @param at server cancellation instant
     */
    public void cancel(Instant at) {
        this.status = "CANCELLED";
        this.cancelledAt = at;
    }

    /**
     * Replaces the range and submission facts of a pending request after revalidation; the request keeps its identity.
     *
     * @param startDate inclusive new first local date
     * @param endDate inclusive new last local date
     * @param reason new non-blank reason
     * @param submittedAt new server submission instant
     * @param firstCountedStartAt new first counted workday's scheduled start
     */
    public void updateRange(
            LocalDate startDate,
            LocalDate endDate,
            String reason,
            Instant submittedAt,
            Instant firstCountedStartAt) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
        this.submittedAt = submittedAt;
        this.firstCountedStartAt = firstCountedStartAt;
    }

    /**
     * Returns the deciding Mentor account identifier once decided.
     *
     * @return deciding Mentor identifier, or {@code null} while pending or cancelled
     */
    public Long decidedByMentorUserId() {
        return decidedByMentorUserId;
    }

    /**
     * Returns the server decision instant once decided.
     *
     * @return decision instant, or {@code null} while pending or cancelled
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
     * Returns the server cancellation instant once cancelled.
     *
     * @return cancellation instant, or {@code null} until cancelled
     */
    public Instant cancelledAt() {
        return cancelledAt;
    }
}
