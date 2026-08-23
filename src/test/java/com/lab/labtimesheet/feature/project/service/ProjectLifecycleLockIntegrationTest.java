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
import java.util.concurrent.atomic.AtomicInteger;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.exception.ProjectRuleViolationException;
import com.lab.labtimesheet.feature.project.model.InvitationResponse;
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
    void concurrentLeadershipChangesHaveOneWinnerFromTheSameLeaderSnapshot() throws Exception {
        long mentorId = user("mentor-leadership-race@example.test", "MENTOR");
        long outgoingLeaderId = intern("outgoing-leadership-race@example.test", "I912");
        long firstReplacementId = intern("first-replacement-leadership-race@example.test", "I913");
        long secondReplacementId = intern("second-replacement-leadership-race@example.test", "I914");
        long projectId = transactions.execute(status -> projects.create(
                mentorId,
                new ProjectCreateCommand(
                        "Leadership race",
                        null,
                        LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 12, 31),
                        outgoingLeaderId)));
        transactions.executeWithoutResult(status -> projects.addMembers(
                mentorId, projectId, java.util.List.of(firstReplacementId, secondReplacementId)));

        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch mentorAccountHeld = new CountDownLatch(1);
        CountDownLatch releaseMentorAccount = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Future<?> holder = executor.submit(() -> transactions.executeWithoutResult(status -> {
                jdbc.queryForObject("select id from app_users where id = ? for update", Long.class, mentorId);
                mentorAccountHeld.countDown();
                awaitLatch(releaseMentorAccount, "leadership race Account release");
            }));
            assertThat(mentorAccountHeld.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> first = executor.submit(() -> runLeadershipRace(
                    started, successes, conflicts, mentorId, projectId, firstReplacementId));
            Future<?> second = executor.submit(() -> runLeadershipRace(
                    started, successes, conflicts, mentorId, projectId, secondReplacementId));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            awaitAccountLockWaiters(2);
            releaseMentorAccount.countDown();

            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            holder.get(10, TimeUnit.SECONDS);
        } finally {
            releaseMentorAccount.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(successes).as("successes=%s conflicts=%s", successes.get(), conflicts.get()).hasValue(1);
        assertThat(conflicts).as("successes=%s conflicts=%s", successes.get(), conflicts.get()).hasValue(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from project_leadership_terms where project_id = ? and ended_at is null",
                Integer.class,
                projectId)).isEqualTo(1);
    }

    @Test
    void concurrentLeaderChangeAndLeaderRemovalHaveOneWinnerFromTheSameSnapshot() throws Exception {
        long mentorId = user("mentor-leader-removal-race@example.test", "MENTOR");
        long outgoingLeaderId = intern("outgoing-leader-removal-race@example.test", "I920");
        long changeLeaderTargetId = intern("change-leader-target-race@example.test", "I921");
        long removalReplacementId = intern("removal-replacement-race@example.test", "I922");
        long projectId = transactions.execute(status -> projects.create(
                mentorId,
                new ProjectCreateCommand(
                        "Leader removal race",
                        null,
                        LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 12, 31),
                        outgoingLeaderId)));
        transactions.executeWithoutResult(status -> projects.addMembers(
                mentorId, projectId, java.util.List.of(changeLeaderTargetId, removalReplacementId)));
        long outgoingMembershipId = membershipId(projectId, outgoingLeaderId);

        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch mentorAccountHeld = new CountDownLatch(1);
        CountDownLatch releaseMentorAccount = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Future<?> holder = executor.submit(() -> transactions.executeWithoutResult(status -> {
                jdbc.queryForObject("select id from app_users where id = ? for update", Long.class, mentorId);
                mentorAccountHeld.countDown();
                awaitLatch(releaseMentorAccount, "leader removal race Account release");
            }));
            assertThat(mentorAccountHeld.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> leaderChange = executor.submit(() -> runLeaderRemovalRace(
                    started,
                    successes,
                    conflicts,
                    () -> transactions.executeWithoutResult(status -> projects.changeLeader(
                            mentorId, projectId, changeLeaderTargetId))));
            Future<?> removal = executor.submit(() -> runLeaderRemovalRace(
                    started,
                    successes,
                    conflicts,
                    () -> transactions.executeWithoutResult(status -> projects.directRemoveMember(
                            mentorId, projectId, outgoingMembershipId, removalReplacementId))));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            awaitAccountLockWaiters(2);
            releaseMentorAccount.countDown();
            leaderChange.get(10, TimeUnit.SECONDS);
            removal.get(10, TimeUnit.SECONDS);
            holder.get(10, TimeUnit.SECONDS);
        } finally {
            releaseMentorAccount.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(successes).hasValue(1);
        assertThat(conflicts).hasValue(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from project_leadership_terms where project_id = ? and ended_at is null",
                Integer.class,
                projectId)).isEqualTo(1);
    }

    private void runLeaderRemovalRace(
            CountDownLatch started,
            AtomicInteger successes,
            AtomicInteger conflicts,
            Runnable mutation) {
        started.countDown();
        try {
            mutation.run();
            successes.incrementAndGet();
        } catch (ProjectRuleViolationException expectedConflict) {
            conflicts.incrementAndGet();
        }
    }

    @Test
    void invitationAcceptanceAndMentorDirectAddHaveOneCommittedWinner() throws Exception {
        long mentorId = user("mentor-invitation-race@example.test", "MENTOR");
        long leaderId = intern("leader-invitation-race@example.test", "I915");
        long inviteeId = intern("invitee-invitation-race@example.test", "I916");
        long projectId = transactions.execute(status -> projects.create(
                mentorId,
                new ProjectCreateCommand(
                        "Invitation race",
                        null,
                        LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 12, 31),
                        leaderId)));
        long invitationId = transactions.execute(status -> projects.issueInvitation(
                leaderId, projectId, inviteeId));

        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> acceptance = executor.submit(() -> runInvitationRace(
                    started,
                    release,
                    () -> transactions.executeWithoutResult(status -> projects.respondToInvitation(
                            inviteeId, invitationId, InvitationResponse.ACCEPT))));
            Future<Boolean> directAdd = executor.submit(() -> runInvitationRace(
                    started,
                    release,
                    () -> transactions.executeWithoutResult(status -> projects.addMember(
                            mentorId, projectId, inviteeId))));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            release.countDown();

            assertThat(acceptance.get(10, TimeUnit.SECONDS)
                    ^ directAdd.get(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(jdbc.queryForObject(
                "select count(*) from project_memberships where project_id = ? and intern_user_id = ? and left_at is null",
                Integer.class,
                projectId,
                inviteeId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select status from project_invitations where id = ?",
                String.class,
                invitationId)).isIn("ACCEPTED", "SUPERSEDED");
    }

    @Test
    void concurrentExitApprovalAndTransferLeaveOnlyAReadyOrPendingState() throws Exception {
        long mentorId = user("mentor-exit-race@example.test", "MENTOR");
        long leaderId = intern("leader-exit-race@example.test", "I917");
        long targetId = intern("target-exit-race@example.test", "I918");
        long recipientId = intern("recipient-exit-race@example.test", "I919");
        long projectId = transactions.execute(status -> projects.create(
                mentorId,
                new ProjectCreateCommand(
                        "Exit race",
                        null,
                        LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 12, 31),
                        leaderId)));
        transactions.executeWithoutResult(status -> projects.addMembers(
                mentorId, projectId, java.util.List.of(targetId, recipientId)));
        long leaderMembershipId = membershipId(projectId, leaderId);
        long targetMembershipId = membershipId(projectId, targetId);
        long recipientMembershipId = membershipId(projectId, recipientId);
        long taskId = insertTask(projectId, targetMembershipId, leaderMembershipId, "Exit race task", "TODO");
        long requestId = transactions.execute(status -> projects.requestMemberRemoval(
                leaderId, projectId, targetMembershipId, "Transfer before approval"));

        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> transfer = executor.submit(() -> runExitRace(started, release, () ->
                    transactions.executeWithoutResult(status -> projects.transferTasks(
                            leaderId,
                            projectId,
                            requestId,
                            targetMembershipId,
                            java.util.Set.of(taskId),
                            recipientMembershipId))));
            Future<Boolean> approval = executor.submit(() -> runExitRace(started, release, () ->
                    transactions.executeWithoutResult(status -> projects.approveExit(
                            mentorId, requestId, "Concurrent decision"))));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            release.countDown();

            assertThat(transfer.get(10, TimeUnit.SECONDS)).isTrue();
            boolean approved = approval.get(10, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject(
                    "select assignee_membership_id from tasks where id = ?", Long.class, taskId))
                    .isEqualTo(recipientMembershipId);
            if (approved) {
                assertThat(jdbc.queryForObject(
                        "select status from project_membership_exit_requests where id = ?",
                        String.class,
                        requestId)).isEqualTo("APPROVED");
                assertThat(jdbc.queryForObject(
                        "select left_at is not null from project_memberships where id = ?",
                        Boolean.class,
                        targetMembershipId)).isTrue();
            } else {
                assertThat(jdbc.queryForObject(
                        "select status from project_membership_exit_requests where id = ?",
                        String.class,
                        requestId)).isEqualTo("PENDING");
                assertThat(jdbc.queryForObject(
                        "select left_at is null from project_memberships where id = ?",
                        Boolean.class,
                        targetMembershipId)).isTrue();
            }
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private boolean runExitRace(CountDownLatch started, CountDownLatch release, Runnable mutation) {
        started.countDown();
        awaitLatch(release, "exit race release");
        try {
            mutation.run();
            return true;
        } catch (ProjectRuleViolationException expectedConflict) {
            return false;
        }
    }

    private boolean runInvitationRace(
            CountDownLatch started, CountDownLatch release, Runnable mutation) {
        started.countDown();
        awaitLatch(release, "invitation race release");
        try {
            mutation.run();
            return true;
        } catch (ProjectAccessDeniedException | ProjectRuleViolationException expectedConflict) {
            return false;
        }
    }

    private void runLeadershipRace(
            CountDownLatch started,
            AtomicInteger successes,
            AtomicInteger conflicts,
            long mentorId,
            long projectId,
            long replacementId) {
        started.countDown();
        try {
            transactions.executeWithoutResult(status -> projects.changeLeader(mentorId, projectId, replacementId));
            successes.incrementAndGet();
        } catch (ProjectRuleViolationException expectedConflict) {
            conflicts.incrementAndGet();
        }
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
        awaitAccountLockWaiters(1);
    }

    private void awaitAccountLockWaiters(int expectedWaiters) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject("""
                    select count(*)
                    from pg_stat_activity activity
                    where activity.pid <> pg_backend_pid()
                      and activity.wait_event_type = 'Lock'
                    """, Integer.class);
            if (waiting != null && waiting >= expectedWaiters) {
                return;
            }
            Thread.yield();
        }
        throw new AssertionError("Timed out waiting for the Project mutation to block on Account lock");
    }
}
