package com.lab.labtimesheet.feature.reporting.model.dto;

import com.lab.labtimesheet.feature.task.model.TaskStatus;
import java.time.LocalDate;

/**
 * Optional filters applied to the authorized current Project/Task dataset.
 *
 * @param projectId selected visible Project, or null while showing the target picker
 * @param memberMembershipId selected same-Project membership, or null for all members
 * @param status selected fixed Task workflow status, or null for all statuses
 * @param dueFrom inclusive due-date lower bound, or null
 * @param dueTo inclusive due-date upper bound, or null
 */
public record ProjectTaskReportFilter(
        Long projectId,
        Long memberMembershipId,
        TaskStatus status,
        LocalDate dueFrom,
        LocalDate dueTo) {
}
