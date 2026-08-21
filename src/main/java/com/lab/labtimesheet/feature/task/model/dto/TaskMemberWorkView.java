package com.lab.labtimesheet.feature.task.model.dto;

/**
 * Retained Task work total for one Project membership.
 *
 * @param membershipId Project membership identifier, not an account identifier
 * @param totalMinutes sum of all retained Task work-log minutes for that membership
 */
public record TaskMemberWorkView(long membershipId, long totalMinutes) {}
