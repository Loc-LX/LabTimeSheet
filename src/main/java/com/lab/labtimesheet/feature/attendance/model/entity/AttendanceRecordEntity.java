package com.lab.labtimesheet.feature.attendance.model.entity;

import com.lab.labtimesheet.feature.calendar.model.AttendancePolicy;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRecord;
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

    @Column(name = "policy_version_id", nullable = false)
    private long policyVersionId;

    @Column(name = "check_in_at", nullable = false)
    private Instant checkInAt;

    @Column(name = "check_out_at")
    private Instant checkOutAt;

    @Version
    private long version;

    /**
     * Creates a new persistence row from server-authoritative raw punch values.
     *
     * @param internUserId scalar account identifier; account data is owned by the identity module
     * @param workDate attached-policy local work date
     * @param policyVersionId persisted policy version identifier fixed at check-in
     * @param checkInAt raw server check-in instant
     * @param checkOutAt raw server checkout instant, normally {@code null} for a new row
     */
    public AttendanceRecordEntity(
            long internUserId,
            LocalDate workDate,
            long policyVersionId,
            Instant checkInAt,
            Instant checkOutAt) {
        this.internUserId = internUserId;
        this.workDate = workDate;
        this.policyVersionId = policyVersionId;
        this.checkInAt = checkInAt;
        this.checkOutAt = checkOutAt;
    }

    /**
     * Rehydrates the immutable domain record with the historical policy read by the Calendar boundary.
     *
     * @param policy historical policy whose identifier must match this row
     * @return attendance domain record
     */
    public AttendanceRecord toDomain(AttendancePolicy policy) {
        if (policy.id() != policyVersionId) {
            throw new IllegalArgumentException("Attendance policy does not match the persisted policy version");
        }
        return new AttendanceRecord(internUserId, workDate, policy, checkInAt, checkOutAt);
    }

    /**
     * Returns the persisted raw attendance identifier used by correction commands.
     *
     * @return database identifier
     */
    public long id() {
        if (id == null) {
            throw new IllegalStateException("Attendance record has not been persisted");
        }
        return id;
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

    /**
     * Returns the immutable policy-version identifier used to load the historical policy through Calendar.
     *
     * @return persisted policy version identifier
     */
    public long policyVersionId() {
        return policyVersionId;
    }
}
