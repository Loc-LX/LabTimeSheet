package com.lab.labtimesheet.feature.task.model.dto;

/**
 * Scoped logged-minute attribution for one Project membership.
 *
 * @param membershipId membership interval that logged the minutes, stable across reassignment
 * @param minutes total effort logged through that membership
 */
public record TaskMemberHours(long membershipId, long minutes) {}
