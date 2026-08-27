package com.lab.labtimesheet.feature.reporting.model.dto;

import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceReportDateContext;
import com.lab.labtimesheet.feature.project.model.dto.ProjectSummary;
import java.time.LocalDate;
import java.util.List;

/**
 * Immutable authorized Daily Project Work Report dataset used by the HTML route.
 *
 * <p>When no Project is selected, Projects without a matching retained log are omitted. When a
 * Project is explicitly selected, {@link #selectedProjectName()} remains available so the view
 * can render an explicit empty state rather than silently omitting the authorized Project.</p>
 *
 * @param reportDate selected local Report date
 * @param dayContext effective Attendance policy/calendar annotation for the selected date
 * @param selectedProjectId optional authorized Project filter
 * @param selectedProjectName selected Project display name, or null in all-Projects mode
 * @param projectOptions complete authorized Project filter options
 * @param projects retained selected-date work grouped by Project
 * @param overallTotalMinutes selected-date total across rendered Projects
 * @param lockedSingleProject whether the report is restricted to one current-Leader Project
 */
public record DailyProjectWorkReportView(
        LocalDate reportDate,
        AttendanceReportDateContext dayContext,
        Long selectedProjectId,
        String selectedProjectName,
        List<ProjectSummary> projectOptions,
        List<DailyProjectWorkReportProject> projects,
        long overallTotalMinutes,
        boolean lockedSingleProject) {

    /**
     * Backward-compatible constructor for existing all-Projects and Mentor-selected views.
     *
     * <p>Only the Daily service can enable the locked mode through the canonical constructor;
     * callers that do not know the Leader authorization fact remain unlocked by default.</p>
     */
    public DailyProjectWorkReportView(
            LocalDate reportDate,
            AttendanceReportDateContext dayContext,
            Long selectedProjectId,
            String selectedProjectName,
            List<ProjectSummary> projectOptions,
            List<DailyProjectWorkReportProject> projects,
            long overallTotalMinutes) {
        this(reportDate, dayContext, selectedProjectId, selectedProjectName,
                projectOptions, projects, overallTotalMinutes, false);
    }

    /** Defensive copies preserve one immutable public report tree. */
    public DailyProjectWorkReportView {
        projectOptions = List.copyOf(projectOptions);
        projects = List.copyOf(projects);
    }

    /** @return whether one authorized Project filter was requested */
    public boolean selectedProject() {
        return selectedProjectId != null;
    }

    /** @return whether any retained selected-date log is available in the scoped dataset */
    public boolean hasRows() {
        return overallTotalMinutes > 0;
    }

    /** @return empty-state heading appropriate to all-Project or selected-Project scope */
    public String emptyTitle() {
        return selectedProject() ? "No retained Task work for this Project" : "No retained Task work for this date";
    }

    /** @return empty-state explanation that preserves valid date/calendar semantics */
    public String emptyDescription() {
        return selectedProject()
                ? "The selected authorized Project has no retained Task work logs on this Report date."
                : "No authorized Project has retained Task work logs on this Report date.";
    }
}
