package com.lab.labtimesheet.feature.project.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.internship.service.InternshipService;
import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.exception.ProjectRuleViolationException;
import com.lab.labtimesheet.feature.project.exception.TaskNotFoundException;
import com.lab.labtimesheet.feature.project.model.dto.ProjectCreateCommand;
import com.lab.labtimesheet.feature.project.model.dto.CreateTaskCommand;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
class ProjectServiceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-14T00:00:00Z");

    @Autowired
    private ProjectService projectService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProjectQueryService projectPages;

    @Autowired
    private InternshipService internships;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void createsProjectMembershipAndLeadershipInOneTransaction() {
        long mentorId = user("mentor-create@example.test", "MENTOR");
        long leaderId = intern("leader-create@example.test", "I001");

        long projectId = projectService.create(
                mentorId,
                new ProjectCreateCommand(
                        "Intern Portal Refresh",
                        "Refresh the portal",
                        LocalDate.of(2026, 8, 15),
                        LocalDate.of(2026, 9, 30),
                        leaderId));

        assertEquals("PLANNED", text("select status from projects where id = ?", projectId));
        assertEquals(1, count("select count(*) from project_memberships where project_id = ? and left_at is null", projectId));
        assertEquals(1, count("select count(*) from project_leadership_terms where project_id = ? and ended_at is null", projectId));
        assertEquals(leaderId, number("""
                select membership.intern_user_id
                from project_leadership_terms leadership
                join project_memberships membership on membership.id = leadership.membership_id
                where leadership.project_id = ? and leadership.ended_at is null
                """, projectId));

        long nonMentorId = intern("not-mentor@example.test", "I002");
        assertThrows(ProjectAccessDeniedException.class, () -> projectService.create(
                nonMentorId,
                new ProjectCreateCommand(
                        "Denied",
                        null,
                        LocalDate.of(2026, 8, 15),
                        LocalDate.of(2026, 8, 31),
                        leaderId)));
        assertEquals(0, count("select count(*) from projects where name = 'Denied'"));
    }

    @Test
    void ownerAddsEligibleMemberAndDuplicateCurrentMembershipIsRejected() {
        long mentorId = user("mentor-add@example.test", "MENTOR");
        long leaderId = intern("leader-add@example.test", "I003");
        long memberId = intern("member-add@example.test", "I004");
        long projectId = createProject(mentorId, leaderId, "Membership");
        long otherProjectId = createProject(mentorId, memberId, "Concurrent membership");

        projectService.addMember(mentorId, projectId, memberId);

        assertEquals(1, count("""
                select count(*) from project_memberships
                where project_id = ? and intern_user_id = ? and left_at is null
                """, projectId, memberId));
        assertEquals(1, count("""
                select count(*) from project_memberships
                where project_id = ? and intern_user_id = ? and left_at is null
                """, otherProjectId, memberId));
        assertThrows(ProjectRuleViolationException.class,
                () -> projectService.addMember(mentorId, projectId, memberId));
        assertThrows(ProjectAccessDeniedException.class,
                () -> projectService.addMember(
                        user("other-mentor@example.test", "MENTOR"), projectId, Long.MAX_VALUE));
    }

    @Test
    void leaderChangeClosesOneTermAndDoesNotMoveTaskAssignments() {
        long mentorId = user("mentor-leader@example.test", "MENTOR");
        long firstLeaderId = intern("leader-one@example.test", "I005");
        long nextLeaderId = intern("leader-two@example.test", "I006");
        long projectId = createProject(mentorId, firstLeaderId, "Leadership");
        projectService.addMember(mentorId, projectId, nextLeaderId);
        long firstMembershipId = membershipId(projectId, firstLeaderId);
        jdbc.update("""
                insert into tasks (
                    project_id, assignee_membership_id, title,
                    created_by_membership_id, assigned_by_membership_id)
                values (?, ?, 'Keep assignee', ?, ?)
                """, projectId, firstMembershipId, firstMembershipId, firstMembershipId);

        projectService.changeLeader(mentorId, projectId, nextLeaderId);

        assertEquals(1, count("select count(*) from project_leadership_terms where project_id = ? and ended_at is null", projectId));
        assertEquals(1, count("select count(*) from project_leadership_terms where project_id = ? and ended_at is not null", projectId));
        assertEquals(firstMembershipId, number("select assignee_membership_id from tasks where project_id = ?", projectId));
    }

    @Test
    void dailyReportProjectQueryTracksCurrentLeaderThroughReplacementAndCompletion() {
        long mentorId = user("mentor-daily-query@example.test", "MENTOR");
        long formerLeaderId = intern("former-daily-query@example.test", "I007");
        long replacementLeaderId = intern("replacement-daily-query@example.test", "I008");
        long ordinaryInternId = intern("ordinary-daily-query@example.test", "I015");
        long otherProjectLeaderId = intern("other-project-daily-query@example.test", "I016");
        long projectId = createProject(mentorId, formerLeaderId, "Daily query leadership");
        createProject(mentorId, otherProjectLeaderId, "Other Daily query leadership");

        assertEquals(projectId,
                projectPages.currentLeaderProjectForDailyReport(formerLeaderId, projectId).id());
        assertTrue(projectPages.hasCurrentLeaderProjectForDailyReport(formerLeaderId));
        assertEquals(List.of(projectId), projectPages.listCurrentLeaderProjectsForDailyReport(formerLeaderId)
                .stream().map(summary -> summary.id()).toList());
        assertFalse(projectPages.hasCurrentLeaderProjectForDailyReport(ordinaryInternId));
        assertEquals(List.of(), projectPages.listCurrentLeaderProjectsForDailyReport(ordinaryInternId));
        assertThrows(ProjectAccessDeniedException.class,
                () -> projectPages.currentLeaderProjectForDailyReport(ordinaryInternId, projectId));
        assertThrows(ProjectAccessDeniedException.class,
                () -> projectPages.currentLeaderProjectForDailyReport(otherProjectLeaderId, projectId));
        assertThrows(ProjectAccessDeniedException.class,
                () -> projectPages.currentLeaderProjectForDailyReport(formerLeaderId, Long.MAX_VALUE));

        projectService.addMember(mentorId, projectId, replacementLeaderId);
        projectService.changeLeader(mentorId, projectId, replacementLeaderId);
        entityManager.clear();

        assertThrows(ProjectAccessDeniedException.class,
                () -> projectPages.currentLeaderProjectForDailyReport(formerLeaderId, projectId));
        assertEquals(projectId,
                projectPages.currentLeaderProjectForDailyReport(replacementLeaderId, projectId).id());
        assertFalse(projectPages.hasCurrentLeaderProjectForDailyReport(formerLeaderId));
        assertTrue(projectPages.hasCurrentLeaderProjectForDailyReport(replacementLeaderId));
        assertEquals(List.of(projectId), projectPages.listCurrentLeaderProjectsForDailyReport(replacementLeaderId)
                .stream().map(summary -> summary.id()).toList());

        var completedAt = dbTime(NOW.plusSeconds(60));
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
                """, dbTime(NOW.plusSeconds(30)), completedAt, completedAt, projectId);
        entityManager.clear();

        assertThrows(ProjectAccessDeniedException.class,
                () -> projectPages.currentLeaderProjectForDailyReport(replacementLeaderId, projectId));
        assertFalse(projectPages.hasCurrentLeaderProjectForDailyReport(replacementLeaderId));
        assertEquals(List.of(), projectPages.listCurrentLeaderProjectsForDailyReport(replacementLeaderId));
    }

    @Test
    void dailyReportProjectQueryDeniesOpenProjectWithoutCurrentLeadershipTerm() {
        long mentorId = user("mentor-daily-leaderless@example.test", "MENTOR");
        long leaderId = intern("leader-daily-leaderless@example.test", "I017");
        long projectId = createProject(mentorId, leaderId, "Leaderless Daily query");

        jdbc.update("""
                update project_leadership_terms
                set ended_at = ?, ended_by_mentor_user_id = ?
                where project_id = ? and ended_at is null
                """, dbTime(NOW.plusSeconds(30)), mentorId, projectId);
        entityManager.clear();

        assertThrows(ProjectAccessDeniedException.class,
                () -> projectPages.currentLeaderProjectForDailyReport(leaderId, projectId));
    }

    @Test
    void listAndDetailQueriesEnforceRoleOwnershipAndMembershipWithoutIdDisclosure() {
        long adminId = user("admin-view@example.test", "ADMIN");
        long mentorId = user("mentor-view@example.test", "MENTOR");
        long otherMentorId = user("other-mentor-view@example.test", "MENTOR");
        long leaderId = intern("leader-view@example.test", "I009");
        long memberId = intern("member-view@example.test", "I010");
        long unrelatedId = intern("unrelated-view@example.test", "I011");
        long projectId = createProject(mentorId, leaderId, "Visible project");
        projectService.addMember(mentorId, projectId, memberId);

        assertEquals(List.of(projectId), projectPages.listVisible(adminId).stream().map(summary -> summary.id()).toList());
        assertEquals(List.of(projectId), projectPages.listVisible(mentorId).stream().map(summary -> summary.id()).toList());
        assertEquals(List.of(), projectPages.listVisible(otherMentorId));
        assertEquals(List.of(projectId), projectPages.listVisible(memberId).stream().map(summary -> summary.id()).toList());
        assertEquals(List.of(), projectPages.listVisible(unrelatedId));
        assertEquals(projectId, projectPages.detail(memberId, projectId).id());
        assertTrue(projectPages.detail(leaderId, projectId).viewerIsCurrentLeader());
        assertFalse(projectPages.detail(memberId, projectId).viewerIsCurrentLeader());
        assertEquals("INTERN", projectPages.authenticatedActor("member-view@example.test").role());
        var taskContext = projectService.taskMutationContext(memberId, projectId);
        assertEquals(mentorId, taskContext.mentorUserId());
        assertEquals("PLANNED", taskContext.status());
        assertEquals(2, taskContext.activeMembers().size());
        assertEquals(membershipId(projectId, leaderId), taskContext.currentLeaderMembershipId());
        assertEquals(taskContext, projectPages.taskContext(memberId, projectId));
        assertThrows(ProjectAccessDeniedException.class,
                () -> projectService.taskMutationContext(otherMentorId, projectId));
        jdbc.update("""
                update projects set status = 'ACTIVE', activated_at = ?, updated_at = ? where id = ?
                """, dbTime(NOW.plusSeconds(30)), dbTime(NOW.plusSeconds(30)), projectId);
        entityManager.clear();
        assertEquals(1, projectPages.dashboardSummary(adminId).activeProjectCount());
        assertEquals(1, projectPages.dashboardSummary(mentorId).activeProjectCount());
        assertEquals(2, projectPages.dashboardSummary(mentorId).distinctActiveMemberCount());
        assertEquals(1, projectPages.dashboardSummary(memberId).activeProjectCount());
        assertThrows(ProjectAccessDeniedException.class, () -> projectPages.detail(otherMentorId, projectId));
        assertThrows(ProjectAccessDeniedException.class, () -> projectPages.detail(unrelatedId, projectId));
        assertThrows(ProjectAccessDeniedException.class, () -> projectPages.detail(unrelatedId, Long.MAX_VALUE));

        jdbc.update("""
                update project_memberships
                set left_at = ?, removed_by_mentor_user_id = ?
                where project_id = ? and intern_user_id = ?
                """, dbTime(NOW.plusSeconds(60)), mentorId, projectId, memberId);
        entityManager.clear();

        assertEquals(List.of(), projectPages.listVisible(memberId));
        assertThrows(ProjectAccessDeniedException.class, () -> projectPages.detail(memberId, projectId));
        assertThrows(ProjectAccessDeniedException.class,
                () -> projectService.taskMutationContext(memberId, projectId));
        assertEquals(0, projectPages.dashboardSummary(memberId).activeProjectCount());
        assertEquals(1, projectPages.dashboardSummary(mentorId).distinctActiveMemberCount());
    }

    @Test
    void completedProjectQueriesReturnHistoricalMembersWithoutRequiringACurrentLeader() {
        long adminId = user("admin-history@example.test", "ADMIN");
        long mentorId = user("mentor-history@example.test", "MENTOR");
        long leaderId = intern("leader-history@example.test", "I012");
        long memberId = intern("member-history@example.test", "I013");
        long projectId = createProject(mentorId, leaderId, "Completed history");
        projectService.addMember(mentorId, projectId, memberId);
        var activatedAt = dbTime(NOW.plusSeconds(30));
        var completedAt = dbTime(NOW.plusSeconds(60));
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
                """, activatedAt, completedAt, completedAt, projectId);
        entityManager.clear();

        assertEquals(List.of(projectId), projectPages.listVisible(memberId).stream()
                .map(summary -> summary.id())
                .toList());
        var ownerDetail = projectPages.detail(mentorId, projectId);
        var adminDetail = projectPages.detail(adminId, projectId);
        var formerMemberDetail = projectPages.detail(memberId, projectId);
        assertNull(ownerDetail.leaderName());
        assertNull(adminDetail.leaderName());
        assertNull(formerMemberDetail.leaderName());
        assertFalse(ownerDetail.canManage());
        assertFalse(adminDetail.canManage());
        assertFalse(formerMemberDetail.canManage());
        assertFalse(ownerDetail.viewerIsCurrentLeader());
        assertFalse(adminDetail.viewerIsCurrentLeader());
        assertFalse(formerMemberDetail.viewerIsCurrentLeader());
        var taskContext = projectPages.taskContext(memberId, projectId);
        assertEquals("COMPLETED", taskContext.status());
        assertNull(taskContext.currentLeaderMembershipId());
        assertEquals(List.of(), taskContext.activeMembers());
        var members = projectPages.members(memberId, projectId);
        assertEquals(2, members.size());
        assertTrue(members.stream().allMatch(member -> member.leftAt() != null));
        assertTrue(members.stream().noneMatch(member -> member.currentLeader()));
    }

    @Test
    void ownerActivatesAPlannedProjectWhenCurrentMemberAndTaskAssigneeGuardsPass() {
        long mentorId = user("mentor-activate@example.test", "MENTOR");
        long leaderId = intern("leader-activate@example.test", "I014");
        long projectId = createProject(mentorId, leaderId, "Ready to activate");

        projectService.activate(mentorId, projectId);

        assertEquals("ACTIVE", text("select status from projects where id = ?", projectId));
        assertEquals(1, count("select count(*) from projects where id = ? and activated_at is not null", projectId));
    }

    /**
     * Protects {@code PRJ-023}, {@code AC-PRJ-015}, and {@code NOT-002}. Observable break:
     * cancelling either source state loses retained work or leaves a pending workflow open.
     * Expected: both Projects retain task history, close intervals at the recorded server time,
     * resolve workflows, notify members, and refuse subsequent Project and Task mutations.
     */
    @Test
    void cancellationRetainsHistoryResolvesWorkflowsAndMakesBothSourceStatesReadOnly() {
        for (boolean activate : List.of(false, true)) {
            String suffix = activate ? "active" : "planned";
            long mentorId = user("mentor-cancel-" + suffix + "@example.test", "MENTOR");
            long leaderId = intern("leader-cancel-" + suffix + "@example.test", activate ? "I090" : "I091");
            long memberId = intern("member-cancel-" + suffix + "@example.test", activate ? "I092" : "I093");
            long inviteeId = intern("invitee-cancel-" + suffix + "@example.test", activate ? "I094" : "I095");
            long projectId = createProject(mentorId, leaderId, "Cancellation " + suffix);
            projectService.addMember(mentorId, projectId, memberId);
            long invitationId = projectService.issueInvitation(leaderId, projectId, inviteeId);
            long exitId = projectService.requestOwnLeave(memberId, projectId, "Leave after cancellation");
            long memberMembershipId = membershipId(projectId, memberId);
            long leaderMembershipId = membershipId(projectId, leaderId);
            long taskId = insertTask(projectId, memberId, "Retained " + suffix, null);
            jdbc.update("update tasks set status = 'IN_PROGRESS', estimated_minutes = 120 where id = ?", taskId);
            jdbc.update("insert into task_comments (task_id, author_user_id, body) values (?, ?, 'Keep comment')",
                    taskId, memberId);
            jdbc.update("""
                    insert into task_work_logs (project_id, task_id, membership_id, work_date, minutes)
                    values (?, ?, ?, date '2026-08-20', 30)
                    """, projectId, taskId, membershipId(projectId, memberId));
            jdbc.update("""
                    insert into task_remaining_effort_forecasts (
                        project_id, task_id, incoming_membership_id, forecasting_leader_membership_id,
                        assignment_started_at, remaining_minutes, actual_minutes_snapshot, initial_note)
                    values (?, ?, ?, ?, ?, 60, 0, 'Keep forecast')
                    """, projectId, taskId, membershipId(projectId, memberId), membershipId(projectId, leaderId),
                    dbTime(NOW));
            if (activate) {
                projectService.activate(mentorId, projectId);
            }
            entityManager.flush();
            entityManager.clear();

            projectService.cancel(mentorId, projectId, "  Scope changed " + suffix + "  ");
            entityManager.flush();
            entityManager.clear();
            long taskVersion = number("select version from tasks where id = ?", taskId);
            long forecastId = number("select id from task_remaining_effort_forecasts where task_id = ?", taskId);
            var taskDetails = taskService.details("member-cancel-" + suffix + "@example.test", projectId, taskId);

            assertAll("PRJ-023 " + suffix,
                    () -> assertEquals("CANCELLED", text("select status from projects where id = ?", projectId)),
                    () -> assertEquals(mentorId, number("select cancelled_by_mentor_user_id from projects where id = ?", projectId)),
                    () -> assertEquals("Scope changed " + suffix, text("select cancellation_reason from projects where id = ?", projectId)),
                    () -> assertEquals(1, count("select count(*) from projects where id = ? and cancelled_at is not null", projectId)),
                    () -> assertEquals(2, count("select count(*) from project_memberships where project_id = ? and left_at is not null and removed_by_mentor_user_id = ?", projectId, mentorId)),
                    () -> assertEquals(1, count("select count(*) from project_leadership_terms where project_id = ? and ended_at is not null and ended_by_mentor_user_id = ?", projectId, mentorId)),
                    () -> assertEquals(1, count("select count(*) from project_invitations where id = ? and status = 'REVOKED' and resolution_code = 'PROJECT_CANCELLED'", invitationId)),
                    () -> assertEquals(1, count("select count(*) from project_membership_exit_requests where id = ? and status = 'SUPERSEDED'", exitId)),
                    () -> assertEquals(1, count("select count(*) from tasks where id = ?", taskId)),
                    () -> assertEquals(1, count("select count(*) from task_comments where task_id = ?", taskId)),
                    () -> assertEquals(1, count("select count(*) from task_work_logs where task_id = ?", taskId)),
                    () -> assertEquals(1, count("select count(*) from tasks where id = ? and status = 'IN_PROGRESS' and estimated_minutes = 120", taskId)),
                    () -> assertEquals(1, count("select count(*) from task_remaining_effort_forecasts where task_id = ? and remaining_minutes = 60 and initial_note = 'Keep forecast'", taskId)),
                    () -> assertTrue(count("select count(*) from notifications where project_id = ? and notification_type = 'MEMBERSHIP_CHANGED' and action_url = '/projects/' || ?::text", projectId, projectId) >= 2),
                    () -> assertTrue(count("select count(*) from notifications where project_id = ? and notification_type = 'LEADERSHIP_CHANGED' and action_url = '/projects/' || ?::text", projectId, projectId) >= 1),
                    () -> assertFalse(projectPages.detail(mentorId, projectId).canManage()),
                    () -> assertEquals("Scope changed " + suffix, projectPages.detail(mentorId, projectId).cancellationReason()),
                    () -> assertThrows(ProjectAccessDeniedException.class,
                            () -> projectService.addMember(mentorId, projectId, inviteeId)),
                    () -> assertThrows(ProjectAccessDeniedException.class, () -> projectService.activate(mentorId, projectId)),
                    () -> assertThrows(ProjectAccessDeniedException.class, () -> projectService.complete(mentorId, projectId)),
                    () -> assertThrows(ProjectRuleViolationException.class, () -> projectService.delete(mentorId, projectId)),
                    () -> assertThrows(ProjectAccessDeniedException.class, () -> projectService.cancel(mentorId, projectId, "Again")),
                    () -> assertThrows(ProjectAccessDeniedException.class, () -> projectService.issueInvitation(leaderId, projectId, inviteeId)),
                    () -> assertThrows(ProjectAccessDeniedException.class, () -> projectService.changeLeader(mentorId, projectId, memberId)),
                    () -> assertThrows(ProjectAccessDeniedException.class, () -> projectService.requestOwnLeave(memberId, projectId, "Again")),
                    () -> assertFalse(taskDetails.canChangeStatus()),
                    () -> assertFalse(taskDetails.canComment()),
                    () -> assertFalse(taskDetails.canEdit()),
                    () -> assertFalse(taskDetails.canDelete()),
                    () -> assertFalse(taskDetails.canReassign()),
                    () -> assertFalse(taskDetails.canLogWork()),
                    () -> assertThrows(TaskNotFoundException.class,
                            () -> taskService.changeStatus("member-cancel-" + suffix + "@example.test",
                                    projectId, taskId, com.lab.labtimesheet.feature.project.model.TaskStatus.DONE)),
                    () -> assertThrows(TaskNotFoundException.class, () -> taskService.create(
                            "member-cancel-" + suffix + "@example.test",
                            new CreateTaskCommand(projectId, memberMembershipId, "Blocked", null,
                                    LocalDate.of(2026, 8, 21), null))),
                    () -> assertThrows(TaskNotFoundException.class, () -> taskService.edit(
                            "member-cancel-" + suffix + "@example.test", projectId, taskId, taskVersion,
                            "Blocked edit", null, null)),
                    () -> assertThrows(TaskNotFoundException.class, () -> taskService.reassign(
                            "leader-cancel-" + suffix + "@example.test", projectId, taskId, taskVersion,
                            leaderMembershipId)),
                    () -> assertThrows(TaskNotFoundException.class, () -> taskService.addComment(
                            "member-cancel-" + suffix + "@example.test", projectId, taskId, "Blocked comment")),
                    () -> assertThrows(TaskNotFoundException.class, () -> taskService.addWorkLog(
                            "member-cancel-" + suffix + "@example.test", projectId, taskId, taskVersion,
                            LocalDate.of(2026, 8, 21), 15, null)),
                    () -> assertThrows(TaskNotFoundException.class, () -> taskService.estimate(
                            "leader-cancel-" + suffix + "@example.test", projectId, taskId, taskVersion, 90)),
                    () -> assertThrows(TaskNotFoundException.class, () -> taskService.correctForecast(
                            "leader-cancel-" + suffix + "@example.test", projectId, taskId,
                            forecastId, 45, "Blocked correction")));
        }
    }

    /** Protects {@code PRJ-023} and {@code AC-PRJ-015}: blank reasons and foreign/MISSING IDs do not mutate or reveal a Project. */
    @Test
    void cancellationRejectsBlankReasonsForeignMentorsAndMissingProjectsWithoutMutation() {
        int sequence = 0;
        for (boolean active : List.of(false, true)) {
            String suffix = active ? "active" : "planned";
            long mentorId = user("mentor-cancel-refusal-" + suffix + "@example.test", "MENTOR");
            long foreignMentorId = user("foreign-cancel-refusal-" + suffix + "@example.test", "MENTOR");
            long leaderId = intern("leader-cancel-refusal-" + suffix + "@example.test", "I" + (96 + sequence++));
            long projectId = createProject(mentorId, leaderId, "Cancellation refusal " + suffix);
            if (active) {
                projectService.activate(mentorId, projectId);
            }

            for (String reason : new String[] {null, "", "   "}) {
                assertThrows(ProjectRuleViolationException.class,
                        () -> projectService.cancel(mentorId, projectId, reason));
            }
            ProjectAccessDeniedException foreign = assertThrows(ProjectAccessDeniedException.class,
                    () -> projectService.cancel(foreignMentorId, projectId, "Not mine"));
            ProjectAccessDeniedException missing = assertThrows(ProjectAccessDeniedException.class,
                    () -> projectService.cancel(foreignMentorId, Long.MAX_VALUE, "Not found"));
            assertEquals(foreign.getMessage(), missing.getMessage());
            assertEquals(active ? "ACTIVE" : "PLANNED", text("select status from projects where id = ?", projectId));
            assertEquals(0, count("select count(*) from projects where id = ? and cancelled_at is not null", projectId));
        }
    }

    /**
     * Protects {@code PRJ-002} and {@code AC-PRJ-014}. Observable break: deleting an empty
     * draft leaves its initial membership, leadership term, or notification behind, or deletes
     * a notification belonging to another Project. Expected: zero rows remain for the deleted
     * Project's membership, leadership, and linked notifications, while the other Project's
     * linked notification count remains exactly two (its creation membership and leadership notices);
     * before deletion the owner's detail page offers the valid empty-draft action.
     */
    @Test
    void ownerCanDeleteOnlyAnEmptyPlannedProjectAndItsLinkedNotifications() {
        long mentorId = user("mentor-delete-planned@example.test", "MENTOR");
        long leaderId = intern("leader-delete-planned@example.test", "I026");
        long projectId = createProject(mentorId, leaderId, "Disposable draft");
        long otherProjectId = createProject(mentorId, leaderId, "Keep notifications");
        int linkedNotificationsBeforeDelete = count("select count(*) from notifications where project_id = ?",
                projectId);
        assertTrue(projectPages.detail(mentorId, projectId).canDelete());
        assertEquals(2, count("select count(*) from notifications where action_url = '/projects/' || ?::text",
                projectId));
        assertEquals(2, count("select count(*) from notifications where action_url = '/projects/' || ?::text",
                otherProjectId));

        projectService.delete(mentorId, projectId);

        assertAll(
                () -> assertEquals(2, linkedNotificationsBeforeDelete,
                        "both notifications raised after V3 must carry their Project identifier"),
                () -> assertEquals(0, count("select count(*) from projects where id = ?", projectId)),
                () -> assertEquals(0, count("select count(*) from project_memberships where project_id = ?", projectId)),
                () -> assertEquals(0, count("select count(*) from project_leadership_terms where project_id = ?", projectId)),
                () -> assertEquals(0, count("select count(*) from notifications where project_id = ?", projectId)),
                () -> assertEquals(0, count("""
                        select count(*) from notifications
                        where action_url = '/projects/' || ?::text
                           or action_url like '/projects/' || ?::text || '/%'
                        """, projectId, projectId)),
                () -> assertEquals(2, count("select count(*) from notifications where project_id = ?", otherProjectId)));
    }

    /**
     * Protects {@code PRJ-002} and {@code AC-PRJ-014}. Observable break: an ordinary Task and
     * its retained comment/work log are physically deleted with a Project draft. Expected:
     * deletion is refused, the page offers no delete action, and exactly one Task, comment, and
     * work-log row remain.
     */
    @Test
    void taskBlocksDraftDeletionAndItsWorkHistoryRemains() {
        long mentorId = user("mentor-delete-task@example.test", "MENTOR");
        long leaderId = intern("leader-delete-task@example.test", "I028");
        long projectId = createProject(mentorId, leaderId, "Task blocker");
        long taskId = insertTask(projectId, leaderId, "Retained Task", null);
        jdbc.update("insert into task_comments (task_id, author_user_id, body) values (?, ?, 'Retained comment')",
                taskId, leaderId);
        jdbc.update("""
                insert into task_work_logs (project_id, task_id, membership_id, work_date, minutes)
                values (?, ?, ?, date '2026-08-20', 30)
                """, projectId, taskId, membershipId(projectId, leaderId));
        entityManager.clear();
        assertFalse(projectPages.detail(mentorId, projectId).canDelete());

        assertThrows(ProjectRuleViolationException.class, () -> projectService.delete(mentorId, projectId));

        assertEquals(1, count("select count(*) from tasks where project_id = ?", projectId));
        assertEquals(1, count("select count(*) from task_comments where task_id = ?", taskId));
        assertEquals(1, count("select count(*) from task_work_logs where project_id = ?", projectId));
    }

    /**
     * Protects {@code PRJ-002} and {@code AC-PRJ-014}. Observable break: a soft-deleted Task is
     * overlooked by the emptiness guard and its row is removed. Expected: deletion is refused
     * the page offers no delete action, and the one soft-deleted Task remains.
     */
    @Test
    void softDeletedTaskStillBlocksDraftDeletion() {
        long mentorId = user("mentor-delete-soft-task@example.test", "MENTOR");
        long leaderId = intern("leader-delete-soft-task@example.test", "I029");
        long projectId = createProject(mentorId, leaderId, "Soft-deleted Task blocker");
        long taskId = insertTask(projectId, leaderId, "Soft-deleted Task", dbTime(NOW.plusSeconds(30)));
        entityManager.clear();
        assertFalse(projectPages.detail(mentorId, projectId).canDelete());

        assertThrows(ProjectRuleViolationException.class, () -> projectService.delete(mentorId, projectId));

        assertEquals(1, count("select count(*) from tasks where id = ? and deleted_at is not null", taskId));
    }

    /**
     * Protects {@code PRJ-002} and {@code AC-PRJ-014}. Observable break: deleting a draft removes
     * a pending invitation instead of refusing the non-empty Project. Expected: the invitation
     * its Project remains, and the page offers no delete action.
     */
    @Test
    void invitationBlocksDraftDeletion() {
        long mentorId = user("mentor-delete-invitation@example.test", "MENTOR");
        long leaderId = intern("leader-delete-invitation@example.test", "I030");
        long inviteeId = intern("invitee-delete-invitation@example.test", "I031");
        long projectId = createProject(mentorId, leaderId, "Invitation blocker");
        long invitationId = projectService.issueInvitation(leaderId, projectId, inviteeId);
        assertFalse(projectPages.detail(mentorId, projectId).canDelete());

        assertThrows(ProjectRuleViolationException.class, () -> projectService.delete(mentorId, projectId));

        assertEquals(1, count("select count(*) from projects where id = ?", projectId));
        assertEquals(1, count("select count(*) from project_invitations where id = ?", invitationId));
    }

    /**
     * Protects {@code PRJ-002} and {@code AC-PRJ-014}. Observable break: deleting a draft removes
     * a pending membership exit request instead of refusing the non-empty Project. Expected:
     * the exit request and Project remain, and the page offers no delete action.
     */
    @Test
    void exitRequestBlocksDraftDeletion() {
        long mentorId = user("mentor-delete-exit@example.test", "MENTOR");
        long leaderId = intern("leader-delete-exit@example.test", "I032");
        long projectId = createProject(mentorId, leaderId, "Exit blocker");
        long requestId = projectService.requestOwnLeave(leaderId, projectId, "Leave draft");
        assertFalse(projectPages.detail(mentorId, projectId).canDelete());

        assertThrows(ProjectRuleViolationException.class, () -> projectService.delete(mentorId, projectId));

        assertEquals(1, count("select count(*) from projects where id = ?", projectId));
        assertEquals(1, count("select count(*) from project_membership_exit_requests where id = ?", requestId));
    }

    /**
     * Protects {@code PRJ-002} and {@code AC-PRJ-014}. Observable break: a second membership is
     * silently removed with an otherwise disposable draft. Expected: deletion is refused and
     * both membership intervals remain and the page offers no delete action.
     */
    @Test
    void secondMembershipBlocksDraftDeletion() {
        long mentorId = user("mentor-delete-member@example.test", "MENTOR");
        long leaderId = intern("leader-delete-member@example.test", "I033");
        long memberId = intern("member-delete-member@example.test", "I034");
        long projectId = createProject(mentorId, leaderId, "Membership blocker");
        projectService.addMember(mentorId, projectId, memberId);
        assertFalse(projectPages.detail(mentorId, projectId).canDelete());

        assertThrows(ProjectRuleViolationException.class, () -> projectService.delete(mentorId, projectId));

        assertEquals(2, count("select count(*) from project_memberships where project_id = ?", projectId));
    }

    /**
     * Protects {@code PRJ-002}, {@code AC-PRJ-014}, and {@code AUTH-002}. Observable break:
     * a foreign Mentor can delete a draft or distinguish a guessed Project ID from a missing one.
     * Expected: both requests raise the same access-denied exception and the owned Project remains.
     */
    @Test
    void foreignMentorCannotDeleteOrEnumerateAnEmptyDraft() {
        long ownerId = user("mentor-delete-owner@example.test", "MENTOR");
        long foreignMentorId = user("mentor-delete-foreign@example.test", "MENTOR");
        long leaderId = intern("leader-delete-foreign@example.test", "I036");
        long projectId = createProject(ownerId, leaderId, "Owned empty draft");

        assertThrows(ProjectAccessDeniedException.class,
                () -> projectService.delete(foreignMentorId, projectId));
        assertThrows(ProjectAccessDeniedException.class,
                () -> projectService.delete(foreignMentorId, Long.MAX_VALUE));

        assertEquals(1, count("select count(*) from projects where id = ?", projectId));
    }

    /**
     * Protects {@code PRJ-002} and {@code AC-PRJ-014}. Observable break: an ACTIVE Project can be
     * physically deleted. Expected: deletion is refused and the ACTIVE status and Project row
     * remain unchanged.
     */
    @Test
    void activeProjectCannotBeDeletedAndRemainsAvailable() {
        long mentorId = user("mentor-delete-active@example.test", "MENTOR");
        long leaderId = intern("leader-delete-active@example.test", "I027");
        long projectId = createProject(mentorId, leaderId, "Protected active project");
        projectService.activate(mentorId, projectId);
        assertFalse(projectPages.detail(mentorId, projectId).canDelete());

        assertThrows(ProjectRuleViolationException.class,
                () -> projectService.delete(mentorId, projectId));

        assertEquals("ACTIVE", text("select status from projects where id = ?", projectId));
        assertEquals(1, count("select count(*) from projects where id = ?", projectId));
    }

    /**
     * Protects {@code PRJ-002} and {@code AC-PRJ-014}. Observable break: a completed Project is
     * treated as a disposable draft and loses its retained membership and leadership intervals.
     * Expected: deletion is refused and the completed Project, membership, and term each remain.
     */
    @Test
    void completedProjectCannotBeDeletedAndRetainsItsIntervals() {
        long mentorId = user("mentor-delete-completed@example.test", "MENTOR");
        long leaderId = intern("leader-delete-completed@example.test", "I035");
        long projectId = createProject(mentorId, leaderId, "Completed Project");
        projectService.activate(mentorId, projectId);
        projectService.complete(mentorId, projectId);
        entityManager.clear();
        assertFalse(projectPages.detail(mentorId, projectId).canDelete());

        assertThrows(ProjectRuleViolationException.class, () -> projectService.delete(mentorId, projectId));

        assertEquals("COMPLETED", text("select status from projects where id = ?", projectId));
        assertEquals(1, count("select count(*) from project_memberships where project_id = ?", projectId));
        assertEquals(1, count("select count(*) from project_leadership_terms where project_id = ?", projectId));
    }

    /**
     * Protects {@code PRJ-002} and the service half of {@code AC-PRJ-014}. Observable break:
     * activation accepts a Project after completion and reopens its closed aggregate. Expected:
     * the service raises the completed-state refusal and the persisted status remains COMPLETED.
     */
    @Test
    void completedProjectCannotBeReactivatedThroughTheService() {
        long mentorId = user("mentor-reactivate-completed@example.test", "MENTOR");
        long leaderId = intern("leader-reactivate-completed@example.test", "I036");
        long projectId = createProject(mentorId, leaderId, "Completed stays terminal");
        projectService.activate(mentorId, projectId);
        projectService.complete(mentorId, projectId);
        entityManager.clear();

        assertThrows(ProjectAccessDeniedException.class, () -> projectService.activate(mentorId, projectId));

        assertEquals("COMPLETED", text("select status from projects where id = ?", projectId));
    }

    @Test
    void adminTerminalReadinessComposesCurrentLeadershipAndUnfinishedTasksBeforeCompletion() {
        long adminId = user("admin-terminal@example.test", "ADMIN");
        long mentorId = user("mentor-terminal@example.test", "MENTOR");
        long departingId = intern("departing-terminal@example.test", "I023");
        long replacementId = intern("replacement-terminal@example.test", "I024");
        long projectId = createProject(mentorId, departingId, "Terminal readiness");
        projectService.addMember(mentorId, projectId, replacementId);
        long departingMembershipId = membershipId(projectId, departingId);
        jdbc.update("""
                insert into tasks (
                    project_id, assignee_membership_id, title,
                    created_by_membership_id, assigned_by_membership_id)
                values (?, ?, 'Transfer before completion', ?, ?)
                """, projectId, departingMembershipId, departingMembershipId, departingMembershipId);

        var blocked = internships.internshipLifecycleReadiness(departingId, adminId);
        assertTrue(blocked.currentLeader());
        assertEquals(1, blocked.unfinishedTaskCount());

        projectService.changeLeader(mentorId, projectId, replacementId);
        var taskBlocked = internships.internshipLifecycleReadiness(departingId, adminId);
        assertFalse(taskBlocked.currentLeader());
        assertEquals(1, taskBlocked.unfinishedTaskCount());

        jdbc.update("update tasks set status = 'DONE', updated_at = ? where project_id = ?",
                dbTime(NOW.plusSeconds(90)), projectId);
        entityManager.clear();
        var ready = internships.internshipLifecycleReadiness(departingId, adminId);
        assertFalse(ready.currentLeader());
        assertEquals(0, ready.unfinishedTaskCount());

        internships.completeInternship(departingId, adminId);
        entityManager.flush();

        assertEquals("COMPLETED", text(
                "select internship_status from intern_profiles where user_id = ?", departingId));
        assertEquals("ACTIVE", text("select account_status from app_users where id = ?", departingId));
    }

    @Test
    void terminalCompletionRecomputesAndRejectsLockedLeaderTaskFacts() {
        long adminId = user("admin-terminal-blocked@example.test", "ADMIN");
        long mentorId = user("mentor-terminal-blocked@example.test", "MENTOR");
        long leaderId = intern("leader-terminal-blocked@example.test", "I025");
        long projectId = createProject(mentorId, leaderId, "Blocked terminal readiness");
        long membershipId = membershipId(projectId, leaderId);
        jdbc.update("""
                insert into tasks (
                    project_id, assignee_membership_id, title,
                    created_by_membership_id, assigned_by_membership_id)
                values (?, ?, 'Still unfinished', ?, ?)
                """, projectId, membershipId, membershipId, membershipId);

        var failure = assertThrows(
                IllegalStateException.class,
                () -> internships.completeInternship(leaderId, adminId));

        assertEquals("Intern is still a current Leader", failure.getMessage());
        assertEquals("ACTIVE", text(
                "select internship_status from intern_profiles where user_id = ?", leaderId));
    }

    /**
     * Protects ACC-022: a non-Leader who owns one unfinished Task is refused completion with the
     * exact Task refusal, and the hand-derived unchanged profile status remains ACTIVE.
     */
    @Test
    void terminalCompletionRejectsUnfinishedTaskWithoutLeadershipAndPreservesProfile() {
        long adminId = user("admin-terminal-task-blocked@example.test", "ADMIN");
        long mentorId = user("mentor-terminal-task-blocked@example.test", "MENTOR");
        long leaderId = intern("leader-terminal-task-blocked@example.test", "I030");
        long departingId = intern("departing-terminal-task-blocked@example.test", "I031");
        long projectId = createProject(mentorId, leaderId, "Task-blocked terminal readiness");
        projectService.addMember(mentorId, projectId, departingId);
        long departingMembershipId = membershipId(projectId, departingId);
        jdbc.update("""
                insert into tasks (
                    project_id, assignee_membership_id, title,
                    created_by_membership_id, assigned_by_membership_id)
                values (?, ?, 'Still unfinished', ?, ?)
                """, projectId, departingMembershipId, departingMembershipId, departingMembershipId);

        var failure = assertThrows(
                IllegalStateException.class,
                () -> internships.completeInternship(departingId, adminId));

        assertEquals("Intern still owns unfinished Tasks", failure.getMessage());
        assertEquals("ACTIVE", text(
                "select internship_status from intern_profiles where user_id = ?", departingId));
    }

    @Test
    void activationRejectsATaskAssignedToAFormerMemberWithoutPartialMutation() {
        long mentorId = user("mentor-guard@example.test", "MENTOR");
        long leaderId = intern("leader-guard@example.test", "I015");
        long formerMemberId = intern("former-assignee@example.test", "I016");
        long projectId = createProject(mentorId, leaderId, "Assignee guard");
        projectService.addMember(mentorId, projectId, formerMemberId);
        long leaderMembershipId = membershipId(projectId, leaderId);
        long formerMembershipId = membershipId(projectId, formerMemberId);
        jdbc.update("""
                insert into tasks (
                    project_id, assignee_membership_id, title,
                    created_by_membership_id, assigned_by_membership_id)
                values (?, ?, 'Former assignee', ?, ?)
                """, projectId, formerMembershipId, leaderMembershipId, leaderMembershipId);
        jdbc.update("""
                update project_memberships
                set left_at = ?, removed_by_mentor_user_id = ?, updated_at = ?
                where id = ?
                """, dbTime(NOW.plusSeconds(60)), mentorId, dbTime(NOW.plusSeconds(60)), formerMembershipId);
        entityManager.clear();

        assertThrows(ProjectRuleViolationException.class, () -> projectService.activate(mentorId, projectId));

        assertEquals("PLANNED", text("select status from projects where id = ?", projectId));
        assertEquals(1, count("select count(*) from tasks where project_id = ? and deleted_at is null", projectId));
    }

    /**
     * Protects {@code PRJ-024}. Observable break: a Mentor entering a Project that began a week
     * ago is refused, so the Project is either not recorded or recorded with a false start date.
     * Expected: the Project commits with its 7 August start while the server date is 14 August,
     * and the initial Leader's membership still begins at the server time of creation, not on
     * 7 August, so the past start opens no work dates before the Leader joined ({@code TSK-014}).
     */
    @Test
    void mentorEntersAProjectThatStartedAWeekBeforeItWasCreated() {
        long mentorId = user("mentor-past-start@example.test", "MENTOR");
        long leaderId = intern("leader-past-start@example.test", "I024");

        long projectId = projectService.create(
                mentorId,
                new ProjectCreateCommand(
                        "Already Running Project",
                        null,
                        LocalDate.of(2026, 8, 7),
                        LocalDate.of(2026, 9, 30),
                        leaderId));

        assertEquals("2026-08-07", text("select start_date::text from projects where id = ?", projectId));
        assertEquals("PLANNED", text("select status from projects where id = ?", projectId));
        assertEquals(NOW, jdbc.queryForObject(
                "select joined_at from project_memberships where project_id = ? and left_at is null",
                OffsetDateTime.class, projectId).toInstant());
    }

    /**
     * Protects {@code PRJ-024}. Observable break: a Project beginning on the service's current
     * business date is rejected as already started. Expected: the Project is persisted with the
     * submitted 14 August start date and remains PLANNED until explicitly activated.
     */
    @Test
    void mentorCanCreateAProjectStartingToday() {
        long mentorId = user("mentor-today-start@example.test", "MENTOR");
        long leaderId = intern("leader-today-start@example.test", "I037");

        long projectId = projectService.create(
                mentorId,
                new ProjectCreateCommand(
                        "Starts Today",
                        null,
                        LocalDate.of(2026, 8, 14),
                        LocalDate.of(2026, 9, 30),
                        leaderId));

        assertEquals("2026-08-14", text("select start_date::text from projects where id = ?", projectId));
        assertEquals("PLANNED", text("select status from projects where id = ?", projectId));
    }

    private long createProject(long mentorId, long leaderId, String name) {
        return projectService.create(
                mentorId,
                new ProjectCreateCommand(
                        name,
                        null,
                        LocalDate.of(2026, 8, 15),
                        LocalDate.of(2026, 9, 30),
                        leaderId));
    }

    private long insertTask(long projectId, long internUserId, String title, OffsetDateTime deletedAt) {
        long membershipId = membershipId(projectId, internUserId);
        return jdbc.queryForObject("""
                insert into tasks (
                    project_id, assignee_membership_id, title,
                    created_by_membership_id, assigned_by_membership_id,
                    deleted_at, deleted_by_membership_id)
                values (?, ?, ?, ?, ?, ?, ?)
                returning id
                """, Long.class, projectId, membershipId, title, membershipId, membershipId,
                deletedAt, deletedAt == null ? null : membershipId);
    }

    private long user(String email, String role) {
        return jdbc.queryForObject("""
                insert into app_users (
                    email, display_name, password_hash, global_role, account_status, activated_at)
                values (?, ?, '{noop}password-password', ?, 'ACTIVE', ?)
                returning id
                """, Long.class, email, email, role, dbTime(NOW));
    }

    private long intern(String email, String studentCode) {
        long userId = user(email, "INTERN");
        jdbc.update("""
                insert into intern_profiles (
                    user_id, student_code, internship_start_date, internship_end_date,
                    internship_status, activated_at)
                values (?, ?, date '2026-08-01', date '2026-12-31', 'ACTIVE', ?)
                """, userId, studentCode, dbTime(NOW));
        return userId;
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

    private OffsetDateTime dbTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
