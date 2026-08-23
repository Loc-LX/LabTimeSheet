package com.lab.labtimesheet.feature.reporting.model.dto;

/**
 * Classification of one calendar date within an attendance/compliance report.
 *
 * <p>Off-days and approved leave never enter the rate denominator or the compliance average; present
 * and absent workdays are the expected workdays over which compliance is averaged (ATT-013, ATT-017).
 */
public enum DayKind {
    /** Configured non-workday or global day off. */
    OFF_DAY,
    /** Expected workday covered by approved leave. */
    LEAVE,
    /** Expected workday with an attendance record. */
    WORKDAY_PRESENT,
    /** Expected workday with no attendance record. */
    WORKDAY_ABSENT
}