package com.lab.labtimesheet.feature.attendance.model.dto;

import java.time.LocalDate;

/**
 * Full-day inclusive leave range plus a non-blank reason as entered by an Intern.
 *
 * @param startDate inclusive first local date
 * @param endDate inclusive last local date
 * @param reason non-blank human-readable reason
 */
public record LeaveSubmissionCommand(LocalDate startDate, LocalDate endDate, String reason) {
}