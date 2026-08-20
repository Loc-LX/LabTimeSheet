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

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "created_by_membership_id", nullable = false)
    private long creatorMembershipId;

    @Column(name = "assigned_by_membership_id", nullable = false)
    private long assignerMembershipId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by_membership_id")
    @Getter(AccessLevel.NONE)
    private Long deletedByMembershipId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @Getter(AccessLevel.NONE)
    private Instant updatedAt;

    @Version
    @Getter(AccessLevel.NONE)
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
     * Reassigns an unfinished Task to another active same-Project membership.
     *
     * <p>Only the current assignee, creator attribution, status, comments, and work logs are
     * retained; the assignee, assignment actor, and assignment instant describe the new current
     * assignment. A {@code DONE} Task must be reopened before reassignment.
     *
     * @param newAssigneeMembershipId active same-Project replacement membership
     * @param assignerMembershipId authenticated reassigning Leader membership
     * @param now server-controlled reassignment instant
     */
    public void reassign(long newAssigneeMembershipId, long assignerMembershipId, Instant now) {
        if (status == TaskStatus.DONE) {
            throw new IllegalArgumentException("A DONE Task must be reopened before reassignment");
        }
        this.assigneeMembershipId = newAssigneeMembershipId;
        this.assignerMembershipId = assignerMembershipId;
        this.assignedAt = now;
        this.updatedAt = now;
    }

    /**
     * Applies an authorized definition edit to title, description, and optional due date.
     *
     * <p>Creator, assigner, assignment time, assignee, status, comments, and work logs are
     * untouched; only definition fields and the update timestamp advance. The caller enforces
     * Leader or creator definition authority before invoking.
     *
     * @param title normalized required title
     * @param description optional normalized description
     * @param dueDate optional validated business due date
     * @param now server-controlled edit instant
     */
    public void edit(String title, String description, LocalDate dueDate, Instant now) {
        this.title = title;
        this.description = description;
        this.dueDate = dueDate;
        this.updatedAt = now;
    }

    /**
     * Marks an unfinished Task soft-deleted with the authenticated definition actor.
     *
     * <p>The row is retained historically and excluded from progress and normal lists; creator
     * attribution, comments, and work logs remain. A {@code DONE} Task must be reopened before
     * deletion.
     *
     * @param deletedByMembershipId authenticated deleting membership identifier
     * @param now server-controlled deletion instant
     */
    public void softDelete(long deletedByMembershipId, Instant now) {
        if (status == TaskStatus.DONE) {
            throw new IllegalArgumentException("A DONE Task must be reopened before deletion");
        }
        if (this.deletedAt != null) {
            throw new IllegalArgumentException("Task is already deleted");
        }
        this.deletedAt = now;
        this.deletedByMembershipId = deletedByMembershipId;
        this.updatedAt = now;
    }

}
