package com.lab.labtimesheet.feature.project.model.dto;

/**
 * Immutable scalar route from an exit request to its owning Project.
 *
 * <p>The request entity is first loaded under its pessimistic lock after the Account and Project
 * lock order has been established.</p>
 *
 * @param projectId owning Project identifier
 */
public record ProjectExitRequestRoute(long projectId) {
}
