package com.lab.labtimesheet.feature.attendance.model.entity;

import com.lab.labtimesheet.feature.attendance.model.AttendancePolicy;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * JPA mapping of an immutable-on-effective attendance policy version and its configured workdays.
 */
@Entity
@Table(name = "attendance_policy_versions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendancePolicyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "timezone_name", nullable = false)
    private String timezoneName;

    @Column(name = "scheduled_start", nullable = false)
    private LocalTime scheduledStart;

    @Column(name = "scheduled_end", nullable = false)
    private LocalTime scheduledEnd;

    @Column(name = "check_in_grace_minutes", nullable = false)
    private int checkInGraceMinutes;

    @Column(name = "checkout_grace_minutes", nullable = false)
    private int checkoutGraceMinutes;

    @Column(name = "monthly_leave_quota", nullable = false)
    private int monthlyLeaveQuota;

    @Column(name = "violation_penalty", nullable = false)
    private BigDecimal violationPenalty;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "attendance_policy_workdays",
            joinColumns = @JoinColumn(name = "policy_version_id"))
    @Column(name = "iso_weekday", nullable = false)
    private Set<Short> isoWeekdays;

    @Version
    private long version;

    /**
     * Converts the persisted version to the immutable policy used for historical boundary calculations.
     *
     * @return domain policy including its persisted identifier and timezone
     */
    public AttendancePolicy toDomain() {
        Set<DayOfWeek> workdays = isoWeekdays.stream()
                .map(day -> DayOfWeek.of(day.intValue()))
                .collect(Collectors.toUnmodifiableSet());
        return new AttendancePolicy(
                id,
                effectiveFrom,
                ZoneId.of(timezoneName),
                scheduledStart,
                scheduledEnd,
                checkInGraceMinutes,
                checkoutGraceMinutes,
                monthlyLeaveQuota,
                violationPenalty,
                workdays);
    }
}
