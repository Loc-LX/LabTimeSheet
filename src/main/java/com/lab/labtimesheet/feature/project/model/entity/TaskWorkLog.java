package com.lab.labtimesheet.feature.project.model.entity;

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
 * Persisted dated Task effort independent from attendance punches.
 *
 * <p>The membership identifier is the immutable historical author. Corrections update only the
 * minutes, note, and update timestamp; the work date, Task, Project, author, and creation instant
 * never move.
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
    @Getter
    private long version;

    /**
     * Creates a dated work log from server-authorized values.
     *
     * @param projectId owning Project identifier
     * @param taskId owning Task identifier
     * @param membershipId historical author membership identifier
     * @param workDate local business date of the effort
     * @param minutes effort in the inclusive range 1 through 1440
     * @param note optional non-blank note, normalized by trimming
     * @param now server-controlled creation instant
     * @throws IllegalArgumentException when date, minutes, or note is invalid
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
        this.workDate = requireDate(workDate);
        this.minutes = requireMinutes(minutes);
        this.note = normalizeNote(note);
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Corrects author-owned effort while retaining the original historical identity.
     *
     * @param minutes replacement effort in the inclusive range 1 through 1440
     * @param note replacement optional normalized note
     * @param now server-controlled correction instant
     * @throws IllegalArgumentException when minutes or note is invalid
     */
    public void correct(int minutes, String note, Instant now) {
        this.minutes = requireMinutes(minutes);
        this.note = normalizeNote(note);
        this.updatedAt = now;
    }

    private static LocalDate requireDate(LocalDate value) {
        if (value == null) {
            throw new IllegalArgumentException("Work date is required");
        }
        return value;
    }

    private static int requireMinutes(int value) {
        if (value < 1 || value > 1440) {
            throw new IllegalArgumentException("Work minutes must be between 1 and 1440");
        }
        return value;
    }

    private static String normalizeNote(String value) {
        if (value == null) {
            return null;
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException("Work-log note cannot be blank");
        }
        return value.trim();
    }
}
