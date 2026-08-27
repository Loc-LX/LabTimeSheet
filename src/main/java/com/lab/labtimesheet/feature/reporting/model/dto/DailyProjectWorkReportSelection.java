package com.lab.labtimesheet.feature.reporting.model.dto;

import com.lab.labtimesheet.feature.project.model.dto.ProjectSummary;
import java.time.LocalDate;
import java.util.List;

/**
 * Immutable Project chooser state for a current Intern Leader opening the Daily report without a
 * selected Project.
 *
 * <p>The Project list is produced by the Project authorization boundary. This view contains no
 * Task, work-log, or Attendance data and therefore cannot be mistaken for a report dataset.</p>
 *
 * @param reportDate optional selected local Report date to carry into the chosen Project report
 * @param projectOptions current eligible Projects led by the authenticated Intern
 */
public record DailyProjectWorkReportSelection(
        LocalDate reportDate,
        List<ProjectSummary> projectOptions) {

    /** Defensive copy keeps the selector state immutable across template rendering. */
    public DailyProjectWorkReportSelection {
        projectOptions = List.copyOf(projectOptions);
    }
}
