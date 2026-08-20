package com.lab.labtimesheet.feature.attendance.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * JPA persistence model for one immutable correction transition in the append-only decision history.
 */
@Entity
@Table(name = "attendance_correction_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceCorrectionEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "correction_id", nullable = false)
    private long correctionId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "from_status")
    private String fromStatus;

    @Column(name = "to_status", nullable = false)
    private String toStatus;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "note")
    private String note;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /**
     * Creates one immutable correction transition.
     *
     * @param correctionId owning correction identifier
     * @param eventType SUBMITTED, APPROVED, REJECTED, REOPENED, AUTO_REJECTED, or LOCKED
     * @param fromStatus previous state, or {@code null} for submission
     * @param toStatus resulting state
     * @param actorUserId acting Intern or Mentor account identifier, or {@code null} for a scheduler
     * @param note optional transition note
     * @param occurredAt server transition instant
     */
    public AttendanceCorrectionEventEntity(
            long correctionId,
            String eventType,
            String fromStatus,
            String toStatus,
            Long actorUserId,
            String note,
            Instant occurredAt) {
        this.correctionId = correctionId;
        this.eventType = eventType;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorUserId = actorUserId;
        this.note = note;
        this.occurredAt = occurredAt;
    }

    /**
     * Returns the owning correction identifier.
     *
     * @return correction identifier
     */
    public long correctionId() {
        return correctionId;
    }

    /**
     * Returns the transition type.
     *
     * @return event type
     */
    public String eventType() {
        return eventType;
    }

    /**
     * Returns the previous state before this transition.
     *
     * @return previous state, or {@code null} for submission
     */
    public String fromStatus() {
        return fromStatus;
    }

    /**
     * Returns the resulting state.
     *
     * @return resulting state
     */
    public String toStatus() {
        return toStatus;
    }

    /**
     * Returns the acting Intern or Mentor account identifier.
     *
     * @return actor identifier, or {@code null} for a scheduler
     */
    public Long actorUserId() {
        return actorUserId;
    }

    /**
     * Returns the optional transition note.
     *
     * @return transition note, or {@code null}
     */
    public String note() {
        return note;
    }

    /**
     * Returns the server transition instant.
     *
     * @return occurred-at instant
     */
    public Instant occurredAt() {
        return occurredAt;
    }
}