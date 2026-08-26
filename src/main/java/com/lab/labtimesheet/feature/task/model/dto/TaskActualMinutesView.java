package com.lab.labtimesheet.feature.task.model.dto;

/**
 * Batch aggregate of retained work minutes for one Task in a Project.
 *
 * <p>The Task producer exposes this projection so report queries can obtain lifetime actual effort
 * with one database aggregate instead of hydrating every historical work-log entity.</p>
 *
 * @param taskId Task whose retained effort was aggregated
 * @param totalMinutes sum of all retained work-log minutes for the Task
 */
public record TaskActualMinutesView(long taskId, long totalMinutes) {}
