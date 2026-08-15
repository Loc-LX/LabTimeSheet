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

/**
 * Minimal Attendance-owned JPA mapping of leave request state used when evaluating frozen leave-day allocations.
 */
@Entity
@Table(name = "leave_requests")
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

    /**
     * Required by JPA.
     */
    protected LeaveRequestEntity() {}

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

    long id() {
        if (id == null) {
            throw new IllegalStateException("Leave request has not been persisted");
        }
        return id;
    }
}
