package com.lab.labtimesheet.feature.project.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Browser form selecting an Intern for direct membership or leadership appointment.
 *
 * @param internUserId positive Intern user identifier; null binding is rejected before mutation
 */
public record ProjectMemberForm(@NotNull @Positive Long internUserId) {
}
