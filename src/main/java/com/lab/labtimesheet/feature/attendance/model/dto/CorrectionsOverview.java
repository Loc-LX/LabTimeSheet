package com.lab.labtimesheet.feature.attendance.model.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Intern correction page state: the displayed month and the Intern's prior corrections newest-first.
 *
 * @param month quota/business month being displayed, first day
 * @param corrections prior corrections newest-first
 */
public record CorrectionsOverview(LocalDate month, List<CorrectionSubmission> corrections) {}