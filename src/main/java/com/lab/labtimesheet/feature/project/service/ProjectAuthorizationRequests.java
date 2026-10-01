package com.lab.labtimesheet.feature.project.service;

import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.identity.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.project.model.entity.ProjectEntity;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMutationRoute;
import com.lab.labtimesheet.platform.authorization.AuthorizationColumn;
import com.lab.labtimesheet.platform.authorization.AuthorizationRequest;
import com.lab.labtimesheet.platform.model.GlobalRole;
import java.util.EnumSet;
import java.util.Set;

/** Resolves Project-owned actor columns from the active account and the target Project aggregate. */
final class ProjectAuthorizationRequests {

    private ProjectAuthorizationRequests() {}

    /**
     * Builds the actor scopes held on this Project; business scope phrases remain with Project.
     *
     * @param actor authenticated account snapshot
     * @param project target Project aggregate
     * @return immutable Project capability request using the actual Project state
     */
    static AuthorizationRequest forProject(AccountIdentity actor, ProjectEntity project) {
        Set<AuthorizationColumn> columns = columns(actor.role(), actor.status(), actor.id(), project);
        return new AuthorizationRequest(columns, project.status().name(), null, null);
    }

    /**
     * Builds actor scopes from account facts already locked by the caller's Project transaction.
     *
     * @param role locked account role
     * @param status locked account status
     * @param actorUserId actor identifier
     * @param project target Project aggregate
     * @return immutable Project capability request using the actual Project state
     */
    static AuthorizationRequest forProject(
            GlobalRole role, AccountStatus status, long actorUserId, ProjectEntity project) {
        return new AuthorizationRequest(columns(role, status, actorUserId, project),
                project.status().name(), null, null);
    }

    /** Creates a creation-capability request for an active account without a Project target yet. */
    static AuthorizationRequest forProjectCreation(GlobalRole role, AccountStatus status) {
        return new AuthorizationRequest(
                role == GlobalRole.MENTOR && status == AccountStatus.ACTIVE
                        ? Set.of(AuthorizationColumn.OWNING_MENTOR) : Set.of(),
                null, null, null);
    }

    /** Resolves the owning-Mentor column from scalar Project route facts before dependent locks. */
    static AuthorizationRequest owningMentorRoute(AccountIdentity actor, ProjectMutationRoute route) {
        boolean owns = actor.status() == AccountStatus.ACTIVE
                && actor.role() == GlobalRole.MENTOR
                && actor.id() == route.mentorUserId();
        return new AuthorizationRequest(
                owns ? Set.of(AuthorizationColumn.OWNING_MENTOR) : Set.of(),
                route.status().name(), null, null);
    }

    /** Resolves only the current-Leader column for operations restricted to the issuing Leader. */
    static AuthorizationRequest currentLeaderOnly(
            GlobalRole role, AccountStatus status, long actorUserId, ProjectEntity project) {
        boolean currentLeader = status == AccountStatus.ACTIVE
                && role == GlobalRole.INTERN
                && project.status() != com.lab.labtimesheet.feature.project.model.ProjectStatus.COMPLETED
                && project.status() != com.lab.labtimesheet.feature.project.model.ProjectStatus.CANCELLED
                && project.currentLeader().internUserId() == actorUserId;
        return new AuthorizationRequest(
                currentLeader ? Set.of(AuthorizationColumn.CURRENT_LEADER) : Set.of(),
                project.status().name(), null, null);
    }

    /** Resolves only the current-Leader column from scalar route facts before dependent locks. */
    static AuthorizationRequest currentLeaderRoute(AccountIdentity actor, ProjectMutationRoute route) {
        boolean currentLeader = actor.status() == AccountStatus.ACTIVE
                && actor.role() == GlobalRole.INTERN
                && route.currentLeaderUserId() != null
                && route.currentLeaderUserId() == actor.id()
                && route.status() != com.lab.labtimesheet.feature.project.model.ProjectStatus.COMPLETED
                && route.status() != com.lab.labtimesheet.feature.project.model.ProjectStatus.CANCELLED;
        return new AuthorizationRequest(
                currentLeader ? Set.of(AuthorizationColumn.CURRENT_LEADER) : Set.of(),
                route.status().name(), null, null);
    }

    private static Set<AuthorizationColumn> columns(
            GlobalRole role, AccountStatus status, long actorUserId, ProjectEntity project) {
        if (status != AccountStatus.ACTIVE) {
            return Set.of();
        }
        EnumSet<AuthorizationColumn> columns = EnumSet.noneOf(AuthorizationColumn.class);
        if (role == GlobalRole.ADMIN) {
            columns.add(AuthorizationColumn.ADMIN);
        }
        if (role == GlobalRole.MENTOR && project.mentorUserId() == actorUserId) {
            columns.add(AuthorizationColumn.OWNING_MENTOR);
        }
        if (role == GlobalRole.INTERN) {
            if (project.status() != com.lab.labtimesheet.feature.project.model.ProjectStatus.COMPLETED
                    && project.status() != com.lab.labtimesheet.feature.project.model.ProjectStatus.CANCELLED
                    && project.currentLeader().internUserId() == actorUserId) {
                columns.add(AuthorizationColumn.CURRENT_LEADER);
            }
            if (project.hasCurrentMember(actorUserId)) {
                columns.add(AuthorizationColumn.ACTIVE_MEMBER_ASSIGNEE);
            }
        }
        return Set.copyOf(columns);
    }
}
