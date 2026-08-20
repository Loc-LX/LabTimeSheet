package com.lab.labtimesheet.feature.attendance.model.dto;

/**
 * An Admin's explicit import choice for one holiday candidate.
 * {@code publicHoliday} may preselect the preview row but never forces this value.
 *
 * @param uuid candidate identifier to import
 * @param dayOff authoritative local day-off decision
 */
public record HolidaySelection(String uuid, boolean dayOff) {}