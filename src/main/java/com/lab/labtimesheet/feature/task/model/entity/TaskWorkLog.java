package com.lab.labtimesheet.feature.task.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Persisted dated effort logged against one current Task by its then-current assignee.
 *
 * <p>The logging membership is retained so reassignment never moves historical attribution. Minutes
 * are validated to the 1 through 1440 range and notes are normalized non-blank text. The JPA version
 * detects conflicting corrections, and the optimistic total-budget check in the owning service guards
 * the combined 1440-minute daily limit.
 */
@Entity
@Table(name = "task_work_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskWorkLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private long projectId;

    @Column(name = "task_id", nullable = false)
    private long taskId;

    @Column(name = "membership_id", nullable = false)
    private long membershipId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(nullable = false)
    private int minutes;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Getter(AccessLevel.NONE)
    private long version;

    /**
     * Creates a dated effort entry from service-validated values.
     *
     * @param projectId owning Project identifier
     * @param taskId owning Task identifier
     * @param membershipId logging membership identifier, which stays stable across reassignment
     * @param workDate validated local effort date
     * @param minutes validated minutes from 1 through 1440
     * @param note optional normalized non-blank note
     * @param now server-controlled creation instant
     */
    public TaskWorkLog(
            long projectId,
            long taskId,
            long membershipId,
            LocalDate workDate,
            int minutes,
            String note,
            Instant now) {
        this.projectId = projectId;
        this.taskId = taskId;
        this.membershipId = membershipId;
        this.workDate = workDate;
        this.minutes = minutes;
        this.note = note;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Applies an author-only correction to effort and note and advances the update timestamp.
     *
     * @param minutes corrected minutes from 1 through 1440
     * @param note optional normalized non-blank note
     * @param now server-controlled correction instant
     */
    public void correct(int minutes, String note, Instant now) {
        this.minutes = minutes;
        this.note = note;
        this.updatedAt = now;
    }

}
