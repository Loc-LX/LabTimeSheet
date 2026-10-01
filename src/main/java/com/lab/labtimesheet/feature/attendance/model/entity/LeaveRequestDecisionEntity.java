package com.lab.labtimesheet.feature.attendance.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/**
 * Append-only JPA mapping of leave request decision history.
 *
 * <p>Each row records one decision or amendment event for a leave request.
 * The database trigger {@code tr_leave_request_decisions_append_only} prevents
 * updates and deletes, so this entity is {@code @Immutable}.</p>
 */
@Entity
@Immutable
@Table(name = "leave_request_decisions")
public class LeaveRequestDecisionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "leave_request_id", nullable = false)
    private long leaveRequestId;

    @Column(name = "decision_kind", nullable = false)
    private String decisionKind;

    @Column(nullable = false)
    private String outcome;

    @Column(name = "decision_note")
    private String decisionNote;

    @Column(name = "actor_user_id", nullable = false)
    private long actorUserId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column
    private String reason;

    /** JPA constructor. */
    protected LeaveRequestDecisionEntity() {}

    /**
     * Creates a new append-only decision row.
     *
     * @param leaveRequestId owning leave request
     * @param decisionKind   DECISION or AMENDMENT
     * @param outcome        APPROVED or REJECTED
     * @param decisionNote   optional note
     * @param actorUserId    deciding Mentor
     * @param occurredAt     server decision timestamp
     * @param reason         mandatory for AMENDMENT, null for DECISION
     */
    public LeaveRequestDecisionEntity(
            long leaveRequestId,
            String decisionKind,
            String outcome,
            String decisionNote,
            long actorUserId,
            Instant occurredAt,
            String reason) {
        this.leaveRequestId = leaveRequestId;
        this.decisionKind = decisionKind;
        this.outcome = outcome;
        this.decisionNote = decisionNote;
        this.actorUserId = actorUserId;
        this.occurredAt = occurredAt;
        this.reason = reason;
    }

    /** Returns the persisted row identifier. */
    public long id() {
        if (id == null) {
            throw new IllegalStateException("Decision has not been persisted");
        }
        return id;
    }

    /** Returns the owning leave request identifier. */
    public long leaveRequestId() { return leaveRequestId; }

    /** Returns the decision kind ({@code DECISION} or {@code AMENDMENT}). */
    public String decisionKind() { return decisionKind; }

    /** Returns the decision outcome ({@code APPROVED} or {@code REJECTED}). */
    public String outcome() { return outcome; }

    /** Returns the optional decision note, or {@code null}. */
    public String decisionNote() { return decisionNote; }

    /** Returns the deciding actor's user identifier. */
    public long actorUserId() { return actorUserId; }

    /** Returns the server timestamp of the decision. */
    public Instant occurredAt() { return occurredAt; }

    /** Returns the mandatory reason for an amendment, or {@code null} for a decision. */
    public String reason() { return reason; }
}
