package com.lab.labtimesheet.feature.project.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.identity.service.BootstrapService;
import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.exception.ProjectRuleViolationException;
import com.lab.labtimesheet.feature.project.model.InvitationResponse;
import com.lab.labtimesheet.feature.project.model.dto.ProjectCreateCommand;
import com.lab.labtimesheet.feature.project.exception.TaskConflictException;
import com.lab.labtimesheet.feature.project.model.dto.RemainingEffortForecastInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.LocalDate;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.lab.labtimesheet.feature.notification.service.NotificationService;
import com.lab.labtimesheet.platform.model.SecurityMode;
import com.lab.labtimesheet.platform.model.dto.SmtpConnection;
import com.lab.labtimesheet.platform.model.dto.SmtpDraft;
import com.lab.labtimesheet.platform.service.SmtpConfigurationService;
import com.lab.labtimesheet.platform.service.SmtpProbe;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.time.Duration;

@Import({TestcontainersConfiguration.class, ProjectInvitationExitIntegrationTest.MailProbeConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProjectInvitationExitIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");

    @Autowired
    private ProjectService projects;

    @Autowired
    private ProjectQueryService projectPages;

    @Autowired
    private NotificationService notifications;

    @Autowired
    private SmtpConfigurationService smtp;

    @Autowired
    private RecordingSmtpProbe mail;

    @Autowired
    private JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BootstrapService bootstrap;

    @Test
    void leaderInvitationAcceptsOnlyTheIntendedInternAndMentorDirectAddSupersedesIt() {
        long mentorId = user("mentor-invite@example.test", "MENTOR");
        long leaderId = intern("leader-invite@example.test", "I101");
        long inviteeId = intern("invitee-invite@example.test", "I102");
        long unrelatedId = intern("unrelated-invite@example.test", "I103");
        long projectId = createProject(mentorId, leaderId, "Invitation");

        assertEquals(
                List.of(leaderId),
                notificationRecipients(
                        "MEMBERSHIP_CHANGED", "/projects/" + projectId, "INITIAL_MEMBER_ADDED"));
        assertEquals(
                List.of(leaderId),
                notificationRecipients(
                        "LEADERSHIP_CHANGED", "/projects/" + projectId, "INITIAL_LEADER_ASSIGNED"));

        long invitationId = projects.issueInvitation(leaderId, projectId, inviteeId);

        assertEquals("PENDING", text("select status from project_invitations where id = ?", invitationId));
        assertEquals(
                List.of(invitationId),
                projectPages.pendingInvitations(inviteeId).stream()
                        .map(invitation -> invitation.invitationId())
                        .toList());
        assertTrue(projectPages.pendingInvitations(unrelatedId).isEmpty());
        assertEquals(
                List.of(inviteeId),
                notificationRecipients(
                        "PROJECT_INVITATION_CREATED", "/projects/" + projectId + "/invitations/" + invitationId));
        assertEquals(membershipId(projectId, leaderId), number("""
                select term.membership_id
                from project_invitations invitation
                join project_leadership_terms term on term.id = invitation.issuing_leadership_term_id
                where invitation.id = ?
                """, invitationId));
        assertThrows(ProjectAccessDeniedException.class,
                () -> projects.respondToInvitation(unrelatedId, invitationId, InvitationResponse.ACCEPT));

        projects.addMember(mentorId, projectId, inviteeId);

        assertTrue(projectPages.pendingInvitations(inviteeId).isEmpty());
        assertEquals(
                List.of(inviteeId),
                notificationRecipients(
                        "MEMBERSHIP_CHANGED", "/projects/" + projectId, "MEMBER_ADDED"));
        assertEquals("SUPERSEDED", text("select status from project_invitations where id = ?", invitationId));
        assertEquals(
                List.of(mentorId, leaderId, inviteeId),
                notificationRecipients(
                        "PROJECT_INVITATION_RESOLVED", "/projects/" + projectId + "/invitations/" + invitationId));
        assertEquals("MENTOR_DIRECT_ADD", text(
                "select resolution_code from project_invitations where id = ?", invitationId));
        assertEquals(1, count("""
                select count(*) from project_memberships
                where project_id = ? and intern_user_id = ? and left_at is null
                """, projectId, inviteeId));
        assertThrows(ProjectAccessDeniedException.class,
                () -> projects.respondToInvitation(unrelatedId, invitationId, InvitationResponse.ACCEPT));
        assertThrows(ProjectRuleViolationException.class,
                () -> projects.respondToInvitation(inviteeId, invitationId, InvitationResponse.ACCEPT));
    }

    @Test
    void invitationResponseRetainsTerminalDecisionAndLeaderCanRevokeOnlyOwnPendingInvitation() {
        long mentorId = user("mentor-revoke@example.test", "MENTOR");
        long leaderId = intern("leader-revoke@example.test", "I104");
        long inviteeId = intern("invitee-revoke@example.test", "I105");
        long otherLeaderId = intern("other-revoke@example.test", "I106");
        long projectId = createProject(mentorId, leaderId, "Revoke");
        projects.addMember(mentorId, projectId, otherLeaderId);
        long invitationId = projects.issueInvitation(leaderId, projectId, inviteeId);

        assertThrows(ProjectAccessDeniedException.class,
                () -> projects.revokeInvitation(otherLeaderId, invitationId));
        projects.revokeInvitation(leaderId, invitationId);
        assertEquals("REVOKED", text("select status from project_invitations where id = ?", invitationId));
        assertEquals("INVITER_REVOKED", text(
                "select resolution_code from project_invitations where id = ?", invitationId));
        assertEquals(
                List.of(mentorId, leaderId, inviteeId),
                notificationRecipients(
                        "PROJECT_INVITATION_RESOLVED", "/projects/" + projectId + "/invitations/" + invitationId));
        assertThrows(ProjectAccessDeniedException.class,
                () -> projects.revokeInvitation(otherLeaderId, invitationId));
        assertThrows(ProjectAccessDeniedException.class,
                () -> projects.respondToInvitation(otherLeaderId, invitationId, InvitationResponse.ACCEPT));
        assertThrows(ProjectRuleViolationException.class,
                () -> projects.respondToInvitation(inviteeId, invitationId, InvitationResponse.ACCEPT));

        long secondInviteeId = intern("invitee-decline@example.test", "I107");
        long secondInvitationId = projects.issueInvitation(leaderId, projectId, secondInviteeId);
        projects.respondToInvitation(secondInviteeId, secondInvitationId, InvitationResponse.DECLINE);
        assertEquals("DECLINED", text("select status from project_invitations where id = ?", secondInvitationId));
        assertEquals("INVITEE_DECLINED", text(
                "select resolution_code from project_invitations where id = ?", secondInvitationId));
        assertEquals(
                List.of(mentorId, leaderId),
                notificationRecipients(
                        "PROJECT_INVITATION_RESOLVED", "/projects/" + projectId + "/invitations/" + secondInvitationId));

        long acceptedInviteeId = intern("invitee-accept@example.test", "I113");
        long acceptedInvitationId = projects.issueInvitation(leaderId, projectId, acceptedInviteeId);
        projects.respondToInvitation(acceptedInviteeId, acceptedInvitationId, InvitationResponse.ACCEPT);
        assertEquals("ACCEPTED", text(
                "select status from project_invitations where id = ?", acceptedInvitationId));
        assertEquals(acceptedInviteeId, number("""
                select intern_user_id from project_memberships
                where id = (select accepted_membership_id from project_invitations where id = ?)
                """, acceptedInvitationId));
        assertEquals(acceptedInviteeId, number(
                "select resolved_by_user_id from project_invitations where id = ?", acceptedInvitationId));
        assertEquals(
                List.of(mentorId, leaderId),
                notificationRecipients(
                        "PROJECT_INVITATION_RESOLVED", "/projects/" + projectId + "/invitations/" + acceptedInvitationId));
        assertEquals(
                List.of(),
                notificationRecipients(
                        "MEMBERSHIP_CHANGED", "/projects/" + projectId, "INVITATION_ACCEPTED"));
    }

    @Test
    void membershipExitNotificationsUseRequestTypeAndCollapseDecisionRecipients() {
        long mentorId = user("mentor-exit-notification@example.test", "MENTOR");
        long leaderId = intern("leader-exit-notification@example.test", "I155");
        long memberId = intern("member-exit-notification@example.test", "I156");
        long projectId = createProject(mentorId, leaderId, "Exit notifications");
        projects.addMember(mentorId, projectId, memberId);
        long memberMembershipId = membershipId(projectId, memberId);

        long removalRequestId = projects.requestMemberRemoval(
                leaderId, projectId, memberMembershipId, "Please hand over the remaining work");
        assertEquals(
                List.of(mentorId, memberId),
                notificationRecipients(
                        "MEMBERSHIP_EXIT_REQUESTED", "/projects/" + projectId + "/exits/" + removalRequestId));

        projects.rejectExit(mentorId, removalRequestId, "Keep the member for now");
        assertEquals(
                List.of(leaderId, memberId),
                notificationRecipients(
                        "MEMBERSHIP_EXIT_RESOLVED", "/projects/" + projectId + "/exits/" + removalRequestId));

        long leaveRequestId = projects.requestOwnLeave(memberId, projectId, "Placement complete");
        assertEquals(
                List.of(mentorId, leaderId),
                notificationRecipients(
                        "MEMBERSHIP_EXIT_REQUESTED", "/projects/" + projectId + "/exits/" + leaveRequestId));

        projects.cancelExit(memberId, leaveRequestId);
        assertEquals(
                List.of(leaderId, memberId),
                notificationRecipients(
                        "MEMBERSHIP_EXIT_RESOLVED", "/projects/" + projectId + "/exits/" + leaveRequestId));
    }

    @Test
    void leaderCannotInviteAnInternWhoseAccountIsNoLongerEligible() {
        long mentorId = user("mentor-ineligible-invite@example.test", "MENTOR");
        long leaderId = intern("leader-ineligible-invite@example.test", "I118");
        long withdrawnId = intern("withdrawn-invite@example.test", "I119");
        long projectId = createProject(mentorId, leaderId, "Ineligible invitation");
        jdbc.update(
                "update intern_profiles set internship_status = 'WITHDRAWN', withdrawn_at = ? "
                        + "where user_id = ?",
                NOW.atOffset(java.time.ZoneOffset.UTC), withdrawnId);

        assertThrows(ProjectRuleViolationException.class,
                () -> projects.issueInvitation(leaderId, projectId, withdrawnId));
        assertEquals(0, count(
                "select count(*) from project_invitations where project_id = ? and invited_intern_user_id = ?",
                projectId,
                withdrawnId));
    }

    @Test
    void completedCurrentLeaderCannotIssueAnInvitation() {
        long mentorId = user("mentor-completed-leader@example.test", "MENTOR");
        long leaderId = intern("leader-completed-leader@example.test", "I129");
        long inviteeId = intern("invitee-completed-leader@example.test", "I130");
        long projectId = createProject(mentorId, leaderId, "Completed Leader");

        completeIntern(leaderId);

        assertThrows(ProjectAccessDeniedException.class,
                () -> projects.issueInvitation(leaderId, projectId, inviteeId));
        assertEquals(0, count(
                "select count(*) from project_invitations where project_id = ?", projectId));
    }

    @Test
    void pendingInvitationIsRevokedWhenInviteeBecomesIneligibleBeforeResponse() {
        long mentorId = user("mentor-response-ineligible@example.test", "MENTOR");
        long leaderId = intern("leader-response-ineligible@example.test", "I123");
        long inviteeId = intern("invitee-response-ineligible@example.test", "I124");
        long projectId = createProject(mentorId, leaderId, "Response eligibility");
        long invitationId = projects.issueInvitation(leaderId, projectId, inviteeId);

        jdbc.update(
                "update intern_profiles set internship_status = 'WITHDRAWN', withdrawn_at = ? where user_id = ?",
                NOW.atOffset(java.time.ZoneOffset.UTC),
                inviteeId);
        entityManager.clear();

        projects.respondToInvitation(inviteeId, invitationId, InvitationResponse.ACCEPT);

        assertEquals("REVOKED", text("select status from project_invitations where id = ?", invitationId));
        assertEquals("INVITEE_INELIGIBLE", text(
                "select resolution_code from project_invitations where id = ?", invitationId));
        assertEquals(0, count(
                "select count(*) from project_memberships where project_id = ? and intern_user_id = ?",
                projectId,
                inviteeId));
    }

    @Test
    void owningMentorMayRevokeAnyPendingInvitationAndLeaderChangeRevokesTheOldTermQueue() {
        long mentorId = user("mentor-term-revoke@example.test", "MENTOR");
        long leaderId = intern("leader-term-revoke@example.test", "I114");
        long replacementId = intern("replacement-term-revoke@example.test", "I115");
        long firstInviteeId = intern("first-term-revoke@example.test", "I116");
        long secondInviteeId = intern("second-term-revoke@example.test", "I117");
        long projectId = createProject(mentorId, leaderId, "Term revocation");
        projects.addMember(mentorId, projectId, replacementId);
        long mentorRevoked = projects.issueInvitation(leaderId, projectId, firstInviteeId);
        projects.revokeInvitation(mentorId, mentorRevoked);
        assertEquals("MENTOR_REVOKED", text(
                "select resolution_code from project_invitations where id = ?", mentorRevoked));

        long leaderChanged = projects.issueInvitation(leaderId, projectId, secondInviteeId);
        projects.changeLeader(mentorId, projectId, replacementId);
        assertEquals(
                List.of(leaderId, replacementId),
                notificationRecipients(
                        "LEADERSHIP_CHANGED", "/projects/" + projectId, "LEADER_CHANGED"));
        assertEquals("REVOKED", text("select status from project_invitations where id = ?", leaderChanged));
        assertEquals("LEADER_CHANGED", text(
                "select resolution_code from project_invitations where id = ?", leaderChanged));
    }

    @Test
    void pendingExitRequiresCorrectParticipantShapeAndOnlyRequesterMayCancel() {
        long mentorId = user("mentor-exit-request@example.test", "MENTOR");
        long leaderId = intern("leader-exit-request@example.test", "I108");
        long memberId = intern("member-exit-request@example.test", "I109");
        long otherMemberId = intern("other-exit-request@example.test", "I110");
        long projectId = createProject(mentorId, leaderId, "Exit request");
        projects.addMembers(mentorId, projectId, java.util.List.of(memberId, otherMemberId));
        long memberMembershipId = membershipId(projectId, memberId);

        assertThrows(ProjectRuleViolationException.class,
                () -> projects.requestMemberRemoval(leaderId, projectId, memberMembershipId, "  "));
        long requestId = projects.requestMemberRemoval(
                leaderId, projectId, memberMembershipId, "Work handover is needed");

        assertEquals("PENDING", text(
                "select status from project_membership_exit_requests where id = ?", requestId));
        assertEquals(1, count("""
                select count(*) from project_memberships
                where id = ? and left_at is null
                """, memberMembershipId));
        var lockedTaskContext = projects.taskMutationContext(leaderId, projectId);
        assertTrue(lockedTaskContext.pendingExitMembershipIds().contains(memberMembershipId));
        var readiness = projectPages.exitReadiness(leaderId, projectId).getFirst();
        assertEquals(requestId, readiness.requestId());
        assertEquals(memberMembershipId, readiness.targetMembershipId());
        assertEquals(0L, readiness.unfinishedTaskCount());
        assertTrue(readiness.readyForApproval());
        var taskContext = projectPages.taskContext(leaderId, projectId);
        assertTrue(taskContext.pendingExitMembershipIds().contains(memberMembershipId));
        assertNotNull(taskContext.activeMembers().stream()
                .filter(member -> member.membershipId() == memberMembershipId)
                .findFirst()
                .orElseThrow()
                .joinedAt());
        var memberView = projectPages.members(leaderId, projectId).stream()
                .filter(member -> member.membershipId() == memberMembershipId)
                .findFirst()
                .orElseThrow();
        assertTrue(memberView.joinedAt() != null);
        assertNull(memberView.leftAt());
        assertThrows(ProjectAccessDeniedException.class,
                () -> projects.cancelExit(otherMemberId, requestId));
        projects.cancelExit(leaderId, requestId);
        assertEquals("CANCELLED", text(
                "select status from project_membership_exit_requests where id = ?", requestId));
        assertThrows(ProjectAccessDeniedException.class,
                () -> projects.cancelExit(otherMemberId, requestId));
        assertEquals(1, count("""
                select count(*) from project_memberships
                where id = ? and left_at is null
                """, memberMembershipId));
    }

    @Test
    void memberLeaveTargetsTheAuthenticatedMemberAndDuplicatePendingRequestIsRejected() {
        long mentorId = user("mentor-own-leave@example.test", "MENTOR");
        long leaderId = intern("leader-own-leave@example.test", "I111");
        long memberId = intern("member-own-leave@example.test", "I112");
        long projectId = createProject(mentorId, leaderId, "Own leave");
        projects.addMember(mentorId, projectId, memberId);

        long requestId = projects.requestOwnLeave(memberId, projectId, "Leaving the placement");
        assertThrows(ProjectRuleViolationException.class,
                () -> projects.requestOwnLeave(memberId, projectId, "Second request"));
        assertThrows(ProjectAccessDeniedException.class,
                () -> projects.cancelExit(leaderId, requestId));
        projects.cancelExit(memberId, requestId);
    }

    @Test
    void internMembershipIntervalsRetainCurrentAndClosedRowsAcrossProjects() {
        long mentorId = user("mentor-intervals@example.test", "MENTOR");
        long internId = intern("intern-intervals@example.test", "I125");
        long otherLeaderId = intern("other-leader-intervals@example.test", "I126");
        long firstProjectId = createProject(mentorId, internId, "Retained intervals one");
        long secondProjectId = createProject(mentorId, otherLeaderId, "Retained intervals two");
        projects.addMember(mentorId, secondProjectId, internId);

        long closedMembershipId = membershipId(firstProjectId, internId);
        long currentMembershipId = membershipId(secondProjectId, internId);
        Instant leftAt = NOW.plusSeconds(60);
        jdbc.update(
                "update project_memberships set left_at = ?, removed_by_mentor_user_id = ?, updated_at = ? "
                        + "where id = ?",
                leftAt.atOffset(java.time.ZoneOffset.UTC),
                mentorId,
                leftAt.atOffset(java.time.ZoneOffset.UTC),
                closedMembershipId);
        entityManager.clear();

        var intervals = projectPages.membershipIntervals(internId);

        assertEquals(List.of(firstProjectId, secondProjectId),
                intervals.stream().map(interval -> interval.projectId()).toList());
        assertEquals(closedMembershipId, intervals.get(0).membershipId());
        assertEquals(leftAt, intervals.get(0).leftAt());
        assertEquals(currentMembershipId, intervals.get(1).membershipId());
        assertNotNull(intervals.get(1).joinedAt());
        assertNull(intervals.get(1).leftAt());
        assertThrows(ProjectAccessDeniedException.class,
                () -> projectPages.membershipIntervals(mentorId));
    }

    @Test
    void intervalProjectionDoesNotPartiallyHydrateAProjectUsedByTheNextTaskContextRead() {
        long mentorId = user("mentor-interval-context@example.test", "MENTOR");
        long leaderId = intern("leader-interval-context@example.test", "I127");
        long memberId = intern("member-interval-context@example.test", "I128");
        long projectId = createProject(mentorId, leaderId, "Interval context");
        projects.addMember(mentorId, projectId, memberId);
        entityManager.clear();

        assertEquals(1, projectPages.membershipIntervals(leaderId).size());

        var context = projectPages.taskContext(leaderId, projectId);

        assertEquals(List.of(leaderId, memberId), context.activeMembers().stream()
                .map(member -> member.userId())
                .toList());
    }

    @Test
    void completedInternCannotCreateOrCancelExitRequests() {
        long mentorId = user("mentor-completed-exit@example.test", "MENTOR");
        long leaderId = intern("leader-completed-exit@example.test", "I120");
        long completedMemberId = intern("completed-member-exit@example.test", "I121");
        long pendingMemberId = intern("pending-member-exit@example.test", "I122");
        long projectId = createProject(mentorId, leaderId, "Completed exit");
        projects.addMembers(mentorId, projectId, java.util.List.of(completedMemberId, pendingMemberId));

        completeIntern(completedMemberId);
        assertThrows(ProjectAccessDeniedException.class,
                () -> projects.requestOwnLeave(completedMemberId, projectId, "Completed placement"));

        long requestId = projects.requestOwnLeave(pendingMemberId, projectId, "Placement ending");
        completeIntern(pendingMemberId);
        assertThrows(ProjectAccessDeniedException.class,
                () -> projects.cancelExit(pendingMemberId, requestId));
        assertEquals("PENDING", text(
                "select status from project_membership_exit_requests where id = ?", requestId));
    }

    @Test
    void removedFormerRequesterCannotCancelItsPendingExitRequest() {
        long mentorId = user("mentor-removed-requester@example.test", "MENTOR");
        long leaderId = intern("leader-removed-requester@example.test", "I131");
        long memberId = intern("member-removed-requester@example.test", "I132");
        long projectId = createProject(mentorId, leaderId, "Removed requester");
        projects.addMember(mentorId, projectId, memberId);
        long requestId = projects.requestOwnLeave(memberId, projectId, "Member leave");
        long membershipId = membershipId(projectId, memberId);
        Instant leftAt = NOW.plusSeconds(60);
        jdbc.update(
                "update project_memberships set left_at = ?, removed_by_mentor_user_id = ?, updated_at = ? "
                        + "where id = ?",
                leftAt.atOffset(java.time.ZoneOffset.UTC),
                mentorId,
                leftAt.atOffset(java.time.ZoneOffset.UTC),
                membershipId);
        entityManager.clear();

        assertThrows(ProjectAccessDeniedException.class,
                () -> projects.cancelExit(memberId, requestId));
        assertEquals("PENDING", text(
                "select status from project_membership_exit_requests where id = ?", requestId));
    }

    @Test
    void pendingExitCannotBeCancelledAfterProjectCompletion() {
        long mentorId = user("mentor-closed-cancel@example.test", "MENTOR");
        long leaderId = intern("leader-closed-cancel@example.test", "I133");
        long memberId = intern("member-closed-cancel@example.test", "I134");
        long projectId = createProject(mentorId, leaderId, "Closed cancellation");
        projects.addMember(mentorId, projectId, memberId);
        long requestId = projects.requestOwnLeave(memberId, projectId, "Member leave");

        completeProject(projectId, mentorId);

        assertThrows(ProjectAccessDeniedException.class,
                () -> projects.cancelExit(memberId, requestId));
        assertEquals("PENDING", text(
                "select status from project_membership_exit_requests where id = ?", requestId));
    }

    @Test
    void unrelatedActorCannotLearnCompletedProjectStateThroughExitMutations() {
        long mentorId = user("mentor-completed-exit-auth@example.test", "MENTOR");
        long leaderId = intern("leader-completed-exit-auth@example.test", "I135");
        long memberId = intern("member-completed-exit-auth@example.test", "I136");
        long unrelatedId = intern("unrelated-completed-exit-auth@example.test", "I137");
        long projectId = createProject(mentorId, leaderId, "Completed exit authorization");
        projects.addMember(mentorId, projectId, memberId);
        long requestId = projects.requestOwnLeave(memberId, projectId, "Member leave");
        long leaderMembershipId = membershipId(projectId, leaderId);

        completeProject(projectId, mentorId);

        assertThrows(ProjectAccessDeniedException.class,
                () -> projects.requestOwnLeave(unrelatedId, projectId, "Unrelated leave"));
        assertThrows(ProjectAccessDeniedException.class,
                () -> projects.requestMemberRemoval(
                        unrelatedId, projectId, leaderMembershipId, "Unrelated removal"));
        assertThrows(ProjectAccessDeniedException.class,
                () -> projects.cancelExit(unrelatedId, requestId));
        assertEquals("PENDING", text(
                "select status from project_membership_exit_requests where id = ?", requestId));
    }

    /**
     * TSK-009, TSK-004 and AC-TSK-011 require an exit batch to reassign current Tasks while retaining
     * their Task-owned history. The worked IN_PROGRESS Task's creator, comment, 45-minute work log,
     * and final Leader assignment actor/time must be present after transfer and remain identical
     * after approval closes the source membership; the TODO and DONE Tasks retain their specified
     * transfer behavior.
     */
    @Test
    void leaderMayRepeatExitTransferBatchesBeforeMentorApprovalWithoutUndoingCompletedTasks() {
        long mentorId = user("mentor-approve-exit@example.test", "MENTOR");
        long leaderId = intern("leader-approve-exit@example.test", "I138");
        long targetId = intern("target-approve-exit@example.test", "I139");
        long recipientId = intern("recipient-approve-exit@example.test", "I140");
        long projectId = createProject(mentorId, leaderId, "Approve exit");
        projects.addMembers(mentorId, projectId, List.of(targetId, recipientId));
        long targetMembershipId = membershipId(projectId, targetId);
        long recipientMembershipId = membershipId(projectId, recipientId);
        long leaderMembershipId = membershipId(projectId, leaderId);
        long firstTaskId = insertTask(projectId, targetMembershipId, leaderMembershipId, "First transfer", "TODO");
        long secondTaskId = insertTask(projectId, targetMembershipId, targetMembershipId, "Second transfer", "IN_PROGRESS");
        long doneTaskId = insertTask(projectId, targetMembershipId, leaderMembershipId, "Completed history", "DONE");
        jdbc.update("insert into task_comments (task_id, author_user_id, body) values (?, ?, ?)",
                secondTaskId, targetId, "Retained exit-batch comment");
        jdbc.update("""
                insert into task_work_logs
                    (project_id, task_id, membership_id, work_date, minutes, note, created_at, updated_at)
                values (?, ?, ?, date '2026-08-20', 45, 'Retained exit-batch effort', ?, ?)
                """, projectId, secondTaskId, targetMembershipId,
                java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
        long requestId = projects.requestMemberRemoval(
                leaderId, projectId, targetMembershipId, "Redistribute before approval");

        projects.transferTasks(
                leaderId,
                projectId,
                requestId,
                targetMembershipId,
                Set.of(firstTaskId),
                Map.of(firstTaskId, number("select version from tasks where id = ?", firstTaskId)),
                Map.of(),
                recipientMembershipId);
        projects.transferTasks(
                leaderId,
                projectId,
                requestId,
                targetMembershipId,
                Set.of(secondTaskId),
                Map.of(secondTaskId, number("select version from tasks where id = ?", secondTaskId)),
                Map.of(secondTaskId, new RemainingEffortForecastInput(90, "Exit batch handover")),
                recipientMembershipId);
        var transferredHistory = projectPages.history(leaderId, projectId).tasks().stream()
                .filter(task -> task.id() == secondTaskId).findFirst().orElseThrow();
        assertEquals(recipientMembershipId, transferredHistory.assigneeMembershipId());
        assertEquals(targetMembershipId, transferredHistory.creatorMembershipId());
        assertEquals(leaderMembershipId, transferredHistory.assignerMembershipId());
        assertNotNull(transferredHistory.assignedAt());
        assertEquals("IN_PROGRESS", transferredHistory.status().name());
        assertEquals(List.of("Retained exit-batch comment"), transferredHistory.comments().stream()
                .map(comment -> comment.body()).toList());
        assertEquals(1, transferredHistory.workLogs().size());
        assertEquals(targetMembershipId, transferredHistory.workLogs().getFirst().membershipId());
        assertEquals(45, transferredHistory.workLogs().getFirst().minutes());
        assertEquals("Retained exit-batch effort", transferredHistory.workLogs().getFirst().note());
        projects.approveExit(mentorId, requestId, "Ready after batches");
        var historyAfterApproval = projectPages.history(mentorId, projectId).tasks().stream()
                .filter(task -> task.id() == secondTaskId).findFirst().orElseThrow();
        assertEquals(transferredHistory, historyAfterApproval);

        assertEquals(
                List.of(targetId),
                notificationRecipients(
                        "MEMBERSHIP_CHANGED", "/projects/" + projectId, "MEMBER_REMOVED"));
        assertEquals("APPROVED", text(
                "select status from project_membership_exit_requests where id = ?", requestId));
        assertEquals(0, count("select count(*) from project_memberships where id = ? and left_at is null", targetMembershipId));
        assertEquals(recipientMembershipId, number(
                "select assignee_membership_id from tasks where id = ?", firstTaskId));
        assertEquals(recipientMembershipId, number(
                "select assignee_membership_id from tasks where id = ?", secondTaskId));
        assertEquals(targetMembershipId, number(
                "select assignee_membership_id from tasks where id = ?", doneTaskId));
    }

    @Test
    void staleExitTransferVersionReturnsConflictWithoutPartialTaskOrRequestMutation() {
        long mentorId = user("mentor-stale-transfer@example.test", "MENTOR");
        long leaderId = intern("leader-stale-transfer@example.test", "I190");
        long targetId = intern("target-stale-transfer@example.test", "I191");
        long recipientId = intern("recipient-stale-transfer@example.test", "I192");
        long projectId = createProject(mentorId, leaderId, "Stale transfer");
        projects.addMembers(mentorId, projectId, List.of(targetId, recipientId));
        long targetMembershipId = membershipId(projectId, targetId);
        long recipientMembershipId = membershipId(projectId, recipientId);
        long leaderMembershipId = membershipId(projectId, leaderId);
        long taskId = insertTask(projectId, targetMembershipId, leaderMembershipId, "Stale Task", "TODO");
        long requestId = projects.requestMemberRemoval(
                leaderId, projectId, targetMembershipId, "Stale browser snapshot");
        long observedVersion = number("select version from tasks where id = ?", taskId);
        int notificationCount = count("select count(*) from notifications");

        jdbc.update("update tasks set version = version + 1 where id = ?", taskId);
        entityManager.clear();

        assertThrows(TaskConflictException.class, () -> projects.transferTasks(
                leaderId,
                projectId,
                requestId,
                targetMembershipId,
                Set.of(taskId),
                Map.of(taskId, observedVersion),
                recipientMembershipId));

        assertEquals(targetMembershipId, number(
                "select assignee_membership_id from tasks where id = ?", taskId));
        assertEquals(observedVersion + 1, number("select version from tasks where id = ?", taskId));
        assertEquals("PENDING", text(
                "select status from project_membership_exit_requests where id = ?", requestId));
        assertEquals(notificationCount, count("select count(*) from notifications"));
    }

    @Test
    void versionedExitTransferRejectsNullTaskVersionsWithoutMutation() {
        long mentorId = user("mentor-null-version-transfer@example.test", "MENTOR");
        long leaderId = intern("leader-null-version-transfer@example.test", "I193");
        long targetId = intern("target-null-version-transfer@example.test", "I194");
        long recipientId = intern("recipient-null-version-transfer@example.test", "I195");
        long projectId = createProject(mentorId, leaderId, "Null version transfer");
        projects.addMembers(mentorId, projectId, List.of(targetId, recipientId));
        long targetMembershipId = membershipId(projectId, targetId);
        long recipientMembershipId = membershipId(projectId, recipientId);
        long leaderMembershipId = membershipId(projectId, leaderId);
        long taskId = insertTask(projectId, targetMembershipId, leaderMembershipId, "Null version Task", "TODO");
        long requestId = projects.requestMemberRemoval(
                leaderId, projectId, targetMembershipId, "Null browser version snapshot");
        long observedVersion = number("select version from tasks where id = ?", taskId);
        int notificationCount = count("select count(*) from notifications");

        jdbc.update("update tasks set version = version + 1 where id = ?", taskId);
        entityManager.clear();

        assertThrows(ProjectRuleViolationException.class, () -> projects.transferTasks(
                leaderId,
                projectId,
                requestId,
                targetMembershipId,
                Set.of(taskId),
                (Map<Long, Long>) null,
                recipientMembershipId));

        assertEquals(targetMembershipId, number(
                "select assignee_membership_id from tasks where id = ?", taskId));
        assertEquals(observedVersion + 1, number("select version from tasks where id = ?", taskId));
        assertEquals("PENDING", text(
                "select status from project_membership_exit_requests where id = ?", requestId));
        assertEquals(notificationCount, count("select count(*) from notifications"));
    }

    @Test
    void leaderExitRequiresReplacementAndLeaderChangeDoesNotMoveAssignments() {
        long mentorId = user("mentor-leader-exit@example.test", "MENTOR");
        long leaderId = intern("leader-leader-exit@example.test", "I141");
        long replacementId = intern("replacement-leader-exit@example.test", "I142");
        long projectId = createProject(mentorId, leaderId, "Leader exit");
        projects.addMember(mentorId, projectId, replacementId);
        long leaderMembershipId = membershipId(projectId, leaderId);
        long replacementMembershipId = membershipId(projectId, replacementId);
        long taskId = insertTask(projectId, leaderMembershipId, leaderMembershipId, "Leader task", "TODO");
        long requestId = projects.requestOwnLeave(leaderId, projectId, "Leader leaving");

        assertThrows(ProjectRuleViolationException.class,
                () -> projects.approveExit(mentorId, requestId, null));
        projects.changeLeader(mentorId, projectId, replacementId);
        assertEquals(leaderMembershipId, number(
                "select assignee_membership_id from tasks where id = ?", taskId));
        projects.transferTasks(
                replacementId, projectId, leaderMembershipId, java.util.Set.of(taskId), replacementMembershipId);
        projects.approveExit(mentorId, requestId, null);

        assertEquals("APPROVED", text(
                "select status from project_membership_exit_requests where id = ?", requestId));
        assertEquals(0, count("select count(*) from project_memberships where id = ? and left_at is null", leaderMembershipId));
    }

    @Test
    void cancellationAndRejectionRestoreEligibilityWithoutUndoingCompletedBatches() {
        long mentorId = user("mentor-reject-exit@example.test", "MENTOR");
        long leaderId = intern("leader-reject-exit@example.test", "I148");
        long targetId = intern("target-reject-exit@example.test", "I149");
        long recipientId = intern("recipient-reject-exit@example.test", "I150");
        long projectId = createProject(mentorId, leaderId, "Reject exit");
        projects.addMembers(mentorId, projectId, List.of(targetId, recipientId));
        long targetMembershipId = membershipId(projectId, targetId);
        long recipientMembershipId = membershipId(projectId, recipientId);
        long leaderMembershipId = membershipId(projectId, leaderId);
        long taskId = insertTask(projectId, targetMembershipId, leaderMembershipId, "Persist batch", "TODO");
        long requestId = projects.requestMemberRemoval(leaderId, projectId, targetMembershipId, "Need review");

        projects.transferTasks(
                leaderId, projectId, targetMembershipId, java.util.Set.of(taskId), recipientMembershipId);
        projects.rejectExit(mentorId, requestId, "Keep member");

        assertEquals("REJECTED", text(
                "select status from project_membership_exit_requests where id = ?", requestId));
        assertEquals(1, count("select count(*) from project_memberships where id = ? and left_at is null", targetMembershipId));
        assertEquals(recipientMembershipId, number(
                "select assignee_membership_id from tasks where id = ?", taskId));

        long secondTaskId = insertTask(projectId, targetMembershipId, leaderMembershipId, "Cancel batch", "TODO");
        long secondRequestId = projects.requestOwnLeave(targetId, projectId, "Second review");
        projects.transferTasks(
                leaderId, projectId, targetMembershipId, java.util.Set.of(secondTaskId), recipientMembershipId);
        projects.cancelExit(targetId, secondRequestId);

        assertEquals("CANCELLED", text(
                "select status from project_membership_exit_requests where id = ?", secondRequestId));
        assertEquals(recipientMembershipId, number(
                "select assignee_membership_id from tasks where id = ?", secondTaskId));
    }

    @Test
    void directMentorRemovalTransfersAllUnfinishedTasksAtomicallyAndKeepsDoneAssignment() {
        long mentorId = user("mentor-direct-remove@example.test", "MENTOR");
        long leaderId = intern("leader-direct-remove@example.test", "I143");
        long targetId = intern("target-direct-remove@example.test", "I144");
        long projectId = createProject(mentorId, leaderId, "Direct removal");
        projects.addMember(mentorId, projectId, targetId);
        long leaderMembershipId = membershipId(projectId, leaderId);
        long targetMembershipId = membershipId(projectId, targetId);
        long unfinishedId = insertTask(projectId, targetMembershipId, leaderMembershipId, "Unfinished", "BLOCKED");
        long doneId = insertTask(projectId, targetMembershipId, leaderMembershipId, "Done", "DONE");

        projects.directRemoveMember(mentorId, projectId, targetMembershipId, null);

        assertEquals(leaderMembershipId, number(
                "select assignee_membership_id from tasks where id = ?", unfinishedId));
        assertEquals(targetMembershipId, number(
                "select assignee_membership_id from tasks where id = ?", doneId));
        assertEquals(0, count("select count(*) from project_memberships where id = ? and left_at is null", targetMembershipId));
    }

    @Test
    void directMentorRemovalRejectsWorkedUnfinishedTasksBeforeAnyMutation() {
        long mentorId = user("mentor-direct-worked-remove@example.test", "MENTOR");
        long leaderId = intern("leader-direct-worked-remove@example.test", "I177");
        long targetId = intern("target-direct-worked-remove@example.test", "I178");
        long projectId = createProject(mentorId, leaderId, "Direct worked removal");
        projects.addMember(mentorId, projectId, targetId);
        long leaderMembershipId = membershipId(projectId, leaderId);
        long targetMembershipId = membershipId(projectId, targetId);
        long taskId = insertTask(projectId, targetMembershipId, leaderMembershipId,
                "Worked unfinished", "IN_PROGRESS");
        jdbc.update("""
                insert into task_work_logs
                    (project_id, task_id, membership_id, work_date, minutes, created_at, updated_at)
                values (?, ?, ?, date '2026-08-20', 45, ?, ?)
                """, projectId, taskId, targetMembershipId,
                java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
        MutationState before = mutationState(projectId);

        assertThrows(ProjectRuleViolationException.class,
                () -> projects.directRemoveMember(mentorId, projectId, targetMembershipId, null));

        assertEquals(before, mutationState(projectId));
        assertEquals(targetMembershipId, number(
                "select assignee_membership_id from tasks where id = ?", taskId));
        assertEquals(1, count("select count(*) from project_memberships where id = ? and left_at is null",
                targetMembershipId));
        assertEquals(leaderMembershipId, number(
                "select membership_id from project_leadership_terms where project_id = ? and ended_at is null",
                projectId));
    }

    @Test
    void directMentorRemovalOfCurrentLeaderRejectsWorkedTasksBeforeLeadershipMutation() {
        long mentorId = user("mentor-direct-worked-leader@example.test", "MENTOR");
        long leaderId = intern("leader-direct-worked-leader@example.test", "I179");
        long replacementId = intern("replacement-direct-worked-leader@example.test", "I180");
        long projectId = createProject(mentorId, leaderId, "Direct worked Leader removal");
        projects.addMember(mentorId, projectId, replacementId);
        long leaderMembershipId = membershipId(projectId, leaderId);
        long taskId = insertTask(projectId, leaderMembershipId, leaderMembershipId,
                "Worked Leader task", "IN_PROGRESS");
        jdbc.update("""
                insert into task_work_logs
                    (project_id, task_id, membership_id, work_date, minutes, created_at, updated_at)
                values (?, ?, ?, date '2026-08-20', 45, ?, ?)
                """, projectId, taskId, leaderMembershipId,
                java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
        MutationState before = mutationState(projectId);
        entityManager.clear();

        assertThrows(ProjectRuleViolationException.class,
                () -> projects.directRemoveMember(mentorId, projectId, leaderMembershipId, replacementId));

        assertEquals(before, mutationState(projectId));
        assertEquals(leaderMembershipId, number(
                "select membership_id from project_leadership_terms where project_id = ? and ended_at is null",
                projectId));
        assertEquals(leaderMembershipId, number(
                "select assignee_membership_id from tasks where id = ?", taskId));
    }

    @Test
    void directLeaderRemovalAutoTransfersOnlyRemainingUnworkedTasksAfterForecastAwareTransfer() {
        long mentorId = user("mentor-direct-forecast-leader@example.test", "MENTOR");
        long leaderId = intern("leader-direct-forecast-leader@example.test", "I181");
        long replacementId = intern("replacement-direct-forecast-leader@example.test", "I182");
        long projectId = createProject(mentorId, leaderId, "Direct forecast Leader removal");
        projects.addMember(mentorId, projectId, replacementId);
        long leaderMembershipId = membershipId(projectId, leaderId);
        long replacementMembershipId = membershipId(projectId, replacementId);
        long workedTaskId = insertTask(projectId, leaderMembershipId, leaderMembershipId,
                "Forecasted Leader task", "IN_PROGRESS");
        long unworkedTaskId = insertTask(projectId, leaderMembershipId, leaderMembershipId,
                "Unworked Leader task", "TODO");
        jdbc.update("""
                insert into task_work_logs
                    (project_id, task_id, membership_id, work_date, minutes, created_at, updated_at)
                values (?, ?, ?, date '2026-08-20', 45, ?, ?)
                """, projectId, workedTaskId, leaderMembershipId,
                java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
        long requestId = projects.requestOwnLeave(leaderId, projectId, "Transfer before removal");
        entityManager.clear();

        projects.changeLeader(mentorId, projectId, replacementId);
        entityManager.clear();
        projects.transferTasks(
                replacementId,
                projectId,
                requestId,
                leaderMembershipId,
                Set.of(workedTaskId),
                Map.of(
                        workedTaskId, number("select version from tasks where id = ?", workedTaskId)),
                Map.of(workedTaskId, new RemainingEffortForecastInput(90, "  hand over  ")),
                replacementMembershipId);
        entityManager.clear();

        assertEquals(replacementMembershipId, number(
                "select assignee_membership_id from tasks where id = ?", workedTaskId));
        assertEquals(leaderMembershipId, number(
                "select assignee_membership_id from tasks where id = ?", unworkedTaskId));
        assertEquals("PENDING", text(
                "select status from project_membership_exit_requests where id = ?", requestId));
        assertEquals(0, count("select count(*) from task_remaining_effort_forecasts "
                + "where project_id = ? and task_id = ?", projectId, unworkedTaskId));

        projects.directRemoveMember(mentorId, projectId, leaderMembershipId, replacementId);

        assertEquals(replacementMembershipId, number(
                "select assignee_membership_id from tasks where id = ?", workedTaskId));
        assertEquals(replacementMembershipId, number(
                "select assignee_membership_id from tasks where id = ?", unworkedTaskId));
        assertEquals(1, count("select count(*) from task_remaining_effort_forecasts "
                + "where project_id = ? and task_id = ?", projectId, workedTaskId));
        assertEquals(0, count("select count(*) from task_remaining_effort_forecasts "
                + "where project_id = ? and task_id = ?", projectId, unworkedTaskId));
        assertEquals(0, count("select count(*) from project_memberships "
                + "where id = ? and left_at is null", leaderMembershipId));
        assertEquals(replacementMembershipId, number(
                "select membership_id from project_leadership_terms "
                        + "where project_id = ? and ended_at is null", projectId));
        assertEquals("APPROVED", text(
                "select status from project_membership_exit_requests where id = ?", requestId));
        assertEquals(4, count("select count(*) from notifications "
                + "where notification_type = 'TASK_REASSIGNED' and action_url in (?, ?)",
                "/projects/" + projectId + "/tasks/" + workedTaskId,
                "/projects/" + projectId + "/tasks/" + unworkedTaskId));
    }

    @Test
    void directMentorRemovalOfCurrentLeaderReplacesLeadershipBeforeTransferring() {
        long mentorId = user("mentor-direct-leader@example.test", "MENTOR");
        long leaderId = intern("leader-direct-leader@example.test", "I151");
        long replacementId = intern("replacement-direct-leader@example.test", "I152");
        long projectId = createProject(mentorId, leaderId, "Direct Leader removal");
        projects.addMember(mentorId, projectId, replacementId);
        long leaderMembershipId = membershipId(projectId, leaderId);
        long replacementMembershipId = membershipId(projectId, replacementId);
        long taskId = insertTask(projectId, leaderMembershipId, leaderMembershipId, "Leader-owned", "IN_PROGRESS");

        projects.directRemoveMember(mentorId, projectId, leaderMembershipId, replacementId);

        assertEquals(
                List.of(leaderId, replacementId),
                notificationRecipients(
                        "LEADERSHIP_CHANGED", "/projects/" + projectId, "LEADER_CHANGED"));
        assertEquals(
                List.of(leaderId),
                notificationRecipients(
                        "MEMBERSHIP_CHANGED", "/projects/" + projectId, "MEMBER_REMOVED"));
        assertEquals(replacementMembershipId, number("select membership_id from project_leadership_terms "
                + "where project_id = ? and ended_at is null", projectId));
        assertEquals(replacementMembershipId, number(
                "select assignee_membership_id from tasks where id = ?", taskId));
        assertEquals(0, count("select count(*) from project_memberships where id = ? and left_at is null", leaderMembershipId));
        assertEquals(1, count("select count(*) from project_leadership_terms where project_id = ? and ended_at is null", projectId));
    }

    @Test
    void exitTransferMustUseThePendingRequestFromTheRoute() {
        long mentorId = user("mentor-transfer-route@example.test", "MENTOR");
        long leaderId = intern("leader-transfer-route@example.test", "I161");
        long targetId = intern("target-transfer-route@example.test", "I162");
        long recipientId = intern("recipient-transfer-route@example.test", "I163");
        long projectId = createProject(mentorId, leaderId, "Transfer route");
        projects.addMembers(mentorId, projectId, List.of(targetId, recipientId));
        long targetMembershipId = membershipId(projectId, targetId);
        long recipientMembershipId = membershipId(projectId, recipientId);
        long leaderMembershipId = membershipId(projectId, leaderId);
        long taskId = insertTask(projectId, targetMembershipId, leaderMembershipId, "Route guarded", "TODO");
        long requestId = projects.requestMemberRemoval(
                leaderId, projectId, targetMembershipId, "Transfer route must be bound");

        assertThrows(ProjectAccessDeniedException.class, () -> projects.transferTasks(
                leaderId,
                projectId,
                requestId + 1,
                targetMembershipId,
                java.util.Set.of(taskId),
                recipientMembershipId));
        assertEquals(targetMembershipId, number(
                "select assignee_membership_id from tasks where id = ?", taskId));
        assertEquals("PENDING", text(
                "select status from project_membership_exit_requests where id = ?", requestId));
    }

    @Test
    void routeBoundExitTransferRejectsTerminalRequestWithoutChangingTask() {
        long mentorId = user("mentor-terminal-transfer@example.test", "MENTOR");
        long leaderId = intern("leader-terminal-transfer@example.test", "I171");
        long targetId = intern("target-terminal-transfer@example.test", "I172");
        long recipientId = intern("recipient-terminal-transfer@example.test", "I173");
        long projectId = createProject(mentorId, leaderId, "Terminal transfer");
        projects.addMembers(mentorId, projectId, List.of(targetId, recipientId));
        long targetMembershipId = membershipId(projectId, targetId);
        long recipientMembershipId = membershipId(projectId, recipientId);
        long leaderMembershipId = membershipId(projectId, leaderId);
        long taskId = insertTask(projectId, targetMembershipId, leaderMembershipId, "Terminal route", "TODO");
        long requestId = projects.requestMemberRemoval(
                leaderId, projectId, targetMembershipId, "Resolve before transfer");
        projects.rejectExit(mentorId, requestId, "Keep current member");
        long retryRequestId = projects.requestMemberRemoval(
                leaderId, projectId, targetMembershipId, "Retry after retained rejection");

        assertThrows(ProjectRuleViolationException.class, () -> projects.transferTasks(
                leaderId,
                projectId,
                requestId,
                targetMembershipId,
                java.util.Set.of(taskId),
                recipientMembershipId));
        assertEquals(targetMembershipId, number(
                "select assignee_membership_id from tasks where id = ?", taskId));
        assertEquals("REJECTED", text(
                "select status from project_membership_exit_requests where id = ?", requestId));
        assertEquals("PENDING", text(
                "select status from project_membership_exit_requests where id = ?", retryRequestId));
    }

    @Test
    void nestedWorkflowRoutesRejectForeignProjectIdsWithoutChangingRetainedState() {
        long mentorId = user("mentor-nested-route-context@example.test", "MENTOR");
        long leaderAId = intern("leader-nested-route-a@example.test", "I174");
        long leaderBId = intern("leader-nested-route-b@example.test", "I175");
        long cancelTargetId = intern("cancel-target-nested-route@example.test", "I176");
        long approveTargetId = intern("approve-target-nested-route@example.test", "I177");
        long rejectTargetId = intern("reject-target-nested-route@example.test", "I178");
        long inviteeId = intern("invitee-nested-route@example.test", "I179");
        long projectAId = createProject(mentorId, leaderAId, "Nested route A");
        long projectBId = createProject(mentorId, leaderBId, "Nested route B");
        projects.addMembers(
                mentorId,
                projectBId,
                List.of(cancelTargetId, approveTargetId, rejectTargetId));
        long invitationId = projects.issueInvitation(leaderBId, projectBId, inviteeId);
        long cancelRequestId = projects.requestOwnLeave(
                cancelTargetId, projectBId, "Cancel route context");
        long approveRequestId = projects.requestMemberRemoval(
                leaderBId, projectBId, membershipId(projectBId, approveTargetId), "Approve route context");
        long rejectRequestId = projects.requestMemberRemoval(
                leaderBId, projectBId, membershipId(projectBId, rejectTargetId), "Reject route context");
        int notificationsBefore = count("select count(*) from notifications");

        assertThrows(ProjectAccessDeniedException.class,
                () -> projects.revokeInvitation(mentorId, projectAId, invitationId));
        assertThrows(ProjectAccessDeniedException.class,
                () -> projects.cancelExit(cancelTargetId, projectAId, cancelRequestId));
        assertThrows(ProjectAccessDeniedException.class,
                () -> projects.approveExit(mentorId, projectAId, approveRequestId, "Wrong route"));
        assertThrows(ProjectAccessDeniedException.class,
                () -> projects.rejectExit(mentorId, projectAId, rejectRequestId, "Wrong route"));

        assertEquals("PENDING", text(
                "select status from project_invitations where id = ?", invitationId));
        assertEquals("PENDING", text(
                "select status from project_membership_exit_requests where id = ?", cancelRequestId));
        assertEquals("PENDING", text(
                "select status from project_membership_exit_requests where id = ?", approveRequestId));
        assertEquals("PENDING", text(
                "select status from project_membership_exit_requests where id = ?", rejectRequestId));
        assertEquals(notificationsBefore, count("select count(*) from notifications"));
    }

    @Test
    void acAuth009ProjectRoleContextOperationMatrixDeniesWithoutStateDisclosureOrMutation() {
        long mentorAId = user("mentor-auth-matrix-a@example.test", "MENTOR");
        long mentorBId = user("mentor-auth-matrix-b@example.test", "MENTOR");
        long adminId = user("admin-auth-matrix@example.test", "ADMIN");
        long leaderAId = intern("leader-auth-matrix-a@example.test", "I184");
        long replacementAId = intern("replacement-auth-matrix-a@example.test", "I185");
        long ordinaryAId = intern("ordinary-auth-matrix-a@example.test", "I186");
        long targetAId = intern("target-auth-matrix-a@example.test", "I187");
        long inviteeAId = intern("invitee-auth-matrix-a@example.test", "I188");
        long wrongInviteeId = intern("wrong-invitee-auth-matrix@example.test", "I189");
        long candidateId = intern("candidate-auth-matrix@example.test", "I190");
        long leaderBId = intern("leader-auth-matrix-b@example.test", "I191");
        long completedLeaderId = intern("completed-leader-auth-matrix@example.test", "I192");
        long removedMemberId = intern("removed-member-auth-matrix@example.test", "I193");
        long projectAId = createProject(mentorAId, leaderAId, "Authorization matrix A");
        createProject(mentorBId, leaderBId, "Authorization matrix B");
        projects.addMembers(mentorAId, projectAId, List.of(replacementAId, ordinaryAId, targetAId));
        projects.changeLeader(mentorAId, projectAId, replacementAId);
        long invitationId = projects.issueInvitation(replacementAId, projectAId, inviteeAId);
        long targetMembershipId = membershipId(projectAId, targetAId);
        long ordinaryMembershipId = membershipId(projectAId, ordinaryAId);
        long replacementMembershipId = membershipId(projectAId, replacementAId);
        long requestId = projects.requestMemberRemoval(
                replacementAId, projectAId, targetMembershipId, "Authorization matrix request");
        long taskId = insertTask(
                projectAId,
                targetMembershipId,
                replacementMembershipId,
                "Authorization matrix task",
                "TODO");

        long completedProjectId = createProject(mentorAId, completedLeaderId, "Authorization matrix completed");
        projects.addMember(mentorAId, completedProjectId, removedMemberId);
        projects.directRemoveMember(
                mentorAId, completedProjectId, membershipId(completedProjectId, removedMemberId), null);
        completeProject(completedProjectId, mentorAId);

        List<AuthorizationCase> cases = List.of(
                new AuthorizationCase(
                        projectAId,
                        "Admin cannot directly add a member",
                        ProjectAccessDeniedException.class,
                        () -> projects.addMember(adminId, projectAId, candidateId)),
                new AuthorizationCase(
                        projectAId,
                        "current Leader cannot use Mentor direct add",
                        ProjectAccessDeniedException.class,
                        () -> projects.addMember(replacementAId, projectAId, candidateId)),
                new AuthorizationCase(
                        projectAId,
                        "non-owning Mentor cannot issue an invitation",
                        ProjectAccessDeniedException.class,
                        () -> projects.issueInvitation(mentorBId, projectAId, candidateId)),
                new AuthorizationCase(
                        projectAId,
                        "ordinary member cannot revoke an invitation",
                        ProjectAccessDeniedException.class,
                        () -> projects.revokeInvitation(ordinaryAId, projectAId, invitationId)),
                new AuthorizationCase(
                        projectAId,
                        "former Leader cannot revoke a current-term invitation",
                        ProjectAccessDeniedException.class,
                        () -> projects.revokeInvitation(leaderAId, projectAId, invitationId)),
                new AuthorizationCase(
                        projectAId,
                        "wrong invitee cannot respond",
                        ProjectAccessDeniedException.class,
                        () -> projects.respondToInvitation(wrongInviteeId, invitationId, InvitationResponse.ACCEPT)),
                new AuthorizationCase(
                        projectAId,
                        "former Leader cannot request member removal",
                        ProjectAccessDeniedException.class,
                        () -> projects.requestMemberRemoval(
                                leaderAId, projectAId, ordinaryMembershipId, "Former Leader attempt")),
                new AuthorizationCase(
                        projectAId,
                        "wrong requester cannot cancel an exit",
                        ProjectAccessDeniedException.class,
                        () -> projects.cancelExit(ordinaryAId, projectAId, requestId)),
                new AuthorizationCase(
                        projectAId,
                        "exit target cannot cancel a Leader removal",
                        ProjectAccessDeniedException.class,
                        () -> projects.cancelExit(targetAId, projectAId, requestId)),
                new AuthorizationCase(
                        projectAId,
                        "ordinary member cannot transfer exit Tasks",
                        ProjectAccessDeniedException.class,
                        () -> projects.transferTasks(
                                ordinaryAId,
                                projectAId,
                                requestId,
                                targetMembershipId,
                                Set.of(taskId),
                                replacementMembershipId)),
                new AuthorizationCase(
                        projectAId,
                        "non-owning Mentor cannot approve an exit",
                        ProjectAccessDeniedException.class,
                        () -> projects.approveExit(mentorBId, projectAId, requestId, "Wrong owner")),
                new AuthorizationCase(
                        projectAId,
                        "non-owning Mentor cannot reject an exit",
                        ProjectAccessDeniedException.class,
                        () -> projects.rejectExit(mentorBId, projectAId, requestId, "Wrong owner")),
                new AuthorizationCase(
                        projectAId,
                        "non-owning Mentor cannot remove a member directly",
                        ProjectAccessDeniedException.class,
                        () -> projects.directRemoveMember(mentorBId, projectAId, targetMembershipId, null)),
                new AuthorizationCase(
                        projectAId,
                        "non-owning Mentor cannot change leadership",
                        ProjectAccessDeniedException.class,
                        () -> projects.changeLeader(mentorBId, projectAId, ordinaryAId)),
                new AuthorizationCase(
                        completedProjectId,
                        "removed member cannot request leave from completed Project",
                        ProjectAccessDeniedException.class,
                        () -> projects.requestOwnLeave(removedMemberId, completedProjectId, "Removed member")));

        for (var authorizationCase : cases) {
            var before = mutationState(authorizationCase.projectId());
            Throwable failure = assertThrows(Throwable.class, authorizationCase.operation(), authorizationCase.label());
            assertTrue(
                    authorizationCase.expectedException().isInstance(failure),
                    authorizationCase.label() + " returned " + failure.getClass().getName());
            assertEquals(before, mutationState(authorizationCase.projectId()), authorizationCase.label());
        }
    }

    @Test
    void routeBoundTransferRejectsAnExistingRequestFromAnotherProject() {
        long mentorId = user("mentor-transfer-foreign-route@example.test", "MENTOR");
        long leaderAId = intern("leader-transfer-foreign-a@example.test", "I180");
        long leaderBId = intern("leader-transfer-foreign-b@example.test", "I181");
        long targetBId = intern("target-transfer-foreign-b@example.test", "I182");
        long recipientBId = intern("recipient-transfer-foreign-b@example.test", "I183");
        long projectAId = createProject(mentorId, leaderAId, "Transfer foreign A");
        long projectBId = createProject(mentorId, leaderBId, "Transfer foreign B");
        projects.addMembers(mentorId, projectBId, List.of(targetBId, recipientBId));
        long targetMembershipId = membershipId(projectBId, targetBId);
        long recipientMembershipId = membershipId(projectBId, recipientBId);
        long leaderMembershipId = membershipId(projectBId, leaderBId);
        long taskId = insertTask(projectBId, targetMembershipId, leaderMembershipId, "Foreign route", "TODO");
        long requestId = projects.requestMemberRemoval(
                leaderBId, projectBId, targetMembershipId, "Foreign route request");

        assertThrows(ProjectAccessDeniedException.class, () -> projects.transferTasks(
                leaderBId,
                projectAId,
                requestId,
                targetMembershipId,
                java.util.Set.of(taskId),
                recipientMembershipId));
        assertEquals(targetMembershipId, number(
                "select assignee_membership_id from tasks where id = ?", taskId));
        assertEquals("PENDING", text(
                "select status from project_membership_exit_requests where id = ?", requestId));
    }

    @Test
    void leaderRemovalRejectsMembershipIdFromAnotherProjectWithoutRuleDetails() {
        long mentorId = user("mentor-cross-project-membership@example.test", "MENTOR");
        long leaderId = intern("leader-cross-project-membership@example.test", "I164");
        long targetId = intern("target-cross-project-membership@example.test", "I165");
        long otherLeaderId = intern("other-leader-cross-project-membership@example.test", "I166");
        long firstProjectId = createProject(mentorId, leaderId, "First cross project");
        long secondProjectId = createProject(mentorId, otherLeaderId, "Second cross project");
        projects.addMember(mentorId, secondProjectId, targetId);
        long foreignMembershipId = membershipId(secondProjectId, targetId);

        assertThrows(ProjectAccessDeniedException.class, () -> projects.requestMemberRemoval(
                leaderId, firstProjectId, foreignMembershipId, "Cross-project identifier"));
        assertEquals(0, count(
                "select count(*) from project_membership_exit_requests where target_membership_id = ?",
                foreignMembershipId));
    }

    @Test
    void leadershipChangeRejectsAnInternFromAnotherProjectWithoutContextLeak() {
        long mentorId = user("mentor-cross-project-leader@example.test", "MENTOR");
        long leaderId = intern("leader-cross-project-leader@example.test", "I167");
        long foreignLeaderId = intern("foreign-leader-cross-project@example.test", "I168");
        long firstProjectId = createProject(mentorId, leaderId, "First leadership project");
        createProject(mentorId, foreignLeaderId, "Foreign leadership project");

        assertThrows(ProjectAccessDeniedException.class, () -> projects.changeLeader(
                mentorId, firstProjectId, foreignLeaderId));
        assertThrows(ProjectAccessDeniedException.class, () -> projects.directRemoveMember(
                mentorId,
                firstProjectId,
                membershipId(firstProjectId, leaderId),
                foreignLeaderId));
        assertEquals(leaderId, number("select membership.intern_user_id "
                + "from project_leadership_terms term "
                + "join project_memberships membership on membership.id = term.membership_id "
                + "where term.project_id = ? and term.ended_at is null", firstProjectId));
    }

    @Test
    void completedProjectsRejectInvitationRevocationWithoutChangingRetainedHistory() {
        long mentorId = user("mentor-completed-invitation-revoke@example.test", "MENTOR");
        long leaderId = intern("leader-completed-invitation-revoke@example.test", "I169");
        long inviteeId = intern("invitee-completed-invitation-revoke@example.test", "I170");
        long projectId = createProject(mentorId, leaderId, "Completed invitation revoke");
        long invitationId = projects.issueInvitation(leaderId, projectId, inviteeId);

        completeProject(projectId, mentorId);

        assertThrows(ProjectRuleViolationException.class,
                () -> projects.revokeInvitation(mentorId, invitationId));
        assertEquals("PENDING", text("select status from project_invitations where id = ?", invitationId));
    }

    @Test
    void projectHistoryExposesStoredMembershipAndLeadershipProvenance() {
        long mentorId = user("mentor-history-provenance@example.test", "MENTOR");
        long leaderId = intern("leader-history-provenance@example.test", "I157");
        long directMemberId = intern("direct-history-provenance@example.test", "I158");
        long invitedMemberId = intern("invited-history-provenance@example.test", "I159");
        long completionLeaderId = intern("completion-history-provenance@example.test", "I160");
        long projectId = createProject(mentorId, leaderId, "History provenance");
        projects.addMember(mentorId, projectId, directMemberId);
        long directMembershipId = membershipId(projectId, directMemberId);

        long invitationId = projects.issueInvitation(leaderId, projectId, invitedMemberId);
        projects.respondToInvitation(invitedMemberId, invitationId, InvitationResponse.ACCEPT);
        long invitedMembershipId = membershipId(projectId, invitedMemberId);
        projects.changeLeader(mentorId, projectId, invitedMemberId);
        projects.directRemoveMember(mentorId, projectId, directMembershipId, null);

        var history = projectPages.history(mentorId, projectId);
        var initialMembership = history.memberships().stream()
                .filter(membership -> membership.internUserId() == leaderId)
                .findFirst()
                .orElseThrow();
        var directMembership = history.memberships().stream()
                .filter(membership -> membership.membershipId() == directMembershipId)
                .findFirst()
                .orElseThrow();
        var invitedMembership = history.memberships().stream()
                .filter(membership -> membership.membershipId() == invitedMembershipId)
                .findFirst()
                .orElseThrow();
        assertEquals(mentorId, initialMembership.addedByUserId());
        assertNull(initialMembership.removedByMentorUserId());
        assertEquals(mentorId, directMembership.addedByUserId());
        assertEquals(mentorId, directMembership.removedByMentorUserId());
        assertEquals(invitedMemberId, invitedMembership.addedByUserId());
        assertNull(invitedMembership.removedByMentorUserId());

        var termsByLeader = history.leadership().stream()
                .collect(java.util.stream.Collectors.toMap(
                        term -> term.leaderName().contains("leader-history-provenance") ? leaderId : invitedMemberId,
                        term -> term));
        assertEquals(mentorId, termsByLeader.get(leaderId).appointedByMentorUserId());
        assertEquals(mentorId, termsByLeader.get(leaderId).endedByMentorUserId());
        assertEquals(mentorId, termsByLeader.get(invitedMemberId).appointedByMentorUserId());
        assertNull(termsByLeader.get(invitedMemberId).endedByMentorUserId());

        long completionProjectId = createProject(mentorId, completionLeaderId, "Completion provenance");
        jdbc.update("update projects set status = 'ACTIVE', activated_at = ?, updated_at = ? where id = ?",
                NOW.atOffset(java.time.ZoneOffset.UTC), NOW.atOffset(java.time.ZoneOffset.UTC), completionProjectId);
        entityManager.clear();
        projects.complete(mentorId, completionProjectId);

        var completionHistory = projectPages.history(mentorId, completionProjectId);
        assertEquals(mentorId, completionHistory.memberships().getFirst().removedByMentorUserId());
        assertEquals(mentorId, completionHistory.leadership().getFirst().appointedByMentorUserId());
        assertEquals(mentorId, completionHistory.leadership().getFirst().endedByMentorUserId());
    }

    @Test
    void removedMemberCannotReadOpenHistoryButReadsRetainedHistoryAfterCompletion() {
        long mentorId = user("mentor-history-boundary@example.test", "MENTOR");
        long leaderId = intern("leader-history-boundary@example.test", "I153");
        long memberId = intern("member-history-boundary@example.test", "I154");
        long projectId = createProject(mentorId, leaderId, "History boundary");
        projects.addMember(mentorId, projectId, memberId);
        long memberMembershipId = membershipId(projectId, memberId);

        projects.directRemoveMember(mentorId, projectId, memberMembershipId, null);

        assertThrows(ProjectAccessDeniedException.class,
                () -> projectPages.history(memberId, projectId));
        assertEquals(2, projectPages.history(mentorId, projectId).memberships().size());

        jdbc.update("update projects set status = 'ACTIVE', activated_at = ?, updated_at = ? where id = ?",
                NOW.atOffset(java.time.ZoneOffset.UTC), NOW.atOffset(java.time.ZoneOffset.UTC), projectId);
        entityManager.clear();
        projects.complete(mentorId, projectId);

        assertEquals(2, projectPages.history(memberId, projectId).memberships().size());
    }

    @Test
    void completionRequiresAllCurrentTasksDoneAndHistoryUsesRetainedRowsWithExactVisibility() {
        long mentorId = user("mentor-complete-project@example.test", "MENTOR");
        long leaderId = intern("leader-complete-project@example.test", "I145");
        long memberId = intern("member-complete-project@example.test", "I146");
        long unrelatedId = intern("unrelated-complete-project@example.test", "I147");
        long projectId = createProject(mentorId, leaderId, "Complete project");
        projects.addMember(mentorId, projectId, memberId);
        long leaderMembershipId = membershipId(projectId, leaderId);
        long memberMembershipId = membershipId(projectId, memberId);
        long taskId = insertTask(projectId, memberMembershipId, leaderMembershipId, "Must finish", "TODO");
        long invitationId = projects.issueInvitation(leaderId, projectId, unrelatedId);
        long requestId = projects.requestOwnLeave(memberId, projectId, "Pending at completion");
        jdbc.update("update projects set status = 'ACTIVE', activated_at = ?, updated_at = ? where id = ?",
                NOW.atOffset(java.time.ZoneOffset.UTC), NOW.atOffset(java.time.ZoneOffset.UTC), projectId);
        entityManager.clear();

        assertThrows(ProjectRuleViolationException.class, () -> projects.complete(mentorId, projectId));
        jdbc.update("update tasks set status = 'DONE' where id = ?", taskId);
        entityManager.clear();
        projects.complete(mentorId, projectId);

        assertEquals(
                List.of(leaderId, memberId),
                notificationRecipients(
                        "MEMBERSHIP_CHANGED", "/projects/" + projectId, "PROJECT_COMPLETED"));
        assertEquals(
                List.of(leaderId),
                notificationRecipients(
                        "LEADERSHIP_CHANGED", "/projects/" + projectId, "LEADER_REMOVED"));
        assertEquals("COMPLETED", text("select status from projects where id = ?", projectId));
        assertEquals("REVOKED", text("select status from project_invitations where id = ?", invitationId));
        assertEquals("SUPERSEDED", text(
                "select status from project_membership_exit_requests where id = ?", requestId));
        assertThrows(ProjectAccessDeniedException.class, () -> projectPages.history(unrelatedId, projectId));
        assertEquals(2, projectPages.history(memberId, projectId).memberships().size());
        assertEquals(1, projectPages.history(mentorId, projectId).tasks().size());
        assertEquals(1, projectPages.history(leaderId, projectId).tasks().size());
    }

    private MutationState mutationState(long projectId) {
        return new MutationState(
                text("select status from projects where id = ?", projectId),
                rows("select id || ':' || intern_user_id || ':' || coalesce(left_at::text, '') "
                        + "from project_memberships where project_id = ? order by id", projectId),
                rows("select id || ':' || membership_id || ':' || coalesce(ended_at::text, '') "
                        + "from project_leadership_terms where project_id = ? order by id", projectId),
                rows("select id || ':' || status || ':' || coalesce(resolution_code, '') "
                        + "from project_invitations where project_id = ? order by id", projectId),
                rows("select id || ':' || status || ':' || target_membership_id "
                        + "from project_membership_exit_requests where project_id = ? order by id", projectId),
                rows("select id || ':' || assignee_membership_id || ':' || status "
                        + "from tasks where project_id = ? order by id", projectId),
                rows("select id || ':' || notification_type || ':' || action_url "
                        + "from notifications where action_url like ? order by id", "/projects/" + projectId + "%"));
    }

    private List<String> rows(String sql, Object... arguments) {
        return List.copyOf(jdbc.queryForList(sql, String.class, arguments));
    }

    private record AuthorizationCase(
            long projectId,
            String label,
            Class<? extends RuntimeException> expectedException,
            Executable operation) {
    }

    private record MutationState(
            String projectStatus,
            List<String> memberships,
            List<String> leadership,
            List<String> invitations,
            List<String> exits,
            List<String> tasks,
            List<String> notifications) {
    }

    /**
     * Protects the "at most one pending invitation per eligible Intern" half of {@code PRJ-018}.
     * Observable break: the guard is lost and a Leader can queue several pending invitations for
     * one Intern, so accepting one leaves the rest live, a later acceptance creates a second
     * membership, and {@code PRJ-017} loses the duplicate-membership property it depends on.
     *
     * <p>The rule is enforced twice, and the test exercises the first layer while the second stands
     * behind it. The service refuses the duplicate with a named rule violation, and the schema
     * carries the partial unique index {@code uq_project_invitations_one_pending} on
     * {@code (project_id, invited_intern_user_id) where status = 'PENDING'} for the concurrent case
     * the service check cannot see. A partial index is a PostgreSQL feature with no H2 equivalent,
     * which is one reason {@code ARC-003} keeps this suite on PostgreSQL.
     *
     * <p>The message is asserted rather than the exception type alone, because this service raises
     * the same type for many rules and a type-only assertion would pass on an unrelated refusal.
     * The surviving row count is asserted too, because an implementation that rejected the second
     * invitation after writing it would satisfy both of the other assertions.
     *
     * <p>The rule's other unasserted half, that an invitation never expires by time, is not covered
     * here. Asserting the absence of an expiry path needs a different instrument than a test that
     * issues one invitation.
     */
    @Test
    void aSecondPendingInvitationForTheSameInternIsRefused() {
        long mentorId = user("mentor-one-pending@example.test", "MENTOR");
        long leaderId = intern("leader-one-pending@example.test", "I181");
        long inviteeId = intern("invitee-one-pending@example.test", "I182");
        long projectId = createProject(mentorId, leaderId, "One pending invitation");

        long first = projects.issueInvitation(leaderId, projectId, inviteeId);
        assertEquals("PENDING", text("select status from project_invitations where id = ?", first));

        ProjectRuleViolationException refusal = assertThrows(
                ProjectRuleViolationException.class,
                () -> projects.issueInvitation(leaderId, projectId, inviteeId),
                "PRJ-018 permits at most one pending invitation per eligible Intern");
        assertTrue(
                refusal.getMessage().contains("A pending invitation already exists"),
                "the refusal must name the duplicate invitation, not an unrelated rule. Actual: "
                        + refusal.getMessage());

        assertEquals(
                1,
                count(
                        "select count(*) from project_invitations "
                                + "where project_id = ? and invited_intern_user_id = ? and status = 'PENDING'",
                        projectId,
                        inviteeId));
    }

    private long createProject(long mentorId, long leaderId, String name) {
        return projects.create(
                mentorId,
                new ProjectCreateCommand(
                        name,
                        null,
                        LocalDate.of(2026, 8, 15),
                        LocalDate.of(2026, 9, 30),
                        leaderId));
    }

    private long user(String email, String role) {
        return jdbc.queryForObject("""
                insert into app_users (
                    email, display_name, password_hash, global_role, account_status, activated_at)
                values (?, ?, '{noop}password-password', ?, 'ACTIVE', ?)
                returning id
                """, Long.class, email, email, role, NOW.atOffset(java.time.ZoneOffset.UTC));
    }

    private long intern(String email, String studentCode) {
        long userId = user(email, "INTERN");
        jdbc.update("""
                insert into intern_profiles (
                    user_id, student_code, internship_start_date, internship_end_date,
                    internship_status, activated_at)
                values (?, ?, date '2026-08-01', date '2026-12-31', 'ACTIVE', ?)
                """, userId, studentCode, NOW.atOffset(java.time.ZoneOffset.UTC));
        return userId;
    }

    private void completeIntern(long userId) {
        jdbc.update("""
                update intern_profiles
                set internship_status = 'COMPLETED', completed_at = ?, updated_at = ?
                where user_id = ?
                """, java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW), userId);
        entityManager.clear();
    }

    private void completeProject(long projectId, long mentorId) {
        var completedAt = NOW.plusSeconds(60).atOffset(java.time.ZoneOffset.UTC);
        jdbc.update("""
                update project_leadership_terms
                set ended_at = ?, ended_by_mentor_user_id = ?
                where project_id = ? and ended_at is null
                """, completedAt, mentorId, projectId);
        jdbc.update("""
                update project_memberships
                set left_at = ?, removed_by_mentor_user_id = ?, updated_at = ?
                where project_id = ? and left_at is null
                """, completedAt, mentorId, completedAt, projectId);
        jdbc.update("""
                update projects
                set status = 'COMPLETED', activated_at = ?, completed_at = ?, updated_at = ?
                where id = ?
                """, NOW.plusSeconds(30).atOffset(java.time.ZoneOffset.UTC),
                completedAt,
                completedAt,
                projectId);
        entityManager.clear();
    }

    private long membershipId(long projectId, long internUserId) {
        return number("""
                select id from project_memberships
                where project_id = ? and intern_user_id = ? and left_at is null
                """, projectId, internUserId);
    }

    private long insertTask(
            long projectId,
            long assigneeMembershipId,
            long actorMembershipId,
            String title,
            String status) {
        return jdbc.queryForObject("""
                insert into tasks (
                    project_id, assignee_membership_id, title, status,
                    created_by_membership_id, assigned_by_membership_id,
                    assigned_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                returning id
                """, Long.class,
                projectId,
                assigneeMembershipId,
                title,
                status,
                actorMembershipId,
                actorMembershipId,
                java.sql.Timestamp.from(NOW),
                java.sql.Timestamp.from(NOW),
                java.sql.Timestamp.from(NOW));
    }

    private int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }

    private long number(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Long.class, arguments);
    }

    private String text(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, String.class, arguments);
    }

    private List<Long> notificationRecipients(String type, String actionUrl) {
        return jdbc.query(
                "select recipient_user_id from notifications "
                        + "where notification_type = ? and action_url = ? order by recipient_user_id",
                (rs, rowNum) -> rs.getLong(1),
                type,
                actionUrl);
    }

    private List<Long> notificationRecipients(String type, String actionUrl, String transition) {
        return jdbc.query(
                "select recipient_user_id from notifications "
                        + "where notification_type = ? and action_url = ? and body like ? "
                        + "order by recipient_user_id",
                (rs, rowNum) -> rs.getLong(1),
                type,
                actionUrl,
                "%Transition: " + transition);
    }

    private List<String> notificationEmailStatuses(String type, String actionUrl) {
        return jdbc.query(
                "select email_status from notifications "
                        + "where notification_type = ? and action_url = ? order by id",
                (rs, rowNum) -> rs.getString(1),
                type,
                actionUrl);
    }

    private void activateSmtp(long adminId) {
        long draftId = smtp.saveDraft(adminId,
                new SmtpDraft("mailpit", 1025, SecurityMode.NONE, null, null,
                        "notification-admin@example.com", "Lab Timesheet"));
        smtp.testDraft(draftId, adminId, "notification-admin@example.com");
        smtp.activate(draftId, adminId);
    }

    /**
     * Protects {@code NOT-002}, {@code AC-NOT-004}, and {@code NOT-010}.
     * Real domain actions across the invitation lifecycle (create, decline, create second, accept, create third, revoke)
     * persist in-app notifications with UNAVAILABLE email status when SMTP is not configured.
     * When SMTP is activated subsequently, retryDueEmails does not retroactively send emails for these notifications.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void invitationWorkflowPersistsUnavailableNotificationsWithoutRetroactiveSend() {
        mail.reset();
        long adminId = user("admin-invite-smtp@example.test", "ADMIN");
        long mentorId = user("mentor-invite-smtp@example.test", "MENTOR");
        long leaderId = intern("leader-invite-smtp@example.test", "I201");
        long invitee1Id = intern("invitee1-invite-smtp@example.test", "I202");
        long invitee2Id = intern("invitee2-invite-smtp@example.test", "I203");
        long invitee3Id = intern("invitee3-invite-smtp@example.test", "I204");

        long projectId = createProject(mentorId, leaderId, "Invite Smtp Test");

        // 1. Leader issues invitation 1 -> CREATED (recipient: invitee1Id per NOT-010)
        long inv1 = projects.issueInvitation(leaderId, projectId, invitee1Id);
        String inv1Url = "/projects/" + projectId + "/invitations/" + inv1;
        assertEquals(List.of(invitee1Id), notificationRecipients("PROJECT_INVITATION_CREATED", inv1Url));
        assertEquals(List.of("UNAVAILABLE"), notificationEmailStatuses("PROJECT_INVITATION_CREATED", inv1Url));
        assertEquals(0, mail.calls);

        // 2. Invitee declines invitation 1 -> RESOLVED ("DECLINED", recipients: mentorId, leaderId per NOT-010)
        projects.respondToInvitation(invitee1Id, inv1, InvitationResponse.DECLINE);
        assertEquals("DECLINED", text("select status from project_invitations where id = ?", inv1));
        assertEquals(Stream.of(mentorId, leaderId).sorted().toList(),
                notificationRecipients("PROJECT_INVITATION_RESOLVED", inv1Url));
        assertEquals(List.of("UNAVAILABLE", "UNAVAILABLE"), notificationEmailStatuses("PROJECT_INVITATION_RESOLVED", inv1Url));
        assertEquals(0, mail.calls);

        // 3. Leader issues invitation 2 -> CREATED (recipient: invitee2Id per NOT-010)
        long inv2 = projects.issueInvitation(leaderId, projectId, invitee2Id);
        String inv2Url = "/projects/" + projectId + "/invitations/" + inv2;
        assertEquals(List.of(invitee2Id), notificationRecipients("PROJECT_INVITATION_CREATED", inv2Url));
        assertEquals(List.of("UNAVAILABLE"), notificationEmailStatuses("PROJECT_INVITATION_CREATED", inv2Url));
        assertEquals(0, mail.calls);

        // 4. Invitee accepts invitation 2 -> RESOLVED ("ACCEPTED", recipients: mentorId, leaderId per NOT-010)
        projects.respondToInvitation(invitee2Id, inv2, InvitationResponse.ACCEPT);
        assertEquals("ACCEPTED", text("select status from project_invitations where id = ?", inv2));
        assertEquals(1, count("select count(*) from project_memberships where project_id = ? and intern_user_id = ? and left_at is null", projectId, invitee2Id));
        assertEquals(Stream.of(mentorId, leaderId).sorted().toList(),
                notificationRecipients("PROJECT_INVITATION_RESOLVED", inv2Url));
        assertEquals(List.of("UNAVAILABLE", "UNAVAILABLE"), notificationEmailStatuses("PROJECT_INVITATION_RESOLVED", inv2Url));
        assertEquals(0, mail.calls);

        // 5. Leader issues invitation 3 -> CREATED
        long inv3 = projects.issueInvitation(leaderId, projectId, invitee3Id);
        String inv3Url = "/projects/" + projectId + "/invitations/" + inv3;
        assertEquals(List.of(invitee3Id), notificationRecipients("PROJECT_INVITATION_CREATED", inv3Url));
        assertEquals(List.of("UNAVAILABLE"), notificationEmailStatuses("PROJECT_INVITATION_CREATED", inv3Url));

        // 6. Mentor or Leader revokes invitation 3 -> RESOLVED ("REVOKED", recipients: mentorId, leaderId, invitee3Id per NOT-010)
        projects.revokeInvitation(mentorId, inv3);
        assertEquals("REVOKED", text("select status from project_invitations where id = ?", inv3));
        assertEquals(Stream.of(mentorId, leaderId, invitee3Id).sorted().toList(),
                notificationRecipients("PROJECT_INVITATION_RESOLVED", inv3Url));
        assertEquals(List.of("UNAVAILABLE", "UNAVAILABLE", "UNAVAILABLE"), notificationEmailStatuses("PROJECT_INVITATION_RESOLVED", inv3Url));
        assertEquals(0, mail.calls);

        // 7. F1: Leader issues invitation 4 -> CREATED, Mentor direct adds invitee4 -> SUPERSEDED
        long invitee4Id = intern("invitee4-invite-smtp@example.test", "I205");
        long inv4 = projects.issueInvitation(leaderId, projectId, invitee4Id);
        String inv4Url = "/projects/" + projectId + "/invitations/" + inv4;
        assertEquals(List.of(invitee4Id), notificationRecipients("PROJECT_INVITATION_CREATED", inv4Url));
        assertEquals(List.of("UNAVAILABLE"), notificationEmailStatuses("PROJECT_INVITATION_CREATED", inv4Url));

        projects.addMembers(mentorId, projectId, List.of(invitee4Id));
        assertEquals("SUPERSEDED", text("select status from project_invitations where id = ?", inv4));
        assertEquals(1, count("select count(*) from project_memberships where project_id = ? and intern_user_id = ? and left_at is null", projectId, invitee4Id));
        assertEquals(Stream.of(mentorId, leaderId, invitee4Id).sorted().toList(),
                notificationRecipients("PROJECT_INVITATION_RESOLVED", inv4Url));
        assertEquals(List.of("UNAVAILABLE", "UNAVAILABLE", "UNAVAILABLE"),
                notificationEmailStatuses("PROJECT_INVITATION_RESOLVED", inv4Url));
        assertEquals(0, mail.calls);

        // 8. F2: Verify no retroactive email replay after SMTP activation (AC-NOT-004, NOT-005)
        int countBeforeSmtp = count("select count(*) from notifications where action_url like ?", "/projects/" + projectId + "%");
        List<String> statusesBeforeSmtp = jdbc.queryForList(
                "select email_status from notifications where action_url like ? order by id", String.class, "/projects/" + projectId + "%");
        assertTrue(statusesBeforeSmtp.stream().allMatch("UNAVAILABLE"::equals));

        activateSmtp(adminId);
        mail.reset();
        assertEquals(0, notifications.retryDueEmails());
        assertEquals(0, mail.calls);

        int countAfterSmtp = count("select count(*) from notifications where action_url like ?", "/projects/" + projectId + "%");
        assertEquals(countBeforeSmtp, countAfterSmtp);
        List<String> statusesAfterSmtp = jdbc.queryForList(
                "select email_status from notifications where action_url like ? order by id", String.class, "/projects/" + projectId + "%");
        assertEquals(List.of("UNAVAILABLE"), statusesAfterSmtp.stream().distinct().toList());
    }

    /**
     * Protects {@code NOT-002}, {@code AC-NOT-004}, and {@code NOT-010}.
     * Real domain actions across the membership exit lifecycle (request own leave, cancel, request removal, reject, approve)
     * persist in-app notifications with UNAVAILABLE email status when SMTP is not configured.
     * When SMTP is activated subsequently, retryDueEmails does not retroactively send emails for these notifications.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void membershipExitWorkflowPersistsUnavailableNotificationsWithoutRetroactiveSend() {
        mail.reset();
        long adminId = user("admin-exit-smtp@example.test", "ADMIN");
        long mentorId = user("mentor-exit-smtp@example.test", "MENTOR");
        long leaderId = intern("leader-exit-smtp@example.test", "I211");
        long memberId = intern("member-exit-smtp@example.test", "I212");
        long replacementId = intern("replacement-exit-smtp@example.test", "I213");

        long projectId = createProject(mentorId, leaderId, "Exit Smtp Test");
        projects.addMembers(mentorId, projectId, List.of(memberId, replacementId));
        long memberMembershipId = membershipId(projectId, memberId);

        // 1. Member requests own leave -> MEMBERSHIP_EXIT_REQUESTED (recipients: mentorId, leaderId per NOT-010)
        long ownLeaveReqId = projects.requestOwnLeave(memberId, projectId, "Personal reasons");
        String ownLeaveUrl = "/projects/" + projectId + "/exits/" + ownLeaveReqId;
        assertEquals("PENDING", text("select status from project_membership_exit_requests where id = ?", ownLeaveReqId));
        assertEquals(Stream.of(mentorId, leaderId).sorted().toList(),
                notificationRecipients("MEMBERSHIP_EXIT_REQUESTED", ownLeaveUrl));
        assertEquals(List.of("UNAVAILABLE", "UNAVAILABLE"), notificationEmailStatuses("MEMBERSHIP_EXIT_REQUESTED", ownLeaveUrl));
        assertEquals(0, mail.calls);

        // 2. Member cancels own leave -> MEMBERSHIP_EXIT_RESOLVED (recipients: memberId, leaderId per NOT-010 - requester, target, currentLeader collapsed)
        projects.cancelExit(memberId, ownLeaveReqId);
        assertEquals("CANCELLED", text("select status from project_membership_exit_requests where id = ?", ownLeaveReqId));
        assertEquals(Stream.of(memberId, leaderId).sorted().toList(),
                notificationRecipients("MEMBERSHIP_EXIT_RESOLVED", ownLeaveUrl));
        assertEquals(List.of("UNAVAILABLE", "UNAVAILABLE"), notificationEmailStatuses("MEMBERSHIP_EXIT_RESOLVED", ownLeaveUrl));
        assertEquals(0, mail.calls);

        // 3. Leader requests member removal -> MEMBERSHIP_EXIT_REQUESTED (recipients: mentorId, memberId per NOT-010)
        long removalReq1Id = projects.requestMemberRemoval(leaderId, projectId, memberMembershipId, "Performance issue");
        String removal1Url = "/projects/" + projectId + "/exits/" + removalReq1Id;
        assertEquals("PENDING", text("select status from project_membership_exit_requests where id = ?", removalReq1Id));
        assertEquals(Stream.of(mentorId, memberId).sorted().toList(),
                notificationRecipients("MEMBERSHIP_EXIT_REQUESTED", removal1Url));
        assertEquals(List.of("UNAVAILABLE", "UNAVAILABLE"), notificationEmailStatuses("MEMBERSHIP_EXIT_REQUESTED", removal1Url));
        assertEquals(0, mail.calls);

        // 4. Mentor rejects exit request 1 -> MEMBERSHIP_EXIT_RESOLVED (recipients: leaderId, memberId)
        projects.rejectExit(mentorId, removalReq1Id, "Give another chance");
        assertEquals("REJECTED", text("select status from project_membership_exit_requests where id = ?", removalReq1Id));
        assertEquals(Stream.of(leaderId, memberId).sorted().toList(),
                notificationRecipients("MEMBERSHIP_EXIT_RESOLVED", removal1Url));
        assertEquals(List.of("UNAVAILABLE", "UNAVAILABLE"), notificationEmailStatuses("MEMBERSHIP_EXIT_RESOLVED", removal1Url));
        assertEquals(0, mail.calls);

        // 5. Leader requests member removal again -> MEMBERSHIP_EXIT_REQUESTED
        long removalReq2Id = projects.requestMemberRemoval(leaderId, projectId, memberMembershipId, "Handover completed");
        String removal2Url = "/projects/" + projectId + "/exits/" + removalReq2Id;
        assertEquals("PENDING", text("select status from project_membership_exit_requests where id = ?", removalReq2Id));
        assertEquals(Stream.of(mentorId, memberId).sorted().toList(),
                notificationRecipients("MEMBERSHIP_EXIT_REQUESTED", removal2Url));
        assertEquals(List.of("UNAVAILABLE", "UNAVAILABLE"), notificationEmailStatuses("MEMBERSHIP_EXIT_REQUESTED", removal2Url));
        assertEquals(0, mail.calls);

        // 6. Mentor approves exit request 2 -> MEMBERSHIP_EXIT_RESOLVED
        projects.approveExit(mentorId, removalReq2Id, "Approved exit");
        assertEquals("APPROVED", text("select status from project_membership_exit_requests where id = ?", removalReq2Id));
        assertEquals(1, count("select count(*) from project_memberships where id = ? and left_at is not null", memberMembershipId));
        assertEquals(Stream.of(leaderId, memberId).sorted().toList(),
                notificationRecipients("MEMBERSHIP_EXIT_RESOLVED", removal2Url));
        assertEquals(List.of("UNAVAILABLE", "UNAVAILABLE"), notificationEmailStatuses("MEMBERSHIP_EXIT_RESOLVED", removal2Url));
        assertEquals(0, mail.calls);

        // 7. F2: Verify no retroactive emails are replayed (AC-NOT-004, NOT-005)
        int countBeforeSmtp = count("select count(*) from notifications where action_url like ?", "/projects/" + projectId + "%");
        List<String> statusesBeforeSmtp = jdbc.queryForList(
                "select email_status from notifications where action_url like ? order by id", String.class, "/projects/" + projectId + "%");
        assertTrue(statusesBeforeSmtp.stream().allMatch("UNAVAILABLE"::equals));

        activateSmtp(adminId);
        mail.reset();
        assertEquals(0, notifications.retryDueEmails());
        assertEquals(0, mail.calls);

        int countAfterSmtp = count("select count(*) from notifications where action_url like ?", "/projects/" + projectId + "%");
        assertEquals(countBeforeSmtp, countAfterSmtp);
        List<String> statusesAfterSmtp = jdbc.queryForList(
                "select email_status from notifications where action_url like ? order by id", String.class, "/projects/" + projectId + "%");
        assertEquals(List.of("UNAVAILABLE"), statusesAfterSmtp.stream().distinct().toList());
    }

    /**
     * Protects {@code NOT-002} and {@code AC-NOT-004}.
     * Real domain actions for membership changes and leadership transitions persist in-app notifications
     * with UNAVAILABLE email status when SMTP is not configured.
     * When SMTP is activated subsequently, retryDueEmails does not retroactively send emails for these notifications.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void membershipAndLeadershipChangesPersistUnavailableNotificationsWithoutRetroactiveSend() {
        mail.reset();
        long adminId = user("admin-member-leader-smtp@example.test", "ADMIN");
        long mentorId = user("mentor-member-leader-smtp@example.test", "MENTOR");
        long initialLeaderId = intern("leader-member-leader-smtp@example.test", "I221");
        long newMemberId = intern("member-member-leader-smtp@example.test", "I222");
        long replacementLeaderId = intern("replacement-member-leader-smtp@example.test", "I223");

        long projectId = createProject(mentorId, initialLeaderId, "Membership and Leadership Test");
        String projectUrl = "/projects/" + projectId;

        // 1. Mentor adds new member -> MEMBERSHIP_CHANGED
        projects.addMember(mentorId, projectId, newMemberId);
        assertEquals(1, count("select count(*) from project_memberships where project_id = ? and intern_user_id = ? and left_at is null", projectId, newMemberId));
        var memberAddedRecipients = notificationRecipients("MEMBERSHIP_CHANGED", projectUrl, "MEMBER_ADDED");
        assertEquals(List.of(newMemberId), memberAddedRecipients);
        var memberAddedStatuses = jdbc.query(
                "select email_status from notifications where notification_type = 'MEMBERSHIP_CHANGED' "
                        + "and action_url = ? and body like ? order by id",
                (rs, rowNum) -> rs.getString(1),
                projectUrl, "%Transition: MEMBER_ADDED");
        assertEquals(List.of("UNAVAILABLE"), memberAddedStatuses);
        assertEquals(0, mail.calls);

        // 2. Mentor adds replacement member then changes leader -> LEADERSHIP_CHANGED
        projects.addMember(mentorId, projectId, replacementLeaderId);
        long formerLeaderMembershipId = membershipId(projectId, initialLeaderId);
        projects.changeLeader(mentorId, projectId, replacementLeaderId);
        long newLeaderMembershipId = membershipId(projectId, replacementLeaderId);
        assertEquals(1, count("select count(*) from project_leadership_terms where project_id = ? and membership_id = ? and ended_at is not null", projectId, formerLeaderMembershipId));
        assertEquals(1, count("select count(*) from project_leadership_terms where project_id = ? and membership_id = ? and ended_at is null", projectId, newLeaderMembershipId));
        var leaderChangedRecipients = notificationRecipients("LEADERSHIP_CHANGED", projectUrl, "LEADER_CHANGED");
        assertEquals(Stream.of(initialLeaderId, replacementLeaderId).sorted().toList(), leaderChangedRecipients);
        var leaderChangedStatuses = jdbc.query(
                "select email_status from notifications where notification_type = 'LEADERSHIP_CHANGED' "
                        + "and action_url = ? and body like ? order by id",
                (rs, rowNum) -> rs.getString(1),
                projectUrl, "%Transition: LEADER_CHANGED");
        assertEquals(List.of("UNAVAILABLE", "UNAVAILABLE"), leaderChangedStatuses);
        assertEquals(0, mail.calls);

        // 3. F2: Verify no retroactive emails are replayed (AC-NOT-004, NOT-005)
        int countBeforeSmtp = count("select count(*) from notifications where action_url like ?", "/projects/" + projectId + "%");
        List<String> statusesBeforeSmtp = jdbc.queryForList(
                "select email_status from notifications where action_url like ? order by id", String.class, "/projects/" + projectId + "%");
        assertTrue(statusesBeforeSmtp.stream().allMatch("UNAVAILABLE"::equals));

        activateSmtp(adminId);
        mail.reset();
        assertEquals(0, notifications.retryDueEmails());
        assertEquals(0, mail.calls);

        int countAfterSmtp = count("select count(*) from notifications where action_url like ?", "/projects/" + projectId + "%");
        assertEquals(countBeforeSmtp, countAfterSmtp);
        List<String> statusesAfterSmtp = jdbc.queryForList(
                "select email_status from notifications where action_url like ? order by id", String.class, "/projects/" + projectId + "%");
        assertEquals(List.of("UNAVAILABLE"), statusesAfterSmtp.stream().distinct().toList());
    }

    /**
     * Protects {@code AC-PRJ-017}, {@code NOT-013}, {@code NOT-002}, {@code D50}.
     * Verifies exact recipient sets for membership and leadership changes across
     * project lifecycle: project creation, member addition, leader replacement,
     * invitation acceptance, exit approval, and project completion.
     * The initiating actor (e.g. accepting Intern) and the owning Mentor are excluded.
     */
    @Test
    void membershipAndLeadershipNotificationsExcludeInitiatorAcrossProjectLifecycle() {
        long mentorId = user("mentor-ac17@example.test", "MENTOR");
        long initialLeaderId = intern("initial-leader-ac17@example.test", "I201");
        long addedMemberId = intern("added-member-ac17@example.test", "I202");
        long invitedInternId = intern("invited-intern-ac17@example.test", "I203");
        long projectId = createProject(mentorId, initialLeaderId, "AC-PRJ-017 Project");

        // 1. Project creation with initial leader
        assertEquals(
                List.of(initialLeaderId),
                notificationRecipients("MEMBERSHIP_CHANGED", "/projects/" + projectId, "INITIAL_MEMBER_ADDED"));
        assertEquals(
                List.of(initialLeaderId),
                notificationRecipients("LEADERSHIP_CHANGED", "/projects/" + projectId, "INITIAL_LEADER_ASSIGNED"));

        // 2. Mentor adds a member
        projects.addMember(mentorId, projectId, addedMemberId);
        assertEquals(
                List.of(addedMemberId),
                notificationRecipients("MEMBERSHIP_CHANGED", "/projects/" + projectId, "MEMBER_ADDED"));

        // 3. Mentor replaces the leader
        projects.changeLeader(mentorId, projectId, addedMemberId);
        assertEquals(
                Stream.of(initialLeaderId, addedMemberId).sorted().toList(),
                notificationRecipients("LEADERSHIP_CHANGED", "/projects/" + projectId, "LEADER_CHANGED"));

        // 4. An Intern accepts an invitation
        long invitationId = projects.issueInvitation(addedMemberId, projectId, invitedInternId);
        projects.respondToInvitation(invitedInternId, invitationId, InvitationResponse.ACCEPT);
        // Under D50 / NOT-013 / AC-PRJ-017, the accepting intern does NOT receive MEMBERSHIP_CHANGED for their own acceptance
        assertEquals(
                List.of(),
                notificationRecipients("MEMBERSHIP_CHANGED", "/projects/" + projectId, "INVITATION_ACCEPTED"));

        // 5. Mentor approves removal of a member (initialLeaderId)
        long initialLeaderMembershipId = membershipId(projectId, initialLeaderId);
        long removalRequestId = projects.requestMemberRemoval(
                addedMemberId, projectId, initialLeaderMembershipId, "Handover completed");
        projects.approveExit(mentorId, removalRequestId, "Removal approved");
        assertEquals(
                List.of(initialLeaderId),
                notificationRecipients("MEMBERSHIP_CHANGED", "/projects/" + projectId, "MEMBER_REMOVED"));

        // 6. Mentor completes the project
        jdbc.update("update projects set status = 'ACTIVE', activated_at = timestamp with time zone '2026-08-13 00:00:00+00', updated_at = timestamp with time zone '2026-08-13 00:00:00+00' where id = ?", projectId);
        jdbc.update("update project_leadership_terms set started_at = timestamp with time zone '2026-08-12 00:00:00+00', ended_at = timestamp with time zone '2026-08-13 00:00:00+00' where project_id = ? and ended_at is not null", projectId);
        jdbc.update("update project_leadership_terms set started_at = timestamp with time zone '2026-08-13 00:00:00+00' where project_id = ? and ended_at is null", projectId);
        entityManager.clear();
        projects.complete(mentorId, projectId);
        assertEquals(
                Stream.of(addedMemberId, invitedInternId).sorted().toList(),
                notificationRecipients("MEMBERSHIP_CHANGED", "/projects/" + projectId, "PROJECT_COMPLETED"));
        assertEquals(
                List.of(addedMemberId),
                notificationRecipients("LEADERSHIP_CHANGED", "/projects/" + projectId, "LEADER_REMOVED"));

        // 7. Verify the acting Mentor never receives MEMBERSHIP_CHANGED or LEADERSHIP_CHANGED
        var allMembershipRecipients = notificationRecipients("MEMBERSHIP_CHANGED", "/projects/" + projectId);
        assertTrue(!allMembershipRecipients.contains(mentorId));
        var allLeadershipRecipients = notificationRecipients("LEADERSHIP_CHANGED", "/projects/" + projectId);
        assertTrue(!allLeadershipRecipients.contains(mentorId));
    }

    /**
     * Protects {@code AC-PRJ-017}, {@code NOT-013}, {@code NOT-002}, {@code D50}.
     * Verifies that cancelling a project notifies all current members with
     * MEMBERSHIP_CHANGED (PROJECT_CANCELLED) and the current leader with
     * LEADERSHIP_CHANGED (LEADER_REMOVED), while excluding the acting Mentor.
     */
    @Test
    void projectCancellationNotifiesCurrentMembersAndLeaderWhileExcludingMentor() {
        long mentorId = user("mentor-cancel@example.test", "MENTOR");
        long leaderId = intern("leader-cancel@example.test", "I204");
        long memberId = intern("member-cancel@example.test", "I205");
        long projectId = createProject(mentorId, leaderId, "Project Cancellation");
        projects.addMember(mentorId, projectId, memberId);

        projects.cancel(mentorId, projectId, "Cancelling project for testing");

        assertEquals(
                Stream.of(leaderId, memberId).sorted().toList(),
                notificationRecipients("MEMBERSHIP_CHANGED", "/projects/" + projectId, "PROJECT_CANCELLED"));
        assertEquals(
                List.of(leaderId),
                notificationRecipients("LEADERSHIP_CHANGED", "/projects/" + projectId, "LEADER_REMOVED"));

        var allMembershipRecipients = notificationRecipients("MEMBERSHIP_CHANGED", "/projects/" + projectId);
        assertTrue(!allMembershipRecipients.contains(mentorId));
        var allLeadershipRecipients = notificationRecipients("LEADERSHIP_CHANGED", "/projects/" + projectId);
        assertTrue(!allLeadershipRecipients.contains(mentorId));
    }

    /**
     * Protects {@code NOT-006}, {@code NOT-012}, {@code AC-NOT-008}, and {@code D49}.
     * Verifies that when SMTP is active and holds message delivery, completing a project
     * (which raises multiple emails) commits its domain action and in-app notifications and
     * returns immediately while messages are still held in PENDING state.
     * Once released, emails reach SENT, and the initial attempt begins within one minute of commit.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void acNot008ProjectCompletionReturnsBeforeSmtpDeliveryAndDispatchesAsynchronously() throws Exception {
        mail.reset();
        bootstrap.bootstrap("admin-ac008@example.test", "Admin User", "correct horse battery staple");
        long adminId = jdbc.queryForObject("select id from app_users where email = ?", Long.class, "admin-ac008@example.test");
        String mentorEmail = "mentor-ac008@example.test";
        long mentorId = user(mentorEmail, "MENTOR");
        long leaderId = intern("leader-ac008@example.test", "I230");
        long memberId = intern("member-ac008@example.test", "I231");

        long projectId = createProject(mentorId, leaderId, "Asynchronous Delivery Project");
        projects.addMember(mentorId, projectId, memberId);

        activateSmtp(adminId);
        mail.reset();

        jdbc.update("update projects set status = 'ACTIVE', activated_at = timestamp with time zone '2026-08-13 00:00:00+00', updated_at = timestamp with time zone '2026-08-13 00:00:00+00' where id = ?", projectId);
        jdbc.update("update project_leadership_terms set started_at = timestamp with time zone '2026-08-12 00:00:00+00', ended_at = timestamp with time zone '2026-08-13 00:00:00+00' where project_id = ? and ended_at is not null", projectId);
        jdbc.update("update project_leadership_terms set started_at = timestamp with time zone '2026-08-13 00:00:00+00' where project_id = ? and ended_at is null", projectId);

        Long minCompletionId = jdbc.queryForObject("select coalesce(max(id), 0) from notifications", Long.class);
        mail.blockForOverlap();
        // Wall-clock instant captured immediately before the HTTP call; used to verify that
        // SMTP dispatch begins within 60 s of the commit, regardless of the fixed test clock
        // (TestcontainersConfiguration supplies Clock.fixed, so created_at is always the
        // anchored test instant, not a live timestamp — cannot compare the two directly).
        Instant wallBeforeDispatch = Instant.now();

        try {
            MvcResult mvcResult = mockMvc.perform(
                    post("/projects/" + projectId + "/complete")
                            .with(csrf())
                            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(mentorEmail).roles("MENTOR")))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();

            // When latch is held: response returned (status 3xx), all completion emails are PENDING
            int statusCode = mvcResult.getResponse().getStatus();
            assertTrue(statusCode >= 300 && statusCode < 400);

            // Project status and in-app notifications committed
            assertEquals("COMPLETED", text("select status from projects where id = ?", projectId));
            List<String> inAppTypes = jdbc.queryForList(
                    "select notification_type from notifications where id > ? and action_url like ? order by id",
                    String.class, minCompletionId, "/projects/" + projectId + "%");
            assertTrue(inAppTypes.contains("MEMBERSHIP_CHANGED"));
            assertTrue(inAppTypes.contains("LEADERSHIP_CHANGED"));

            // While held, all email statuses are PENDING
            List<String> heldStatuses = jdbc.queryForList(
                    "select email_status from notifications where id > ? and action_url like ? and email_status is not null order by id",
                    String.class, minCompletionId, "/projects/" + projectId + "%");
            assertFalse(heldStatuses.isEmpty());
            assertTrue(heldStatuses.stream().allMatch("PENDING"::equals));

            // Release SMTP delivery
            mail.releaseOverlap();

            // Await delivery to reach SENT
            long deadline = System.currentTimeMillis() + 5000;
            List<String> finalStatuses;
            do {
                Thread.sleep(50);
                finalStatuses = jdbc.queryForList(
                        "select email_status from notifications where id > ? and action_url like ? and email_status is not null order by id",
                        String.class, minCompletionId, "/projects/" + projectId + "%");
            } while (System.currentTimeMillis() < deadline && !finalStatuses.stream().allMatch("SENT"::equals));

            assertTrue(finalStatuses.stream().allMatch("SENT"::equals));
            assertTrue(mail.calls >= 2);

            // For each email, the fake sender's first attempt must begin within 60 s of the
            // HTTP call that triggered the commit (wallBeforeDispatch).  We compare against wall
            // time rather than DB created_at because the test clock is fixed and does not advance
            // with real time, so the two clocks cannot be subtracted meaningfully.
            List<String> sentRecipients = jdbc.queryForList(
                    "select email_to from notifications where id > ? and action_url like ? and email_status = 'SENT'",
                    String.class, minCompletionId, "/projects/" + projectId + "%");
            assertFalse(sentRecipients.isEmpty());
            for (String emailTo : sentRecipients) {
                Instant deliveryStartedAt = mail.sendStartTimes.get(emailTo);
                assertNotNull(deliveryStartedAt, "fake sender must record delivery start time for " + emailTo);
                long secondsAfterDispatch = deliveryStartedAt.getEpochSecond() - wallBeforeDispatch.getEpochSecond();
                assertTrue(secondsAfterDispatch >= 0 && secondsAfterDispatch <= 60,
                        "delivery attempt must begin within 60s of commit dispatch; wallBefore="
                                + wallBeforeDispatch.getEpochSecond() + ", delivered="
                                + deliveryStartedAt.getEpochSecond() + ", diff=" + secondsAfterDispatch);
            }
        } finally {
            mail.releaseOverlap();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MailProbeConfiguration {
        @Bean
        @Primary
        RecordingSmtpProbe recordingSmtpProbe() {
            return new RecordingSmtpProbe();
        }
    }

    static final class RecordingSmtpProbe implements SmtpProbe {
        private volatile int calls;
        private volatile boolean blockForOverlap;
        private volatile CountDownLatch entered = new CountDownLatch(0);
        private volatile CountDownLatch release = new CountDownLatch(0);
        private final java.util.concurrent.CopyOnWriteArrayList<String> recipients =
                new java.util.concurrent.CopyOnWriteArrayList<>();
        final java.util.concurrent.ConcurrentHashMap<String, Instant> sendStartTimes =
                new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public void send(SmtpConnection connection, String recipient, String subject, String body) {
            sendStartTimes.putIfAbsent(recipient, Instant.now());
            calls++;
            recipients.add(recipient);
            if (blockForOverlap) {
                entered.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("timed out waiting to release SMTP overlap");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("SMTP overlap interrupted", interrupted);
                }
            }
        }

        void reset() {
            calls = 0;
            recipients.clear();
            sendStartTimes.clear();
            blockForOverlap = false;
            entered = new CountDownLatch(0);
            release = new CountDownLatch(0);
        }

        void blockForOverlap() {
            blockForOverlap = true;
            entered = new CountDownLatch(1);
            release = new CountDownLatch(1);
        }

        boolean awaitEntered() throws InterruptedException {
            return entered.await(10, TimeUnit.SECONDS);
        }

        void releaseOverlap() {
            release.countDown();
        }
    }
}
