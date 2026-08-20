package com.lab.labtimesheet.feature.reporting.service;

import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportSummary;
import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportTask;
import com.lab.labtimesheet.feature.task.model.TaskProgress;
import com.lab.labtimesheet.feature.task.model.TaskStatus;

import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Pure Project/Task report formulas shared by every output format.
 *
 * <p>This service owns no persistence or authorization. It aggregates only from supplied Task rows,
 * so HTML, Excel, and PDF cannot drift in totals, rounding, or {@code N/A} handling (RPT-001, RPT-009).
 */
@Service
public final class ProjectTaskReportService {

    /**
     * Aggregates a filtered Task set into shared report totals.
     *
     * <p>Completion and status counts reuse the Task feature's {@link TaskProgress}; minutes are summed
     * from each Task's logged-work total; completion is empty ({@code N/A}) when there are no Tasks.
     *
     * @param tasks non-deleted Tasks already filtered by Project, member, status, and work-date range
     * @return hand-checkable report totals
     */
    public ProjectTaskReportSummary summarize(List<ProjectTaskReportTask> tasks) {
        TaskProgress progress = TaskProgress.from(tasks.stream().map(ProjectTaskReportTask::status).toList());
        long totalMinutes = tasks.stream().mapToLong(ProjectTaskReportTask::loggedMinutes).sum();
        long blockedCount = progress.count(TaskStatus.BLOCKED);
        return new ProjectTaskReportSummary(progress, totalMinutes, blockedCount, progress.completionPercentage());
    }

    /**
     * Decides whether the viewer may see per-member Project hours rather than aggregate totals only.
     *
     * <p>Admin, the owning Mentor, and the current Leader may see per-member detail; ordinary members
     * receive aggregate progress and hours only (AUTH-010, RPT-005).
     *
     * @param admin viewer is an Admin
     * @param owningMentor viewer is the owning Mentor of the Project
     * @param currentLeader viewer is the current Leader of the Project
     * @return {@code true} when per-member detail is authorized
     */
    public boolean allowPerMemberDetail(boolean admin, boolean owningMentor, boolean currentLeader) {
        return admin || owningMentor || currentLeader;
    }
}