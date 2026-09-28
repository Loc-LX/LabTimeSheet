package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lab.labtimesheet.feature.attendance.exception.AttendanceExceptionRequestException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceExceptionKind;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceRecordRepository;
import com.lab.labtimesheet.feature.identity.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.identity.service.BootstrapService;
import com.lab.labtimesheet.feature.internship.service.InternshipService;
import com.lab.labtimesheet.platform.model.GlobalRole;
import com.lab.labtimesheet.platform.model.SecurityMode;
import com.lab.labtimesheet.platform.model.dto.SmtpDraft;
import com.lab.labtimesheet.platform.service.SmtpConfigurationService;
import java.time.Instant;
import java.time.LocalDate;
import java.sql.Timestamp;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

@Import(AttendancePersistenceIntegrationTest.IntegrationConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class AttendanceExceptionRequestIntegrationTest {

    private static final LocalDate WORK_DATE = LocalDate.of(2026, 8, 14);
    private static final Instant SCHEDULED_END = Instant.parse("2026-08-14T08:30:00Z");

    @Autowired private AttendanceExceptionRequestService requests;
    @Autowired private AttendanceExceptionService exceptions;
    @Autowired private AttendanceApplicationService attendance;
    @Autowired private AttendanceRecordRepository records;
    @Autowired private BootstrapService bootstrap;
    @Autowired private AccountService accounts;
    @Autowired private InternshipService internships;
    @Autowired private AttendancePersistenceIntegrationTest.MutableClock clock;
    @Autowired private AttendancePersistenceIntegrationTest.RecordingSmtpProbe mail;
    @Autowired private SmtpConfigurationService smtp;
    @Autowired private com.lab.labtimesheet.platform.service.MailDeliveryService mailDelivery;
    @Autowired private JdbcTemplate jdbc;

    private long adminId;
    private long mentorId;
    private long internId;
    private AttendanceActor actor;
    private List<Long> fixtureInternIds;

    @BeforeEach
    void prepareActiveInternAndMentor() {
        clock.set(Instant.parse("2026-08-14T00:00:00Z"));
        fixtureInternIds = new ArrayList<>();
        bootstrap.bootstrap("admin@example.test", "Admin", "correct horse battery staple");
        adminId = accounts.requireActiveAdminId("admin@example.test");
        if (!mailDelivery.isAvailable()) {
            long draftId = smtp.saveDraft(adminId, new SmtpDraft(
                    "mailpit", 1025, SecurityMode.NONE, null, null, "admin@example.test", "Lab Timesheet"));
            smtp.testDraft(draftId, adminId, "admin@example.test");
            smtp.activate(draftId, adminId);
        }
        String fixture = UUID.randomUUID().toString();
        mentorId = createActiveUser("mentor-" + fixture + "@example.test", GlobalRole.MENTOR, null);
        internId = createActiveUser("intern-" + fixture + "@example.test", GlobalRole.INTERN,
                "INT-" + fixture.substring(0, 8));
        internships.activateInternship(internId, adminId);
        fixtureInternIds.add(internId);
        actor = new AttendanceActor(internId, GlobalRole.INTERN);
    }

    @AfterEach
    void removeAttendanceFixtures() {
        jdbc.update("delete from notifications where notification_type = 'ATTENDANCE_EXCEPTION_SUBMITTED'");
        for (long fixtureInternId : fixtureInternIds) {
            jdbc.update("delete from attendance_exception_decisions where attendance_exception_id in "
                    + "(select id from attendance_exceptions where attendance_record_id in "
                    + "(select id from attendance_records where intern_user_id = ?))", fixtureInternId);
            jdbc.update("delete from attendance_exceptions where attendance_record_id in "
                    + "(select id from attendance_records where intern_user_id = ?)", fixtureInternId);
            jdbc.update("delete from attendance_periods where intern_user_id = ?", fixtureInternId);
            jdbc.update("delete from attendance_records where intern_user_id = ?", fixtureInternId);
        }
    }

    /**
     * Protects {@code EXC-002} and {@code AC-EXC-001}: an eligible own late record submitted before its inclusive
     * deadline must persist the request with both distinct deadlines. Observable break: an accepted request is
     * missing, mistimed, or not PENDING. Expected: PENDING, submission deadline 2026-08-16T10:00Z and decision
     * deadline 48 hours after the injected submission instant.
     */
    @Test
    void ownLateViolationIsAcceptedAtFortySevenHoursWithDistinctDeadlines() {
        long recordId = lateRecord();
        Instant submittedAt = SCHEDULED_END.plusSeconds(47 * 3600L);
        clock.set(submittedAt);

        long id = requests.requestExcuse(actor, recordId, AttendanceExceptionKind.LATE_ARRIVAL, "Train delay");

        var request = exceptions.find(id).orElseThrow();
        assertThat(request.status()).hasToString("PENDING");
        assertThat(request.submissionDeadline()).isEqualTo(SCHEDULED_END.plusSeconds(48 * 3600L));
        assertThat(request.decisionDeadline()).isEqualTo(submittedAt.plusSeconds(48 * 3600L));
    }

    /**
     * Protects {@code EXC-002} and {@code AC-EXC-001}: the 48-hour submission boundary is inclusive. Observable break:
     * PostgreSQL has no request at the exact deadline. Expected: exactly one PENDING request is persisted.
     */
    @Test
    void exactScheduledEndPlusFortyEightHoursIsAccepted() {
        long recordId = lateRecord();
        clock.set(SCHEDULED_END.plusSeconds(48 * 3600L));

        long id = requests.requestExcuse(actor, recordId, AttendanceExceptionKind.LATE_ARRIVAL, "Transit delay");

        assertThat(exceptions.find(id).orElseThrow().status()).hasToString("PENDING");
    }

    /**
     * Protects {@code EXC-002} and {@code AC-EXC-001}: submissions after scheduled end plus 48 hours are refused.
     * Observable break: a late request appears in storage. Expected: the call fails and no exception row exists.
     */
    @Test
    void requestAfterFortyEightHoursIsRefusedWithoutWriting() {
        long recordId = lateRecord();
        clock.set(SCHEDULED_END.plusSeconds(49 * 3600L));

        assertThatThrownBy(() -> requests.requestExcuse(actor, recordId,
                AttendanceExceptionKind.LATE_ARRIVAL, "Too late"))
                .isInstanceOf(AttendanceExceptionRequestException.class);
        assertThat(exceptionCount(recordId)).isZero();
    }

    /**
     * Protects {@code EXC-002} and {@code AC-EXC-001}: every request requires nonblank explanation. Observable break:
     * a blank value becomes a retained exception. Expected: both blank and whitespace-only values fail before insert.
     */
    @Test
    void blankReasonIsRefusedWithoutWriting() {
        long recordId = lateRecord();

        for (String reason : List.of("", "   ")) {
            assertThatThrownBy(() -> requests.requestExcuse(actor, recordId,
                    AttendanceExceptionKind.LATE_ARRIVAL, reason))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(exceptionCount(recordId)).isZero();
    }

    /**
     * Protects {@code EXC-002} and {@code DB-016}: one row/kind can have only one request. Observable break: a
     * duplicate replaces or adds to the original. Expected: the second call fails and the first reason remains.
     */
    @Test
    void duplicateRequestIsRefusedAndOriginalRemains() {
        long recordId = lateRecord();
        long id = requests.requestExcuse(actor, recordId, AttendanceExceptionKind.LATE_ARRIVAL, "Original");

        assertThatThrownBy(() -> requests.requestExcuse(actor, recordId,
                AttendanceExceptionKind.LATE_ARRIVAL, "Replacement"))
                .isInstanceOf(AttendanceExceptionRequestException.class);
        assertThat(exceptions.find(id).orElseThrow().reason()).isEqualTo("Original");
        assertThat(exceptionCount(recordId)).isEqualTo(1L);
    }

    /**
     * Protects {@code EXC-001}, {@code EXC-002}, and {@code AC-EXC-001}: an early-departure request requires an actual
     * early checkout. Observable break: a normal late-violation record is accepted as early departure. Expected: the
     * service refuses it and persists no row of that kind.
     */
    @Test
    void earlyDepartureKindIsRefusedWhenRecordWasNotEarly() {
        long recordId = lateRecord();

        assertThatThrownBy(() -> requests.requestExcuse(actor, recordId,
                AttendanceExceptionKind.EARLY_DEPARTURE, "Early"))
                .isInstanceOf(AttendanceExceptionRequestException.class);
        assertThat(exceptionCount(recordId)).isZero();
    }

    /**
     * Protects {@code AUTH-002}, {@code EXC-002}, and {@code AC-EXC-001}: a guessed foreign and nonexistent attendance
     * identifier disclose the same denial. Observable break: error text reveals row existence. Expected: identical
     * exception class and message for both identifiers, with no request persisted.
     */
    @Test
    void otherPersonsAndUnknownRowsHaveTheSameDenial() {
        long otherInternId = createActiveUser("other-" + UUID.randomUUID() + "@example.test", GlobalRole.INTERN,
                "OTH-" + UUID.randomUUID().toString().substring(0, 8));
        fixtureInternIds.add(otherInternId);
        internships.activateInternship(otherInternId, adminId);
        clock.set(Instant.parse("2026-08-14T02:01:00Z"));
        attendance.checkIn(otherInternId);
        long otherRecord = records.findByInternUserIdAndWorkDate(otherInternId, WORK_DATE).orElseThrow().id();

        Throwable foreign = captureDenial(otherRecord);
        Throwable missing = captureDenial(Long.MAX_VALUE);

        assertThat(foreign).isInstanceOf(AccessDeniedException.class);
        assertThat(foreign.getClass()).isEqualTo(missing.getClass());
        assertThat(foreign.getMessage()).isEqualTo(missing.getMessage());
        assertThat(exceptionCount(otherRecord)).isZero();
    }

    /**
     * Protects {@code EXC-002}, {@code ATT-020}, and {@code AC-EXC-001}: finalized periods reject requests.
     * Observable break: a request is inserted for a finalized month. Expected: refusal and zero exception rows.
     */
    @Test
    void finalizedAttendancePeriodRefusesRequest() {
        long recordId = lateRecord();
        jdbc.update("""
                insert into attendance_periods (intern_user_id, period_month, status, finalized_at)
                values (?, ?, 'FINALIZED', ?)
                """, internId, LocalDate.of(2026, 8, 1), Timestamp.from(clock.instant()));

        assertThatThrownBy(() -> requests.requestExcuse(actor, recordId,
                AttendanceExceptionKind.LATE_ARRIVAL, "Late reason"))
                .isInstanceOf(AttendanceExceptionRequestException.class);
        assertThat(exceptionCount(recordId)).isZero();
    }

    /**
     * Protects {@code NOT-002}, {@code NOT-011}, and {@code AC-EXC-001}: an active responsible Mentor receives the
     * submission in-app and via designated email, linked to Attendance. Expected: one correct row, null Project ID,
     * and the Mentor email retained in the email payload.
     */
    @Test
    void activeResponsibleMentorReceivesSubmissionNotice() {
        assignMentor(mentorId);
        long recordId = lateRecord();

        requests.requestExcuse(actor, recordId, AttendanceExceptionKind.LATE_ARRIVAL, "Train delay");

        var notice = notificationFor(mentorId);
        assertThat(notice.get("notification_type")).isEqualTo("ATTENDANCE_EXCEPTION_SUBMITTED");
        assertThat(notice.get("action_url")).isEqualTo("/attendance");
        assertThat(notice.get("project_id")).isNull();
        assertThat(notice.get("email_to")).isEqualTo(accounts.requireIdentityById(mentorId).email());
    }

    /**
     * Protects {@code D47}, {@code NOT-011}, and {@code AC-EXC-001}: with no responsible Mentor, every active Admin
     * receives one notice and no Mentor does. Expected: recipient IDs exactly equal active Admin identity IDs.
     */
    @Test
    void missingResponsibleMentorFallsBackToEveryActiveAdmin() {
        long recordId = lateRecord();
        List<Long> expectedAdmins = accounts.activeAdminIdentities().stream().map(identity -> identity.id()).toList();

        requests.requestExcuse(actor, recordId, AttendanceExceptionKind.LATE_ARRIVAL, "Train delay");

        assertThat(notificationRecipientIds()).containsExactlyInAnyOrderElementsOf(expectedAdmins);
        assertThat(notificationRecipientIds()).doesNotContain(mentorId);
    }

    /**
     * Protects {@code D47}, {@code NOT-011}, and {@code AC-EXC-001}: a locked responsible Mentor is unavailable.
     * Expected: all active Admins receive the notice, the locked Mentor receives none, and the request is accepted.
     */
    @Test
    void lockedResponsibleMentorFallsBackToActiveAdminsAndRequestIsAccepted() {
        assignMentor(mentorId);
        accounts.lockAccount(mentorId, adminId);
        long recordId = lateRecord();
        List<Long> expectedAdmins = accounts.activeAdminIdentities().stream().map(identity -> identity.id()).toList();

        long requestId = requests.requestExcuse(actor, recordId,
                AttendanceExceptionKind.LATE_ARRIVAL, "Train delay");

        assertThat(exceptions.find(requestId).orElseThrow().status()).hasToString("PENDING");
        assertThat(notificationRecipientIds()).containsExactlyInAnyOrderElementsOf(expectedAdmins);
        assertThat(notificationRecipientIds()).doesNotContain(mentorId);
    }

    private long createActiveUser(String email, GlobalRole role, String studentCode) {
        mail.clear();
        var creation = internships.create(new CreateAccountCommand(
                email, role == GlobalRole.INTERN ? "Intern" : "Mentor", role, studentCode,
                role == GlobalRole.INTERN ? LocalDate.of(2026, 8, 1) : null,
                role == GlobalRole.INTERN ? LocalDate.of(2026, 12, 31) : null), adminId);
        assertThat(creation.deliverySucceeded()).isTrue();
        assertThat(accounts.activate(mail.onlyActivationToken(), "new secure fixture password")).isTrue();
        return creation.userId();
    }

    private long lateRecord() {
        clock.set(Instant.parse("2026-08-14T02:01:00Z"));
        attendance.checkIn(internId);
        return records.findByInternUserIdAndWorkDate(internId, WORK_DATE).orElseThrow().id();
    }

    private void assignMentor(long mentorUserId) {
        jdbc.update("update intern_profiles set responsible_mentor_user_id = ? where user_id = ?",
                mentorUserId, internId);
    }

    private Throwable captureDenial(long recordId) {
        try {
            requests.requestExcuse(actor, recordId, AttendanceExceptionKind.LATE_ARRIVAL, "Train delay");
            throw new AssertionError("request must be denied");
        } catch (Throwable failure) {
            return failure;
        }
    }

    private java.util.Map<String, Object> notificationFor(long recipientId) {
        return jdbc.queryForMap("""
                select notification_type, action_url, project_id, email_to
                from notifications where recipient_user_id = ? and notification_type = 'ATTENDANCE_EXCEPTION_SUBMITTED'
                """, recipientId);
    }

    private List<Long> notificationRecipientIds() {
        return jdbc.queryForList("""
                select recipient_user_id from notifications
                where notification_type = 'ATTENDANCE_EXCEPTION_SUBMITTED'
                order by recipient_user_id
                """, Long.class);
    }

    private long exceptionCount(long attendanceRecordId) {
        return jdbc.queryForObject("select count(*) from attendance_exceptions where attendance_record_id = ?",
                Long.class, attendanceRecordId);
    }
}
