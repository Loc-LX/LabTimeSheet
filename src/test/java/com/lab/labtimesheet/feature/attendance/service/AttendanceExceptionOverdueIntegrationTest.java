package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceExceptionDecisionKind;
import com.lab.labtimesheet.feature.attendance.model.AttendanceExceptionKind;
import com.lab.labtimesheet.feature.attendance.model.AttendanceExceptionOutcome;
import com.lab.labtimesheet.feature.attendance.model.AttendanceExceptionSource;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceRecordRepository;
import com.lab.labtimesheet.feature.identity.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.identity.service.BootstrapService;
import com.lab.labtimesheet.feature.internship.service.InternshipService;
import com.lab.labtimesheet.platform.model.GlobalRole;
import com.lab.labtimesheet.platform.model.SecurityMode;
import com.lab.labtimesheet.platform.model.dto.SmtpDraft;
import com.lab.labtimesheet.platform.service.SmtpConfigurationService;
import com.lab.labtimesheet.platform.service.MailDeliveryService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@Import(AttendancePersistenceIntegrationTest.IntegrationConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AttendanceExceptionOverdueIntegrationTest {

    private static final Instant T = Instant.parse("2026-08-14T02:01:00Z");
    private static final LocalDate WORK_DATE = LocalDate.of(2026, 8, 14);

    @Autowired private AttendanceExceptionService exceptions;
    @Autowired private AttendanceExceptionRequestService requests;
    @Autowired private AttendanceApplicationService attendance;
    @Autowired private AttendanceReportQueryService reports;
    @Autowired private AttendanceRecordRepository records;
    @Autowired private BootstrapService bootstrap;
    @Autowired private AccountService accounts;
    @Autowired private InternshipService internships;
    @Autowired private AttendancePersistenceIntegrationTest.MutableClock clock;
    @Autowired private AttendancePersistenceIntegrationTest.RecordingSmtpProbe mail;
    @Autowired private SmtpConfigurationService smtp;
    @Autowired private MailDeliveryService mailDelivery;
    @Autowired private JdbcTemplate jdbc;

    private long adminId;
    private long mentorId;
    private long internId;
    private long recordId;
    private String fixture;

    @BeforeEach
    void createAttendanceFixture() {
        fixture = UUID.randomUUID().toString();
        clock.set(T);
        bootstrap.bootstrap("admin@example.test", "Admin", "correct horse battery staple");
        adminId = accounts.requireActiveAdminId("admin@example.test");
        if (!mailDelivery.isAvailable()) {
            long draftId = smtp.saveDraft(adminId, new SmtpDraft(
                    "mailpit", 1025, SecurityMode.NONE, null, null, "admin@example.test", "Lab Timesheet"));
            smtp.testDraft(draftId, adminId, "admin@example.test");
            smtp.activate(draftId, adminId);
        }
        mentorId = createActiveUser("mentor-" + fixture + "@example.test", GlobalRole.MENTOR, null);
        internId = createActiveUser("intern-" + fixture + "@example.test", GlobalRole.INTERN,
                "INT-" + fixture.substring(0, 8));
        internships.activateInternship(internId, adminId);
        attendance.checkIn(internId);
        recordId = records.findByInternUserIdAndWorkDate(internId, WORK_DATE).orElseThrow().id();
    }

    /**
     * Protects {@code EXC-003} and {@code AC-EXC-003}: a request cannot become overdue before its inclusive
     * decision boundary. Observable break: the row changes one second early or stays pending at the boundary.
     * Expected: no change at T+48h-1s; at T+48h status is OVERDUE with no decision row and three null decision fields.
     */
    @Test
    void requestBecomesOverdueAtInclusiveFortyEightHourBoundary() {
        long id = pendingRequest();
        clock.set(T.plusSeconds(48 * 3600L - 1));

        exceptions.expireOverdue(100);
        assertExpiry(id, "PENDING", activeAdminIds(), 0);

        clock.set(T.plusSeconds(48 * 3600L));
        exceptions.expireOverdue(100);
        assertExpiry(id, "OVERDUE", activeAdminIds(), 1);
        var overdue = exceptions.find(id).orElseThrow();
        assertThat(overdue.status()).hasToString("OVERDUE");
        assertThat(overdue.decisions()).isEmpty();
        assertThat(overdue.decidedByMentorUserId()).isNull();
        assertThat(overdue.decidedAt()).isNull();
        assertThat(overdue.decisionNote()).isNull();
    }

    /**
     * Protects {@code EXC-003}, {@code NOT-011}, and {@code AC-EXC-003}: repeated scheduled and read-time expiry
     * must not duplicate transitions or reminders. Observable break: repeated calls add notifications. Expected: one
     * changed row and exactly one SYSTEM reminder after two sweeps and one expireIfDue call.
     */
    @Test
    void repeatedExpiryCallsSendOnlyOneReminder() {
        long id = pendingRequest();
        clock.set(T.plusSeconds(48 * 3600L));

        exceptions.expireOverdue(100);
        assertExpiry(id, "OVERDUE", activeAdminIds(), 1);
        exceptions.expireOverdue(100);
        assertExpiry(id, "OVERDUE", activeAdminIds(), 1);
        exceptions.expireIfDue(id);
        assertExpiry(id, "OVERDUE", activeAdminIds(), 1);
    }

    /**
     * Protects {@code D47}, {@code NOT-011}, data-model {@code C.6}, and {@code AC-EXC-003}: an active responsible
     * Mentor receives an in-app-only SYSTEM reminder without Project scope. Expected: one notification for mentorId,
     * action /attendance, null project_id, and NOT_REQUIRED email status.
     */
    @Test
    void reminderUsesSystemInAppOnlyAndActiveResponsibleMentor() {
        assignMentor(internId, mentorId);
        long id = pendingRequest();
        clock.set(T.plusSeconds(48 * 3600L));

        exceptions.expireIfDue(id);

        var notification = jdbc.queryForMap("""
                select notification_type, title, action_url, project_id, email_status
                from notifications where recipient_user_id = ? and title = ?
                """, mentorId, "Attendance exception request overdue");
        assertThat(notification).containsEntry("notification_type", "SYSTEM")
                .containsEntry("action_url", "/attendance")
                .containsEntry("project_id", null)
                .containsEntry("email_status", "NOT_REQUIRED");
        assertExpiry(id, "OVERDUE", List.of(mentorId), 1);
    }

    /**
     * Protects {@code D47}, {@code NOT-011}, and {@code AC-EXC-003}: absent or locked responsible Mentors must not
     * receive the overdue reminder. Observable break: fallback omits an active Admin or notifies unavailable Mentor.
     * Expected: every active Admin and no Mentor receives each reminder for the two fallback cases.
     */
    @Test
    void missingOrLockedMentorFallsBackToEveryActiveAdmin() {
        List<Long> adminIds = accounts.activeAdminIdentities().stream().map(identity -> identity.id()).toList();
        long noMentorRequest = pendingRequest();
        expireIfDueAfterClock(noMentorRequest, T.plusSeconds(48 * 3600L));
        assertExpiry(noMentorRequest, "OVERDUE", adminIds, 1);
        assertThat(reminderRecipients(noMentorRequest)).containsExactlyInAnyOrderElementsOf(adminIds);

        long secondIntern = createActiveUser("second-intern-" + fixture + "@example.test", GlobalRole.INTERN,
                "SEC-" + fixture.substring(0, 8));
        internships.activateInternship(secondIntern, adminId);
        clock.set(T);
        attendance.checkIn(secondIntern);
        long secondRecord = records.findByInternUserIdAndWorkDate(secondIntern, WORK_DATE).orElseThrow().id();
        assignMentor(secondIntern, mentorId);
        accounts.lockAccount(mentorId, adminId);
        long lockedMentorRequest = requestFor(secondIntern, secondRecord);
        expireIfDueAfterClock(lockedMentorRequest, T.plusSeconds(48 * 3600L));

        assertExpiry(lockedMentorRequest, "OVERDUE", adminIds, 1);
        assertThat(reminderRecipients(lockedMentorRequest)).containsExactlyInAnyOrderElementsOf(adminIds)
                .doesNotContain(mentorId);
    }

    /**
     * Protects {@code EXC-003}, {@code DB-017}, and {@code AC-EXC-003}: decided requests and direct Mentor marks
     * are outside the overdue transition. Observable break: either row becomes OVERDUE. Expected: EXCUSED and the
     * MENTOR_MARK row remain unchanged, with no reminder.
     */
    @Test
    void decidedRequestAndMentorMarkAreNotExpired() {
        long decidedRequest = pendingRequest();
        exceptions.appendDecision(decidedRequest, AttendanceExceptionDecisionKind.DECISION,
                AttendanceExceptionOutcome.EXCUSED, "Approved", mentorId, null);
        long mark = exceptions.open(recordId, AttendanceExceptionKind.EARLY_DEPARTURE,
                AttendanceExceptionSource.MENTOR_MARK, "Marked by Mentor", T, T.plusSeconds(1));
        exceptions.appendDecision(mark, AttendanceExceptionDecisionKind.DECISION,
                AttendanceExceptionOutcome.EXCUSED, "Marked", mentorId, null);
        clock.set(T.plusSeconds(48 * 3600L));

        exceptions.expireOverdue(100);
        assertExpiry(decidedRequest, "EXCUSED", activeAdminIds(), 0);
        assertExpiry(mark, "EXCUSED", activeAdminIds(), 0);
        assertThat(exceptions.find(decidedRequest).orElseThrow().status()).hasToString("EXCUSED");
        assertThat(exceptions.find(mark).orElseThrow().status()).hasToString("EXCUSED");
    }

    /**
     * Protects {@code EXC-003}, {@code NOT-011}, and {@code AC-EXC-003}: read-time expiry applies the same locked,
     * idempotent transition as the scheduled sweep. Observable break: a due row stays pending or gets duplicate
     * reminders. Expected: one OVERDUE transition and one reminder after repeated read-time calls.
     */
    @Test
    void readTimeExpiryTransitionsDueRequestOnce() {
        long id = pendingRequest();
        clock.set(T.plusSeconds(48 * 3600L));

        exceptions.expireIfDue(id);
        assertExpiry(id, "OVERDUE", activeAdminIds(), 1);
        exceptions.expireIfDue(id);

        assertExpiry(id, "OVERDUE", activeAdminIds(), 1);
    }

    /**
     * Protects {@code EXC-003}, {@code AC-EXC-003}, and {@code AC-ATT-009}: unresolved request lookup must keep the
     * work month visible until decision. Observable break: PENDING/OVERDUE is omitted or another month is included.
     * Expected: true while pending and overdue in August, false after decision and for September.
     */
    @Test
    void unresolvedRequestQueryTracksStatusAndWorkMonth() {
        long id = pendingRequest();
        assertThat(exceptions.hasUnresolvedExceptionRequest(internId, YearMonth.of(2026, 8))).isTrue();
        assertThat(exceptions.hasUnresolvedExceptionRequest(internId, YearMonth.of(2026, 9))).isFalse();
        clock.set(T.plusSeconds(48 * 3600L));
        exceptions.expireIfDue(id);
        assertExpiry(id, "OVERDUE", activeAdminIds(), 1);
        assertThat(exceptions.hasUnresolvedExceptionRequest(internId, YearMonth.of(2026, 8))).isTrue();
        exceptions.appendDecision(id, AttendanceExceptionDecisionKind.DECISION,
                AttendanceExceptionOutcome.EXCUSED, "Approved", mentorId, null);
        assertThat(exceptions.hasUnresolvedExceptionRequest(internId, YearMonth.of(2026, 8))).isFalse();
    }

    /**
     * Protects {@code EXC-001}, {@code EXC-003}, and {@code AC-EXC-003}: changing exception state must not erase the
     * recorded late violation from compliance reporting. Observable break: an overdue request removes the late flag.
     * Expected: the report continues to expose one late day for the work date.
     */
    @Test
    void complianceReportStillCountsLateViolationAfterOverdueTransition() {
        long id = pendingRequest();
        clock.set(T.plusSeconds(48 * 3600L));
        exceptions.expireIfDue(id);

        var report = reports.query(new AttendanceActor(internId, GlobalRole.INTERN), internId, WORK_DATE, WORK_DATE);
        assertThat(report.days()).singleElement().extracting("late").isEqualTo(true);
    }

    private long pendingRequest() {
        return requestFor(internId, recordId);
    }

    private long requestFor(long ownerId, long ownedRecordId) {
        clock.set(T);
        long id = requests.requestExcuse(new AttendanceActor(ownerId, GlobalRole.INTERN), ownedRecordId,
                AttendanceExceptionKind.LATE_ARRIVAL, "Train delay");
        return id;
    }

    private void expireIfDueAfterClock(long id, Instant instant) {
        clock.set(instant);
        exceptions.expireIfDue(id);
    }

    private long createActiveUser(String email, GlobalRole role, String studentCode) {
        mail.clear();
        var creation = internships.create(new CreateAccountCommand(
                email, role == GlobalRole.INTERN ? "Intern" : "Mentor", role, studentCode,
                role == GlobalRole.INTERN ? LocalDate.of(2026, 8, 1) : null,
                role == GlobalRole.INTERN ? LocalDate.of(2026, 12, 31) : null), adminId);
        assertThat(creation.deliverySucceeded()).isTrue();
        assertThat(accounts.activate(mail.activationTokenFor(email), "new secure fixture password")).isTrue();
        return creation.userId();
    }

    private void assignMentor(long ownerId, long responsibleMentorId) {
        jdbc.update("update intern_profiles set responsible_mentor_user_id = ? where user_id = ?",
                responsibleMentorId, ownerId);
    }

    private List<Long> reminderRecipients(long exceptionId) {
        return jdbc.queryForList("""
                select n.recipient_user_id from notifications n
                join attendance_exceptions e on n.body like ('%(' || e.id || ')%')
                where e.id = ? and n.title = ?
                """, Long.class, exceptionId, "Attendance exception request overdue");
    }

    private List<Long> activeAdminIds() {
        return accounts.activeAdminIdentities().stream().map(identity -> identity.id()).toList();
    }

    private void assertExpiry(long exceptionId, String expectedStatus, List<Long> recipientIds, int expectedReminders) {
        assertThat(exceptions.find(exceptionId).orElseThrow().status()).hasToString(expectedStatus);
        for (long recipientId : recipientIds) {
            int reminders = jdbc.queryForObject("""
                    select count(*) from notifications
                    where recipient_user_id = ? and notification_type = 'SYSTEM'
                      and title = 'Attendance exception request overdue'
                      and body like ?
                    """, Integer.class, recipientId, "%(" + exceptionId + ")%");
            assertThat(reminders).isEqualTo(expectedReminders);
        }
    }
}
