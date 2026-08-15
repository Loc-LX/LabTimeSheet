package com.lab.labtimesheet.feature.project.model.dto;

/**
 * Active authenticated actor information exposed to Project web consumers.
 *
 * @param userId stable account identifier
 * @param role immutable global role name
 */
public record ProjectActorView(long userId, String role) {
}
