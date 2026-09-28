package com.lab.labtimesheet.feature.attendance.model.entity;

import com.lab.labtimesheet.feature.attendance.model.AttendanceExceptionKind;
import com.lab.labtimesheet.feature.attendance.model.AttendanceExceptionOutcome;
import com.lab.labtimesheet.feature.attendance.model.AttendanceExceptionSource;
import com.lab.labtimesheet.feature.attendance.model.AttendanceExceptionStatus;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceExceptionView;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceExceptionDecisionView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** JPA mapping for the current state of one attendance exception. */
@Entity
@Table(name = "attendance_exceptions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceExceptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attendance_record_id", nullable = false)
    private long attendanceRecordId;

    @Column(name = "violation_kind", nullable = false)
    private String violationKind;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private String reason;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "submission_deadline", nullable = false)
    private Instant submissionDeadline;

    @Column(name = "decision_deadline", nullable = false)
    private Instant decisionDeadline;

    @Column(nullable = false)
    private String status;

    @Column(name = "decided_by_mentor_user_id")
    private Long decidedByMentorUserId;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note")
    private String decisionNote;

    /**
     * Creates the initial pending request row at the authoritative submission instant.
     *
     * @param attendanceRecordId attendance row that retains the violation
     * @param kind late arrival or early departure
     * @param source request or direct Mentor mark
     * @param reason nonblank request/mark reason
     * @param submittedAt server submission instant
     * @param submissionDeadline submission boundary retained by V3
     * @param decisionDeadline decision boundary retained by V3
     */
    public AttendanceExceptionEntity(long attendanceRecordId, AttendanceExceptionKind kind,
            AttendanceExceptionSource source, String reason, Instant submittedAt,
            Instant submissionDeadline, Instant decisionDeadline) {
        this.attendanceRecordId = attendanceRecordId;
        this.violationKind = kind.name();
        this.source = source.name();
        this.reason = reason;
        this.submittedAt = submittedAt;
        this.submissionDeadline = submissionDeadline;
        this.decisionDeadline = decisionDeadline;
        this.status = AttendanceExceptionStatus.PENDING.name();
    }

    /** @return persisted exception identifier */
    public long id() { return id; }

    /** @return current status */
    public AttendanceExceptionStatus status() { return AttendanceExceptionStatus.valueOf(status); }

    /**
     * Synchronizes the current effective decision fields with the decision row inserted in this transaction.
     *
     * @param outcome effective decision outcome
     * @param actorUserId deciding Mentor identifier
     * @param occurredAt server time used by the decision row
     * @param note note stored in the decision row
     */
    public void applyDecision(AttendanceExceptionOutcome outcome, long actorUserId, Instant occurredAt, String note) {
        status = outcome.name();
        decidedByMentorUserId = actorUserId;
        decidedAt = occurredAt;
        decisionNote = note;
    }

    /**
     * Projects current state and ordered append-only history to the Attendance service boundary.
     *
     * @param decisions ordered decision history
     * @return immutable view
     */
    public AttendanceExceptionView toView(List<AttendanceExceptionDecisionView> decisions) {
        return new AttendanceExceptionView(id, attendanceRecordId, AttendanceExceptionKind.valueOf(violationKind),
                AttendanceExceptionSource.valueOf(source), reason, submittedAt, submissionDeadline, decisionDeadline,
                status(), decidedByMentorUserId, decidedAt, decisionNote, decisions);
    }
}
