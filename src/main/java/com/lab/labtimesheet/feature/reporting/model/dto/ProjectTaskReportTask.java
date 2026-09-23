package com.lab.labtimesheet.feature.reporting.model.dto;

import com.lab.labtimesheet.feature.project.model.TaskStatus;

/**
 * One Task row feeding the shared Project/Task report dataset.
 *
 * <p>Status drives completion and status counts; minutes are the Task's logged-work total within the
 * requested work-date range supplied by the Task feature.
 *
 * @param status current non-deleted Task status
 * @param loggedMinutes total logged work minutes within the filtered work-date range
 */
public record ProjectTaskReportTask(TaskStatus status, long loggedMinutes) {}