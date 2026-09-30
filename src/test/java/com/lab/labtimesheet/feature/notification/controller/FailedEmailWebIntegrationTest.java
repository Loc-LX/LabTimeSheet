package com.lab.labtimesheet.feature.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.identity.service.BootstrapService;
import com.lab.labtimesheet.feature.notification.service.NotificationService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full Spring context web integration test for the Admin failed-email inspection and manual-retry flow.
 *
 * <p>Protects NOT-007, AC-NOT-002, AC-NOT-007, and the platform permission matrix:
 * <ul>
 *   <li>Admin GET /admin/notifications/failed-email inspects only FAILED ordinary emails and shows recipient, title, attempts, and last error.</li>
 *   <li>Unauthorized actors (Mentor, Intern) receive 403 Forbidden; unauthenticated requests redirect to /login; requests missing CSRF receive 403; none mutate any rows.</li>
 *   <li>Authorized retry re-enters bounded retry state without creating duplicate in-app rows, flashing {@code Email retry started.}.</li>
 *   <li>Retry on non-failed or non-existent rows is refused and flashes {@code This email is no longer in a failed state.}.</li>
 * </ul>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FailedEmailWebIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@example.test";
    private static final String MENTOR_EMAIL = "mentor@example.test";
    private static final String INTERN_EMAIL = "intern@example.test";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private BootstrapService bootstrap;

    @Autowired
    private AccountService accounts;

    @Autowired
    private NotificationService notifications;

    @Autowired
    private JdbcTemplate jdbc;

    private long adminUserId;
    private long mentorUserId;
    private long internUserId;
    private long failedNotificationId;
    private long pendingNotificationId;
    private long sentNotificationId;
    private long unavailableNotificationId;
    private long notRequiredNotificationId;

    @BeforeEach
    void setUpDatabase() {
        bootstrap.bootstrap(ADMIN_EMAIL, "Admin User", "AdminPass!2026");
        adminUserId = accounts.requireActiveAdminId(ADMIN_EMAIL);

        mentorUserId = jdbc.queryForObject(
                "INSERT INTO app_users (email, display_name, global_role, account_status, password_hash, activated_at, created_at, updated_at, version) "
                        + "VALUES (?, 'Mentor User', 'MENTOR', 'ACTIVE', '{noop}pass', NOW(), NOW(), NOW(), 0) RETURNING id",
                Long.class, MENTOR_EMAIL);

        internUserId = jdbc.queryForObject(
                "INSERT INTO app_users (email, display_name, global_role, account_status, password_hash, activated_at, created_at, updated_at, version) "
                        + "VALUES (?, 'Intern User', 'INTERN', 'ACTIVE', '{noop}pass', NOW(), NOW(), NOW(), 0) RETURNING id",
                Long.class, INTERN_EMAIL);
        jdbc.update(
                "INSERT INTO intern_profiles (user_id, student_code, internship_start_date, internship_end_date, internship_status, activated_at, created_at, updated_at, version) "
                        + "VALUES (?, 'STU-FE-001', '2026-08-01', '2026-12-31', 'ACTIVE', NOW(), NOW(), NOW(), 0)",
                internUserId);

        Instant now = Instant.now();

        // 1. FAILED row (NOT-012, V8: email_to, email_subject, email_body required; attempts 6; last error present)
        failedNotificationId = jdbc.queryForObject(
                "INSERT INTO notifications (recipient_user_id, notification_type, title, body, action_url, email_status, "
                        + "email_to, email_subject, email_body, email_attempts, email_last_error, created_at, updated_at, version) "
                        + "VALUES (?, 'PROJECT_INVITATION_CREATED', 'Project invitation for Intern', 'You have been invited', '/projects/1', "
                        + "'FAILED', 'failed-intern@example.test', 'Project Invitation', 'Full body text', 6, 'ConnectException', ?, ?, 0) "
                        + "RETURNING id",
                Long.class, internUserId, Timestamp.from(now), Timestamp.from(now));

        // 2. PENDING row (NOT-012, V8: payload required; next_attempt_at required by ck_notifications_email_retry)
        pendingNotificationId = jdbc.queryForObject(
                "INSERT INTO notifications (recipient_user_id, notification_type, title, body, action_url, email_status, "
                        + "email_to, email_subject, email_body, email_attempts, email_next_attempt_at, created_at, updated_at, version) "
                        + "VALUES (?, 'TASK_COMMENTED', 'Pending Task update', 'Task needs attention', '/tasks/1', "
                        + "'PENDING', 'pending-intern@example.test', 'Task update', 'Full body text', 1, ?, ?, ?, 0) "
                        + "RETURNING id",
                Long.class, internUserId, Timestamp.from(now.plusSeconds(300)), Timestamp.from(now), Timestamp.from(now));

        // 3. SENT row (NOT-012, V8: payload required; sent_at required by ck_notifications_email_sent)
        sentNotificationId = jdbc.queryForObject(
                "INSERT INTO notifications (recipient_user_id, notification_type, title, body, action_url, email_status, "
                        + "email_to, email_subject, email_body, email_attempts, email_sent_at, created_at, updated_at, version) "
                        + "VALUES (?, 'LEAVE_DECIDED', 'Sent Leave approval', 'Leave was approved', '/leave/1', "
                        + "'SENT', 'sent-intern@example.test', 'Leave approval', 'Full body text', 1, ?, ?, ?, 0) "
                        + "RETURNING id",
                Long.class, internUserId, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));

        // 4. UNAVAILABLE row (NOT-012, V8: payload MUST be null)
        unavailableNotificationId = jdbc.queryForObject(
                "INSERT INTO notifications (recipient_user_id, notification_type, title, body, action_url, email_status, "
                        + "email_attempts, created_at, updated_at, version) "
                        + "VALUES (?, 'MEMBERSHIP_EXIT_REQUESTED', 'Unavailable Exit notice', 'Exit requested', '/exit/1', "
                        + "'UNAVAILABLE', 0, ?, ?, 0) RETURNING id",
                Long.class, internUserId, Timestamp.from(now), Timestamp.from(now));

        // 5. NOT_REQUIRED row (NOT-012, V8: payload MUST be null)
        notRequiredNotificationId = jdbc.queryForObject(
                "INSERT INTO notifications (recipient_user_id, notification_type, title, body, action_url, email_status, "
                        + "email_attempts, created_at, updated_at, version) "
                        + "VALUES (?, 'TASK_ASSIGNED', 'Not Required Task notice', 'Self assigned task', '/tasks/2', "
                        + "'NOT_REQUIRED', 0, ?, ?, 0) RETURNING id",
                Long.class, internUserId, Timestamp.from(now), Timestamp.from(now));
    }

    /**
     * Case a: Admin GET lists only the FAILED email with recipient, title, attempts, and last error.
     * Titles of PENDING, SENT, UNAVAILABLE, and NOT_REQUIRED rows are strictly absent.
     * Protects NOT-007, AC-NOT-002, AC-NOT-007.
     */
    @Test
    void adminGetShowsOnlyFailedEmailDetailsAndOmitsOtherStates() throws Exception {
        mvc.perform(get("/admin/notifications/failed-email")
                        .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("failed-intern@example.test")))
                .andExpect(content().string(containsString("Project invitation for Intern")))
                .andExpect(content().string(containsString("6")))
                .andExpect(content().string(containsString("ConnectException")))
                .andExpect(content().string(not(containsString("Pending Task update"))))
                .andExpect(content().string(not(containsString("Sent Leave approval"))))
                .andExpect(content().string(not(containsString("Unavailable Exit notice"))))
                .andExpect(content().string(not(containsString("Not Required Task notice"))));
    }

    /**
     * Case b: Unauthorized roles (Mentor, Intern) receive 403 on both GET and POST;
     * unauthenticated requests redirect to /login; requests missing CSRF receive 403;
     * no rows in the database are mutated.
     * Protects NOT-007 authorization and platform permission matrix.
     */
    @Test
    void unauthorizedActorsAndMissingCsrfAreRefusedWithoutMutatingData() throws Exception {
        List<Map<String, Object>> snapshotBefore = dumpNotifications();

        // Mentor: GET and POST both 403
        mvc.perform(get("/admin/notifications/failed-email").with(user(MENTOR_EMAIL).roles("MENTOR")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/notifications/failed-email/" + failedNotificationId + "/retry")
                        .with(user(MENTOR_EMAIL).roles("MENTOR")).with(csrf()))
                .andExpect(status().isForbidden());

        // Intern: GET and POST both 403
        mvc.perform(get("/admin/notifications/failed-email").with(user(INTERN_EMAIL).roles("INTERN")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/notifications/failed-email/" + failedNotificationId + "/retry")
                        .with(user(INTERN_EMAIL).roles("INTERN")).with(csrf()))
                .andExpect(status().isForbidden());

        // Unauthenticated: redirects to /login
        mvc.perform(get("/admin/notifications/failed-email"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/**/login"));
        mvc.perform(post("/admin/notifications/failed-email/" + failedNotificationId + "/retry").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/**/login"));

        // Admin POST missing CSRF: 403
        mvc.perform(post("/admin/notifications/failed-email/" + failedNotificationId + "/retry")
                        .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isForbidden());

        // Verify zero mutations
        List<Map<String, Object>> snapshotAfter = dumpNotifications();
        assertThat(snapshotAfter).isEqualTo(snapshotBefore);
    }

    /**
     * Case c: Admin POST retry on a FAILED email redirects to list, flashes "Email retry started.",
     * and re-enters bounded retry state without creating duplicate in-app rows.
     *
     * <p>Because test environment has no active SMTP configuration, {@link NotificationService#retryFailedEmail}
     * re-queues the row to PENDING (attempts=0) and attempts immediate delivery via {@code retryLocked};
     * the absent SMTP configuration triggers {@code IllegalStateException}, which {@code retainPendingRetry}
     * handles by retaining {@code PENDING} state with attempts=1, recording {@code last_error = 'IllegalStateException'},
     * and scheduling the next attempt for now + 1 minute (NOT-006). Thus the row leaves FAILED and enters PENDING.
     * Protects NOT-007, AC-NOT-002, AC-NOT-007.
     */
    @Test
    void adminPostRetryOnFailedEmailStartsRetryCycleAndFlashesSuccess() throws Exception {
        int initialCount = jdbc.queryForObject("SELECT count(*) FROM notifications", Integer.class);

        mvc.perform(post("/admin/notifications/failed-email/" + failedNotificationId + "/retry")
                        .with(user(ADMIN_EMAIL).roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/notifications/failed-email"))
                .andExpect(flash().attribute("message", "Email retry started."));

        // Row is no longer FAILED: absent SMTP causes immediate retry failure which retains PENDING state
        String updatedStatus = jdbc.queryForObject(
                "SELECT email_status FROM notifications WHERE id = ?", String.class, failedNotificationId);
        assertThat(updatedStatus).isEqualTo("PENDING");

        int updatedAttempts = jdbc.queryForObject(
                "SELECT email_attempts FROM notifications WHERE id = ?", Integer.class, failedNotificationId);
        assertThat(updatedAttempts).isEqualTo(1);

        String lastError = jdbc.queryForObject(
                "SELECT email_last_error FROM notifications WHERE id = ?", String.class, failedNotificationId);
        assertThat(lastError).isEqualTo("IllegalStateException");

        // Total rows must not duplicate (in-app notification uniqueness preserved)
        int finalCount = jdbc.queryForObject("SELECT count(*) FROM notifications", Integer.class);
        assertThat(finalCount).isEqualTo(initialCount);
    }

    /**
     * Case d: Admin POST retry on a SENT email or non-existent ID (99999) flashes
     * "This email is no longer in a failed state." and mutates no rows.
     * Protects NOT-007 idempotency and terminal state invariants.
     */
    @Test
    void adminPostRetryOnNonFailedOrNonExistentRowFlashesNoLongerFailedMessage() throws Exception {
        List<Map<String, Object>> snapshotBefore = dumpNotifications();

        // Retry on already-SENT row
        mvc.perform(post("/admin/notifications/failed-email/" + sentNotificationId + "/retry")
                        .with(user(ADMIN_EMAIL).roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/notifications/failed-email"))
                .andExpect(flash().attribute("message", "This email is no longer in a failed state."));

        // Retry on non-existent row ID 99999
        mvc.perform(post("/admin/notifications/failed-email/99999/retry")
                        .with(user(ADMIN_EMAIL).roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/notifications/failed-email"))
                .andExpect(flash().attribute("message", "This email is no longer in a failed state."));

        // Verify zero mutations
        List<Map<String, Object>> snapshotAfter = dumpNotifications();
        assertThat(snapshotAfter).isEqualTo(snapshotBefore);
    }

    private List<Map<String, Object>> dumpNotifications() {
        return jdbc.queryForList("SELECT id, email_status, email_attempts, email_last_error, version FROM notifications ORDER BY id");
    }
}
