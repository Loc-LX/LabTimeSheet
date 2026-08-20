package com.lab.labtimesheet.feature.task.model.dto;

/**
 * Author-only inputs for correcting one existing Task work log entry.
 *
 * <p>The work date is immutable after creation; only minutes and the optional note are corrected.
 *
 * @param minutes corrected minutes from 1 through 1440
 * @param note optional normalized non-blank note, or null
 */
public record LogWorkCorrection(int minutes, String note) {}
