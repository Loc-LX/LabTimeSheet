package com.lab.labtimesheet.feature.attendance.model.entity;

import com.lab.labtimesheet.feature.attendance.model.CorrectionEventType;
import com.lab.labtimesheet.feature.attendance.model.CorrectionStatus;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionEventView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Append-only JPA mapping for one correction transition event. */
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

    @Column
    private String note;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /**
     * Creates one immutable transition event.
     *
     * @param correctionId attached correction identifier
     * @param eventType transition kind
     * @param fromStatus previous correction state, or {@code null} for submission
     * @param toStatus resulting correction state
     * @param actorUserId Mentor actor, or {@code null} for scheduler transitions
     * @param note optional safe decision or expiry note
     * @param occurredAt server timestamp
     */
    public AttendanceCorrectionEventEntity(
            long correctionId,
            CorrectionEventType eventType,
            CorrectionStatus fromStatus,
            CorrectionStatus toStatus,
            Long actorUserId,
            String note,
            Instant occurredAt) {
        this.correctionId = correctionId;
        this.eventType = eventType.name();
        this.fromStatus = fromStatus == null ? null : fromStatus.name();
        this.toStatus = toStatus.name();
        this.actorUserId = actorUserId;
        this.note = note;
        this.occurredAt = occurredAt;
    }

    /**
     * Converts this append-only row into its non-secret DTO.
     *
     * @return immutable transition projection
     */
    public CorrectionEventView toView() {
        return new CorrectionEventView(
                id,
                CorrectionEventType.valueOf(eventType),
                fromStatus == null ? null : CorrectionStatus.valueOf(fromStatus),
                CorrectionStatus.valueOf(toStatus),
                actorUserId,
                note,
                occurredAt);
    }
}
