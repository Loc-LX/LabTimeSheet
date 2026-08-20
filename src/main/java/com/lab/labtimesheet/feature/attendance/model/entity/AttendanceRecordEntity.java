package com.lab.labtimesheet.feature.attendance.model.entity;

import com.lab.labtimesheet.feature.attendance.model.AttendanceRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * JPA persistence model for one Intern/work-date punch row with its permanently attached policy version.
 */
@Entity
@Table(name = "attendance_records")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "intern_user_id", nullable = false)
    private long internUserId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "policy_version_id", nullable = false)
    private AttendancePolicyEntity policy;

    @Column(name = "check_in_at", nullable = false)
    private Instant checkInAt;

    @Column(name = "check_out_at")
    private Instant checkOutAt;

    @Version
    private long version;

    /**
     * Creates a new persistence row from server-authoritative raw punch values.
     *
     * @param internUserId scalar account identifier; account data remains owned by the account feature
     * @param workDate attached-policy local work date
     * @param policy persisted policy version fixed at check-in
     * @param checkInAt raw server check-in instant
     * @param checkOutAt raw server checkout instant, normally {@code null} for a new row
     */
    public AttendanceRecordEntity(
            long internUserId,
            LocalDate workDate,
            AttendancePolicyEntity policy,
            Instant checkInAt,
            Instant checkOutAt) {
        this.internUserId = internUserId;
        this.workDate = workDate;
        this.policy = policy;
        this.checkInAt = checkInAt;
        this.checkOutAt = checkOutAt;
    }

    /**
     * Rehydrates the immutable domain record without replacing the historical policy.
     *
     * @return attendance domain record
     */
    public AttendanceRecord toDomain() {
        return new AttendanceRecord(internUserId, workDate, policy.toDomain(), checkInAt, checkOutAt);
    }

    /**
     * Returns the persisted identifier assigned by the database.
     *
     * @return attendance record identifier
     */
    public long id() {
        if (id == null) {
            throw new IllegalStateException("Attendance record has not been persisted");
        }
        return id;
    }

    /**
     * Returns the immutable raw server check-in instant.
     *
     * @return raw check-in instant
     */
    public Instant checkInAt() {
        return checkInAt;
    }

    /**
     * Returns the immutable raw server checkout instant, or {@code null} while open or corrected.
     *
     * @return raw checkout instant, or {@code null}
     */
    public Instant checkOutAt() {
        return checkOutAt;
    }

    /**
     * Stores the first accepted raw checkout; callers must enforce cutoff and single-write rules transactionally.
     *
     * @param checkOutAt accepted server checkout instant
     */
    public void setCheckOutAt(Instant checkOutAt) {
        this.checkOutAt = checkOutAt;
    }

    /**
     * Returns the immutable local date used for account eligibility revalidation at checkout.
     *
     * @return persisted work date
     */
    public LocalDate workDate() {
        return workDate;
    }
}
