package com.lab.labtimesheet.feature.attendance.model.entity;

import com.lab.labtimesheet.feature.attendance.model.AttendancePolicy;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendancePolicyCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendancePolicyHistoryItem;
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
import java.time.Instant;
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

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "attendance_policy_workdays",
            joinColumns = @JoinColumn(name = "policy_version_id"))
    @Column(name = "iso_weekday", nullable = false)
    private Set<Short> isoWeekdays;

    @Version
    private long version;

    /**
     * Creates an Admin-authored future policy version before its first persistence flush.
     *
     * @param command validated policy values
     * @param actorUserId Admin actor
     * @param now creation instant
     */
    public AttendancePolicyEntity(AttendancePolicyCommand command, long actorUserId, Instant now) {
        this.effectiveFrom = command.effectiveFrom();
        this.timezoneName = command.zoneId().getId();
        this.scheduledStart = command.scheduledStart();
        this.scheduledEnd = command.scheduledEnd();
        this.checkInGraceMinutes = command.checkInGraceMinutes();
        this.checkoutGraceMinutes = command.checkoutGraceMinutes();
        this.monthlyLeaveQuota = command.monthlyLeaveQuota();
        this.violationPenalty = command.violationPenalty();
        this.isoWeekdays = command.workdays().stream().map(day -> (short) day.getValue()).collect(Collectors.toSet());
        this.createdByUserId = actorUserId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Replaces an as-yet ineffective version without changing its identity or effective date.
     *
     * @param command replacement values; effective date must match the persisted version
     * @param actorUserId Admin performing the replacement; retained as update attribution only
     * @param now replacement instant
     */
    public void replace(AttendancePolicyCommand command, long actorUserId, Instant now) {
        if (!effectiveFrom.equals(command.effectiveFrom())) {
            throw new IllegalArgumentException("An existing policy keeps its effective date");
        }
        this.timezoneName = command.zoneId().getId();
        this.scheduledStart = command.scheduledStart();
        this.scheduledEnd = command.scheduledEnd();
        this.checkInGraceMinutes = command.checkInGraceMinutes();
        this.checkoutGraceMinutes = command.checkoutGraceMinutes();
        this.monthlyLeaveQuota = command.monthlyLeaveQuota();
        this.violationPenalty = command.violationPenalty();
        this.isoWeekdays = command.workdays().stream().map(day -> (short) day.getValue()).collect(Collectors.toSet());
        this.updatedAt = now;
    }

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

    /**
     * Returns the local date at which this policy becomes effective.
     *
     * @return first governed local date
     */
    public LocalDate effectiveFrom() {
        return effectiveFrom;
    }

    /**
     * Returns this version's optimistic-lock value.
     *
     * @return JPA version
     */
    public long version() {
        return version;
    }

    /**
     * Converts the retained policy and non-secret actor metadata into the Admin History projection.
     *
     * @return history-safe policy item
     */
    public AttendancePolicyHistoryItem toHistory() {
        return new AttendancePolicyHistoryItem(
                id, effectiveFrom, toDomain(), createdByUserId, createdAt, updatedAt, version);
    }
}
