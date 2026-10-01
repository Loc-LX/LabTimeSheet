package com.lab.labtimesheet.feature.project.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationAction;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationEvent;
import com.lab.labtimesheet.feature.notification.service.NotificationService;
import com.lab.labtimesheet.feature.project.model.dto.ProjectCreateCommand;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.annotation.Transactional;

/**
 * Protects {@code PRJ-023} and {@code AC-PRJ-015}: a publication failure after cancellation has
 * changed the Project and resolved its pending workflows must roll back every row, including any
 * earlier notification written by the same transaction.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProjectCancellationRollbackIntegrationTest {

    @Autowired
    private ProjectService projects;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoSpyBean
    private NotificationService notifications;

    @Test
    void publishFailureRollsBackProjectMembershipLeadershipWorkflowsAndEarlierNotifications() {
        long mentorId = user("mentor-cancel-rollback@example.test", "MENTOR");
        long leaderId = intern("leader-cancel-rollback@example.test", "IR001");
        long memberId = intern("member-cancel-rollback@example.test", "IR002");
        long inviteeId = intern("invitee-cancel-rollback@example.test", "IR003");
        long projectId = projects.create(mentorId, new ProjectCreateCommand(
                "Cancellation rollback", null,
                LocalDate.of(2026, 8, 15), LocalDate.of(2026, 9, 30), leaderId));
        projects.addMember(mentorId, projectId, memberId);
        long invitationId = projects.issueInvitation(leaderId, projectId, inviteeId);
        long exitId = projects.requestOwnLeave(memberId, projectId, "Leave after cancellation");
        projects.activate(mentorId, projectId);

        var before = snapshot(projectId, invitationId, exitId);
        TestTransaction.flagForCommit();
        TestTransaction.end();

        NotificationService notificationSpy = AopTestUtils.getUltimateTargetObject(notifications);
        clearInvocations(notificationSpy);
        doCallRealMethod().doCallRealMethod()
                .doThrow(new IllegalStateException("forced publish failure"))
                .when(notificationSpy).publish(
                        any(NotificationEvent.class), any(NotificationAction.class), anyCollection());

        var failure = assertThrows(IllegalStateException.class,
                () -> projects.cancel(mentorId, projectId, "Project no longer needed"));

        assertEquals("forced publish failure", failure.getMessage());
        verify(notificationSpy, times(3)).publish(
                any(NotificationEvent.class), any(NotificationAction.class), anyCollection());
        assertEquals(before, snapshot(projectId, invitationId, exitId),
                "all state and the first two notifications must be rolled back");
    }

    private Map<String, Object> snapshot(long projectId, long invitationId, long exitId) {
        var project = jdbc.queryForMap("""
                select status::text as status, cancelled_by_mentor_user_id, cancelled_at, cancellation_reason
                from projects where id = ?
                """, projectId);
        var memberships = jdbc.queryForList("""
                select id, left_at, removed_by_mentor_user_id from project_memberships
                where project_id = ? order by id
                """, projectId);
        var leadership = jdbc.queryForList("""
                select id, ended_at, ended_by_mentor_user_id from project_leadership_terms
                where project_id = ? order by id
                """, projectId);
        var invitation = jdbc.queryForMap("""
                select status::text as status, resolution_code::text as resolution_code
                from project_invitations where id = ?
                """, invitationId);
        var exitRequest = jdbc.queryForMap("""
                select status::text as status from project_membership_exit_requests where id = ?
                """, exitId);
        var notificationCount = jdbc.queryForObject(
                "select count(*) from notifications where project_id = ?", Long.class, projectId);
        return Map.of(
                "project", project,
                "memberships", memberships,
                "leadership", leadership,
                "invitation", invitation,
                "exitRequest", exitRequest,
                "notifications", notificationCount);
    }

    private long user(String email, String role) {
        return jdbc.queryForObject("""
                insert into app_users (
                    email, display_name, password_hash, global_role, account_status, activated_at)
                values (?, ?, '{noop}password-password', ?, 'ACTIVE', now())
                returning id
                """, Long.class, email, email, role);
    }

    private long intern(String email, String studentCode) {
        long userId = user(email, "INTERN");
        jdbc.update("""
                insert into intern_profiles (
                    user_id, student_code, internship_start_date, internship_end_date,
                    internship_status, activated_at)
                values (?, ?, date '2026-08-01', date '2026-12-31', 'ACTIVE', now())
                """, userId, studentCode);
        return userId;
    }
}
