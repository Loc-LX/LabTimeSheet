package com.lab.labtimesheet.feature.project.model.dto;

/**
 * Immutable scalar route from an invitation to its Project and intended Intern.
 *
 * <p>Reading this route does not place an invitation or Project entity in the persistence
 * context before Account locks are acquired.</p>
 *
 * @param projectId owning Project identifier
 * @param invitedInternUserId intended Intern account identifier
 */
public record ProjectInvitationRoute(
        long projectId,
        long invitedInternUserId) {
}
