package com.lab.labtimesheet.feature.project.model.entity;

import com.lab.labtimesheet.feature.project.model.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Append-only evidence for a Task block, unblock, or reopen. */
@Entity
@Table(name = "task_status_transitions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskStatusTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private long taskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 24)
    private TaskStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 24)
    private TaskStatus toStatus;

    @Column(name = "actor_user_id", nullable = false)
    private long actorUserId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(columnDefinition = "text")
    private String reason;

    /** Creates one immutable transition fact for the owning service transaction. */
    public TaskStatusTransition(
            long taskId,
            TaskStatus fromStatus,
            TaskStatus toStatus,
            long actorUserId,
            Instant occurredAt,
            String reason) {
        this.taskId = taskId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorUserId = actorUserId;
        this.occurredAt = occurredAt;
        this.reason = reason;
    }
}
