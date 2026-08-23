package com.lab.labtimesheet.feature.attendance.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import com.lab.labtimesheet.feature.attendance.model.LeaveStatus;
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

    /**
     * Creates a pending leave request before its frozen eligible-day allocations are persisted.
     *
     * @param internUserId owning Intern
     * @param startDate inclusive requested range start
     * @param endDate inclusive requested range end
     * @param reason normalized explanation
     * @param submittedAt server submission timestamp
     * @param firstCountedStartAt first scheduled workday start that freezes the request
     */
    public LeaveRequestEntity(
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
        this.status = LeaveStatus.PENDING.name();
        this.submittedAt = submittedAt;
        this.firstCountedStartAt = firstCountedStartAt;
    }

    /**
     * Returns the persisted request identifier, failing before the initial flush.
     *
     * @return database identifier
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
     * @return Intern identifier
     */
    public long internUserId() {
        return internUserId;
    }

    /**
     * Returns the inclusive requested range start.
     *
     * @return first requested local date
     */
    public LocalDate startDate() {
        return startDate;
    }

    /**
     * Returns the inclusive requested range end.
     *
     * @return last requested local date
     */
    public LocalDate endDate() {
        return endDate;
    }

    /**
     * Returns the normalized reason.
     *
     * @return non-blank explanation
     */
    public String reason() {
        return reason;
    }

    /**
     * Returns the current durable leave state.
     *
     * @return pending, approved, rejected, or cancelled
     */
    public LeaveStatus status() {
        return LeaveStatus.valueOf(status);
    }

    /**
     * Returns the first scheduled start that freezes this request.
     *
     * @return first counted start instant
     */
    public Instant firstCountedStartAt() {
        return firstCountedStartAt;
    }

    /**
     * Returns the submission timestamp.
     *
     * @return server submission instant
     */
    public Instant submittedAt() {
        return submittedAt;
    }

    /**
     * Returns the optional Mentor decision actor.
     *
     * @return Mentor identifier, or {@code null} for pending/automatic rejection
     */
    public Long decidedByMentorUserId() {
        return decidedByMentorUserId;
    }

    /**
     * Returns the optional Mentor decision timestamp.
     *
     * @return decision instant, or {@code null} while pending
     */
    public Instant decidedAt() {
        return decidedAt;
    }

    /**
     * Returns the optional cancellation timestamp.
     *
     * @return cancellation instant, or {@code null} when not cancelled
     */
    public Instant cancelledAt() {
        return cancelledAt;
    }

    /**
     * Replaces the still-pending range before its first counted start.
     *
     * @param startDate replacement inclusive range start
     * @param endDate replacement inclusive range end
     * @param reason replacement normalized reason
     * @param firstCountedStartAt replacement first counted start instant
     */
    public void edit(LocalDate startDate, LocalDate endDate, String reason, Instant firstCountedStartAt) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
        this.firstCountedStartAt = firstCountedStartAt;
    }

    /**
     * Applies a Mentor approval inside the decision boundary.
     *
     * @param mentorUserId active Mentor actor
     * @param now server decision timestamp
     */
    public void approve(long mentorUserId, Instant now) {
        requirePending();
        status = LeaveStatus.APPROVED.name();
        decidedByMentorUserId = mentorUserId;
        decidedAt = now;
    }

    /**
     * Applies a Mentor rejection inside the decision boundary.
     *
     * @param actorUserId active Mentor actor
     * @param now server decision timestamp
     */
    public void reject(long actorUserId, Instant now) {
        if (status != null && !LeaveStatus.PENDING.name().equals(status)) {
            throw new IllegalStateException("Only pending leave can be rejected");
        }
        status = LeaveStatus.REJECTED.name();
        decidedByMentorUserId = actorUserId;
        decidedAt = now;
    }

    /**
     * Applies the scheduler/request-time expiry transition without inventing an account foreign key.
     *
     * @param now server timestamp at the inclusive first-counted-start boundary
     */
    public void autoReject(Instant now) {
        if (status != null && !LeaveStatus.PENDING.name().equals(status)) {
            return;
        }
        status = LeaveStatus.REJECTED.name();
        decidedByMentorUserId = null;
        decidedAt = now;
    }

    /**
     * Cancels a pending or approved request before its first counted start.
     *
     * @param now server cancellation timestamp
     */
    public void cancel(Instant now) {
        if (!LeaveStatus.PENDING.name().equals(status) && !LeaveStatus.APPROVED.name().equals(status)) {
            throw new IllegalStateException("Only pending or approved leave can be cancelled");
        }
        status = LeaveStatus.CANCELLED.name();
        cancelledAt = now;
    }

    private void requirePending() {
        if (!LeaveStatus.PENDING.name().equals(status)) {
            throw new IllegalStateException("Only pending leave can be approved");
        }
    }
}
