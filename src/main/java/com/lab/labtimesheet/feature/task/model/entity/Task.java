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

/**
 * Persisted Task aggregate row with one current same-Project assignee.
 *
 * <p>Creator attribution never changes. Assignment actor/time describe the current assignment,
 * deletion is soft and historical, and the JPA version detects conflicting updates. Newly created
 * Tasks always begin in TODO; status changes must follow the fixed {@link TaskStatus} graph.
 */
@Entity
@Table(name = "tasks")
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

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "created_by_membership_id", nullable = false)
    private long creatorMembershipId;

    @Column(name = "assigned_by_membership_id", nullable = false)
    private long assignerMembershipId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by_membership_id")
    private Long deletedByMembershipId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    /** Constructor reserved for JPA materialization. */
    protected Task() {}

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
     * Returns the persistence identity.
     *
     * @return Task identifier, or {@code null} before insertion
     */
    public Long getId() {
        return id;
    }

    /**
     * Returns the aggregate identity.
     *
     * @return owning Project identifier
     */
    public long getProjectId() {
        return projectId;
    }

    /**
     * Returns the current assignment identity.
     *
     * @return current same-Project assignee membership identifier
     */
    public long getAssigneeMembershipId() {
        return assigneeMembershipId;
    }

    /**
     * Returns the display title.
     *
     * @return normalized Task title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Returns the descriptive text.
     *
     * @return optional normalized description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the workflow state.
     *
     * @return current fixed workflow status
     */
    public TaskStatus getStatus() {
        return status;
    }

    /**
     * Returns the business deadline.
     *
     * @return optional validated due date
     */
    public LocalDate getDueDate() {
        return dueDate;
    }

    /**
     * Returns current assignment timing.
     *
     * @return instant when the current assignment was established
     */
    public Instant getAssignedAt() {
        return assignedAt;
    }

    /**
     * Returns original creator attribution.
     *
     * @return immutable creating membership identifier
     */
    public long getCreatorMembershipId() {
        return creatorMembershipId;
    }

    /**
     * Returns current assignment attribution.
     *
     * @return membership identifier responsible for the current assignment
     */
    public long getAssignerMembershipId() {
        return assignerMembershipId;
    }

    /**
     * Returns lifecycle visibility state.
     *
     * @return soft-deletion instant, or {@code null} while current
     */
    public Instant getDeletedAt() {
        return deletedAt;
    }

    /**
     * Returns creation timing.
     *
     * @return immutable creation instant
     */
    public Instant getCreatedAt() {
        return createdAt;
    }
}
