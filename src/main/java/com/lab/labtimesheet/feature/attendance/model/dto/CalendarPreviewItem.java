package com.lab.labtimesheet.feature.attendance.model.dto;

/**
 * Admin review row for a HolidayAPI candidate. Public status influences only the initial checkbox state.
 *
 * @param candidate non-secret upstream candidate and provenance
 * @param selectedByDefault initial local day-off choice
 */
public record CalendarPreviewItem(HolidayCandidate candidate, boolean selectedByDefault) {}
