package com.lab.labtimesheet.feature.task.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Persisted append-only Task comment.
 *
 * <p>The author is retained as a user identifier so membership or leadership changes do not move
 * historical attribution. This entity intentionally exposes no edit or delete operation.
 */
@Entity
@Table(name = "task_comments")
public class TaskComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private long taskId;

    @Column(name = "author_user_id", nullable = false)
    private long authorUserId;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Constructor reserved for JPA materialization. */
    protected TaskComment() {}

    /**
     * Creates an immutable comment from server-authorized values.
     *
     * @param taskId owning Task identifier
     * @param authorUserId authenticated historical author user identifier
     * @param body normalized non-blank comment text
     * @param createdAt server-controlled creation instant
     */
    public TaskComment(long taskId, long authorUserId, String body, Instant createdAt) {
        this.taskId = taskId;
        this.authorUserId = authorUserId;
        this.body = body;
        this.createdAt = createdAt;
    }

    /**
     * Returns the persistence identity.
     *
     * @return comment identifier, or {@code null} before insertion
     */
    public Long getId() {
        return id;
    }

    /**
     * Returns the owning record identity.
     *
     * @return owning Task identifier
     */
    public long getTaskId() {
        return taskId;
    }

    /**
     * Returns historical authorship.
     *
     * @return immutable historical author user identifier
     */
    public long getAuthorUserId() {
        return authorUserId;
    }

    /**
     * Returns comment content.
     *
     * @return normalized comment text
     */
    public String getBody() {
        return body;
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
