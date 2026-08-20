package com.lab.labtimesheet.feature.task.model.dto;

import java.time.LocalDate;

/**
 * Inputs for creating one dated Task work log entry.
 *
 * @param workDate local effort date that must not be in the future and must fall within the
 *                 Project date range and the logging member's membership interval
 * @param minutes logged minutes from 1 through 1440
 * @param note optional normalized non-blank note, or null
 */
public record LogWorkCommand(LocalDate workDate, int minutes, String note) {}
