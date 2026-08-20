package com.lab.labtimesheet.feature.attendance.model.dto;

/**
 * Result of one Admin holiday import action.
 *
 * @param imported number of new locally stored events
 * @param skipped number of already-imported source UUIDs left untouched
 */
public record HolidayImportSummary(int imported, int skipped) {}