package com.lab.labtimesheet.feature.account.model.entity;

import java.time.Instant;
import java.time.LocalDate;

import com.lab.labtimesheet.feature.account.model.InternshipStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Persistent internship lifecycle and inclusive eligibility dates for an Intern account.
 * The shared primary key is the owning account identifier without a cross-feature entity relationship.
 */
@Entity
@Table(name = "intern_profiles")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InternProfile {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "student_code", nullable = false, length = 64)
    @Getter
    private String studentCode;

    @Column(length = 120)
    private String department;

    @Column(length = 32)
    private String phone;

    @Column(name = "internship_start_date", nullable = false)
    @Getter
    private LocalDate internshipStartDate;

    @Column(name = "internship_end_date", nullable = false)
    @Getter
    private LocalDate internshipEndDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "internship_status", nullable = false, length = 24)
    @Getter
    private InternshipStatus internshipStatus;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Getter
    private long version;

    private InternProfile(
            long userId, String studentCode, LocalDate internshipStartDate, LocalDate internshipEndDate, Instant now) {
        this.userId = userId;
        this.studentCode = studentCode;
        this.internshipStartDate = internshipStartDate;
        this.internshipEndDate = internshipEndDate;
        this.internshipStatus = InternshipStatus.NOT_STARTED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Creates an internship awaiting its separately authorized start transition.
     *
     * @param userId owning Intern account identifier
     * @param studentCode university student code
     * @param internshipStartDate inclusive eligibility start date
     * @param internshipEndDate inclusive eligibility end date
     * @param now server timestamp
     * @return new not-started internship profile
     */
    public static InternProfile notStarted(
            long userId, String studentCode, LocalDate internshipStartDate, LocalDate internshipEndDate, Instant now) {
        return new InternProfile(userId, studentCode, internshipStartDate, internshipEndDate, now);
    }

    /**
     * Transitions a not-started internship to active.
     *
     * @param now server activation timestamp
     * @throws IllegalStateException when the internship already left the not-started state
     */
    public void activate(Instant now) {
        if (internshipStatus != InternshipStatus.NOT_STARTED) {
            throw new IllegalStateException("Only a not-started internship can activate");
        }
        internshipStatus = InternshipStatus.ACTIVE;
        activatedAt = now;
        updatedAt = now;
    }

    /**
     * Completes an active internship and retains the completion timestamp for history.
     *
     * @param now server completion timestamp
     * @throws IllegalStateException when the internship is not active
     */
    public void complete(Instant now) {
        if (internshipStatus != InternshipStatus.ACTIVE) {
            throw new IllegalStateException("Only an active internship can complete");
        }
        internshipStatus = InternshipStatus.COMPLETED;
        completedAt = now;
        updatedAt = now;
    }

    /**
     * Withdraws an internship that has not started or is currently active.
     *
     * @param now server withdrawal timestamp
     * @throws IllegalStateException when the internship is already terminal
     */
    public void withdraw(Instant now) {
        if (internshipStatus != InternshipStatus.NOT_STARTED && internshipStatus != InternshipStatus.ACTIVE) {
            throw new IllegalStateException("Only a not-started or active internship can withdraw");
        }
        internshipStatus = InternshipStatus.WITHDRAWN;
        withdrawnAt = now;
        updatedAt = now;
    }

    /**
     * Replaces the Intern profile details after an authorized Admin edit. Dates are expected to
     * form an inclusive range and the student code a normalized value, both validated by the caller.
     * The lifecycle state and timestamps are untouched.
     *
     * @param studentCode replacement student code
     * @param internshipStartDate replacement inclusive start date
     * @param internshipEndDate replacement inclusive end date
     * @param now server edit timestamp
     */
    public void updateFields(
            String studentCode, LocalDate internshipStartDate, LocalDate internshipEndDate, Instant now) {
        this.studentCode = studentCode;
        this.internshipStartDate = internshipStartDate;
        this.internshipEndDate = internshipEndDate;
        this.updatedAt = now;
    }

}
