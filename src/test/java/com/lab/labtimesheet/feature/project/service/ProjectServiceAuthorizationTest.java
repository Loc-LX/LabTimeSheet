package com.lab.labtimesheet.feature.project.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.internship.model.InternshipStatus;
import com.lab.labtimesheet.feature.internship.model.dto.LockedAccountMutationEligibility;
import com.lab.labtimesheet.feature.internship.service.InternshipService;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.notification.service.NotificationService;
import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.model.ProjectStatus;
import com.lab.labtimesheet.feature.project.model.dto.ProjectInvitationNotificationRoute;
import com.lab.labtimesheet.feature.project.model.dto.ProjectInvitationRoute;
import com.lab.labtimesheet.feature.project.model.entity.ProjectEntity;
import com.lab.labtimesheet.feature.project.model.entity.ProjectInvitationEntity;
import com.lab.labtimesheet.feature.project.repository.ProjectExitRequestRepository;
import com.lab.labtimesheet.feature.project.repository.ProjectInvitationRepository;
import com.lab.labtimesheet.feature.project.repository.ProjectRepository;
import com.lab.labtimesheet.platform.authorization.AuthorizationCapability;
import com.lab.labtimesheet.platform.authorization.AuthorizationPolicy;
import com.lab.labtimesheet.platform.authorization.AuthorizationRequest;
import com.lab.labtimesheet.platform.model.GlobalRole;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Proves that a scoped invitation action reaches the shared authorization policy before mutation. */
class ProjectServiceAuthorizationTest {

    /** Protects AUTH-012, AUTH-011, and PRJ-018: removing the policy call must fail before invitation state changes. */
    @Test
    void refusesUnrelatedAdminRevocationWhenPolicyDeniesItsProjectInvitationColumn() {
        long ownerId = 10L;
        long projectId = 20L;
        long inviteeId = 30L;
        long leaderId = 40L;
        long invitationId = 50L;
        ProjectRepository projects = mock(ProjectRepository.class);
        ProjectInvitationRepository invitations = mock(ProjectInvitationRepository.class);
        ProjectExitRequestRepository exits = mock(ProjectExitRequestRepository.class);
        AccountService accounts = mock(AccountService.class);
        InternshipService internships = mock(InternshipService.class);
        ProjectQueryService queries = mock(ProjectQueryService.class);
        TaskQueryService taskQueries = mock(TaskQueryService.class);
        TaskTransferService taskTransfers = mock(TaskTransferService.class);
        NotificationService notifications = mock(NotificationService.class);
        AuthorizationPolicy policy = ProjectAuthorizationTestPolicy.create();
        ProjectService service = new ProjectService(projects, invitations, exits, accounts, internships,
                queries, taskQueries, taskTransfers, notifications, Clock.systemUTC(), policy);

        ProjectEntity project = mock(ProjectEntity.class);
        ProjectInvitationEntity invitation = mock(ProjectInvitationEntity.class);
        given(invitations.findRouteById(invitationId))
                .willReturn(Optional.of(new ProjectInvitationRoute(projectId, inviteeId)));
        given(invitations.findNotificationRouteById(invitationId)).willReturn(Optional.of(
                new ProjectInvitationNotificationRoute(projectId, inviteeId, leaderId, ownerId)));
        long adminId = 5L;
        given(internships.lockedAccountMutationEligibility(List.of(adminId, inviteeId, leaderId, ownerId)))
                .willReturn(List.of(
                        snapshot(adminId, GlobalRole.ADMIN),
                        snapshot(inviteeId, GlobalRole.INTERN),
                        snapshot(leaderId, GlobalRole.INTERN),
                        snapshot(ownerId, GlobalRole.MENTOR)));
        given(projects.findLockedById(projectId)).willReturn(Optional.of(project));
        given(project.mentorUserId()).willReturn(ownerId);
        given(project.status()).willReturn(ProjectStatus.ACTIVE);
        given(invitations.findLockedById(invitationId)).willReturn(Optional.of(invitation));
        assertThat(policy.allows(AuthorizationCapability.PROJECT_INVITATIONS,
                new AuthorizationRequest(java.util.Set.of(), ProjectStatus.ACTIVE.name(), null, null))).isFalse();

        assertThatThrownBy(() -> service.revokeInvitation(adminId, projectId, invitationId))
                .isInstanceOf(ProjectAccessDeniedException.class);

        verifyNoInteractions(notifications);
    }

    private static LockedAccountMutationEligibility snapshot(long userId, GlobalRole role) {
        return new LockedAccountMutationEligibility(userId, role, AccountStatus.ACTIVE,
                role == GlobalRole.INTERN ? Optional.of(InternshipStatus.ACTIVE) : Optional.empty());
    }
}
