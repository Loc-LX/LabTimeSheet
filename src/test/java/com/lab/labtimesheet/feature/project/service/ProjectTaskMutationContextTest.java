package com.lab.labtimesheet.feature.project.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.model.InvitationResponse;
import com.lab.labtimesheet.feature.project.model.dto.ProjectInvitationRoute;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMutationRoute;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskContext;
import com.lab.labtimesheet.feature.project.model.entity.ProjectEntity;
import com.lab.labtimesheet.feature.project.model.entity.ProjectExitRequestEntity;
import com.lab.labtimesheet.feature.project.model.entity.ProjectMembershipEntity;
import com.lab.labtimesheet.feature.project.repository.ProjectRepository;
import com.lab.labtimesheet.feature.project.repository.ProjectExitRequestRepository;
import com.lab.labtimesheet.feature.project.repository.ProjectInvitationRepository;
import com.lab.labtimesheet.feature.task.service.TaskQueryService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectTaskMutationContextTest {

    @Mock
    private ProjectRepository projects;

    @Mock
    private ProjectInvitationRepository invitations;

    @Mock
    private ProjectExitRequestRepository exitRequests;

    @Mock
    private AccountService accounts;

    @Mock
    private ProjectQueryService queries;

    @Mock
    private TaskQueryService taskQueries;

    @Mock
    private ProjectEntity project;

    @Mock
    private ProjectExitRequestEntity pendingExit;

    @Mock
    private ProjectMembershipEntity currentMember;

    @Test
    void locksCurrentMembersBeforeLoadingTheProjectForTaskMutationContext() {
        long actorUserId = 20L;
        long projectId = 30L;
        var expected = new ProjectTaskContext(
                projectId,
                10L,
                "ACTIVE",
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 9, 30),
                40L,
                List.of(),
                Set.of(41L));
        var service = new ProjectService(
                projects, invitations, exitRequests, accounts, queries, taskQueries, Clock.systemUTC());
        when(projects.findMutationRouteById(projectId))
                .thenReturn(Optional.of(new ProjectMutationRoute(projectId, 10L, actorUserId)));
        when(projects.findCurrentInternUserIdsByProjectId(projectId)).thenReturn(List.of(actorUserId));
        when(projects.findLockedById(projectId)).thenReturn(Optional.of(project));
        when(project.memberships()).thenReturn(List.of(currentMember));
        when(currentMember.isCurrent()).thenReturn(true);
        when(currentMember.internUserId()).thenReturn(actorUserId);
        when(exitRequests.findLockedPendingByProjectId(projectId)).thenReturn(List.of(pendingExit));
        when(pendingExit.targetMembershipId()).thenReturn(41L);
        when(queries.taskContext(actorUserId, project, Set.of(41L))).thenReturn(expected);

        var actual = service.taskMutationContext(actorUserId, projectId);

        assertSame(expected, actual);
        verify(projects).findMutationRouteById(projectId);
        verify(projects).findCurrentInternUserIdsByProjectId(projectId);
        verify(accounts).lockedAccountMutationEligibility(List.of(actorUserId, actorUserId));
        verify(projects).findLockedById(projectId);
        verify(projects, never()).findById(projectId);
        verify(exitRequests).findLockedPendingByProjectId(projectId);
        verify(queries).taskContext(actorUserId, project, Set.of(41L));
    }

    @Test
    void rejectsAnUnrelatedActorBeforeTakingCurrentMemberAccountLocks() {
        long actorUserId = 20L;
        long projectId = 30L;
        var service = new ProjectService(
                projects, invitations, exitRequests, accounts, queries, taskQueries, Clock.systemUTC());
        when(projects.findMutationRouteById(projectId))
                .thenReturn(Optional.of(new ProjectMutationRoute(projectId, 10L, 40L)));
        when(projects.findCurrentInternUserIdsByProjectId(projectId)).thenReturn(List.of(40L, 41L));

        assertThrows(ProjectAccessDeniedException.class,
                () -> service.taskMutationContext(actorUserId, projectId));

        verifyNoInteractions(accounts);
        verify(projects, never()).findLockedById(projectId);
        verify(exitRequests, never()).findLockedPendingByProjectId(projectId);
    }

    @Test
    void rejectsAnUnrelatedActorBeforeTakingActivationAccountLocks() {
        long actorUserId = 20L;
        long projectId = 30L;
        var service = new ProjectService(
                projects, invitations, exitRequests, accounts, queries, taskQueries, Clock.systemUTC());
        when(projects.findMutationRouteById(projectId))
                .thenReturn(Optional.of(new ProjectMutationRoute(projectId, 10L, 40L)));

        assertThrows(ProjectAccessDeniedException.class,
                () -> service.activate(actorUserId, projectId));

        verifyNoInteractions(accounts);
        verify(projects, never()).findCurrentInternUserIdsByProjectId(projectId);
        verify(projects, never()).findLockedById(projectId);
    }

    @Test
    void rejectsInvitationActorMismatchBeforeTakingLifecycleOrProjectLocks() {
        long actorUserId = 20L;
        long invitationId = 30L;
        var service = new ProjectService(
                projects, invitations, exitRequests, accounts, queries, taskQueries, Clock.systemUTC());
        when(invitations.findRouteById(invitationId))
                .thenReturn(Optional.of(new ProjectInvitationRoute(40L, 50L)));

        assertThrows(ProjectAccessDeniedException.class,
                () -> service.respondToInvitation(actorUserId, invitationId, InvitationResponse.ACCEPT));

        verifyNoInteractions(accounts);
        verify(projects, never()).findLockedById(40L);
        verify(invitations, never()).findLockedById(invitationId);
    }
}
