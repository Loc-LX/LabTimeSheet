package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lab.labtimesheet.feature.attendance.exception.CorrectionException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionDecision;
import com.lab.labtimesheet.feature.attendance.model.CorrectionStatus;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionRequestCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionView;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceCorrectionEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceRecordEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceCorrectionRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceRecordRepository;
import com.lab.labtimesheet.feature.identity.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.identity.service.BootstrapService;
import com.lab.labtimesheet.feature.internship.service.InternshipService;
import com.lab.labtimesheet.platform.model.GlobalRole;
import com.lab.labtimesheet.platform.model.SecurityMode;
import com.lab.labtimesheet.platform.model.dto.SmtpDraft;
import com.lab.labtimesheet.platform.service.MailDeliveryService;
import com.lab.labtimesheet.platform.service.SmtpConfigurationService;
import com.lab.labtimesheet.feature.calendar.model.dto.AttendancePolicyCommand;
import com.lab.labtimesheet.feature.calendar.service.AttendancePolicyApplicationService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration test suite for missed-checkout correction submission eligibility,
 * cutoff rules, inclusive 48-hour submission deadline, and separate 48-hour decision window (MC-02).
 */
@Import(AttendancePersistenceIntegrationTest.IntegrationConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CorrectionSubmissionWindowIntegrationTest {

    @Autowired private BootstrapService bootstrap;
    @Autowired private AccountService accounts;
    @Autowired private InternshipService internships;
    @Autowired private SmtpConfigurationService smtp;
    @Autowired private AttendancePersistenceIntegrationTest.RecordingSmtpProbe mail;
    @Autowired private AttendancePersistenceIntegrationTest.MutableClock clock;
    @Autowired private MailDeliveryService mailDelivery;
    @Autowired private AttendanceCorrectionApplicationService corrections;
    @Autowired private AttendanceRecordRepository records;
    @Autowired private AttendanceCorrectionRepository correctionRequests;
    @Autowired private AttendancePolicyApplicationService policyApplication;
    @Autowired private JdbcTemplate jdbc;

    private long adminId;
    private long mentorId;
    private long internId;
    private AttendanceActor internActor;
    private AttendanceActor mentorActor;

    @BeforeEach
    void prepareActiveInternAndMentor() {
        clock.set(Instant.parse("2026-08-01T00:00:00Z"));
        bootstrap.bootstrap("admin@example.test", "Admin", "correct horse battery staple");
        adminId = accounts.requireActiveAdminId("admin@example.test");
        if (!mailDelivery.isAvailable()) {
            long draftId = smtp.saveDraft(adminId, new SmtpDraft(
                    "mailpit", 1025, SecurityMode.NONE, null, null, "admin@example.test", "Lab Timesheet"));
            smtp.testDraft(draftId, adminId, "admin@example.test");
            smtp.activate(draftId, adminId);
        }
        String fixture = UUID.randomUUID().toString();
        mentorId = createActiveUser("mentor-mc02-" + fixture + "@example.test", GlobalRole.MENTOR, null);
        internId = createActiveUser("intern-mc02-" + fixture + "@example.test", GlobalRole.INTERN,
                "INT-" + fixture.substring(0, 8));
        internships.activateInternship(internId, adminId);
        internActor = new AttendanceActor(internId, GlobalRole.INTERN);
        mentorActor = new AttendanceActor(mentorId, GlobalRole.MENTOR);
        jdbc.update("update intern_profiles set responsible_mentor_user_id = ? where user_id = ?",
                mentorId, internId);
    }

    /**
     * Protects {@code COR-001} and {@code AC-COR-001}: Intern attempts correction at or before checkout cutoff.
     * Hand-derived calculation from attached seeded policy 1 (zone Asia/Ho_Chi_Minh UTC+7, scheduled 08:30 to 15:30, checkout grace 30 min):
     * For work date 2026-08-03: scheduled end E = 2026-08-03T08:30:00Z (15:30 local), checkout grace G = 30 min,
     * cutoff = E + G = 2026-08-03T09:00:00Z (16:00 local), cutoff - 1 min = 2026-08-03T08:59:00Z.
     * Observable break: request accepted before or at cutoff while normal checkout remains available.
     * Expected: rejected with "Correction opens after the attached checkout cutoff", 0 corrections created.
     */
    @Test
    void cutoffBoundaryRejectsAttemptsAtOrBeforeCutoff() {
        LocalDate workDate = LocalDate.of(2026, 8, 3);
        long recordId = createMissingCheckoutRecord(workDate, Instant.parse("2026-08-03T01:30:00Z"));

        // Case: now = cutoff - 1 minute
        clock.set(Instant.parse("2026-08-03T08:59:00Z"));
        assertThatThrownBy(() -> corrections.submit(
                internActor, recordId,
                new CorrectionRequestCommand(LocalDateTime.of(2026, 8, 3, 15, 0), "Before cutoff")))
                .isInstanceOf(CorrectionException.class)
                .hasMessage("Correction opens after the attached checkout cutoff");

        // Case: now = cutoff exactly
        clock.set(Instant.parse("2026-08-03T09:00:00Z"));
        assertThatThrownBy(() -> corrections.submit(
                internActor, recordId,
                new CorrectionRequestCommand(LocalDateTime.of(2026, 8, 3, 15, 0), "At cutoff")))
                .isInstanceOf(CorrectionException.class)
                .hasMessage("Correction opens after the attached checkout cutoff");

        assertThat(correctionRequests.findByAttendanceRecordId(recordId)).isEmpty();
    }

    /**
     * Protects {@code COR-001} and {@code AC-COR-001}: Intern submits correction immediately after checkout cutoff.
     * Hand-derived calculation from policy 1: For work date 2026-08-04, E = 2026-08-04T08:30:00Z, G = 30 min,
     * cutoff = E + G = 2026-08-04T09:00:00Z. Cutoff + 1 us = 2026-08-04T09:00:00.000001Z.
     * Observable break: request rejected immediately after cutoff when checkout window closes.
     * Expected: accepted with PENDING status, 1 correction row created.
     */
    @Test
    void submissionAcceptedImmediatelyAfterCheckoutCutoff() {
        LocalDate workDate = LocalDate.of(2026, 8, 4);
        long recordId = createMissingCheckoutRecord(workDate, Instant.parse("2026-08-04T01:30:00Z"));

        clock.set(Instant.parse("2026-08-04T09:00:00.000001Z"));
        CorrectionView view = corrections.submit(
                internActor, recordId,
                new CorrectionRequestCommand(LocalDateTime.of(2026, 8, 4, 15, 0), "Cutoff plus one microsecond"));

        assertThat(view).isNotNull();
        assertThat(view.status()).isEqualTo(CorrectionStatus.PENDING);
        assertThat(correctionRequests.findByAttendanceRecordId(recordId)).isPresent();
    }

    /**
     * Protects {@code COR-003}, {@code COR-004}, {@code D23}, and {@code AC-COR-001}: submission accepted at exactly E + 48h.
     * Hand-derived calculation from policy 1: For work date 2026-08-05, scheduled end E = 2026-08-05T08:30:00Z (15:30 local).
     * Inclusive submission deadline = E + 48 hours = 2026-08-07T08:30:00Z (15:30 two days later).
     * Decision deadline for submission at now = now + 48 hours = 2026-08-09T08:30:00Z.
     * Observable break: request rejected at inclusive deadline or deadlines conflated.
     * Expected: accepted, row stores submission_deadline = 2026-08-07T08:30:00Z and decision_deadline = 2026-08-09T08:30:00Z,
     * decision deadline strictly after submission deadline proving distinct windows.
     */
    @Test
    void submissionAcceptedAtInclusiveScheduledEndPlusFortyEightHours() {
        LocalDate workDate = LocalDate.of(2026, 8, 5);
        long recordId = createMissingCheckoutRecord(workDate, Instant.parse("2026-08-05T01:30:00Z"));

        Instant exactDeadline = Instant.parse("2026-08-07T08:30:00Z");
        clock.set(exactDeadline);

        CorrectionView view = corrections.submit(
                internActor, recordId,
                new CorrectionRequestCommand(LocalDateTime.of(2026, 8, 5, 15, 0), "Submitted at exact 48h deadline"));

        assertThat(view).isNotNull();
        AttendanceCorrectionEntity entity = correctionRequests.findByAttendanceRecordId(recordId).orElseThrow();
        Instant expectedSubmissionDeadline = Instant.parse("2026-08-07T08:30:00Z");
        Instant expectedDecisionDeadline = Instant.parse("2026-08-09T08:30:00Z");
        assertThat(entity.submissionDeadline()).isEqualTo(expectedSubmissionDeadline);
        assertThat(entity.decisionDeadline()).isEqualTo(expectedDecisionDeadline);
        assertThat(entity.decisionDeadline()).isAfter(entity.submissionDeadline());
    }

    /**
     * Protects {@code COR-003} and {@code AC-COR-001}: submission rejected immediately after 48-hour deadline.
     * Hand-derived calculation from policy 1: For work date 2026-08-06, E = 2026-08-06T08:30:00Z.
     * Submission deadline = E + 48 hours = 2026-08-08T08:30:00Z.
     * First later instant now = E + 48h + 1 us = 2026-08-08T08:30:00.000001Z.
     * Observable break: late request accepted after deadline.
     * Expected: rejected with "Correction submission deadline has passed", 0 corrections created.
     */
    @Test
    void submissionRefusedImmediatelyAfterFortyEightHourDeadline() {
        LocalDate workDate = LocalDate.of(2026, 8, 6);
        long recordId = createMissingCheckoutRecord(workDate, Instant.parse("2026-08-06T01:30:00Z"));

        clock.set(Instant.parse("2026-08-08T08:30:00.000001Z"));
        assertThatThrownBy(() -> corrections.submit(
                internActor, recordId,
                new CorrectionRequestCommand(LocalDateTime.of(2026, 8, 6, 15, 0), "Too late")))
                .isInstanceOf(CorrectionException.class)
                .hasMessage("Correction submission deadline has passed");

        assertThat(correctionRequests.findByAttendanceRecordId(recordId)).isEmpty();
    }

    /**
     * Protects {@code COR-001} and {@code AC-COR-001}: at most one correction request per attendance row.
     * Hand-derived calculation from policy 1: For work date 2026-08-07, E = 2026-08-07T08:30:00Z, cutoff = 2026-08-07T09:00:00Z.
     * Submission at 2026-08-07T10:00:00Z succeeds once. Second submission on the same row is attempted.
     * Observable break: second correction accepted or corrupts existing request.
     * Expected: rejected with "One correction request already exists for this attendance row", exactly 1 row persists.
     */
    @Test
    void duplicateCorrectionRequestOnSameRowIsRefused() {
        LocalDate workDate = LocalDate.of(2026, 8, 7);
        long recordId = createMissingCheckoutRecord(workDate, Instant.parse("2026-08-07T01:30:00Z"));

        clock.set(Instant.parse("2026-08-07T10:00:00Z"));
        corrections.submit(
                internActor, recordId,
                new CorrectionRequestCommand(LocalDateTime.of(2026, 8, 7, 15, 0), "First submission"));
        assertThat(correctionRequests.findByAttendanceRecordId(recordId)).isPresent();

        assertThatThrownBy(() -> corrections.submit(
                internActor, recordId,
                new CorrectionRequestCommand(LocalDateTime.of(2026, 8, 7, 15, 0), "Second submission")))
                .isInstanceOf(CorrectionException.class)
                .hasMessage("One correction request already exists for this attendance row");

        long count = jdbc.queryForObject("select count(*) from attendance_corrections where attendance_record_id = ?",
                Long.class, recordId);
        assertThat(count).isEqualTo(1L);
    }

    /**
     * Protects {@code COR-001} and {@code AC-COR-001}: corrections require a missing raw checkout.
     * Hand-derived calculation from policy 1: For work date 2026-08-10, row has check_in_at = 2026-08-10T01:30:00Z
     * and raw check_out_at = 2026-08-10T08:30:00Z. Now = 2026-08-10T09:30:00Z (after cutoff).
     * Observable break: correction permitted on row with existing raw checkout.
     * Expected: rejected with "Corrections require a missing raw checkout", 0 corrections created.
     */
    @Test
    void correctionRefusedWhenRawCheckoutAlreadyExists() {
        LocalDate workDate = LocalDate.of(2026, 8, 10);
        AttendanceRecordEntity rowWithCheckout = new AttendanceRecordEntity(
                internId,
                workDate,
                1L,
                Instant.parse("2026-08-10T01:30:00Z"),
                Instant.parse("2026-08-10T08:30:00Z"));
        long recordId = records.saveAndFlush(rowWithCheckout).id();

        clock.set(Instant.parse("2026-08-10T09:30:00Z"));
        assertThatThrownBy(() -> corrections.submit(
                internActor, recordId,
                new CorrectionRequestCommand(LocalDateTime.of(2026, 8, 10, 15, 0), "Already checked out")))
                .isInstanceOf(CorrectionException.class)
                .hasMessage("Corrections require a missing raw checkout");

        assertThat(correctionRequests.findByAttendanceRecordId(recordId)).isEmpty();
    }

    /**
     * Protects {@code COR-002} and {@code AC-COR-002}: proposed checkout must be after check-in, on the same local date, and not in the future.
     * Hand-derived calculation from policy 1: For work date 2026-08-11, check-in = 2026-08-11T02:00:00Z (09:00 local).
     * Now = 2026-08-11T10:00:00Z (17:00 local).
     * Tested invalid values:
     * 1) Preceding check-in: 08:30 local = 2026-08-11T01:30:00Z (before check-in).
     * 2) Different local date: 2026-08-12T10:00:00 (next date).
     * 3) Future relative to now: 17:30 local = 2026-08-11T10:30:00Z (after now).
     * Observable break: invalid proposal accepted or partially persisted.
     * Expected: each rejected with "Proposed checkout must be after check-in, same date, and not future", row count remains 0.
     */
    @Test
    void invalidProposedCheckoutValuesAreRefusedWithoutCreatingRow() {
        LocalDate workDate = LocalDate.of(2026, 8, 11);
        long recordId = createMissingCheckoutRecord(workDate, Instant.parse("2026-08-11T02:00:00Z"));

        clock.set(Instant.parse("2026-08-11T10:00:00Z"));

        // 1) Preceding check-in (08:30 local is before 09:00 check-in)
        assertThatThrownBy(() -> corrections.submit(
                internActor, recordId,
                new CorrectionRequestCommand(LocalDateTime.of(2026, 8, 11, 8, 30), "Preceding check-in")))
                .isInstanceOf(CorrectionException.class)
                .hasMessage("Proposed checkout must be after check-in, same date, and not future");

        // 2) Different local date (Aug 12 10:00 local is after check-in and before now Aug 12 11:00 local, but on next date)
        clock.set(Instant.parse("2026-08-12T04:00:00Z"));
        assertThatThrownBy(() -> corrections.submit(
                internActor, recordId,
                new CorrectionRequestCommand(LocalDateTime.of(2026, 8, 12, 10, 0), "Different local date")))
                .isInstanceOf(CorrectionException.class)
                .hasMessage("Proposed checkout must be after check-in, same date, and not future");

        // 3) Future relative to now (17:30 local is 10:30 UTC > now 10:00 UTC)
        clock.set(Instant.parse("2026-08-11T10:00:00Z"));
        assertThatThrownBy(() -> corrections.submit(
                internActor, recordId,
                new CorrectionRequestCommand(LocalDateTime.of(2026, 8, 11, 17, 30), "Future checkout")))
                .isInstanceOf(CorrectionException.class)
                .hasMessage("Proposed checkout must be after check-in, same date, and not future");

        assertThat(correctionRequests.findByAttendanceRecordId(recordId)).isEmpty();
    }

    /**
     * Protects {@code COR-004}, {@code COR-005}, {@code D23}, and {@code AC-COR-005}: separate 48-hour decision window from submission instant S.
     * Hand-derived calculation from policy 1: For work date 2026-08-12, submitted at S = 2026-08-12T09:30:00Z.
     * Decision deadline = S + 48 hours = 2026-08-14T09:30:00Z exactly to the microsecond.
     * Mentor approves at decision_deadline - 1 us = 2026-08-14T09:29:59.999999Z.
     * Observable break: decision window closes at 24 hours or before decision_deadline.
     * Expected: approved successfully with status APPROVED.
     */
    @Test
    void decisionWindowExtendsFortyEightHoursFromSubmissionAndMentorMayApproveBeforeExpiry() {
        LocalDate workDate = LocalDate.of(2026, 8, 12);
        long recordId = createMissingCheckoutRecord(workDate, Instant.parse("2026-08-12T01:30:00Z"));

        Instant submissionTime = Instant.parse("2026-08-12T09:30:00Z");
        clock.set(submissionTime);

        CorrectionView view = corrections.submit(
                internActor, recordId,
                new CorrectionRequestCommand(LocalDateTime.of(2026, 8, 12, 15, 0), "Submitted at S"));

        Instant expectedDecisionDeadline = Instant.parse("2026-08-14T09:30:00Z");
        assertThat(view.decisionDeadline()).isEqualTo(expectedDecisionDeadline);
        AttendanceCorrectionEntity entity = correctionRequests.findById(view.id()).orElseThrow();
        assertThat(entity.decisionDeadline()).isEqualTo(expectedDecisionDeadline);

        // Mentor approves at decision_deadline - 1 microsecond
        Instant approveAt = expectedDecisionDeadline.minus(1, ChronoUnit.MICROS);
        clock.set(approveAt);

        CorrectionView decidedView = corrections.decide(
                mentorActor, view.id(), CorrectionDecision.APPROVE, "Approved before deadline", null);

        assertThat(decidedView.status()).isEqualTo(CorrectionStatus.APPROVED);
        assertThat(correctionRequests.findById(view.id()).orElseThrow().status()).isEqualTo(CorrectionStatus.APPROVED);
    }

    /**
     * Protects Task MC-02 deadline preservation invariant: existing rows retain historical 24-hour deadlines without migration rewrite.
     * Hand-derived calculation: row inserted via JDBC with historical 24h deadlines
     * (submission deadline E + 24h = 2026-08-14T08:30:00Z, decision deadline S + 24h = 2026-08-14T09:30:00Z).
     * Observable break: read path recalculates or updates deadlines to 48 hours.
     * Expected: view and DB columns retain original 24-hour timestamps.
     */
    @Test
    void historicalTwentyFourHourDeadlinesArePreservedOnStoredRecords() {
        LocalDate workDate = LocalDate.of(2026, 8, 13);
        long recordId = createMissingCheckoutRecord(workDate, Instant.parse("2026-08-13T01:30:00Z"));

        Instant submittedAt = Instant.parse("2026-08-13T09:30:00Z");
        Instant historicalSubmissionDeadline = Instant.parse("2026-08-14T08:30:00Z");
        Instant historicalDecisionDeadline = Instant.parse("2026-08-14T09:30:00Z");

        jdbc.update("""
                insert into attendance_corrections (
                    attendance_record_id, requested_checkout_at, reason, status,
                    submitted_at, submission_deadline, decision_deadline, version)
                values (?, ?, 'Old 24-hour rule correction', 'PENDING', ?, ?, ?, 0)
                """,
                recordId,
                Timestamp.from(Instant.parse("2026-08-13T08:00:00Z")),
                Timestamp.from(submittedAt),
                Timestamp.from(historicalSubmissionDeadline),
                Timestamp.from(historicalDecisionDeadline));

        Long correctionId = jdbc.queryForObject(
                "select id from attendance_corrections where attendance_record_id = ?",
                Long.class, recordId);

        clock.set(submittedAt.plusSeconds(3600));
        CorrectionView view = corrections.view(internActor, correctionId);

        assertThat(view.submissionDeadline()).isEqualTo(historicalSubmissionDeadline);
        assertThat(view.decisionDeadline()).isEqualTo(historicalDecisionDeadline);

        Map<String, Object> row = jdbc.queryForMap(
                "select submission_deadline, decision_deadline from attendance_corrections where id = ?",
                correctionId);
        assertThat(((Timestamp) row.get("submission_deadline")).toInstant()).isEqualTo(historicalSubmissionDeadline);
        assertThat(((Timestamp) row.get("decision_deadline")).toInstant()).isEqualTo(historicalDecisionDeadline);
    }

    /**
     * Protects {@code COR-001}, {@code COR-003}, and {@code AC-COR-001}: cutoff and submission deadline
     * derive from the attached policy version, not the default seeded policy.
     * Hand-derived calculation from a separately scheduled policy (zone UTC, 09:15–16:45, checkout grace 11 min):
     * For work date 2026-08-17, E = 2026-08-17T16:45:00Z, cutoff = E + 11 min = 2026-08-17T16:56:00Z,
     * submission deadline = E + 48 hours = 2026-08-19T16:45:00Z.
     * Observable break: if default seeded policy (15:30 Asia/Ho_Chi_Minh = 08:30 UTC, grace 30 min) is used instead,
     * cutoff = 2026-08-17T09:00:00Z and deadline = 2026-08-19T08:30:00Z, both wrong.
     * Expected: at now = cutoff (16:56:00Z), rejected with "Correction opens after the attached checkout cutoff".
     * At now = cutoff + 1 µs, accepted with submission_deadline = 2026-08-19T16:45:00Z.
     */
    @Test
    void correctionWindowsFollowTheAttachedPolicyVersion() {
        var laterPolicy = policyApplication.schedule(adminId, new AttendancePolicyCommand(
                LocalDate.of(2026, 9, 1),
                ZoneId.of("UTC"),
                LocalTime.of(9, 15),
                LocalTime.of(16, 45),
                7,
                11,
                4,
                new BigDecimal("0.10"),
                Set.of(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY)));
        long laterPolicyId = laterPolicy.policy().id();

        LocalDate workDate = LocalDate.of(2026, 8, 17);
        long recordId = createMissingCheckoutRecord(workDate, Instant.parse("2026-08-17T09:20:00Z"), laterPolicyId);

        // At cutoff exactly: rejected
        Instant cutoff = Instant.parse("2026-08-17T16:56:00Z");
        clock.set(cutoff);
        assertThatThrownBy(() -> corrections.submit(
                internActor, recordId,
                new CorrectionRequestCommand(LocalDateTime.of(2026, 8, 17, 16, 30), "At cutoff")))
                .isInstanceOf(CorrectionException.class)
                .hasMessage("Correction opens after the attached checkout cutoff");

        // At cutoff + 1 microsecond: accepted
        clock.set(cutoff.plus(1, ChronoUnit.MICROS));
        CorrectionView view = corrections.submit(
                internActor, recordId,
                new CorrectionRequestCommand(LocalDateTime.of(2026, 8, 17, 16, 30), "Just after cutoff"));

        assertThat(view).isNotNull();
        assertThat(view.status()).isEqualTo(CorrectionStatus.PENDING);
        Instant expectedSubmissionDeadline = Instant.parse("2026-08-19T16:45:00Z");
        assertThat(view.submissionDeadline()).isEqualTo(expectedSubmissionDeadline);
        AttendanceCorrectionEntity entity = correctionRequests.findByAttendanceRecordId(recordId).orElseThrow();
        assertThat(entity.submissionDeadline()).isEqualTo(expectedSubmissionDeadline);
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

    private long createMissingCheckoutRecord(LocalDate workDate, Instant checkInAt) {
        return createMissingCheckoutRecord(workDate, checkInAt, 1L);
    }

    private long createMissingCheckoutRecord(LocalDate workDate, Instant checkInAt, long policyVersionId) {
        AttendanceRecordEntity entity = new AttendanceRecordEntity(
                internId,
                workDate,
                policyVersionId,
                checkInAt,
                null);
        return records.saveAndFlush(entity).id();
    }
}
