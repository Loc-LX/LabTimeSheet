package com.lab.labtimesheet.feature.attendance.model.entity;

import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.Immutable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Read-only mapping used by Attendance request guards to inspect the existing monthly period state. */
@Entity
@Immutable
@Table(name = "attendance_periods")
public class AttendancePeriodEntity {

    @Id
    private Long id;

    @Column(name = "intern_user_id", nullable = false)
    private long internUserId;

    @Column(name = "period_month", nullable = false)
    private LocalDate periodMonth;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "finalized_at")
    private Instant finalizedAt;
}
