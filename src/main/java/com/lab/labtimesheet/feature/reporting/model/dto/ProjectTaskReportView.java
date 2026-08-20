package com.lab.labtimesheet.feature.reporting.model.dto;

import com.lab.labtimesheet.feature.project.model.dto.ProjectSummary;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskMemberView;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import java.util.List;
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
 */
public record ProjectTaskReportView(
        ProjectTaskReportFilter filter,
        List<ProjectSummary> projectOptions,
        List<ProjectTaskMemberView> memberOptions,
        List<ProjectTaskReportRow> rows,
        long totalTasks,
        long doneTasks,
        String completionRate) {

    /** Copies collections so template consumers cannot mutate the authorized dataset. */
    public ProjectTaskReportView {
        projectOptions = List.copyOf(projectOptions);
        memberOptions = List.copyOf(memberOptions);
        rows = List.copyOf(rows);
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
