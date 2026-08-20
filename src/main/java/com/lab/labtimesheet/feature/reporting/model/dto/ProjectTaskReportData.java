package com.lab.labtimesheet.feature.reporting.model.dto;

import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportTask;
import java.util.List;
import java.util.Objects;

/**
 * Project/Task report dataset and viewer visibility flags supplied by the Project and Task features.
 *
 * <p>The provider owns Project membership, ownership, and leadership queries, so it supplies the
 * owning-Mentor and current-Leader flags used by {@code ProjectTaskReportService.allowPerMemberDetail}
 * and returns per-member rows only for authorized viewers (AUTH-010, RPT-005).
 *
 * @param projectId stable Project identifier
 * @param projectName user-facing Project name
 * @param owningMentor the viewer is the owning Mentor of the Project
 * @param currentLeader the viewer is the current Leader of the Project
 * @param tasks non-deleted Tasks already filtered by member, status, and work-date range
 * @param members per-member hour rows, empty when the viewer may not see per-member detail
 */
public record ProjectTaskReportData(
        long projectId,
        String projectName,
        boolean owningMentor,
        boolean currentLeader,
        List<ProjectTaskReportTask> tasks,
        List<ProjectTaskReportMember> members) {

    /**
     * Validates required report fields.
     */
    public ProjectTaskReportData {
        Objects.requireNonNull(projectName, "projectName");
        Objects.requireNonNull(tasks, "tasks");
        Objects.requireNonNull(members, "members");
    }
}