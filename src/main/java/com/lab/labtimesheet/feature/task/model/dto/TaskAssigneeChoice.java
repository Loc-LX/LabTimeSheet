package com.lab.labtimesheet.feature.task.model.dto;

/**
 * One authorized Task assignee option for the create form.
 *
 * @param membershipId active same-Project membership identifier, never a user identifier
 * @param displayName safe display label supplied by the Project feature
 */
public record TaskAssigneeChoice(long membershipId, String displayName) {}
