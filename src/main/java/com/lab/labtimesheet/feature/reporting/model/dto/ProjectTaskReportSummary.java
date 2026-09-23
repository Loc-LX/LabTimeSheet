package com.lab.labtimesheet.feature.reporting.model.dto;

import com.lab.labtimesheet.feature.project.model.TaskProgress;

import java.util.OptionalDouble;

/**
 * Hand-checkable Project/Task report totals shared by HTML, Excel, and PDF output.
 *
 * <p>An empty completion percentage renders as {@code N/A} when the Project has no current Tasks
 * (PRJ-015, RPT-009).
 *
 * @param progress status counts for the filtered Task set
 * @param totalMinutes sum of logged work minutes within the filtered work-date range
 * @param blockedCount number of currently blocked Tasks
 * @param completionPercentage DONE Tasks as a percentage of all counted Tasks, or empty when none
 */
public record ProjectTaskReportSummary(
        TaskProgress progress,
        long totalMinutes,
        long blockedCount,
        OptionalDouble completionPercentage) {}