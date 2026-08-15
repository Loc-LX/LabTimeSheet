package com.lab.labtimesheet.feature.project.model.dto;

/**
 * Current eligible Project member exposed to Task services without sharing Project entities.
 *
 * @param membershipId active membership-interval identifier used by Task foreign keys
 * @param userId Intern account identifier used for actor authorization
 * @param displayName current Account display name
 */
public record ProjectTaskMemberView(long membershipId, long userId, String displayName) {
}
