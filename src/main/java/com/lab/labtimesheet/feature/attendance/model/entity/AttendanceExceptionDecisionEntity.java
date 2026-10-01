package com.lab.labtimesheet.feature.attendance.model.entity;

import com.lab.labtimesheet.feature.attendance.model.AttendanceExceptionDecisionKind;
import com.lab.labtimesheet.feature.attendance.model.AttendanceExceptionOutcome;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceExceptionDecisionView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** Immutable JPA mapping for one append-only attendance exception decision. */
@Entity
@Immutable
@Table(name = "attendance_exception_decisions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceExceptionDecisionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attendance_exception_id", nullable = false)
    private long attendanceExceptionId;

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

    /**
     * Creates an immutable decision history row.
     *
     * @param exceptionId parent exception identifier
     * @param kind decision, amendment, or reversal
     * @param outcome effective outcome represented by the row
     * @param note optional decision note
     * @param actorUserId already-authorized actor identifier
     * @param occurredAt server-authoritative instant
     * @param reason required for amendment and reversal
     */
    public AttendanceExceptionDecisionEntity(long exceptionId, AttendanceExceptionDecisionKind kind,
            AttendanceExceptionOutcome outcome, String note, long actorUserId, Instant occurredAt, String reason) {
        this.attendanceExceptionId = exceptionId;
        this.decisionKind = kind.name();
        this.outcome = outcome.name();
        this.decisionNote = note;
        this.actorUserId = actorUserId;
        this.occurredAt = occurredAt;
        this.reason = reason;
    }

    /** @return immutable decision history view */
    public AttendanceExceptionDecisionView toView() {
        return new AttendanceExceptionDecisionView(id, AttendanceExceptionDecisionKind.valueOf(decisionKind),
                AttendanceExceptionOutcome.valueOf(outcome), decisionNote, actorUserId, occurredAt, reason);
    }
}
