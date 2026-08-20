package com.lab.labtimesheet.feature.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.model.dto.ProjectCreateCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL proof that Project membership mutations retain Account-owned lifecycle locks. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProjectLifecycleLockIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");

    @Autowired
    private ProjectService projects;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactions;

    @Test
    void ownLeaveRetainsAccountAndInternProfileLocksUntilOuterProjectTransactionCommits() throws Exception {
        long mentorId = user("mentor-lock-retention@example.test", "MENTOR");
        long leaderId = intern("leader-lock-retention@example.test", "I901");
        long memberId = intern("member-lock-retention@example.test", "I902");
        long projectId = transactions.execute(status -> projects.create(
                mentorId,
                new ProjectCreateCommand(
                        "Lock retention",
                        null,
                        LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 12, 31),
                        leaderId)));
        transactions.executeWithoutResult(status -> projects.addMember(mentorId, projectId, memberId));

        CountDownLatch mutationReturned = new CountDownLatch(1);
        CountDownLatch allowOuterCommit = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Future<?> mutation = executor.submit(() -> transactions.executeWithoutResult(status -> {
                projects.requestOwnLeave(memberId, projectId, "Retention proof");
                mutationReturned.countDown();
                awaitLatch(allowOuterCommit, "outer Project transaction commit");
            }));
            assertThat(mutationReturned.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Long> accountProbe = executor.submit(() -> transactions.execute(status -> jdbc.queryForObject(
                    "select id from app_users where id = ? for update", Long.class, memberId)));
            Future<Long> profileProbe = executor.submit(() -> transactions.execute(status -> jdbc.queryForObject(
                    "select user_id from intern_profiles where user_id = ? for update", Long.class, memberId)));

            assertThatThrownBy(() -> accountProbe.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            assertThatThrownBy(() -> profileProbe.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            allowOuterCommit.countDown();
            mutation.get(10, TimeUnit.SECONDS);
            assertThat(accountProbe.get(10, TimeUnit.SECONDS)).isEqualTo(memberId);
            assertThat(profileProbe.get(10, TimeUnit.SECONDS)).isEqualTo(memberId);
        } finally {
            allowOuterCommit.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(jdbc.queryForObject(
                "select count(*) from project_membership_exit_requests where project_id = ? and requester_membership_id = "
                        + "(select id from project_memberships where project_id = ? and intern_user_id = ?)",
                Integer.class,
                projectId,
                projectId,
                memberId)).isEqualTo(1);
    }

    @Test
    void staleRoutingAndAccountReadsCannotIssueAfterLeadershipAndAccountStateChange() throws Exception {
        // Create the pending invitee first so the issuing mutation blocks on that target Account
        // before it reaches the current-Leader row. The Mentor can therefore replace the Leader
        // while the invitation request is waiting, exercising the post-lock route recheck.
        long inviteeId = intern("invitee-stale-route@example.test", "I905");
        long mentorId = user("mentor-stale-route@example.test", "MENTOR");
        long leaderId = intern("leader-stale-route@example.test", "I903");
        long replacementId = intern("replacement-stale-route@example.test", "I904");
        long projectId = transactions.execute(status -> projects.create(
                mentorId,
                new ProjectCreateCommand(
                        "Stale route",
                        null,
                        LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 12, 31),
                        leaderId)));
        transactions.executeWithoutResult(status -> projects.addMember(mentorId, projectId, replacementId));

        CountDownLatch inviteeAccountHeld = new CountDownLatch(1);
        CountDownLatch releaseInviteeAccount = new CountDownLatch(1);
        CountDownLatch mutationStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> holder = executor.submit(() -> transactions.executeWithoutResult(status -> {
                jdbc.queryForObject(
                        "select id from app_users where id = ? for update", Long.class, inviteeId);
                jdbc.update(
                        "update app_users set account_status = 'LOCKED', locked_at = ? where id = ?",
                        NOW.atOffset(ZoneOffset.UTC),
                        inviteeId);
                inviteeAccountHeld.countDown();
                awaitLatch(releaseInviteeAccount, "invitee Account release");
            }));
            assertThat(inviteeAccountHeld.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> mutation = executor.submit(() -> transactions.executeWithoutResult(status -> {
                mutationStarted.countDown();
                projects.issueInvitation(leaderId, projectId, inviteeId);
            }));
            assertThat(mutationStarted.await(5, TimeUnit.SECONDS)).isTrue();
            awaitAccountLockWait();

            transactions.executeWithoutResult(status -> {
                projects.changeLeader(mentorId, projectId, replacementId);
            });

            releaseInviteeAccount.countDown();
            assertThatThrownBy(() -> mutation.get(10, TimeUnit.SECONDS))
                    .hasRootCauseInstanceOf(ProjectAccessDeniedException.class);
            holder.get(10, TimeUnit.SECONDS);
        } finally {
            releaseInviteeAccount.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(jdbc.queryForObject(
                "select count(*) from project_invitations where project_id = ? and invited_intern_user_id = ?",
                Integer.class,
                projectId,
                inviteeId)).isZero();
    }

    @Test
    void exitRequestLocksMentorNotificationRecipientBeforeProject() throws Exception {
        long mentorId = user("mentor-exit-notification-lock@example.test", "MENTOR");
        long leaderId = intern("leader-exit-notification-lock@example.test", "I908");
        long projectId = transactions.execute(status -> projects.create(
                mentorId,
                new ProjectCreateCommand(
                        "Exit notification lock order",
                        null,
                        LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 12, 31),
                        leaderId)));

        CountDownLatch mentorAccountHeld = new CountDownLatch(1);
        CountDownLatch releaseMentorAccount = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Future<?> holder = executor.submit(() -> transactions.executeWithoutResult(status -> {
                jdbc.queryForObject(
                        "select id from app_users where id = ? for update", Long.class, mentorId);
                mentorAccountHeld.countDown();
                awaitLatch(releaseMentorAccount, "Mentor Account release");
            }));
            assertThat(mentorAccountHeld.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Long> mutation = executor.submit(() -> transactions.execute(status ->
                    projects.requestOwnLeave(leaderId, projectId, "Mentor notification lock order")));
            awaitAccountLockWait();

            Future<Long> projectProbe = executor.submit(() -> transactions.execute(status -> jdbc.queryForObject(
                    "select id from projects where id = ? for update", Long.class, projectId)));
            assertThat(projectProbe.get(1, TimeUnit.SECONDS)).isEqualTo(projectId);

            releaseMentorAccount.countDown();
            long requestId = mutation.get(10, TimeUnit.SECONDS);
            holder.get(10, TimeUnit.SECONDS);

            assertThat(jdbc.queryForList(
                    "select recipient_user_id from notifications "
                            + "where notification_type = 'MEMBERSHIP_EXIT_REQUESTED' and action_url = ? "
                            + "order by recipient_user_id",
                    Long.class,
                    "/projects/" + projectId + "/exits/" + requestId))
                    .containsExactly(mentorId, leaderId);
        } finally {
            releaseMentorAccount.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void leaderChangeLocksNotificationRecipientsBeforeProject() throws Exception {
        long mentorId = user("mentor-notification-lock@example.test", "MENTOR");
        long outgoingLeaderId = intern("outgoing-notification-lock@example.test", "I906");
        long replacementId = intern("replacement-notification-lock@example.test", "I907");
        long projectId = transactions.execute(status -> projects.create(
                mentorId,
                new ProjectCreateCommand(
                        "Notification lock order",
                        null,
                        LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 12, 31),
                        outgoingLeaderId)));
        transactions.executeWithoutResult(status -> projects.addMember(mentorId, projectId, replacementId));

        CountDownLatch outgoingAccountHeld = new CountDownLatch(1);
        CountDownLatch releaseOutgoingAccount = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Future<?> holder = executor.submit(() -> transactions.executeWithoutResult(status -> {
                jdbc.queryForObject(
                        "select id from app_users where id = ? for update", Long.class, outgoingLeaderId);
                outgoingAccountHeld.countDown();
                awaitLatch(releaseOutgoingAccount, "outgoing Leader Account release");
            }));
            assertThat(outgoingAccountHeld.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> mutation = executor.submit(() -> transactions.executeWithoutResult(status ->
                    projects.changeLeader(mentorId, projectId, replacementId)));
            awaitAccountLockWait();

            Future<Long> projectProbe = executor.submit(() -> transactions.execute(status -> jdbc.queryForObject(
                    "select id from projects where id = ? for update", Long.class, projectId)));
            assertThat(projectProbe.get(1, TimeUnit.SECONDS)).isEqualTo(projectId);

            releaseOutgoingAccount.countDown();
            mutation.get(10, TimeUnit.SECONDS);
            holder.get(10, TimeUnit.SECONDS);
        } finally {
            releaseOutgoingAccount.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(jdbc.queryForList(
                "select recipient_user_id from notifications "
                        + "where notification_type = 'LEADERSHIP_CHANGED' and action_url = ? "
                        + "and body like ? order by recipient_user_id",
                Long.class,
                "/projects/" + projectId,
                "%Transition: LEADER_CHANGED%")).containsExactly(outgoingLeaderId, replacementId);
    }

    @Test
    void exitDecisionLocksHistoricalRequesterBeforeProject() throws Exception {
        long mentorId = user("mentor-historical-requester-lock@example.test", "MENTOR");
        long outgoingLeaderId = intern("outgoing-historical-requester-lock@example.test", "I909");
        long targetId = intern("target-historical-requester-lock@example.test", "I910");
        long replacementId = intern("replacement-historical-requester-lock@example.test", "I911");
        long projectId = transactions.execute(status -> projects.create(
                mentorId,
                new ProjectCreateCommand(
                        "Historical requester lock order",
                        null,
                        LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 12, 31),
                        outgoingLeaderId)));
        transactions.executeWithoutResult(status ->
                projects.addMembers(mentorId, projectId, java.util.List.of(targetId, replacementId)));
        long outgoingMembershipId = membershipId(projectId, outgoingLeaderId);
        long targetMembershipId = membershipId(projectId, targetId);
        long requestId = transactions.execute(status ->
                projects.requestMemberRemoval(outgoingLeaderId, projectId, targetMembershipId,
                        "Historical requester decision"));

        transactions.executeWithoutResult(status ->
                projects.directRemoveMember(mentorId, projectId, outgoingMembershipId, replacementId));

        CountDownLatch outgoingAccountHeld = new CountDownLatch(1);
        CountDownLatch releaseOutgoingAccount = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Future<?> holder = executor.submit(() -> transactions.executeWithoutResult(status -> {
                jdbc.queryForObject(
                        "select id from app_users where id = ? for update", Long.class, outgoingLeaderId);
                outgoingAccountHeld.countDown();
                awaitLatch(releaseOutgoingAccount, "historical requester Account release");
            }));
            assertThat(outgoingAccountHeld.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> decision = executor.submit(() -> transactions.executeWithoutResult(status ->
                    projects.approveExit(mentorId, requestId, "Approved after replacement")));
            awaitAccountLockWait();

            Future<Long> projectProbe = executor.submit(() -> transactions.execute(status -> jdbc.queryForObject(
                    "select id from projects where id = ? for update", Long.class, projectId)));
            assertThat(projectProbe.get(1, TimeUnit.SECONDS)).isEqualTo(projectId);

            releaseOutgoingAccount.countDown();
            decision.get(10, TimeUnit.SECONDS);
            holder.get(10, TimeUnit.SECONDS);
        } finally {
            releaseOutgoingAccount.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(jdbc.queryForList(
                "select recipient_user_id from notifications "
                        + "where notification_type = 'MEMBERSHIP_EXIT_RESOLVED' and action_url = ? "
                        + "order by recipient_user_id",
                Long.class,
                "/projects/" + projectId + "/exits/" + requestId))
                .containsExactly(outgoingLeaderId, targetId, replacementId);
        assertThat(jdbc.queryForObject(
                "select left_at is not null from project_memberships where id = ?",
                Boolean.class,
                targetMembershipId)).isTrue();
    }

    private long membershipId(long projectId, long internUserId) {
        return jdbc.queryForObject(
                "select id from project_memberships where project_id = ? and intern_user_id = ?",
                Long.class,
                projectId,
                internUserId);
    }

    private long user(String email, String role) {
        return jdbc.queryForObject("""
                insert into app_users (
                    email, display_name, password_hash, global_role, account_status, activated_at)
                values (?, ?, '{noop}password-password', ?, 'ACTIVE', ?)
                returning id
                """, Long.class, email, email, role, NOW.atOffset(ZoneOffset.UTC));
    }

    private long intern(String email, String studentCode) {
        long userId = user(email, "INTERN");
        jdbc.update("""
                insert into intern_profiles (
                    user_id, student_code, internship_start_date, internship_end_date,
                    internship_status, activated_at)
                values (?, ?, date '2026-08-01', date '2026-12-31', 'ACTIVE', ?)
                """, userId, studentCode, NOW.atOffset(ZoneOffset.UTC));
        return userId;
    }

    private static void awaitLatch(CountDownLatch latch, String name) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for " + name);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for " + name, interrupted);
        }
    }

    private void awaitAccountLockWait() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject("""
                    select count(*)
                    from pg_stat_activity activity
                    where activity.pid <> pg_backend_pid()
                      and activity.wait_event_type = 'Lock'
                    """, Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.yield();
        }
        throw new AssertionError("Timed out waiting for the Project mutation to block on Account lock");
    }
}
