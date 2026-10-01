package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lab.labtimesheet.feature.attendance.exception.AttendanceRecordNotFoundException;
import com.lab.labtimesheet.feature.attendance.exception.CorrectionException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.CorrectionEventType;
import com.lab.labtimesheet.feature.attendance.model.CorrectionStatus;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionDecision;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionEventView;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionRequestCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionView;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceRecordEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceCorrectionEventRepository;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration test suite for responsible-Mentor correction decisions, AMEND, REVERSE, and append-only history (MC-04).
 *
 * <p>Rules protected: {@code COR-005}, {@code COR-006}, {@code COR-007},
 * {@code AC-COR-003}, {@code AC-COR-004}, {@code ATT-024}, {@code AUTH-003}, {@code AUTH-012}.
 */
@Import(AttendancePersistenceIntegrationTest.IntegrationConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CorrectionDecisionHistoryIntegrationTest {

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
    @Autowired private AttendanceCorrectionEventRepository correctionEvents;
    @Autowired private JdbcTemplate jdbc;

    private long adminId;
    private long mentorId;
    private long internId;
    private AttendanceActor internActor;
    private AttendanceActor mentorActor;

    @BeforeEach
    void prepareResponsibleMentorAndIntern() {
        clock.set(Instant.parse("2026-08-01T00:00:00Z"));
        bootstrap.bootstrap("admin@example.test", "Admin", "correct horse battery staple");
        adminId = accounts.requireActiveAdminId("admin@example.test");
        if (!mailDelivery.isAvailable()) {
            long draftId = smtp.saveDraft(adminId, new SmtpDraft(
                    "mailpit", 1025, SecurityMode.NONE, null, null, "admin@example.test", "Lab Timesheet"));
            smtp.testDraft(draftId, adminId, "admin@example.test");
            smtp.activate(draftId, adminId);
        }
        String fixture = UUID.randomUUID().toString().substring(0, 8);
        mentorId = createActiveUser("mentor-mc04-" + fixture + "@example.test", GlobalRole.MENTOR, null);
        internId = createActiveUser("intern-mc04-" + fixture + "@example.test", GlobalRole.INTERN,
                "INT-MC04-" + fixture);
        internships.activateInternship(internId, adminId);
        internActor = new AttendanceActor(internId, GlobalRole.INTERN);
        mentorActor = new AttendanceActor(mentorId, GlobalRole.MENTOR);
        // Set responsible Mentor for this Intern
        jdbc.update("update intern_profiles set responsible_mentor_user_id = ? where user_id = ?",
                mentorId, internId);
    }

    /**
     * Protects {@code COR-005}, {@code COR-007}, {@code AC-COR-003}, {@code AC-COR-004},
     * {@code ATT-024}: responsible Mentor approves, then amends the decision note after the decision deadline,
     * then reverses to REJECTED, then fails to amend without a reason.
     *
     * <p>Hand-derived expectations (policy 1: zone Asia/Ho_Chi_Minh UTC+7, scheduled 08:30–15:30, grace 30 min):
     * Work date 2026-08-12; check-in = 01:30 UTC; correction submitted at S = 2026-08-12T09:30:00Z.
     * Proposed checkout is 15:00 on 2026-08-12 in Asia/Ho_Chi_Minh (UTC+7), which corresponds to 2026-08-12T08:00:00Z.
     * Because scheduledEnd is 15:30 Asia/Ho_Chi_Minh and proposed 15:00 is strictly before scheduledEnd,
     * earlyDeparture is true and missingCheckout is false after approve.
     * decision_deadline = S + 48h = 2026-08-14T09:30:00Z.
     * Approve at T0 = 2026-08-12T10:00:00Z → APPROVED, effectiveCheckout = proposedCheckout instant = 2026-08-12T08:00:00Z.
     * Raw checkout always null. After approve: missingCheckout flag cleared, earlyDeparture flag is true.
     * Advance clock 2 days past deadline to T1 = 2026-08-14T11:00:00Z.
     * AMEND with new note "amended note" and reason "correction needed" → events: SUBMITTED, APPROVED, AMENDED
     *   where AMENDED: from=APPROVED, to=APPROVED, note=reason "correction needed".
     * REVERSE with reason "wrong decision" → REJECTED, events: SUBMITTED, APPROVED, AMENDED, REVERSED
     *   where REVERSED: from=APPROVED, to=REJECTED, note=reason "wrong decision".
     *   After reverse: effective checkout = null, missingCheckout flag restored (true), earlyDeparture is false.
     * AMEND without reason → rejected with "A reason is required to amend or reverse a correction decision",
     *   no new event row; REVERSED event row unchanged (snapshot before = snapshot after).
     * Status never returns to PENDING. No OVERDUE or LOCKED events on an already-decided correction.
     */
    @Test
    void responsibleMentorApprovesAmendsThenReversesAndFailsToAmendWithoutReason() {
        LocalDate workDate = LocalDate.of(2026, 8, 12);
        Instant checkIn = Instant.parse("2026-08-12T01:30:00Z");
        long recordId = createMissingCheckoutRecord(workDate, checkIn);

        Instant submissionTime = Instant.parse("2026-08-12T09:30:00Z");
        clock.set(submissionTime);
        CorrectionView submitted = corrections.submit(internActor, recordId,
                new CorrectionRequestCommand(LocalDateTime.of(2026, 8, 12, 15, 0), "Forgot checkout"));
        long correctionId = submitted.id();

        // T0: approve
        Instant approveTime = Instant.parse("2026-08-12T10:00:00Z");
        clock.set(approveTime);
        CorrectionView afterApprove = corrections.decide(mentorActor, correctionId,
                CorrectionDecision.APPROVE, "looks good", null);

        assertThat(afterApprove.status()).isEqualTo(CorrectionStatus.APPROVED);
        // After approve: effective checkout = proposed checkout instant (2026-08-12T08:00:00Z)
        assertThat(afterApprove.effectiveCheckoutAt()).isEqualTo(Instant.parse("2026-08-12T08:00:00Z"));
        // Raw checkout always null for a correction
        assertThat(afterApprove.rawCheckoutAt()).isNull();
        // Missing-checkout flag cleared after approve
        assertThat(afterApprove.violations().missingCheckout()).isFalse();
        // Early departure flag is true because proposed 15:00 is before scheduledEnd 15:30
        assertThat(afterApprove.violations().earlyDeparture()).isTrue();

        List<CorrectionEventView> eventsAfterApprove = correctionEvents
                .findByCorrectionIdOrderByOccurredAtAscIdAsc(correctionId).stream()
                .map(e -> e.toView())
                .toList();
        assertThat(eventsAfterApprove).hasSize(2);
        assertThat(eventsAfterApprove.get(1).occurredAt()).isEqualTo(approveTime);

        // Snapshot before AMEND
        List<java.util.Map<String, Object>> snapshotBeforeAmend = queryEventSnapshot(correctionId);

        // Advance past decision_deadline: 2026-08-14T09:30:00Z + 1.5 hours
        Instant afterDeadline = Instant.parse("2026-08-14T11:00:00Z");
        clock.set(afterDeadline);

        // AMEND with new note + reason
        CorrectionView afterAmend = corrections.decide(mentorActor, correctionId,
                CorrectionDecision.AMEND, "amended note", "correction needed");

        assertThat(afterAmend.status()).isEqualTo(CorrectionStatus.APPROVED);

        // Check events: SUBMITTED, APPROVED, AMENDED in order
        List<CorrectionEventView> eventsAfterAmend = correctionEvents
                .findByCorrectionIdOrderByOccurredAtAscIdAsc(correctionId).stream()
                .map(e -> e.toView())
                .toList();
        assertThat(eventsAfterAmend).hasSize(3);
        assertThat(eventsAfterAmend.get(0).type()).isEqualTo(CorrectionEventType.SUBMITTED);
        assertThat(eventsAfterAmend.get(1).type()).isEqualTo(CorrectionEventType.APPROVED);
        assertThat(eventsAfterAmend.get(1).actorUserId()).isEqualTo(mentorId);
        CorrectionEventView amendedEvent = eventsAfterAmend.get(2);
        assertThat(amendedEvent.type()).isEqualTo(CorrectionEventType.AMENDED);
        // AMENDED: from_status = to_status = APPROVED; note = reason
        assertThat(amendedEvent.fromStatus()).isEqualTo(CorrectionStatus.APPROVED);
        assertThat(amendedEvent.toStatus()).isEqualTo(CorrectionStatus.APPROVED);
        assertThat(amendedEvent.note()).isEqualTo("correction needed");
        assertThat(amendedEvent.actorUserId()).isEqualTo(mentorId);
        assertThat(amendedEvent.occurredAt()).isEqualTo(afterDeadline);

        // Snapshot comparison after AMEND: prior events unchanged, exactly 1 appended row
        List<java.util.Map<String, Object>> snapshotAfterAmend = queryEventSnapshot(correctionId);
        assertThat(snapshotAfterAmend).hasSize(snapshotBeforeAmend.size() + 1);
        assertThat(snapshotAfterAmend.subList(0, snapshotBeforeAmend.size())).isEqualTo(snapshotBeforeAmend);

        // Snapshot before REVERSE
        List<java.util.Map<String, Object>> snapshotBeforeReverse = queryEventSnapshot(correctionId);

        // REVERSE with reason at T2
        Instant reverseTime = Instant.parse("2026-08-14T12:00:00Z");
        clock.set(reverseTime);
        CorrectionView afterReverse = corrections.decide(mentorActor, correctionId,
                CorrectionDecision.REVERSE, null, "wrong decision");

        assertThat(afterReverse.status()).isEqualTo(CorrectionStatus.REJECTED);
        // After reverse: effective checkout = null (raw null, approved proposal no longer applies)
        assertThat(afterReverse.effectiveCheckoutAt()).isNull();
        // Missing-checkout flag restored after reverse
        assertThat(afterReverse.violations().missingCheckout()).isTrue();
        assertThat(afterReverse.violations().earlyDeparture()).isFalse();

        // Check events: SUBMITTED, APPROVED, AMENDED, REVERSED in order
        List<CorrectionEventView> eventsAfterReverse = correctionEvents
                .findByCorrectionIdOrderByOccurredAtAscIdAsc(correctionId).stream()
                .map(e -> e.toView())
                .toList();
        assertThat(eventsAfterReverse).hasSize(4);

        // Old events unchanged (snapshot before == snapshot after)
        assertThat(eventsAfterReverse.get(0).type()).isEqualTo(CorrectionEventType.SUBMITTED);
        assertThat(eventsAfterReverse.get(1).type()).isEqualTo(CorrectionEventType.APPROVED);
        // AMENDED event unchanged
        CorrectionEventView amendedEventSnapshot = eventsAfterReverse.get(2);
        assertThat(amendedEventSnapshot.type()).isEqualTo(CorrectionEventType.AMENDED);
        assertThat(amendedEventSnapshot.fromStatus()).isEqualTo(CorrectionStatus.APPROVED);
        assertThat(amendedEventSnapshot.toStatus()).isEqualTo(CorrectionStatus.APPROVED);
        assertThat(amendedEventSnapshot.note()).isEqualTo("correction needed");

        CorrectionEventView reversedEvent = eventsAfterReverse.get(3);
        assertThat(reversedEvent.type()).isEqualTo(CorrectionEventType.REVERSED);
        assertThat(reversedEvent.fromStatus()).isEqualTo(CorrectionStatus.APPROVED);
        assertThat(reversedEvent.toStatus()).isEqualTo(CorrectionStatus.REJECTED);
        assertThat(reversedEvent.note()).isEqualTo("wrong decision");
        assertThat(reversedEvent.actorUserId()).isEqualTo(mentorId);
        assertThat(reversedEvent.occurredAt()).isEqualTo(reverseTime);

        // Snapshot comparison after REVERSE: prior events unchanged, exactly 1 appended row
        List<java.util.Map<String, Object>> snapshotAfterReverse = queryEventSnapshot(correctionId);
        assertThat(snapshotAfterReverse).hasSize(snapshotBeforeReverse.size() + 1);
        assertThat(snapshotAfterReverse.subList(0, snapshotBeforeReverse.size())).isEqualTo(snapshotBeforeReverse);

        // Status never returned to PENDING after initial submission — check DB
        long reopenedToPendingCount = jdbc.queryForObject(
                "select count(*) from attendance_correction_events where correction_id = ? and event_type <> 'SUBMITTED' and to_status = 'PENDING'",
                Long.class, correctionId);
        assertThat(reopenedToPendingCount).isZero();
        assertThat(correctionRequests.findById(correctionId).orElseThrow().status())
                .isNotEqualTo(CorrectionStatus.PENDING);

        // No OVERDUE or LOCKED events on already-decided correction
        long overdueCount = jdbc.queryForObject(
                "select count(*) from attendance_correction_events where correction_id = ? and event_type in ('OVERDUE','LOCKED')",
                Long.class, correctionId);
        assertThat(overdueCount).isZero();

        // AMEND without reason: rejected, nothing changes
        List<java.util.Map<String, Object>> snapshotBeforeFailedAmend = queryEventSnapshot(correctionId);
        int eventCountBefore = eventsAfterReverse.size();
        assertThatThrownBy(() -> corrections.decide(mentorActor, correctionId,
                CorrectionDecision.AMEND, "new note", null))
                .isInstanceOf(CorrectionException.class)
                .hasMessage("A reason is required to amend or reverse a correction decision");

        List<java.util.Map<String, Object>> snapshotAfterFailedAmend = queryEventSnapshot(correctionId);
        assertThat(snapshotAfterFailedAmend).isEqualTo(snapshotBeforeFailedAmend);

        long eventCountAfter = jdbc.queryForObject(
                "select count(*) from attendance_correction_events where correction_id = ?",
                Long.class, correctionId);
        assertThat(eventCountAfter).isEqualTo(eventCountBefore);

        // Correction remains REJECTED after failure
        assertThat(correctionRequests.findById(correctionId).orElseThrow().status())
                .isEqualTo(CorrectionStatus.REJECTED);
    }

    /**
     * Protects {@code AUTH-003}, {@code AUTH-012}, {@code ATT-024}, the responsible-Mentor half of COR-009; its finalized-period half is not exercised:
     * only the responsible Mentor may decide a correction; non-responsible Mentor, Admin,
     * deactivated responsible Mentor, and owning Intern all receive AccessDeniedException,
     * and no row or event is created. Non-existent id throws AttendanceRecordNotFoundException.
     *
     * <p>Hand-derived expectation: before each probe, event count = 1 (SUBMITTED). After each
     * denied probe, event count = 1 (unchanged). DB rows unchanged, including status,
     * decided_by_mentor_user_id, decided_at, and decision_note.
     */
    @Test
    void onlyResponsibleMentorIsAllowedToDecideOtherActorsAreDenied() {
        LocalDate workDate = LocalDate.of(2026, 8, 18);
        long recordId = createMissingCheckoutRecord(workDate, Instant.parse("2026-08-18T01:30:00Z"));
        clock.set(Instant.parse("2026-08-18T09:30:00Z"));
        CorrectionView submitted = corrections.submit(internActor, recordId,
                new CorrectionRequestCommand(LocalDateTime.of(2026, 8, 18, 15, 0), "Auth probe"));
        long correctionId = submitted.id();

        // Baseline correction fields before any auth probe
        java.util.Map<String, Object> fieldsBefore = jdbc.queryForMap(
                "select status, decided_by_mentor_user_id, decided_at, decision_note from attendance_corrections where id = ?",
                correctionId);

        // Create a second active Mentor who is NOT the responsible Mentor
        String fixture = UUID.randomUUID().toString().substring(0, 8);
        long otherMentorId = createActiveUser("mentor-other-" + fixture + "@example.test", GlobalRole.MENTOR, null);
        AttendanceActor otherMentor = new AttendanceActor(otherMentorId, GlobalRole.MENTOR);

        // Probe: other active Mentor (not responsible) → AccessDeniedException
        long eventsBefore = countEvents(correctionId);
        assertThatThrownBy(() -> corrections.decide(otherMentor, correctionId,
                CorrectionDecision.APPROVE, null, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(countEvents(correctionId)).isEqualTo(eventsBefore);
        assertThat(jdbc.queryForMap(
                "select status, decided_by_mentor_user_id, decided_at, decision_note from attendance_corrections where id = ?",
                correctionId)).isEqualTo(fieldsBefore);

        // Probe: Admin → AccessDeniedException
        AttendanceActor adminActor = new AttendanceActor(adminId, GlobalRole.ADMIN);
        assertThatThrownBy(() -> corrections.decide(adminActor, correctionId,
                CorrectionDecision.APPROVE, null, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(countEvents(correctionId)).isEqualTo(eventsBefore);
        assertThat(jdbc.queryForMap(
                "select status, decided_by_mentor_user_id, decided_at, decision_note from attendance_corrections where id = ?",
                correctionId)).isEqualTo(fieldsBefore);

        // Probe: owning Intern → AccessDeniedException
        assertThatThrownBy(() -> corrections.decide(internActor, correctionId,
                CorrectionDecision.APPROVE, null, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(countEvents(correctionId)).isEqualTo(eventsBefore);
        assertThat(jdbc.queryForMap(
                "select status, decided_by_mentor_user_id, decided_at, decision_note from attendance_corrections where id = ?",
                correctionId)).isEqualTo(fieldsBefore);

        // Probe: locked (deactivated) responsible Mentor → AccessDeniedException
        accounts.lockAccount(mentorId, adminId);
        assertThatThrownBy(() -> corrections.decide(mentorActor, correctionId,
                CorrectionDecision.APPROVE, null, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(countEvents(correctionId)).isEqualTo(eventsBefore);
        assertThat(jdbc.queryForMap(
                "select status, decided_by_mentor_user_id, decided_at, decision_note from attendance_corrections where id = ?",
                correctionId)).isEqualTo(fieldsBefore);

        // Restore mentor for cleanup
        accounts.unlockAccount(mentorId, adminId);

        // Probe: non-existent id → AttendanceRecordNotFoundException
        long nonExistentId = 999_999_999L;
        assertThatThrownBy(() -> corrections.decide(mentorActor, nonExistentId,
                CorrectionDecision.APPROVE, null, null))
                .isInstanceOf(AttendanceRecordNotFoundException.class);
    }

    /**
     * Protects {@code AC-COR-004}: legacy REOPENED events stored before MC-04
     * must still load without error when viewing correction history, and report type REOPENED.
     */
    @Test
    void legacyReopenedEventStillLoadsInCorrectionHistory() {
        LocalDate workDate = LocalDate.of(2026, 8, 10);
        long recordId = createMissingCheckoutRecord(workDate, Instant.parse("2026-08-10T01:30:00Z"));
        clock.set(Instant.parse("2026-08-10T09:30:00Z"));
        CorrectionView submitted = corrections.submit(internActor, recordId,
                new CorrectionRequestCommand(LocalDateTime.of(2026, 8, 10, 15, 0), "Forgot checkout"));
        long correctionId = submitted.id();

        // Simulate legacy REOPENED event stored in DB before MC-04
        jdbc.update(
                "insert into attendance_correction_events (correction_id, event_type, from_status, to_status, actor_user_id, note, occurred_at) "
                        + "values (?, 'REOPENED', 'APPROVED', 'PENDING', ?, 'legacy reopen note', '2026-08-10 11:00:00+00'::timestamptz)",
                correctionId, mentorId);

        // Loading view must not fail and must contain the REOPENED event
        CorrectionView view = corrections.view(internActor, correctionId);
        assertThat(view.events()).isNotEmpty();
        CorrectionEventView reopenedEvent = view.events().stream()
                .filter(e -> "REOPENED".equals(e.type().name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected REOPENED event to be present in history"));
        assertThat(reopenedEvent.type()).isEqualTo(CorrectionEventType.REOPENED);
        assertThat(reopenedEvent.fromStatus()).isEqualTo(CorrectionStatus.APPROVED);
        assertThat(reopenedEvent.toStatus()).isEqualTo(CorrectionStatus.PENDING);
    }

    /**
     * Protects {@code COR-007}: a legacy locked decided correction can still be amended with a reason.
     * Legacy {@code locked_at} marker must not block amending or reversing an already-decided correction.
     */
    @Test
    void legacyLockedDecidedCorrectionCanStillBeAmendedWithReason() {
        LocalDate workDate = LocalDate.of(2026, 8, 11);
        long recordId = createMissingCheckoutRecord(workDate, Instant.parse("2026-08-11T01:30:00Z"));
        clock.set(Instant.parse("2026-08-11T09:30:00Z"));
        CorrectionView submitted = corrections.submit(internActor, recordId,
                new CorrectionRequestCommand(LocalDateTime.of(2026, 8, 11, 15, 0), "Forgot checkout"));
        long correctionId = submitted.id();

        // Approve the correction
        clock.set(Instant.parse("2026-08-11T10:00:00Z"));
        corrections.decide(mentorActor, correctionId, CorrectionDecision.APPROVE, "initial approve", null);

        // Simulate legacy locked_at marker via JDBC
        jdbc.update("update attendance_corrections set locked_at = '2026-08-11 12:00:00+00'::timestamptz where id = ?",
                correctionId);

        // AMEND with reason must succeed despite legacy locked_at
        clock.set(Instant.parse("2026-08-11T14:00:00Z"));
        CorrectionView amended = corrections.decide(mentorActor, correctionId,
                CorrectionDecision.AMEND, "amended note on locked row", "valid amendment reason");

        assertThat(amended.status()).isEqualTo(CorrectionStatus.APPROVED);
        List<CorrectionEventView> events = correctionEvents
                .findByCorrectionIdOrderByOccurredAtAscIdAsc(correctionId).stream()
                .map(e -> e.toView())
                .toList();
        assertThat(events).hasSize(3); // SUBMITTED, APPROVED, AMENDED
        CorrectionEventView lastEvent = events.get(2);
        assertThat(lastEvent.type()).isEqualTo(CorrectionEventType.AMENDED);
        assertThat(lastEvent.note()).isEqualTo("valid amendment reason");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

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

    private long createMissingCheckoutRecord(LocalDate workDate, Instant checkInAt) {
        AttendanceRecordEntity entity = new AttendanceRecordEntity(internId, workDate, 1L, checkInAt, null);
        return records.saveAndFlush(entity).id();
    }

    private long countEvents(long correctionId) {
        return jdbc.queryForObject(
                "select count(*) from attendance_correction_events where correction_id = ?",
                Long.class, correctionId);
    }

    private List<java.util.Map<String, Object>> queryEventSnapshot(long correctionId) {
        return jdbc.queryForList(
                "select id, correction_id, event_type, from_status, to_status, actor_user_id, note, occurred_at "
                        + "from attendance_correction_events where correction_id = ? order by id asc",
                correctionId);
    }
}
