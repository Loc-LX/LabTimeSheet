package com.lab.labtimesheet.feature.notification.service;

import com.lab.labtimesheet.feature.internship.service.InternshipService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.time.ZoneId;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.identity.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.identity.service.BootstrapService;
import com.lab.labtimesheet.feature.notification.model.NotificationEmailStatus;
import com.lab.labtimesheet.feature.notification.model.NotificationType;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationAction;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationEvent;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationRecipient;
import com.lab.labtimesheet.feature.notification.model.entity.NotificationEntity;
import com.lab.labtimesheet.feature.notification.repository.NotificationRepository;
import com.lab.labtimesheet.platform.model.GlobalRole;
import com.lab.labtimesheet.platform.model.SecurityMode;
import com.lab.labtimesheet.platform.model.dto.SmtpConnection;
import com.lab.labtimesheet.platform.model.dto.SmtpDraft;
import com.lab.labtimesheet.platform.service.SmtpConfigurationService;
import com.lab.labtimesheet.platform.service.SmtpProbe;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/** PostgreSQL proof for the Platform-owned notification persistence and delivery boundary. */
@Import({TestcontainersConfiguration.class, NotificationServiceIntegrationTest.MailProbeConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class NotificationServiceIntegrationTest {
    @Autowired
    private BootstrapService bootstrap;

    @Autowired
    private AccountService accounts;

    @Autowired
    private InternshipService internships;

    @Autowired
    private SmtpConfigurationService smtp;

    @Autowired
    private NotificationService notifications;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private RecordingSmtpProbe mail;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private java.time.Clock clock;

    @Test
    void unavailableDeliveryPersistsOneDeduplicatedRowAndNeverReplays() {
        bootstrap.bootstrap("notification-admin@example.com", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("notification-admin@example.com");

        publish(
                new NotificationEvent(
                        NotificationType.PROJECT_INVITATION_CREATED, "CREATED", "Project invitation", "Review invite"),
                new NotificationAction("/projects/7/invitation", false),
                List.of(
                        new NotificationRecipient(adminId, "notification-admin@example.com"),
                        new NotificationRecipient(adminId, "notification-admin@example.com")));

        assertThat(countFor(adminId)).isEqualTo(1);
        assertThat(statusFor(adminId)).isEqualTo("UNAVAILABLE");
        assertThat(mail.calls).isZero();

        activateSmtp(adminId);
        mail.reset();
        assertThat(countFor(adminId)).isEqualTo(1);
        assertThat(mail.calls).isZero();
    }

    @Test
    void transientFailureLeavesDomainCommittedAndRetainsPendingRetryState() {
        bootstrap.bootstrap("notification-failure@example.com", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("notification-failure@example.com");
        activateSmtp(adminId);
        mail.reset();
        mail.fail = true;

        publish(
                new NotificationEvent(
                        NotificationType.LEAVE_DECIDED, "REVERTED", "Leave updated", "The leave decision changed"),
                new NotificationAction("/attendance/leave", false),
                List.of(new NotificationRecipient(adminId, "notification-failure@example.com")));

        awaitEmailAttempts(adminId, 1);
        awaitMailCalls(1);

        assertThat(countFor(adminId)).isEqualTo(1);
        assertThat(statusFor(adminId)).isEqualTo("PENDING");
        assertThat(attemptsFor(adminId)).isEqualTo(1);
        assertThat(nextAttemptFor(adminId)).isNotNull();
        assertThat(mail.calls).isEqualTo(1);
        assertThat(mail.lastTransactionActive).isFalse();
    }

    @Test
    void ordinaryEmailRetriesUseTheFiveBoundedDelaysThenBecomeTerminalAndManualRetryReusesTheRow() {
        bootstrap.bootstrap("notification-retry@example.com", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("notification-retry@example.com");
        activateSmtp(adminId);
        mail.reset();
        mail.fail = true;

        publish(
                new NotificationEvent(
                        NotificationType.LEAVE_DECIDED, "APPROVED", "Leave approved", "The leave was approved"),
                new NotificationAction("/attendance/leave", false),
                List.of(new NotificationRecipient(adminId, "notification-retry@example.com")));
        long notificationId = jdbc.queryForObject(
                "select id from notifications where recipient_user_id = ?", Long.class, adminId);
        Instant base = Instant.parse("2026-08-14T00:00:00Z");
        awaitEmailAttempts(adminId, 1);
        assertThat(nextAttemptFor(adminId).toString()).contains("2026-08-14 07:01");
        List<Instant> delays = List.of(
                base.plusSeconds(5 * 60),
                base.plusSeconds(30 * 60),
                base.plusSeconds(2 * 60 * 60),
                base.plusSeconds(12 * 60 * 60));
        for (int retry = 0; retry < delays.size(); retry++) {
            markDue(notificationId, base);
            assertThat(notifications.retryDueEmails()).isEqualTo(1);
            assertThat(attemptsFor(adminId)).isEqualTo(retry + 2);
            assertThat(nextAttemptFor(adminId)).isNotNull();
            String expectedLocal = delays.get(retry).atZone(ZoneId.of("UTC"))
                    .withZoneSameInstant(ZoneId.of("Asia/Ho_Chi_Minh"))
                    .toLocalDateTime()
                    .toString()
                    .substring(0, 16)
                    .replace('T', ' ');
            assertThat(nextAttemptFor(adminId).toString()).contains(expectedLocal);
        }
        markDue(notificationId, base);
        assertThat(notifications.retryDueEmails()).isEqualTo(1);
        assertThat(statusFor(adminId)).isEqualTo("FAILED");
        assertThat(attemptsFor(adminId)).isEqualTo(6);
        assertThat(nextAttemptFor(adminId)).isNull();
        assertThat(countFor(adminId)).isEqualTo(1);

        mail.fail = false;
        assertThat(notifications.retryFailedEmail(notificationId, adminId)).isTrue();
        awaitEmailStatus(adminId, "SENT");
        assertThat(statusFor(adminId)).isEqualTo("SENT");
        assertThat(countFor(adminId)).isEqualTo(1);
    }

    @Test
    void callerCommitPersistsDomainAndNotificationBeforeAfterCommitDelivery() {
        bootstrap.bootstrap("notification-success@example.com", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("notification-success@example.com");
        activateSmtp(adminId);
        mail.reset();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.update("update app_users set display_name = ? where id = ?", "Committed marker", adminId);
            notifications.publish(
                    new NotificationEvent(
                            NotificationType.PROJECT_INVITATION_RESOLVED,
                            "ACCEPTED",
                            "Invitation resolved",
                            "The invitation was accepted"),
                    new NotificationAction("/projects/7/invitation", false),
                    List.of(new NotificationRecipient(adminId, "notification-success@example.com")));
        });

        assertThat(jdbc.queryForObject(
                "select display_name from app_users where id = ?", String.class, adminId))
                .isEqualTo("Committed marker");
        assertThat(countFor(adminId)).isEqualTo(1);
        awaitEmailStatus(adminId, "SENT");
        assertThat(statusFor(adminId)).isEqualTo("SENT");
        assertThat(mail.calls).isEqualTo(1);
        assertThat(mail.lastTransactionActive).isFalse();
    }

    /**
     * Protects {@code NOT-006}, {@code D49}, and email-delivery PLAN Design point 3.
     * Verifies that the delivery lease acquired during initial delivery dispatch prevents an
     * overlapping {@link NotificationService#retryDueEmails()} sweep from attempting or sending
     * a duplicate email, returning 0 attempted messages while delivery is in flight.
     */
    @Test
    void overlappingRetryWorkerSendsNothingWhileTheDeliveryLeaseIsHeld() throws Exception {
        bootstrap.bootstrap("notification-overlap@example.com", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("notification-overlap@example.com");
        activateSmtp(adminId);
        mail.reset();
        mail.blockForOverlap();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> immediate = executor.submit(() -> publish(
                    new NotificationEvent(
                            NotificationType.LEAVE_DECIDED, "APPROVED", "Leave approved", "The leave was approved"),
                    new NotificationAction("/attendance/leave", false),
                    List.of(new NotificationRecipient(adminId, "notification-overlap@example.com"))));
            assertThat(mail.awaitEntered()).isTrue();

            Future<Integer> worker = executor.submit(notifications::retryDueEmails);
            assertThat(worker.get(500, TimeUnit.MILLISECONDS)).isZero();
            assertThat(mail.calls).isEqualTo(1);

            mail.releaseOverlap();
            immediate.get(10, TimeUnit.SECONDS);
            awaitEmailStatus(adminId, "SENT");
            assertThat(worker.get(10, TimeUnit.SECONDS)).isZero();
        } finally {
            mail.releaseOverlap();
            executor.shutdownNow();
        }

        assertThat(mail.calls).isEqualTo(1);
        assertThat(statusFor(adminId)).isEqualTo("SENT");
    }

    @Test
    void oneRecipientFailureDoesNotStarveLaterRecipients() {
        bootstrap.bootstrap("notification-first@example.com", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("notification-first@example.com");
        activateSmtp(adminId);
        long secondId = internships.create(new CreateAccountCommand(
                "notification-second@example.com", "Second recipient", GlobalRole.MENTOR, null, null, null), adminId)
                .userId();
        mail.reset();
        mail.failRecipient = "notification-first@example.com";

        publish(
                new NotificationEvent(
                        NotificationType.MEMBERSHIP_EXIT_RESOLVED,
                        "APPROVED",
                        "Membership exit resolved",
                        "The membership exit was approved"),
                new NotificationAction("/projects/7/members", false),
                List.of(
                        new NotificationRecipient(adminId, "notification-first@example.com"),
                        new NotificationRecipient(secondId, "notification-second@example.com")));

        awaitMailCalls(2);
        awaitEmailStatus(secondId, "SENT");

        assertThat(countFor(adminId)).isEqualTo(1);
        assertThat(countFor(secondId)).isEqualTo(1);
        assertThat(statusFor(adminId)).isEqualTo("PENDING");
        assertThat(statusFor(secondId)).isEqualTo("SENT");
        assertThat(mail.calls).isEqualTo(2);
        assertThat(mail.recipients).containsExactly(
                "notification-first@example.com", "notification-second@example.com");
    }

    @Test
    void projectExitLeaveAndCorrectionFamiliesRetainRequiredTransitions() {
        bootstrap.bootstrap("notification-families@example.com", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("notification-families@example.com");
        List<NotificationEvent> events = List.of(
                new NotificationEvent(
                        NotificationType.PROJECT_INVITATION_CREATED, "CREATED", "Invitation created", "Invite created"),
                new NotificationEvent(
                        NotificationType.PROJECT_INVITATION_RESOLVED, "ACCEPTED", "Invitation resolved", "Invite accepted"),
                new NotificationEvent(
                        NotificationType.MEMBERSHIP_EXIT_REQUESTED, "REQUESTED", "Exit requested", "Exit requested"),
                new NotificationEvent(
                        NotificationType.MEMBERSHIP_EXIT_RESOLVED, "APPROVED", "Exit resolved", "Exit approved"),
                new NotificationEvent(
                        NotificationType.LEAVE_SUBMITTED, "SUBMITTED", "Leave submitted", "Leave submitted"),
                new NotificationEvent(
                        NotificationType.LEAVE_DECIDED, "REVERTED", "Leave reverted", "Leave reverted"),
                new NotificationEvent(
                        NotificationType.LEAVE_DECIDED, "AUTO_REJECTED", "Leave auto-rejected", "Leave auto-rejected"),
                new NotificationEvent(
                        NotificationType.CORRECTION_SUBMITTED, "SUBMITTED", "Correction submitted", "Correction submitted"),
                new NotificationEvent(
                        NotificationType.CORRECTION_DECIDED, "REVERTED", "Correction reverted", "Correction reverted"),
                new NotificationEvent(
                        NotificationType.CORRECTION_DECIDED,
                        "AUTO_REJECTED",
                        "Correction auto-rejected",
                        "Correction auto-rejected"));

        for (NotificationEvent event : events) {
            publish(
                    event,
                    new NotificationAction("/attendance/requests", false),
                    List.of(new NotificationRecipient(adminId, "notification-families@example.com")));
        }

        assertThat(countFor(adminId)).isEqualTo(events.size());
        assertThat(jdbc.queryForList(
                "select notification_type from notifications where recipient_user_id = ? order by id",
                String.class, adminId)).containsExactly(
                        "PROJECT_INVITATION_CREATED", "PROJECT_INVITATION_RESOLVED",
                        "MEMBERSHIP_EXIT_REQUESTED", "MEMBERSHIP_EXIT_RESOLVED",
                        "LEAVE_SUBMITTED", "LEAVE_DECIDED", "LEAVE_DECIDED",
                        "CORRECTION_SUBMITTED", "CORRECTION_DECIDED", "CORRECTION_DECIDED");
        assertThat(jdbc.queryForList(
                "select body from notifications where recipient_user_id = ? order by id", String.class, adminId))
                .allMatch(body -> body.contains("Transition:"));
        assertThat(jdbc.queryForObject(
                "select count(*) from notifications where recipient_user_id = ? and email_status = 'UNAVAILABLE'",
                Integer.class, adminId)).isEqualTo(events.size());
        assertThat(mail.calls).isZero();
    }

    @Test
    void inAppOnlyAndSelfTaskActionsDoNotRequestOrdinaryEmail() {
        bootstrap.bootstrap("notification-in-app@example.com", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("notification-in-app@example.com");

        publish(
                new NotificationEvent(
                        NotificationType.TASK_COMMENTED, "COMMENTED", "Task comment", "A Task was commented"),
                new NotificationAction("/tasks/7", false),
                List.of(new NotificationRecipient(adminId, "notification-in-app@example.com")));
        publish(
                new NotificationEvent(
                        NotificationType.TASK_ASSIGNED, "SELF_TASK", "Task created", "Your self-assigned Task"),
                new NotificationAction("/tasks/8", true),
                List.of(new NotificationRecipient(adminId, "notification-in-app@example.com")));

        assertThat(countFor(adminId)).isEqualTo(1);
        assertThat(statusFor(adminId)).isEqualTo("NOT_REQUIRED");
    }

    @Test
    void publicationRequiresCallerTransactionAndRollsBackWithDomainMarker() {
        bootstrap.bootstrap("notification-transaction@example.com", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("notification-transaction@example.com");
        activateSmtp(adminId);
        mail.reset();

        assertThatThrownBy(() -> notifications.publish(
                new NotificationEvent(
                        NotificationType.PROJECT_INVITATION_CREATED,
                        "CREATED",
                        "Project invitation",
                        "Review invite"),
                new NotificationAction("/projects/7/invitation", false),
                List.of(new NotificationRecipient(adminId, "notification-transaction@example.com"))))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(countFor(adminId)).isZero();
        assertThat(mail.calls).isZero();

        String originalDisplayName = jdbc.queryForObject(
                "select display_name from app_users where id = ?", String.class, adminId);
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.update("update app_users set display_name = ? where id = ?", "Rolled back", adminId);
            notifications.publish(
                    new NotificationEvent(
                            NotificationType.PROJECT_INVITATION_CREATED,
                            "CREATED",
                            "Project invitation",
                            "Review invite"),
                    new NotificationAction("/projects/7/invitation", false),
                    List.of(new NotificationRecipient(adminId, "notification-transaction@example.com")));
            throw new IllegalStateException("rollback domain marker");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject(
                "select display_name from app_users where id = ?", String.class, adminId))
                .isEqualTo(originalDisplayName);
        assertThat(countFor(adminId)).isZero();
        assertThat(mail.calls).isZero();
    }

    @Test
    void designatedEmailCannotBeSuppressedAndSelfTaskMarkerHasExactTaskShape() {
        bootstrap.bootstrap("notification-designation@example.com", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("notification-designation@example.com");
        activateSmtp(adminId);
        mail.reset();

        publish(
                new NotificationEvent(
                        NotificationType.PROJECT_INVITATION_CREATED,
                        "CREATED",
                        "Project invitation",
                        "Review invite"),
                new NotificationAction("/projects/7/invitation", false),
                List.of(new NotificationRecipient(adminId, "notification-designation@example.com")));
        awaitEmailStatus(adminId, "SENT");
        assertThat(statusFor(adminId)).isEqualTo("SENT");
        assertThat(mail.calls).isEqualTo(1);

        publish(
                new NotificationEvent(NotificationType.TASK_COMMENTED, "COMMENTED", "Task comment", "Comment"),
                new NotificationAction("/tasks/7", false),
                List.of(new NotificationRecipient(adminId, "notification-designation@example.com")));
        assertThat(countFor(adminId)).isEqualTo(2);
        assertThat(statusFor(adminId)).isEqualTo("NOT_REQUIRED");
        assertThat(mail.calls).isEqualTo(1);

        publish(
                new NotificationEvent(
                        NotificationType.TASK_ASSIGNED,
                        "SELF_ASSIGNED",
                        "Task created",
                        "Your self-assigned Task"),
                new NotificationAction("/tasks/8", true),
                List.of(new NotificationRecipient(adminId, "notification-designation@example.com")));
        assertThat(countFor(adminId)).isEqualTo(2);
        assertThat(mail.calls).isEqualTo(1);

        assertThatThrownBy(() -> publish(
                new NotificationEvent(NotificationType.TASK_COMMENTED, "SELF_ASSIGNED", "Task", "Task"),
                new NotificationAction("/tasks/9", true),
                List.of(new NotificationRecipient(adminId, "notification-designation@example.com"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> publish(
                new NotificationEvent(
                        NotificationType.PROJECT_INVITATION_CREATED, "SELF_ASSIGNED", "Invite", "Invite"),
                new NotificationAction("/projects/9", true),
                List.of(new NotificationRecipient(adminId, "notification-designation@example.com"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> publish(
                new NotificationEvent(NotificationType.TASK_ASSIGNED, "ASSIGNED", "Task", "Task"),
                new NotificationAction("/tasks/10", true),
                List.of(new NotificationRecipient(adminId, "notification-designation@example.com"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * AC-NOT-007 / NOT-012, NOT-004, NOT-005, NOT-006, NOT-007: Complete lifecycle state and payload matrix.
     *
     * <p>Protects the 5-state notification delivery lifecycle from publication through Admin retry and
     * terminal transitions:
     * <ul>
     *   <li>(a) When SMTP is inactive, email-designated event C commits with state {@code UNAVAILABLE} and no delivery payload.</li>
     *   <li>(b) When SMTP is active and mail succeeds, email-designated event A commits with state {@code SENT}, non-null {@code email_sent_at}, and payload; non-designated event B commits with state {@code NOT_REQUIRED} and no payload.</li>
     *   <li>(c) When SMTP fails, email-designated event D begins in state {@code PENDING} with payload and {@code email_next_attempt_at}, progresses through 5 retries (6 attempts total) per NOT-006, and reaches terminal {@code FAILED} with null next attempt.</li>
     *   <li>(d) SMTP activation does not retroactively send or alter existing {@code UNAVAILABLE} row C (NOT-005).</li>
     *   <li>(e) Admin manual retry succeeds only for {@code FAILED} row D (re-entering {@code PENDING}) and returns {@code false} without mutating {@code SENT} A, {@code NOT_REQUIRED} B, or {@code UNAVAILABLE} C.</li>
     *   <li>(f) Attempting to leave terminal states {@code SENT}, {@code NOT_REQUIRED}, and {@code UNAVAILABLE} via due worker and entity mutators is refused: {@code markSent} and {@code retainPendingRetry} are no-ops; {@code requeueFailedEmail} throws {@link IllegalStateException}.</li>
     *   <li>(g) The in-app notification rows, their titles, bodies, and total count remain invariant throughout.</li>
     * </ul>
     *
     * <p>Observable break: If UNAVAILABLE rows are retroactively sent, terminal states allow transitions,
     * or non-FAILED rows are retried, delivery invariants are breached.
     * Hand-derived expected values: A=SENT (sent_at not null, payload non-null), B=NOT_REQUIRED (no payload),
     * C=UNAVAILABLE (no payload), D starts PENDING (attempt 1) -> FAILED (attempt 6) -> PENDING upon retry;
     * retry on A, B, C returns false; terminal mutations rejected; row count remains 4.
     */
    @Test
    void acNot007StateMatrixFromCreationThroughAdminRetryAndTerminalRefusals() {
        bootstrap.bootstrap("notification-matrix@example.com", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("notification-matrix@example.com");

        // a. Before active SMTP: publish notification C (email required). Verify: UNAVAILABLE, no payload.
        publish(
                new NotificationEvent(
                        NotificationType.PROJECT_INVITATION_CREATED, "CREATED", "Matrix Notification C", "Body of C"),
                new NotificationAction("/projects/7/invitation", false),
                List.of(new NotificationRecipient(adminId, "notification-matrix@example.com")));

        long idC = jdbc.queryForObject(
                "select id from notifications where recipient_user_id = ? and title = ?",
                Long.class, adminId, "Matrix Notification C");
        Map<String, Object> rowC = jdbc.queryForMap(
                "select email_status, email_to, email_subject, email_body, email_attempts, email_next_attempt_at, email_sent_at, title, body from notifications where id = ?",
                idC);
        String titleC = (String) rowC.get("title");
        String bodyC = (String) rowC.get("body");
        assertThat(rowC.get("email_status")).isEqualTo("UNAVAILABLE");
        assertThat(rowC.get("email_to")).isNull();
        assertThat(rowC.get("email_subject")).isNull();
        assertThat(rowC.get("email_body")).isNull();
        assertThat(rowC.get("email_next_attempt_at")).isNull();
        assertThat(rowC.get("email_sent_at")).isNull();
        assertThat(mail.calls).isZero();

        // b. Enable SMTP with fake mail SUCCESS: publish A (email required); verify SENT with email_sent_at.
        //    Publish B (no email required, e.g. TASK_STATUS_CHANGED); verify NOT_REQUIRED, no payload.
        activateSmtp(adminId);
        mail.reset();
        mail.fail = false;

        publish(
                new NotificationEvent(
                        NotificationType.LEAVE_DECIDED, "APPROVED", "Matrix Notification A", "Body of A"),
                new NotificationAction("/attendance/leave", false),
                List.of(new NotificationRecipient(adminId, "notification-matrix@example.com")));

        long idA = jdbc.queryForObject(
                "select id from notifications where recipient_user_id = ? and title = ?",
                Long.class, adminId, "Matrix Notification A");
        awaitEmailStatus(adminId, "SENT");
        Map<String, Object> rowA = jdbc.queryForMap(
                "select email_status, email_to, email_subject, email_body, email_attempts, email_next_attempt_at, email_sent_at, title, body from notifications where id = ?",
                idA);
        String titleA = (String) rowA.get("title");
        String bodyA = (String) rowA.get("body");
        assertThat(rowA.get("email_status")).isEqualTo("SENT");
        assertThat(rowA.get("email_sent_at")).isNotNull();
        assertThat(rowA.get("email_to")).isEqualTo("notification-matrix@example.com");
        assertThat(rowA.get("email_subject")).isEqualTo("Matrix Notification A");
        assertThat(rowA.get("email_body")).isNotNull();
        assertThat(mail.calls).isEqualTo(1);

        publish(
                new NotificationEvent(
                        NotificationType.TASK_STATUS_CHANGED, "IN_PROGRESS", "Matrix Notification B", "Body of B"),
                new NotificationAction("/tasks/1", false),
                List.of(new NotificationRecipient(adminId, "notification-matrix@example.com")));

        long idB = jdbc.queryForObject(
                "select id from notifications where recipient_user_id = ? and title = ?",
                Long.class, adminId, "Matrix Notification B");
        Map<String, Object> rowB = jdbc.queryForMap(
                "select email_status, email_to, email_subject, email_body, email_attempts, email_next_attempt_at, email_sent_at, title, body from notifications where id = ?",
                idB);
        String titleB = (String) rowB.get("title");
        String bodyB = (String) rowB.get("body");
        assertThat(rowB.get("email_status")).isEqualTo("NOT_REQUIRED");
        assertThat(rowB.get("email_to")).isNull();
        assertThat(rowB.get("email_subject")).isNull();
        assertThat(rowB.get("email_body")).isNull();
        assertThat(rowB.get("email_next_attempt_at")).isNull();
        assertThat(rowB.get("email_sent_at")).isNull();
        assertThat(mail.calls).isEqualTo(1);

        // c. Set fake mail to FAILURE: publish D. Right after first attempt, verify D is PENDING with payload and email_next_attempt_at.
        //    Then exhaust retries per NOT-006 by advancing clock via existing helper. Verify D becomes FAILED after exactly 6 attempts.
        mail.fail = true;
        publish(
                new NotificationEvent(
                        NotificationType.MEMBERSHIP_EXIT_RESOLVED, "APPROVED", "Matrix Notification D", "Body of D"),
                new NotificationAction("/projects/7/members", false),
                List.of(new NotificationRecipient(adminId, "notification-matrix@example.com")));

        long idD = jdbc.queryForObject(
                "select id from notifications where recipient_user_id = ? and title = ?",
                Long.class, adminId, "Matrix Notification D");
        awaitEmailAttempts(adminId, 1);
        Map<String, Object> rowD = jdbc.queryForMap(
                "select email_status, email_to, email_subject, email_body, email_attempts, email_next_attempt_at, email_sent_at, title, body from notifications where id = ?",
                idD);
        String titleD = (String) rowD.get("title");
        String bodyD = (String) rowD.get("body");
        assertThat(rowD.get("email_status")).isEqualTo("PENDING");
        assertThat(rowD.get("email_to")).isEqualTo("notification-matrix@example.com");
        assertThat(rowD.get("email_subject")).isEqualTo("Matrix Notification D");
        assertThat(rowD.get("email_body")).isNotNull();
        assertThat(rowD.get("email_next_attempt_at")).isNotNull();
        assertThat(((Number) rowD.get("email_attempts")).intValue()).isEqualTo(1);

        Instant base = Instant.parse("2026-08-14T00:00:00Z");
        for (int retry = 0; retry < 4; retry++) {
            markDue(idD, base);
            assertThat(notifications.retryDueEmails()).isEqualTo(1);
            assertThat(jdbc.queryForObject("select email_attempts from notifications where id = ?", Integer.class, idD))
                    .isEqualTo(retry + 2);
            assertThat(jdbc.queryForObject("select email_status from notifications where id = ?", String.class, idD))
                    .isEqualTo("PENDING");
            assertThat(jdbc.queryForObject("select email_next_attempt_at from notifications where id = ?", Object.class, idD))
                    .isNotNull();
            assertInboxContent(idD, titleD, bodyD);
        }
        markDue(idD, base);
        assertThat(notifications.retryDueEmails()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select email_status from notifications where id = ?", String.class, idD))
                .isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select email_attempts from notifications where id = ?", Integer.class, idD))
                .isEqualTo(6);
        assertThat(jdbc.queryForObject("select email_next_attempt_at from notifications where id = ?", Object.class, idD))
                .isNull();
        assertInboxContent(idD, titleD, bodyD);

        // d. Verify C remains UNAVAILABLE: enabling SMTP does not alter existing row states.
        Map<String, Object> rowCAfter = jdbc.queryForMap(
                "select email_status, email_to, email_subject, email_body, email_attempts, email_next_attempt_at, email_sent_at from notifications where id = ?",
                idC);
        assertThat(rowCAfter.get("email_status")).isEqualTo("UNAVAILABLE");
        assertThat(rowCAfter.get("email_to")).isNull();
        assertThat(rowCAfter.get("email_subject")).isNull();
        assertThat(rowCAfter.get("email_body")).isNull();
        assertThat(rowCAfter.get("email_next_attempt_at")).isNull();
        assertThat(rowCAfter.get("email_sent_at")).isNull();
        assertInboxContent(idC, titleC, bodyC);

        // e. Admin calls retryFailedEmail sequentially on A, B, C, D while fake still fails. Only D returns true and returns to PENDING; A, B, C return false and remain unchanged.
        Map<String, Object> rowABeforeManualRetry = deliveryAndInboxState(idA);
        Map<String, Object> rowBBeforeManualRetry = deliveryAndInboxState(idB);
        Map<String, Object> rowCBeforeManualRetry = deliveryAndInboxState(idC);
        assertThat(notifications.retryFailedEmail(idA, adminId)).isFalse();
        assertThat(jdbc.queryForObject("select email_status from notifications where id = ?", String.class, idA)).isEqualTo("SENT");

        assertThat(notifications.retryFailedEmail(idB, adminId)).isFalse();
        assertThat(jdbc.queryForObject("select email_status from notifications where id = ?", String.class, idB)).isEqualTo("NOT_REQUIRED");

        assertThat(notifications.retryFailedEmail(idC, adminId)).isFalse();
        assertThat(jdbc.queryForObject("select email_status from notifications where id = ?", String.class, idC)).isEqualTo("UNAVAILABLE");

        assertThat(notifications.retryFailedEmail(idD, adminId)).isTrue();
        awaitEmailAttempts(adminId, 1);
        assertThat(jdbc.queryForObject("select email_status from notifications where id = ?", String.class, idD)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select email_attempts from notifications where id = ?", Integer.class, idD)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select email_next_attempt_at from notifications where id = ?", Object.class, idD)).isNotNull();
        assertThat(deliveryAndInboxState(idA)).isEqualTo(rowABeforeManualRetry);
        assertThat(deliveryAndInboxState(idB)).isEqualTo(rowBBeforeManualRetry);
        assertThat(deliveryAndInboxState(idC)).isEqualTo(rowCBeforeManualRetry);
        assertInboxContent(idA, titleA, bodyA);
        assertInboxContent(idB, titleB, bodyB);
        assertInboxContent(idC, titleC, bodyC);
        assertInboxContent(idD, titleD, bodyD);
        assertThat(countFor(adminId)).isEqualTo(4);

        // f. Attempt transition out of terminal states: advance clock far ahead and call retryDueEmails(); then in one transaction, call markSent, retainPendingRetry, and requeueFailedEmail directly on entities A, B, and C. Verify status of all three rows is unchanged (requeueFailedEmail must throw IllegalStateException).
        markDue(idD, Instant.parse("2020-01-01T00:00:00Z"));
        notifications.retryDueEmails();

        assertThat(jdbc.queryForObject("select email_status from notifications where id = ?", String.class, idA)).isEqualTo("SENT");
        assertThat(jdbc.queryForObject("select email_status from notifications where id = ?", String.class, idB)).isEqualTo("NOT_REQUIRED");
        assertThat(jdbc.queryForObject("select email_status from notifications where id = ?", String.class, idC)).isEqualTo("UNAVAILABLE");
        assertThat(deliveryAndInboxState(idA)).isEqualTo(rowABeforeManualRetry);
        assertThat(deliveryAndInboxState(idB)).isEqualTo(rowBBeforeManualRetry);
        assertThat(deliveryAndInboxState(idC)).isEqualTo(rowCBeforeManualRetry);
        assertInboxContent(idA, titleA, bodyA);
        assertInboxContent(idB, titleB, bodyB);
        assertInboxContent(idC, titleC, bodyC);
        assertInboxContent(idD, titleD, bodyD);
        assertThat(countFor(adminId)).isEqualTo(4);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant now = Instant.now();
            for (long id : List.of(idA, idB, idC)) {
                NotificationEntity entity = notificationRepository.findById(id).orElseThrow();
                NotificationEmailStatus currentStatus = entity.getEmailStatus();

                entity.markSent(now);
                assertThat(entity.getEmailStatus()).isEqualTo(currentStatus);

                entity.retainPendingRetry(now, now.plusSeconds(300), "terminal refusal check");
                assertThat(entity.getEmailStatus()).isEqualTo(currentStatus);

                assertThatThrownBy(() -> entity.requeueFailedEmail(now))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("Only a failed email can be retried");
                assertThat(entity.getEmailStatus()).isEqualTo(currentStatus);
            }
        });

        assertThat(jdbc.queryForObject("select email_status from notifications where id = ?", String.class, idA)).isEqualTo("SENT");
        assertThat(jdbc.queryForObject("select email_status from notifications where id = ?", String.class, idB)).isEqualTo("NOT_REQUIRED");
        assertThat(jdbc.queryForObject("select email_status from notifications where id = ?", String.class, idC)).isEqualTo("UNAVAILABLE");
        assertInboxContent(idA, titleA, bodyA);
        assertInboxContent(idB, titleB, bodyB);
        assertInboxContent(idC, titleC, bodyC);
        assertInboxContent(idD, titleD, bodyD);

        // g. Throughout: notification row count and in-app content (title, body) for A, B, C, D remain unchanged.
        assertThat(jdbc.queryForObject("select count(*) from notifications where recipient_user_id = ?", Integer.class, adminId))
                .isEqualTo(4);

        assertInboxContent(idA, titleA, bodyA);
        assertInboxContent(idB, titleB, bodyB);
        assertInboxContent(idC, titleC, bodyC);
        assertInboxContent(idD, titleD, bodyD);
    }

    /**
     * Protects {@code NOT-006} and the 5-minute lease requirement.
     * When two concurrent delivery attempts race for the same due PENDING notification,
     * atomic lease acquisition ensures exactly one worker delivers the email and the other
     * aborts without sending a duplicate.
     */
    @Test
    void concurrencyLeaseContentionEnsuresSingleDelivery() throws Exception {
        bootstrap.bootstrap("notification-admin@example.com", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("notification-admin@example.com");
        activateSmtp(adminId);
        mail.reset();

        Instant now = clock.instant();
        long notifId = jdbc.queryForObject(
                """
                insert into notifications (recipient_user_id, notification_type, title, body, action_url,
                    email_status, email_to, email_subject, email_body, email_attempts, email_next_attempt_at,
                    created_at, updated_at, version)
                values (?, 'PROJECT_INVITATION_CREATED', 'Lease Test', 'Lease Test Body', '/projects/1',
                    'PENDING', 'lease-recipient@example.com', 'Lease Subject', 'Lease Body', 0, ?,
                    ?, ?, 0)
                returning id
                """,
                Long.class, adminId, Timestamp.from(now.minusSeconds(10)), Timestamp.from(now), Timestamp.from(now));

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 2; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        notifications.deliver(notifId);
                    } catch (Exception ignored) {
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertThat(doneLatch.await(5, TimeUnit.SECONDS)).isTrue();

            assertThat(mail.calls).isEqualTo(1);
            awaitEmailStatus(adminId, "SENT");
            assertThat(statusFor(adminId)).isEqualTo("SENT");
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Protects {@code NOT-006} and {@code D49}.
     *
     * <p>A PENDING notification due at {@code T} acquires a real 5-minute delivery lease via
     * {@link NotificationRepository#acquireEmailDeliveryLease(long, Instant, Instant)} with
     * {@code now = T}, advancing {@code email_next_attempt_at} to {@code T + 5 min} with update
     * count 1 without sending, simulating an application stop/crash immediately after lease acquisition.
     *
     * <p>When the service clock is set to {@code T + 4 min} (within the active lease window),
     * {@link NotificationService#retryDueEmails()} returns 0 and sends no mail.
     * When the service clock advances to {@code T + 5 min + 1 s} (after lease expiry),
     * {@code retryDueEmails()} dispatches exactly 1 message, returning 1 and transitioning the
     * notification row to {@code SENT}.
     *
     * <p>Observable break: if the lease boundary is not respected, a concurrent or restarted worker
     * could deliver the same message twice. If the row is never retried after expiry, crash recovery
     * is broken.
     *
     * <p>Hand-derived expected values: update count = 1 and {@code email_next_attempt_at = T + 5 min};
     * {@code retryDueEmails()} with Clock = T + 4 min returns 0; {@code retryDueEmails()} with
     * Clock = T + 5 min + 1 s returns 1 and status becomes {@code SENT}.
     */
    @Test
    void pendingNotificationWithLiveLeaseIsNotRetriedUntilLeaseExpires() {
        bootstrap.bootstrap("notification-lease-restart@example.com", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("notification-lease-restart@example.com");
        activateSmtp(adminId);
        mail.reset();

        // T = the fixed test-clock instant (Clock.fixed from TestcontainersConfiguration).
        Instant T = clock.instant();

        // Insert a PENDING row due at T (email_next_attempt_at = T).
        long notifId = jdbc.queryForObject(
                """
                insert into notifications (recipient_user_id, notification_type, title, body, action_url,
                    email_status, email_to, email_subject, email_body, email_attempts, email_next_attempt_at,
                    created_at, updated_at, version)
                values (?, 'LEAVE_DECIDED', 'Lease Restart Test', 'Restart Body', '/attendance/leave',
                    'PENDING', 'notification-lease-restart@example.com', 'Restart Subject', 'Restart Body',
                    0, ?,
                    ?, ?, 0)
                returning id
                """,
                Long.class,
                adminId,
                Timestamp.from(T),
                Timestamp.from(T),
                Timestamp.from(T));

        // Real lease acquisition step with now = T (advancing email_next_attempt_at to T + 5 min),
        // simulating the application stopping immediately after acquiring the lease without sending.
        int acquired = new TransactionTemplate(transactionManager).execute(
                status -> notificationRepository.acquireEmailDeliveryLease(
                        notifId, T, T.plus(Duration.ofMinutes(5))));

        assertThat(acquired).isEqualTo(1);
        Timestamp expectedNextAttempt = Timestamp.from(T.plus(Duration.ofMinutes(5)));
        assertThat(jdbc.queryForObject(
                "select email_next_attempt_at from notifications where id = ?", Timestamp.class, notifId))
                .isEqualTo(expectedNextAttempt);

        try {
            // With Clock = T + 4 min: retryDueEmails sends 0 emails (within lease).
            ReflectionTestUtils.setField(
                    notifications, "clock", Clock.fixed(T.plus(Duration.ofMinutes(4)), ZoneId.of("Asia/Ho_Chi_Minh")));
            assertThat(notifications.retryDueEmails()).isZero();
            assertThat(mail.calls).isZero();
            assertThat(statusFor(adminId)).isEqualTo("PENDING");

            // With Clock = T + 5 min + 1 s: retryDueEmails sends exactly 1 email and row becomes SENT.
            ReflectionTestUtils.setField(
                    notifications,
                    "clock",
                    Clock.fixed(T.plus(Duration.ofMinutes(5)).plusSeconds(1), ZoneId.of("Asia/Ho_Chi_Minh")));
            assertThat(notifications.retryDueEmails()).isEqualTo(1);
            awaitEmailStatus(adminId, "SENT");
            assertThat(statusFor(adminId)).isEqualTo("SENT");
            assertThat(mail.calls).isEqualTo(1);
        } finally {
            ReflectionTestUtils.setField(notifications, "clock", clock);
        }
    }

    private void assertInboxContent(long notificationId, String expectedTitle, String expectedBody) {
        assertThat(jdbc.queryForObject("select title from notifications where id = ?", String.class, notificationId))
                .isEqualTo(expectedTitle);
        assertThat(jdbc.queryForObject("select body from notifications where id = ?", String.class, notificationId))
                .isEqualTo(expectedBody);
    }

    private Map<String, Object> deliveryAndInboxState(long notificationId) {
        return jdbc.queryForMap(
                "select email_status, email_to, email_subject, email_body, email_attempts, email_next_attempt_at, email_sent_at, title, body from notifications where id = ?",
                notificationId);
    }

    private int publish(
            NotificationEvent event, NotificationAction action, List<NotificationRecipient> recipients) {
        Integer result = new TransactionTemplate(transactionManager)
                .execute(status -> notifications.publish(event, action, recipients));
        return result == null ? 0 : result;
    }

    private void activateSmtp(long adminId) {
        long draftId = smtp.saveDraft(adminId,
                new SmtpDraft("mailpit", 1025, SecurityMode.NONE, null, null,
                        "notification-admin@example.com", "Lab Timesheet"));
        smtp.testDraft(draftId, adminId, "notification-admin@example.com");
        smtp.activate(draftId, adminId);
    }

    private int countFor(long recipientId) {
        return jdbc.queryForObject(
                "select count(*) from notifications where recipient_user_id = ?", Integer.class, recipientId);
    }

    private String statusFor(long recipientId) {
        return jdbc.queryForObject(
                "select email_status from notifications where recipient_user_id = ? order by id desc limit 1",
                String.class, recipientId);
    }

    private int attemptsFor(long recipientId) {
        return jdbc.queryForObject(
                "select email_attempts from notifications where recipient_user_id = ? order by id desc limit 1",
                Integer.class, recipientId);
    }

    private Object nextAttemptFor(long recipientId) {
        return jdbc.queryForObject(
                "select email_next_attempt_at from notifications where recipient_user_id = ? order by id desc limit 1",
                Object.class, recipientId);
    }

    private void markDue(long notificationId, Instant now) {
        jdbc.update("update notifications set email_next_attempt_at = ? where id = ?", Timestamp.from(now), notificationId);
    }

    private void awaitEmailAttempts(long recipientId, int expectedAttempts) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (attemptsFor(recipientId) >= expectedAttempts) {
                    return;
                }
            } catch (Exception ignored) {
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }

    private void awaitEmailStatus(long recipientId, String expectedStatus) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (expectedStatus.equals(statusFor(recipientId))) {
                    return;
                }
            } catch (Exception ignored) {
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }

    private void awaitMailCalls(int expectedCalls) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (mail.calls >= expectedCalls) {
                return;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
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
        private final Object lock = new Object();
        private volatile boolean fail;
        private volatile String failRecipient;
        private volatile int calls;
        private volatile boolean lastTransactionActive;
        private volatile boolean blockForOverlap;
        private volatile CountDownLatch entered = new CountDownLatch(0);
        private volatile CountDownLatch release = new CountDownLatch(0);
        private final java.util.concurrent.CopyOnWriteArrayList<String> recipients =
                new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public void send(SmtpConnection connection, String recipient, String subject, String body) {
            synchronized (lock) {
                calls++;
                recipients.add(recipient);
            }
            lastTransactionActive = TransactionSynchronizationManager.isActualTransactionActive();
            if (fail || recipient.equals(failRecipient)) {
                throw new IllegalStateException("simulated SMTP failure");
            }
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
            synchronized (lock) {
                calls = 0;
                recipients.clear();
            }
            fail = false;
            failRecipient = null;
            lastTransactionActive = false;
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
