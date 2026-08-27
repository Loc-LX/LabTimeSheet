package com.lab.labtimesheet.feature.task.model.entity;

import com.lab.labtimesheet.feature.task.model.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Persisted Task aggregate row with one current same-Project assignee.
 *
 * <p>Creator attribution never changes. Assignment actor/time describe the current assignment,
 * deletion is soft and historical, and the JPA version detects conflicting updates. Newly created
 * Tasks always begin in TODO; status changes must follow the fixed {@link TaskStatus} graph.
 */
@Entity
@Table(name = "tasks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private long projectId;

    @Column(name = "assignee_membership_id", nullable = false)
    private long assigneeMembershipId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TaskStatus status;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "created_by_membership_id", nullable = false)
    private long creatorMembershipId;

    @Column(name = "assigned_by_membership_id", nullable = false)
    private long assignerMembershipId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by_membership_id")
    @Getter
    private Long deletedByMembershipId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @Getter
    private Instant updatedAt;

    @Version
    @Getter
    private long version;

    /**
     * Creates a TODO Task and records the creating membership as both creator and assigner.
     *
     * @param projectId owning Project identifier
     * @param assigneeMembershipId active membership identifier in the same Project
     * @param title normalized required title
     * @param description optional normalized description
     * @param dueDate optional validated business due date
     * @param actorMembershipId authenticated creating membership identifier
     * @param now server-controlled creation and assignment instant
     */
    public Task(
            long projectId,
            long assigneeMembershipId,
            String title,
            String description,
            LocalDate dueDate,
            long actorMembershipId,
            Instant now) {
        this.projectId = projectId;
        this.assigneeMembershipId = assigneeMembershipId;
        this.title = title;
        this.description = description;
        this.status = TaskStatus.TODO;
        this.dueDate = dueDate;
        this.assignedAt = now;
        this.creatorMembershipId = actorMembershipId;
        this.assignerMembershipId = actorMembershipId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Creates a Task with an optional Leader-provided whole-Task estimate. */
    public Task(long projectId, long assigneeMembershipId, String title, String description,
            LocalDate dueDate, long actorMembershipId, Instant now, Integer estimatedMinutes) {
        this(projectId, assigneeMembershipId, title, description, dueDate, actorMembershipId, now);
        applyEstimatedMinutes(estimatedMinutes, now);
    }

    /** Applies an estimate using the caller's injected server clock. */
    public void applyEstimatedMinutes(Integer estimatedMinutes, Instant now) {
        if (estimatedMinutes != null && (estimatedMinutes < 1 || estimatedMinutes > 527040)) {
            throw new IllegalArgumentException("Task estimate must be between 1 and 527040 minutes");
        }
        this.estimatedMinutes = estimatedMinutes;
        this.updatedAt = now;
    }


    /**
     * Applies one permitted fixed-graph status transition and advances the update timestamp.
     *
     * @param target next Task status
     * @param now server-controlled mutation instant
     * @throws IllegalArgumentException when the requested direct transition is forbidden
     */
    public void changeStatus(TaskStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalArgumentException("Task status transition is not allowed");
        }
        status = target;
        updatedAt = now;
    }

    /**
     * Replaces editable Task definition fields without changing attribution or lifecycle facts.
     *
     * @param title normalized required title
     * @param description optional normalized description
     * @param dueDate optional validated business due date
     * @param now server-controlled edit instant
     */
    public void updateDefinition(String title, String description, LocalDate dueDate, Instant now) {
        this.title = title;
        this.description = description;
        this.dueDate = dueDate;
        this.updatedAt = now;
    }

    /**
     * Changes the current assignee while retaining creator, status, comments, logs, and creation
     * lifecycle attribution.
     *
     * @param assigneeMembershipId eligible same-Project recipient membership
     * @param assignerMembershipId authenticated Leader or guarded Project-operation actor
     * @param now server-controlled assignment instant
     */
    public void reassign(long assigneeMembershipId, long assignerMembershipId, Instant now) {
        this.assigneeMembershipId = assigneeMembershipId;
        this.assignerMembershipId = assignerMembershipId;
        this.assignedAt = now;
        this.updatedAt = now;
    }

    /**
     * Marks an unfinished Task deleted while retaining the row and all historical attribution.
     *
     * @param deleterMembershipId authenticated same-Project membership performing the deletion
     * @param now server-controlled deletion instant
     * @throws IllegalStateException when this Task is already deleted
     */
    public void softDelete(long deleterMembershipId, Instant now) {
        if (deletedAt != null) {
            throw new IllegalStateException("Task is already deleted");
        }
        deletedAt = now;
        deletedByMembershipId = deleterMembershipId;
        updatedAt = now;
    }

    /**
     * Indicates whether this row is excluded from current Task lists and progress.
     *
     * @return true after soft deletion
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }

}
