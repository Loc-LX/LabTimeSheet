package com.lab.labtimesheet.feature.project.model;

import com.lab.labtimesheet.feature.project.model.entity.ProjectMembershipEntity;
import java.time.Instant;

/**
 * In-transaction handoff between closing the current leadership term and opening its replacement.
 * It exists so the old interval can be flushed before PostgreSQL validates the new current term.
 *
 * @param replacement active same-Project membership appointed as Leader
 * @param effectiveAt end/start instant shared by the adjacent leadership terms
 */
public record ProjectLeaderChange(ProjectMembershipEntity replacement, Instant effectiveAt) {
}
