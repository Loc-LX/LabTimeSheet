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
}
