package com.lab.labtimesheet.feature.reporting.model.dto;

import com.lab.labtimesheet.feature.project.model.dto.ProjectSummary;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskMemberView;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * Authorized current Project/Task HTML dataset and its filter options.
 *
 * <p>Rows are already scoped by Project and Task services before Reporting applies presentation
 * filters. Soft-deleted and inaccessible Tasks never enter this projection.</p>
 *
 * @param filter submitted filter values retained for form redisplay
 * @param projectOptions Projects visible to the actor
 * @param memberOptions current eligible members of the selected Project
 * @param rows filtered current Task rows
 * @param totalTasks filtered Task count
 * @param doneTasks filtered DONE count
 * @param completionRate formatted DONE percentage, or {@code N/A} with no rows
 * @param statusCounts filtered current Task counts by every workflow status
 * @param totalLoggedMinutes sum of filtered retained Task work-log minutes
 * @param detailedMemberHours whether the viewer may inspect the member-hours breakdown
 * @param memberHours filtered retained minutes by authorized Project membership
 */
public record ProjectTaskReportView(
        ProjectTaskReportFilter filter,
        List<ProjectSummary> projectOptions,
        List<ProjectTaskMemberView> memberOptions,
        List<ProjectTaskReportRow> rows,
        long totalTasks,
        long doneTasks,
        String completionRate,
        Map<TaskStatus, Long> statusCounts,
        long totalLoggedMinutes,
        boolean detailedMemberHours,
        List<ProjectTaskReportMemberHours> memberHours) {

    /** Copies collections so template consumers cannot mutate the authorized dataset. */
    public ProjectTaskReportView {
        projectOptions = List.copyOf(projectOptions);
        memberOptions = List.copyOf(memberOptions);
        rows = List.copyOf(rows);
        statusCounts = Map.copyOf(statusCounts);
        memberHours = List.copyOf(memberHours);
    }

    /**
     * Retains the original summary construction contract for callers that only need task totals.
     *
     * @param filter submitted filter values
     * @param projectOptions authorized Project options
     * @param memberOptions authorized current-member options
     * @param rows filtered Task rows
     * @param totalTasks filtered Task count
     * @param doneTasks filtered DONE count
     * @param completionRate formatted completion percentage or {@code N/A}
     */
    public ProjectTaskReportView(
            ProjectTaskReportFilter filter,
            List<ProjectSummary> projectOptions,
            List<ProjectTaskMemberView> memberOptions,
            List<ProjectTaskReportRow> rows,
            long totalTasks,
            long doneTasks,
            String completionRate) {
        this(filter, projectOptions, memberOptions, rows, totalTasks, doneTasks, completionRate,
                emptyStatusCounts(), 0L, false, List.of());
    }

    private static Map<TaskStatus, Long> emptyStatusCounts() {
        Map<TaskStatus, Long> counts = new EnumMap<>(TaskStatus.class);
        for (TaskStatus status : TaskStatus.values()) {
            counts.put(status, 0L);
        }
        return counts;
    }

    /**
     * Returns the filtered TODO count for compact metric-card rendering.
     *
     * @return TODO row count
     */
    public long todoTasks() {
        return statusCounts.getOrDefault(TaskStatus.TODO, 0L);
    }

    /**
     * Returns the filtered IN_PROGRESS count for compact metric-card rendering.
     *
     * @return IN_PROGRESS row count
     */
    public long inProgressTasks() {
        return statusCounts.getOrDefault(TaskStatus.IN_PROGRESS, 0L);
    }

    /**
     * Returns the filtered BLOCKED count for compact metric-card rendering.
     *
     * @return BLOCKED row count
     */
    public long blockedTasks() {
        return statusCounts.getOrDefault(TaskStatus.BLOCKED, 0L);
    }

    /**
     * Formats the selected project's completion ratio using a stable locale.
     *
     * @param total filtered Task denominator
     * @param done filtered DONE numerator
     * @return one-decimal percentage or {@code N/A}
     */
    public static String percentage(long total, long done) {
        return total == 0 ? "N/A" : String.format(Locale.ROOT, "%.1f%%", done * 100.0 / total);
    }
}
