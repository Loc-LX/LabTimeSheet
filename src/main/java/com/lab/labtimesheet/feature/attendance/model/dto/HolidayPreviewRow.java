package com.lab.labtimesheet.feature.attendance.model.dto;

/**
 * One rendered preview row for the Admin holiday-import screen.
 *
 * @param candidate interpreted candidate to display
 * @param preselected whether the row defaults to selected (public holidays only)
 * @param alreadyImported whether the source UUID already exists locally
 */
public record HolidayPreviewRow(HolidayCandidate candidate, boolean preselected, boolean alreadyImported) {}