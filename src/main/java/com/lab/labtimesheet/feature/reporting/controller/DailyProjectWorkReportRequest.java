package com.lab.labtimesheet.feature.reporting.controller;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Parses and reconciles raw Daily-report request values after the role boundary has run.
 *
 * <p>Daily parameters intentionally remain strings at the MVC boundary. This lets the
 * controller deny an Admin before Spring's typed argument conversion can turn malformed input
 * into a disclosed {@code 400} response.</p>
 */
final class DailyProjectWorkReportRequest {

    private DailyProjectWorkReportRequest() {
    }

    /**
     * Parses the optional Project identifier after authorization.
     *
     * @param rawProjectId untrusted query-string value
     * @return parsed identifier, or {@code null} when omitted/blank
     * @throws IllegalArgumentException when the value is not a decimal long
     */
    static Long parseProjectId(String rawProjectId) {
        if (rawProjectId == null || rawProjectId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(rawProjectId.strip());
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Project identifier is invalid", invalid);
        }
    }

    /**
     * Parses one optional ISO local date after authorization.
     *
     * @param rawDate untrusted query-string value
     * @return parsed date, or {@code null} when omitted/blank
     * @throws IllegalArgumentException when the value is not an ISO local date
     */
    static LocalDate parseDate(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(rawDate.strip());
        } catch (DateTimeParseException invalid) {
            throw new IllegalArgumentException("Report date is invalid", invalid);
        }
    }

    /**
     * Reconciles the Daily HTML/export date aliases.
     *
     * @param date value from the {@code date} alias
     * @param reportDate value from the {@code reportDate} alias
     * @return the one supplied date, or {@code null} when neither was supplied
     * @throws IllegalArgumentException when both aliases disagree
     */
    static LocalDate mergeDates(LocalDate date, LocalDate reportDate) {
        if (date != null && reportDate != null && !date.equals(reportDate)) {
            throw new IllegalArgumentException("Report date parameters must match");
        }
        return date != null ? date : reportDate;
    }
}
