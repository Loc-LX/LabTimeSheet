package com.lab.labtimesheet.feature.calendar.service;

import com.lab.labtimesheet.feature.calendar.model.AttendancePolicy;
import java.time.LocalDate;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Deterministically resolves immutable attendance policy versions for local dates or server instants.
 */
public final class AttendancePolicyTimeline {

    private final List<AttendancePolicy> policies;

    /**
     * Snapshots and orders the supplied versions by effective date.
     *
     * @param policies available policy versions, normally including the 1970 seed
     */
    public AttendancePolicyTimeline(Collection<AttendancePolicy> policies) {
        this.policies = policies.stream()
                .sorted(Comparator.comparing(AttendancePolicy::effectiveFrom))
                .toList();
    }

    /**
     * Resolves the latest version effective on or before a local business date.
     *
     * @param date local business date
     * @return governing policy version
     */
    public AttendancePolicy resolve(LocalDate date) {
        Objects.requireNonNull(date, "date");
        return policies.stream()
                .filter(policy -> !policy.effectiveFrom().isAfter(date))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new IllegalArgumentException("no attendance policy applies on " + date));
    }

    /**
     * Resolves an instant against each version's own timezone and effective date.
     *
     * @param instant authoritative server instant
     * @return governing policy version
     */
    public AttendancePolicy resolve(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        return policies.stream()
                .filter(policy -> !policy.effectiveFrom().isAfter(
                        instant.atZone(policy.zoneId()).toLocalDate()))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new IllegalArgumentException("no attendance policy applies at " + instant));
    }
}
