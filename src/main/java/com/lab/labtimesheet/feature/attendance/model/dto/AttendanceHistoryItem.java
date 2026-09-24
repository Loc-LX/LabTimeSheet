package com.lab.labtimesheet.feature.attendance.model.dto;

import com.lab.labtimesheet.feature.calendar.model.AttendancePolicy;
import com.lab.labtimesheet.feature.attendance.model.AttendanceViolations;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Presentation and reporting DTO for one historical attendance row.
 * Raw instants remain available for precise consumers while display accessors consistently render
 * {@code dd/MM/yyyy} and policy-local 24-hour {@code HH:mm} values from the attached policy timezone.
 *
 * @param workDate immutable policy-local work date
 * @param checkInAt raw server check-in instant
 * @param checkOutAt effective checkout instant (approved correction or raw checkout), or {@code null} when absent
 * @param policy historical policy version attached to the row
 * @param violations all applicable violations at query time
 * @param attendanceRecordId raw row identifier used to open an authorized correction workflow
 */
public record AttendanceHistoryItem(
        LocalDate workDate,
        Instant checkInAt,
        Instant checkOutAt,
        AttendancePolicy policy,
        AttendanceViolations violations,
        long attendanceRecordId) {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    /** Retains the earlier report/test constructor when no actionable row identifier is needed. */
    public AttendanceHistoryItem(
            LocalDate workDate,
            Instant checkInAt,
            Instant checkOutAt,
            AttendancePolicy policy,
            AttendanceViolations violations) {
        this(workDate, checkInAt, checkOutAt, policy, violations, 0L);
    }

    /**
     * Formats the business date as {@code dd/MM/yyyy}.
     *
     * @return presentation-ready work date
     */
    public String workDateDisplay() {
        return DATE_FORMAT.format(workDate);
    }

    /**
     * Formats raw check-in in the attached policy timezone as 24-hour {@code HH:mm}.
     *
     * @return presentation-ready local check-in time
     */
    public String checkInTimeDisplay() {
        return TIME_FORMAT.format(checkInAt.atZone(policy.zoneId()));
    }

    /**
     * Formats effective checkout in the attached policy timezone or reports {@code Missing} when absent.
     *
     * @return presentation-ready local checkout value
     */
    public String checkOutTimeDisplay() {
        return checkOutAt == null ? "Missing" : TIME_FORMAT.format(checkOutAt.atZone(policy.zoneId()));
    }

    /**
     * Formats the attached policy's local scheduled start as {@code HH:mm}.
     *
     * @return presentation-ready scheduled start
     */
    public String scheduledStartDisplay() {
        return TIME_FORMAT.format(policy.scheduledStart());
    }

    /**
     * Formats the attached policy's local scheduled end as {@code HH:mm}.
     *
     * @return presentation-ready scheduled end
     */
    public String scheduledEndDisplay() {
        return TIME_FORMAT.format(policy.scheduledEnd());
    }

    /**
     * Returns every applicable violation in stable presentation order, or only {@code On time}
     * when no violation applies.
     *
     * @return immutable, non-empty presentation labels
     */
    public List<String> violationLabels() {
        List<String> labels = new ArrayList<>(3);
        if (violations.late()) {
            labels.add("Late");
        }
        if (violations.earlyDeparture()) {
            labels.add("Early departure");
        }
        if (violations.missingCheckout()) {
            labels.add("Missing checkout");
        }
        return labels.isEmpty() ? List.of("On time") : List.copyOf(labels);
    }

    /**
     * Joins every applicable violation for table and export cells.
     *
     * @return comma-separated violation labels, or {@code On time}
     */
    public String resultDisplay() {
        return String.join(", ", violationLabels());
    }
}
