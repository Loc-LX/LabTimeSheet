package com.lab.labtimesheet.feature.project.service;

import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.identity.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.project.model.TaskStatus;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskContext;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskMemberView;
import com.lab.labtimesheet.feature.project.model.entity.Task;
import com.lab.labtimesheet.platform.authorization.AuthorizationColumn;
import com.lab.labtimesheet.platform.authorization.AuthorizationRequest;
import com.lab.labtimesheet.platform.model.GlobalRole;
import java.util.EnumSet;
import java.util.Set;

/** Resolves Task capability columns from stored identity and Project-owned Task context. */
final class TaskAuthorizationRequests {

    private TaskAuthorizationRequests() {}

    /** Builds role and Project-scope columns without inferring membership or assignment. */
    static AuthorizationRequest forProject(AccountIdentity actor, ProjectTaskContext project) {
        return request(baseColumns(actor, project), project, null, null);
    }

    /** Adds the active-member scope only when a member is assigning the Task to themselves. */
    static AuthorizationRequest forCreate(
            AccountIdentity actor, ProjectTaskContext project, Long assigneeMembershipId) {
        EnumSet<AuthorizationColumn> columns = baseColumns(actor, project);
        ProjectTaskMemberView membership = activeMembership(actor, project);
        if (isOpen(project) && membership != null && assigneeMembershipId != null
                && membership.membershipId() == assigneeMembershipId) {
            columns.add(AuthorizationColumn.ACTIVE_MEMBER_ASSIGNEE);
        }
        return request(columns, project, null, null);
    }

    /** Adds active-member scope for the matrix's any-active-member Task comment capability. */
    static AuthorizationRequest forComment(AccountIdentity actor, ProjectTaskContext project) {
        EnumSet<AuthorizationColumn> columns = baseColumns(actor, project);
        if (activeMembership(actor, project) != null) {
            columns.add(AuthorizationColumn.ACTIVE_MEMBER_ASSIGNEE);
        }
        return request(columns, project, null, null);
    }

    /** Adds assignee scope only for the actor assigned to this persisted Task. */
    static AuthorizationRequest forAssignedTask(
            AccountIdentity actor, ProjectTaskContext project, Task task, TaskStatus target) {
        EnumSet<AuthorizationColumn> columns = baseColumns(actor, project);
        ProjectTaskMemberView membership = activeMembership(actor, project);
        if (membership != null && task.getAssigneeMembershipId() == membership.membershipId()) {
            columns.add(AuthorizationColumn.ACTIVE_MEMBER_ASSIGNEE);
        } else {
            columns.remove(AuthorizationColumn.CURRENT_LEADER);
        }
        return request(columns, project, task.getStatus().name(), target == null ? null : target.name());
    }

    /** Adds work-log scope when the actor owns the retained row and remains a Project member. */
    static AuthorizationRequest forOwnWorkLog(
            AccountIdentity actor, ProjectTaskContext project, Task task, long authorMembershipId) {
        EnumSet<AuthorizationColumn> columns = baseColumns(actor, project);
        ProjectTaskMemberView membership = activeMembership(actor, project);
        if (membership != null
                && membership.membershipId() == authorMembershipId) {
            columns.add(AuthorizationColumn.ACTIVE_MEMBER_ASSIGNEE);
        } else {
            columns.remove(AuthorizationColumn.CURRENT_LEADER);
        }
        return request(columns, project, task.getStatus().name(), null);
    }

    /** Supplies owning-Mentor and current-Leader scope for BLOCK_UNBLOCK_REOPEN_TASK. */
    static AuthorizationRequest forOwningMentorTask(
            AccountIdentity actor, ProjectTaskContext project, Task task, TaskStatus target) {
        EnumSet<AuthorizationColumn> columns = EnumSet.noneOf(AuthorizationColumn.class);
        if (actor.status() == AccountStatus.ACTIVE && actor.role() == GlobalRole.MENTOR
                && actor.id() == project.mentorUserId()) {
            columns.add(AuthorizationColumn.OWNING_MENTOR);
        }
        if (actor.status() == AccountStatus.ACTIVE && actor.role() == GlobalRole.INTERN) {
            ProjectTaskMemberView membership = activeMembership(actor, project);
            if (membership != null && project.currentLeaderMembershipId() != null
                    && membership.membershipId() == project.currentLeaderMembershipId()) {
                columns.add(AuthorizationColumn.CURRENT_LEADER);
            }
        }
        return request(columns, project, task.getStatus().name(), target == null ? null : target.name());
    }

    /** Adds member scope only when the creator remains the Task's current assignee. */
    static AuthorizationRequest forCreatorAssigneeTask(
            AccountIdentity actor, ProjectTaskContext project, Task task) {
        EnumSet<AuthorizationColumn> columns = baseColumns(actor, project);
        ProjectTaskMemberView membership = activeMembership(actor, project);
        if (membership != null
                && task.getCreatorMembershipId() == membership.membershipId()
                && task.getAssigneeMembershipId() == membership.membershipId()) {
            columns.add(AuthorizationColumn.ACTIVE_MEMBER_ASSIGNEE);
        }
        return request(columns, project, task.getStatus().name(), null);
    }

    /** Adds active-member scope for a current reader; closed history is authorized separately. */
    static AuthorizationRequest forReader(AccountIdentity actor, ProjectTaskContext project) {
        EnumSet<AuthorizationColumn> columns = baseColumns(actor, project);
        if (activeMembership(actor, project) != null) {
            columns.add(AuthorizationColumn.ACTIVE_MEMBER_ASSIGNEE);
        }
        return request(columns, project, null, null);
    }

    private static EnumSet<AuthorizationColumn> baseColumns(
            AccountIdentity actor, ProjectTaskContext project) {
        EnumSet<AuthorizationColumn> columns = EnumSet.noneOf(AuthorizationColumn.class);
        if (actor.status() != AccountStatus.ACTIVE) {
            return columns;
        }
        if (actor.role() == GlobalRole.ADMIN) {
            columns.add(AuthorizationColumn.ADMIN);
        }
        if (actor.role() == GlobalRole.MENTOR && actor.id() == project.mentorUserId()) {
            columns.add(AuthorizationColumn.OWNING_MENTOR);
        }
        if (actor.role() == GlobalRole.INTERN) {
            ProjectTaskMemberView membership = activeMembership(actor, project);
            if (membership != null && project.currentLeaderMembershipId() != null
                    && membership.membershipId() == project.currentLeaderMembershipId()
                    && isOpen(project)) {
                columns.add(AuthorizationColumn.CURRENT_LEADER);
            }
        }
        return columns;
    }

    private static ProjectTaskMemberView activeMembership(AccountIdentity actor, ProjectTaskContext project) {
        if (actor.status() != AccountStatus.ACTIVE || actor.role() != GlobalRole.INTERN) {
            return null;
        }
        return project.activeMembers().stream()
                .filter(member -> member.userId() == actor.id())
                .findFirst()
                .orElse(null);
    }

    private static boolean isOpen(ProjectTaskContext project) {
        return "PLANNED".equals(project.status()) || "ACTIVE".equals(project.status());
    }

    private static AuthorizationRequest request(
            Set<AuthorizationColumn> columns,
            ProjectTaskContext project,
            String recordState,
            String targetState) {
        return new AuthorizationRequest(columns, project.status(), recordState, targetState);
    }
}
