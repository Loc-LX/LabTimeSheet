package com.lab.labtimesheet.feature.project.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.exception.ProjectRuleViolationException;
import com.lab.labtimesheet.feature.project.model.InvitationResponse;
import com.lab.labtimesheet.feature.project.model.dto.ProjectCreateCommand;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProjectInvitationExitIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");

    @Autowired
    private ProjectService projects;

    @Autowired
    private ProjectQueryService projectPages;

    @Autowired
    private JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void leaderInvitationAcceptsOnlyTheIntendedInternAndMentorDirectAddSupersedesIt() {
        long mentorId = user("mentor-invite@example.test", "MENTOR");
        long leaderId = intern("leader-invite@example.test", "I101");
        long inviteeId = intern("invitee-invite@example.test", "I102");
        long unrelatedId = intern("unrelated-invite@example.test", "I103");
        long projectId = createProject(mentorId, leaderId, "Invitation");

        long invitationId = projects.issueInvitation(leaderId, projectId, inviteeId);

        assertEquals("PENDING", text("select status from project_invitations where id = ?", invitationId));
        assertEquals(membershipId(projectId, leaderId), number("""
                select term.membership_id
                from project_invitations invitation
                join project_leadership_terms term on term.id = invitation.issuing_leadership_term_id
                where invitation.id = ?
                """, invitationId));
        assertThrows(ProjectAccessDeniedException.class,
                () -> projects.respondToInvitation(unrelatedId, invitationId, InvitationResponse.ACCEPT));

        projects.addMember(mentorId, projectId, inviteeId);

        assertEquals("SUPERSEDED", text("select status from project_invitations where id = ?", invitationId));
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

    private int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }

    private long number(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Long.class, arguments);
    }

    private String text(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, String.class, arguments);
    }
}
