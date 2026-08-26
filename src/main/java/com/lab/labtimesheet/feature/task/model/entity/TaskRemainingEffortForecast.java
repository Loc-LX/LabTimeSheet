package com.lab.labtimesheet.feature.task.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Immutable append-only initial Remaining effort forecast persisted at manual reassignment. */
@Entity
@Table(name = "task_remaining_effort_forecasts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskRemainingEffortForecast {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "project_id", nullable = false) private long projectId;
    @Column(name = "task_id", nullable = false) private long taskId;
    @Column(name = "incoming_membership_id", nullable = false) private long incomingMembershipId;
    @Column(name = "forecasting_leader_membership_id", nullable = false) private long forecastingLeaderMembershipId;
    @Column(name = "assignment_started_at", nullable = false) private Instant assignmentStartedAt;
    @Column(name = "remaining_minutes", nullable = false) private int remainingMinutes;
    @Column(name = "actual_minutes_snapshot", nullable = false) private long actualMinutesSnapshot;
    @Column(name = "initial_note") private String initialNote;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    public TaskRemainingEffortForecast(long projectId, long taskId, long incomingMembershipId,
            long forecastingLeaderMembershipId, Instant assignmentStartedAt, int remainingMinutes,
            long actualMinutesSnapshot, String initialNote, Instant createdAt) {
        this.projectId = projectId;
        this.taskId = taskId;
        this.incomingMembershipId = incomingMembershipId;
        this.forecastingLeaderMembershipId = forecastingLeaderMembershipId;
        this.assignmentStartedAt = assignmentStartedAt;
        this.remainingMinutes = remainingMinutes;
        this.actualMinutesSnapshot = actualMinutesSnapshot;
        this.initialNote = initialNote;
        this.createdAt = createdAt;
    }
}
