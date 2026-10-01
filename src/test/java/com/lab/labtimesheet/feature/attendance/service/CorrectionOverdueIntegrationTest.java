package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lab.labtimesheet.feature.attendance.exception.CorrectionException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.CorrectionEventType;
import com.lab.labtimesheet.feature.attendance.model.CorrectionStatus;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionDecision;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionEventView;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionRequestCommand;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceCorrectionEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceCorrectionEventEntity;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies missed-checkout correction OVERDUE transitions, D47 routing, decision processing, and monthly queries.
 * Protects {@code COR-005}, {@code COR-006}, {@code COR-007}, {@code COR-008}, {@code DB-017}, {@code DB-018},
 * {@code NOT-011}, and {@code D47}.
 */
@Import(AttendancePersistenceIntegrationTest.IntegrationConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CorrectionOverdueIntegrationTest {

    private static final Instant T_CHECKIN = Instant.parse("2026-08-14T02:00:00Z"); // 09:00 +07
    private static final Instant T_SUBMIT = Instant.parse("2026-08-14T11:00:00Z"); // 18:00 +07
    private static final LocalDate WORK_DATE = LocalDate.of(2026, 8, 14);

    @Autowired private AttendanceCorrectionApplicationService corrections;
    @Autowired private AttendanceApplicationService attendance;
    @Autowired private AttendanceRecordRepository records;
    @Autowired private AttendanceCorrectionRepository correctionRepository;
    @Autowired private AttendanceCorrectionEventRepository correctionEvents;
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
    private long otherMentorId;
    private long internId;
    private long recordId;
    private String fixture;

    @BeforeEach
    void setupFixture() {
        fixture = UUID.randomUUID().toString();
        clock.set(T_CHECKIN);
        bootstrap.bootstrap("admin@example.test", "Admin", "correct horse battery staple");
        adminId = accounts.requireActiveAdminId("admin@example.test");
        if (!mailDelivery.isAvailable()) {
            long draftId = smtp.saveDraft(adminId, new SmtpDraft(
                    "mailpit", 1025, SecurityMode.NONE, null, null, "admin@example.test", "Lab Timesheet"));
            smtp.testDraft(draftId, adminId, "admin@example.test");
            smtp.activate(draftId, adminId);
        }
        mentorId = createActiveUser("mentor-" + fixture + "@example.test", GlobalRole.MENTOR, null);
        otherMentorId = createActiveUser("other-mentor-" + fixture + "@example.test", GlobalRole.MENTOR, null);
        internId = createActiveUser("intern-" + fixture + "@example.test", GlobalRole.INTERN,
                "INT-" + fixture.substring(0, 8));
        assignMentor(internId, mentorId);
        internships.activateInternship(internId, adminId);
        attendance.checkIn(internId);
        recordId = records.findByInternUserIdAndWorkDate(internId, WORK_DATE).orElseThrow().id();
    }

    /**
     * Protects {@code COR-005}, {@code DB-018}, and {@code AC-COR-005}: a correction remains PENDING at deadline - 1 us,
     * then transitions to OVERDUE on the first read access across all access paths (Mentor list, Intern list,
     * prepareHistory, view). Decided fields and locked_at remain null; events are exactly [SUBMITTED, OVERDUE].
     */
    @Test
    void accessTransitionsPendingToOverdueAtDeadlineAcrossAllAccessPaths() {
        // Path a: Mentor list
        long corrMentorList = createPendingCorrection(internId, recordId);
        AttendanceCorrectionEntity entity = correctionRepository.findById(corrMentorList).orElseThrow();
        Instant deadline = entity.decisionDeadline();

        clock.set(deadline.minusNanos(1_000));
        var listBefore = corrections.list(new AttendanceActor(mentorId, GlobalRole.MENTOR));
        assertThat(listBefore.stream().filter(s -> s.id() == corrMentorList).findFirst().orElseThrow().status())
                .isEqualTo("PENDING");

        clock.set(deadline);
        var listAfter = corrections.list(new AttendanceActor(mentorId, GlobalRole.MENTOR));
        assertThat(listAfter.stream().filter(s -> s.id() == corrMentorList).findFirst().orElseThrow().status())
                .isEqualTo("OVERDUE");
        assertUndecidedOverdueState(corrMentorList);

        // Path b: Intern list
        long corrInternList = createAdditionalPendingCorrection();
        Instant deadlineIntern = correctionRepository.findById(corrInternList).orElseThrow().decisionDeadline();
        clock.set(deadlineIntern);
        var internList = corrections.list(new AttendanceActor(internId, GlobalRole.INTERN));
        assertThat(internList.stream().filter(s -> s.id() == corrInternList).findFirst().orElseThrow().status())
                .isEqualTo("OVERDUE");
        assertUndecidedOverdueState(corrInternList);

        // Path c: prepareHistory
        long corrHistory = createAdditionalPendingCorrection();
        Instant deadlineHistory = correctionRepository.findById(corrHistory).orElseThrow().decisionDeadline();
        AttendanceRecordEntity historyEntity = records.findById(
                correctionRepository.findById(corrHistory).orElseThrow().attendanceRecordId()).orElseThrow();
        clock.set(deadlineHistory);
        corrections.prepareHistory(List.of(historyEntity));
        assertUndecidedOverdueState(corrHistory);

        // Path d: view
        long corrView = createAdditionalPendingCorrection();
        Instant deadlineView = correctionRepository.findById(corrView).orElseThrow().decisionDeadline();
        clock.set(deadlineView);
        var view = corrections.view(new AttendanceActor(internId, GlobalRole.INTERN), corrView);
        assertThat(view.status()).isEqualTo(CorrectionStatus.OVERDUE);
        assertUndecidedOverdueState(corrView);
    }

    /**
     * Protects {@code COR-005}, {@code NOT-011}, and {@code DB-018}: repeated read-time access and scheduled expire(100)
     * must not duplicate transitions or reminders. Observable break: duplicate OVERDUE events or notifications.
     */
    @Test
    void repeatedAccessAndScheduledSweepProduceExactlyOneOverdueEventAndReminder() {
        long id = createPendingCorrection(internId, recordId);
        Instant deadline = correctionRepository.findById(id).orElseThrow().decisionDeadline();
        clock.set(deadline);

        corrections.view(new AttendanceActor(mentorId, GlobalRole.MENTOR), id);
        corrections.view(new AttendanceActor(internId, GlobalRole.INTERN), id);
        corrections.list(new AttendanceActor(mentorId, GlobalRole.MENTOR));
        corrections.list(new AttendanceActor(internId, GlobalRole.INTERN));
        int expired = corrections.expire(100);

        assertThat(expired).isEqualTo(0);
        assertThat(correctionRepository.findById(id).orElseThrow().status()).isEqualTo(CorrectionStatus.OVERDUE);
        List<CorrectionEventView> eventList =
                correctionEvents.findByCorrectionIdOrderByOccurredAtAscIdAsc(id).stream()
                        .map(AttendanceCorrectionEventEntity::toView)
                        .toList();
        assertThat(eventList).extracting(CorrectionEventView::type)
                .containsExactly(CorrectionEventType.SUBMITTED, CorrectionEventType.OVERDUE);
        assertThat(countOverdueReminders(mentorId)).isEqualTo(1);
    }

    /**
     * Protects {@code COR-005} and {@code NOT-011}: scheduler expire transitions due requests when run before
     * user access, reports the exact changed count, and respects batch size.
     */
    @Test
    void schedulerTransitionsDueRequestsAndRespectsBatchSize() {
        clock.set(Instant.parse("2026-12-31T00:00:00Z"));
        corrections.expire(1000);

        long id1 = createPendingCorrection(internId, recordId);
        long id2 = createAdditionalPendingCorrection();
        long id3 = createAdditionalPendingCorrection();
        Instant maxDeadline = correctionRepository.findById(id3).orElseThrow().decisionDeadline();
        clock.set(maxDeadline.plusSeconds(10));

        int batch1 = corrections.expire(2);
        assertThat(batch1).isEqualTo(2);

        int batch2 = corrections.expire(2);
        assertThat(batch2).isEqualTo(1);

        int batch3 = corrections.expire(2);
        assertThat(batch3).isEqualTo(0);

        for (long id : List.of(id1, id2, id3)) {
            assertUndecidedOverdueState(id);
        }
        assertThat(countOverdueReminders(mentorId)).isEqualTo(3);
    }

    /**
     * Protects {@code COR-005}: an already OVERDUE row is excluded from the scheduler candidate batch.
     * Observable break: with batch size one, an older OVERDUE row consumes the sole slot and leaves a due PENDING row
     * untouched; the hand-derived expected transition count is one for the pending row.
     */
    @Test
    void overdueRowsDoNotConsumeSchedulerBatchSlots() {
        long alreadyOverdueId = createPendingCorrection(internId, recordId);
        Instant firstDeadline = correctionRepository.findById(alreadyOverdueId).orElseThrow().decisionDeadline();
        clock.set(firstDeadline);
        assertThat(corrections.expire(1)).isEqualTo(1);
        assertThat(correctionRepository.findById(alreadyOverdueId).orElseThrow().status())
                .isEqualTo(CorrectionStatus.OVERDUE);

        long pendingId = createAdditionalPendingCorrection();
        Instant pendingDeadline = correctionRepository.findById(pendingId).orElseThrow().decisionDeadline();
        clock.set(pendingDeadline.plusNanos(1));

        assertThat(corrections.expire(1)).isEqualTo(1);
        assertThat(correctionRepository.findById(pendingId).orElseThrow().status())
                .isEqualTo(CorrectionStatus.OVERDUE);
        assertThat(correctionRepository.findById(alreadyOverdueId).orElseThrow().status())
                .isEqualTo(CorrectionStatus.OVERDUE);
    }

    /**
     * Protects {@code COR-006} and {@code COR-007}: Mentor can approve an OVERDUE request; effective checkout is
     * reflected in prepareHistory matching requested proposal, and event history records APPROVED from OVERDUE.
     */
    @Test
    void mentorApprovesOverdueRequest() {
        long id = createPendingCorrection(internId, recordId);
        AttendanceCorrectionEntity entity = correctionRepository.findById(id).orElseThrow();
        Instant deadline = entity.decisionDeadline();
        clock.set(deadline);

        var decided = corrections.decide(new AttendanceActor(mentorId, GlobalRole.MENTOR), id,
                CorrectionDecision.APPROVE, "Approved overdue request", null);
        assertThat(decided.status()).isEqualTo(CorrectionStatus.APPROVED);

        var loaded = correctionRepository.findById(id).orElseThrow();
        assertThat(loaded.status()).isEqualTo(CorrectionStatus.APPROVED);
        assertThat(loaded.decidedByMentorUserId()).isEqualTo(mentorId);
        assertThat(loaded.decidedAt()).isEqualTo(deadline);
        assertThat(loaded.lockedAt()).isNull();

        List<CorrectionEventView> eventList =
                correctionEvents.findByCorrectionIdOrderByOccurredAtAscIdAsc(id).stream()
                        .map(AttendanceCorrectionEventEntity::toView)
                        .toList();
        assertThat(eventList).extracting(CorrectionEventView::type)
                .containsExactly(CorrectionEventType.SUBMITTED, CorrectionEventType.OVERDUE, CorrectionEventType.APPROVED);
        assertThat(eventList.get(2).fromStatus()).isEqualTo(CorrectionStatus.OVERDUE);
        assertThat(eventList.get(2).toStatus()).isEqualTo(CorrectionStatus.APPROVED);

        AttendanceRecordEntity recordEntity = records.findById(recordId).orElseThrow();
        Map<Long, Instant> history = corrections.prepareHistory(List.of(recordEntity));
        assertThat(history.get(recordId)).isEqualTo(entity.requestedCheckoutAt());
    }

    /**
     * Protects {@code COR-006} and {@code COR-007}: Mentor can reject an OVERDUE request; event records REJECTED from OVERDUE.
     */
    @Test
    void mentorRejectsOverdueRequest() {
        long id = createPendingCorrection(internId, recordId);
        Instant deadline = correctionRepository.findById(id).orElseThrow().decisionDeadline();
        clock.set(deadline);

        var decided = corrections.decide(new AttendanceActor(mentorId, GlobalRole.MENTOR), id,
                CorrectionDecision.REJECT, "Rejected overdue request", null);
        assertThat(decided.status()).isEqualTo(CorrectionStatus.REJECTED);

        var loaded = correctionRepository.findById(id).orElseThrow();
        assertThat(loaded.status()).isEqualTo(CorrectionStatus.REJECTED);
        assertThat(loaded.decidedByMentorUserId()).isEqualTo(mentorId);
        assertThat(loaded.lockedAt()).isNull();

        List<CorrectionEventView> eventList =
                correctionEvents.findByCorrectionIdOrderByOccurredAtAscIdAsc(id).stream()
                        .map(AttendanceCorrectionEventEntity::toView)
                        .toList();
        assertThat(eventList).extracting(CorrectionEventView::type)
                .containsExactly(CorrectionEventType.SUBMITTED, CorrectionEventType.OVERDUE, CorrectionEventType.REJECTED);
        assertThat(eventList.get(2).fromStatus()).isEqualTo(CorrectionStatus.OVERDUE);
        assertThat(eventList.get(2).toStatus()).isEqualTo(CorrectionStatus.REJECTED);
    }

    /**
     * Protects {@code COR-005}, {@code COR-007}, and {@code AC-COR-003}:
     * a decided request after the deadline is never locked ({@code locked_at} is null, no {@code LOCKED} event).
     * An AMEND without reason is refused, while an AMEND with reason succeeds, keeping status {@code APPROVED}.
     */
    @Test
    void decidedRequestAfterDeadlineIsNotLockedAndStaysAmendable() {
        long id = createPendingCorrection(internId, recordId);
        corrections.decide(new AttendanceActor(mentorId, GlobalRole.MENTOR), id,
                CorrectionDecision.APPROVE, "Initial approval", null);
        Instant deadline = correctionRepository.findById(id).orElseThrow().decisionDeadline();
        clock.set(deadline.plusSeconds(3600));

        corrections.view(new AttendanceActor(mentorId, GlobalRole.MENTOR), id);
        corrections.list(new AttendanceActor(mentorId, GlobalRole.MENTOR));
        corrections.expire(100);

        var loaded = correctionRepository.findById(id).orElseThrow();
        assertThat(loaded.lockedAt()).isNull();
        assertThat(loaded.status()).isEqualTo(CorrectionStatus.APPROVED);

        List<CorrectionEventView> eventList =
                correctionEvents.findByCorrectionIdOrderByOccurredAtAscIdAsc(id).stream()
                        .map(AttendanceCorrectionEventEntity::toView)
                        .toList();
        assertThat(eventList).extracting(CorrectionEventView::type)
                .containsExactly(CorrectionEventType.SUBMITTED, CorrectionEventType.APPROVED);

        // AMEND without reason: rejected, status remains APPROVED, events unchanged
        assertThatThrownBy(() -> corrections.decide(new AttendanceActor(mentorId, GlobalRole.MENTOR), id,
                CorrectionDecision.AMEND, "Attempted amend after deadline without reason", null))
                .isInstanceOf(CorrectionException.class)
                .hasMessage("A reason is required to amend or reverse a correction decision");

        var loadedAfterRefusal = correctionRepository.findById(id).orElseThrow();
        assertThat(loadedAfterRefusal.status()).isEqualTo(CorrectionStatus.APPROVED);
        assertThat(correctionEvents.findByCorrectionIdOrderByOccurredAtAscIdAsc(id)).hasSize(2);

        // AMEND with reason: succeeds after deadline, status remains APPROVED
        var amended = corrections.decide(new AttendanceActor(mentorId, GlobalRole.MENTOR), id,
                CorrectionDecision.AMEND, "Amended note after deadline", "valid reason for amendment");
        assertThat(amended.status()).isEqualTo(CorrectionStatus.APPROVED);

        var loadedAfterAmend = correctionRepository.findById(id).orElseThrow();
        assertThat(loadedAfterAmend.status()).isEqualTo(CorrectionStatus.APPROVED);
        assertThat(loadedAfterAmend.lockedAt()).isNull();

        List<CorrectionEventView> eventsAfterAmend =
                correctionEvents.findByCorrectionIdOrderByOccurredAtAscIdAsc(id).stream()
                        .map(AttendanceCorrectionEventEntity::toView)
                        .toList();
        assertThat(eventsAfterAmend).extracting(CorrectionEventView::type)
                .containsExactly(CorrectionEventType.SUBMITTED, CorrectionEventType.APPROVED, CorrectionEventType.AMENDED);
        assertThat(eventsAfterAmend.get(2).note()).isEqualTo("valid reason for amendment");
    }

    /**
     * Protects {@code COR-005}, {@code COR-007}, and {@code COR-008}: AMEND and REVERSE are only valid
     * on already-decided corrections (APPROVED or REJECTED). Both AMEND and REVERSE on PENDING and OVERDUE
     * corrections are rejected without writing any history events.
     */
    @Test
    void amendAndReverseAreRejectedForPendingAndOverdueWithoutWritingHistory() {
        long id = createPendingCorrection(internId, recordId);

        // PENDING: AMEND rejected
        assertThatThrownBy(() -> corrections.decide(new AttendanceActor(mentorId, GlobalRole.MENTOR), id,
                CorrectionDecision.AMEND, "Premature amend", "valid reason"))
                .isInstanceOf(CorrectionException.class)
                .hasMessage("Only approved or rejected correction can be amended or reversed");
        assertThat(correctionRepository.findById(id).orElseThrow().status()).isEqualTo(CorrectionStatus.PENDING);
        assertThat(correctionEvents.findByCorrectionIdOrderByOccurredAtAscIdAsc(id)).hasSize(1);

        // PENDING: REVERSE rejected
        assertThatThrownBy(() -> corrections.decide(new AttendanceActor(mentorId, GlobalRole.MENTOR), id,
                CorrectionDecision.REVERSE, null, "valid reason"))
                .isInstanceOf(CorrectionException.class)
                .hasMessage("Only approved or rejected correction can be amended or reversed");
        assertThat(correctionRepository.findById(id).orElseThrow().status()).isEqualTo(CorrectionStatus.PENDING);
        assertThat(correctionEvents.findByCorrectionIdOrderByOccurredAtAscIdAsc(id)).hasSize(1);

        // Advance past deadline to OVERDUE
        Instant deadline = correctionRepository.findById(id).orElseThrow().decisionDeadline();
        clock.set(deadline);
        assertThat(corrections.expire(100)).isEqualTo(1);
        assertThat(correctionRepository.findById(id).orElseThrow().status()).isEqualTo(CorrectionStatus.OVERDUE);
        assertThat(correctionEvents.findByCorrectionIdOrderByOccurredAtAscIdAsc(id)).hasSize(2);

        // OVERDUE: AMEND rejected
        assertThatThrownBy(() -> corrections.decide(new AttendanceActor(mentorId, GlobalRole.MENTOR), id,
                CorrectionDecision.AMEND, "Overdue amend", "valid reason"))
                .isInstanceOf(CorrectionException.class)
                .hasMessage("Only approved or rejected correction can be amended or reversed");
        assertThat(correctionEvents.findByCorrectionIdOrderByOccurredAtAscIdAsc(id)).hasSize(2);

        // OVERDUE: REVERSE rejected
        assertThatThrownBy(() -> corrections.decide(new AttendanceActor(mentorId, GlobalRole.MENTOR), id,
                CorrectionDecision.REVERSE, null, "valid reason"))
                .isInstanceOf(CorrectionException.class)
                .hasMessage("Only approved or rejected correction can be amended or reversed");
        assertThat(correctionEvents.findByCorrectionIdOrderByOccurredAtAscIdAsc(id)).hasSize(2);

        var overdue = correctionRepository.findById(id).orElseThrow();
        assertThat(overdue.status()).isEqualTo(CorrectionStatus.OVERDUE);
        assertThat(overdue.decidedAt()).isNull();
        assertThat(overdue.decidedByMentorUserId()).isNull();
        assertThat(overdue.lockedAt()).isNull();
    }

    /**
     * Protects {@code DB-017}: legacy event enum kinds AUTO_REJECTED and LOCKED still exist for backwards-compatible reading.
     */
    @Test
    void legacyEventTypesStillExist() {
        assertThat(CorrectionEventType.valueOf("AUTO_REJECTED")).isEqualTo(CorrectionEventType.AUTO_REJECTED);
        assertThat(CorrectionEventType.valueOf("LOCKED")).isEqualTo(CorrectionEventType.LOCKED);
    }

    /**
     * Protects {@code D47}, {@code NOT-011}, and data-model {@code C.6}: submission and overdue notifications route
     * according to D47.
     * - Submission notice goes only to active responsible Mentor, not other Mentors.
     * - Fallback to active Admins if no responsible Mentor.
     * - Fallback to active Admins if responsible Mentor is locked.
     * - Fallback to active Admins if responsible Mentor is deactivated.
     * - OVERDUE reminder is SYSTEM, projectId is null, action /attendance/corrections.
     * - Decision notice goes to Intern.
     */
    @Test
    void d47NotificationRouting() {
        // Subcase 1: Active responsible mentor receives submission notification; other mentor does not
        long id = createPendingCorrection(internId, recordId);
        int mentorNotifs = countNotifications(mentorId, "Correction submitted");
        int otherMentorNotifs = countNotifications(otherMentorId, "Correction submitted");
        assertThat(mentorNotifs).isEqualTo(1);
        assertThat(otherMentorNotifs).isEqualTo(0);

        // Subcase 2: Intern without responsible mentor falls back to all active Admins
        long internNoMentor = createActiveUser("intern-nomentor-" + fixture + "@example.test", GlobalRole.INTERN,
                "NOM-" + fixture.substring(0, 8));
        internships.activateInternship(internNoMentor, adminId);
        long recordNoMentor = createMissingCheckoutRecord(internNoMentor, LocalDate.of(2026, 8, 17),
                Instant.parse("2026-08-17T02:00:00Z"));
        int adminNotifsBefore = countNotifications(adminId, "Correction submitted");
        clock.set(Instant.parse("2026-08-17T11:00:00Z"));
        corrections.submit(new AttendanceActor(internNoMentor, GlobalRole.INTERN), recordNoMentor,
                new CorrectionRequestCommand(LocalDateTime.of(2026, 8, 17, 16, 45), "Missed checkout"));
        int adminNotifsAfter = countNotifications(adminId, "Correction submitted");
        assertThat(adminNotifsAfter - adminNotifsBefore).isEqualTo(1);

        // Subcase 3: Responsible mentor is LOCKED -> falls back to Admins
        long internLockedMentor = createActiveUser("intern-lockedmentor-" + fixture + "@example.test", GlobalRole.INTERN,
                "LCK-" + fixture.substring(0, 8));
        assignMentor(internLockedMentor, otherMentorId);
        internships.activateInternship(internLockedMentor, adminId);
        accounts.lockAccount(otherMentorId, adminId);
        long recordLockedMentor = createMissingCheckoutRecord(internLockedMentor, LocalDate.of(2026, 8, 18),
                Instant.parse("2026-08-18T02:00:00Z"));
        adminNotifsBefore = countNotifications(adminId, "Correction submitted");
        clock.set(Instant.parse("2026-08-18T11:00:00Z"));
        corrections.submit(new AttendanceActor(internLockedMentor, GlobalRole.INTERN), recordLockedMentor,
                new CorrectionRequestCommand(LocalDateTime.of(2026, 8, 18, 16, 45), "Missed checkout"));
        adminNotifsAfter = countNotifications(adminId, "Correction submitted");
        assertThat(adminNotifsAfter - adminNotifsBefore).isEqualTo(1);
        assertThat(countNotifications(otherMentorId, "Correction submitted")).isEqualTo(0);

        // Subcase 3b: Responsible mentor is DEACTIVATED -> falls back to Admins
        long deactivatedMentorId = createActiveUser(
                "deactivated-mentor-" + fixture + "@example.test", GlobalRole.MENTOR, null);
        long internDeactivatedMentor = createActiveUser(
                "intern-deactivated-mentor-" + fixture + "@example.test", GlobalRole.INTERN,
                "DEA-" + fixture.substring(0, 8));
        assignMentor(internDeactivatedMentor, deactivatedMentorId);
        internships.activateInternship(internDeactivatedMentor, adminId);
        accounts.deactivateAccount(deactivatedMentorId, adminId);
        long recordDeactivatedMentor = createMissingCheckoutRecord(internDeactivatedMentor, LocalDate.of(2026, 8, 19),
                Instant.parse("2026-08-19T02:00:00Z"));
        adminNotifsBefore = countNotifications(adminId, "Correction submitted");
        clock.set(Instant.parse("2026-08-19T11:00:00Z"));
        corrections.submit(new AttendanceActor(internDeactivatedMentor, GlobalRole.INTERN), recordDeactivatedMentor,
                new CorrectionRequestCommand(LocalDateTime.of(2026, 8, 19, 16, 45), "Missed checkout"));
        adminNotifsAfter = countNotifications(adminId, "Correction submitted");
        assertThat(adminNotifsAfter - adminNotifsBefore).isEqualTo(1);
        assertThat(countNotifications(deactivatedMentorId, "Correction submitted")).isZero();

        // Subcase 4: Overdue reminder has SYSTEM type, projectId null, action /attendance/corrections
        Instant deadline = correctionRepository.findById(id).orElseThrow().decisionDeadline();
        clock.set(deadline);
        corrections.expire(100);
        var reminder = jdbc.queryForMap("""
                select notification_type, title, action_url, project_id, email_status
                from notifications where recipient_user_id = ? and title = ?
                """, mentorId, "Correction request overdue");
        assertThat(reminder).containsEntry("notification_type", "SYSTEM")
                .containsEntry("title", "Correction request overdue")
                .containsEntry("action_url", "/attendance/corrections")
                .containsEntry("project_id", null);

        // Subcase 5: Decision notice goes to Intern
        corrections.decide(new AttendanceActor(mentorId, GlobalRole.MENTOR), id,
                CorrectionDecision.APPROVE, "Approved", null);
        int internDecisionNotifs = countNotifications(internId, "Correction decision");
        assertThat(internDecisionNotifs).isGreaterThanOrEqualTo(1);
    }

    /**
     * Protects {@code COR-005} and monthly finalization: hasUnresolvedCorrection returns true for PENDING or OVERDUE
     * in the work month, false for other months, and false for decided corrections.
     */
    @Test
    void hasUnresolvedCorrectionTracksStatusAndMonth() {
        long id = createPendingCorrection(internId, recordId);
        assertThat(corrections.hasUnresolvedCorrection(internId, YearMonth.of(2026, 8))).isTrue();
        assertThat(corrections.hasUnresolvedCorrection(internId, YearMonth.of(2026, 9))).isFalse();

        Instant deadline = correctionRepository.findById(id).orElseThrow().decisionDeadline();
        clock.set(deadline);
        corrections.expire(100);
        assertThat(correctionRepository.findById(id).orElseThrow().status()).isEqualTo(CorrectionStatus.OVERDUE);
        assertThat(corrections.hasUnresolvedCorrection(internId, YearMonth.of(2026, 8))).isTrue();

        corrections.decide(new AttendanceActor(mentorId, GlobalRole.MENTOR), id,
                CorrectionDecision.APPROVE, "Approved", null);
        assertThat(corrections.hasUnresolvedCorrection(internId, YearMonth.of(2026, 8))).isFalse();
    }

    private int dayOffset = 3;

    private long createPendingCorrection(long ownerInternId, long attendanceRecId) {
        clock.set(T_SUBMIT);
        var result = corrections.submit(new AttendanceActor(ownerInternId, GlobalRole.INTERN), attendanceRecId,
                new CorrectionRequestCommand(LocalDateTime.of(2026, 8, 14, 16, 45), "Missed checkout"));
        return result.id();
    }

    private long createAdditionalPendingCorrection() {
        LocalDate date = WORK_DATE.plusDays(dayOffset++);
        Instant checkIn = date.atTime(9, 0).atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        long recId = createMissingCheckoutRecord(internId, date, checkIn);
        Instant submitTime = date.atTime(18, 0).atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        clock.set(submitTime);
        var result = corrections.submit(new AttendanceActor(internId, GlobalRole.INTERN), recId,
                new CorrectionRequestCommand(LocalDateTime.of(date.getYear(), date.getMonth(), date.getDayOfMonth(), 16, 45), "Missed checkout"));
        return result.id();
    }

    private long createMissingCheckoutRecord(long targetInternId, LocalDate date, Instant checkIn) {
        AttendanceRecordEntity entity = new AttendanceRecordEntity(
                targetInternId,
                date,
                1L,
                checkIn,
                null);
        return records.saveAndFlush(entity).id();
    }

    private void assertUndecidedOverdueState(long correctionId) {
        var entity = correctionRepository.findById(correctionId).orElseThrow();
        assertThat(entity.status()).isEqualTo(CorrectionStatus.OVERDUE);
        assertThat(entity.decidedByMentorUserId()).isNull();
        assertThat(entity.decidedAt()).isNull();
        assertThat(entity.lockedAt()).isNull();
        List<CorrectionEventView> eventList =
                correctionEvents.findByCorrectionIdOrderByOccurredAtAscIdAsc(correctionId).stream()
                        .map(AttendanceCorrectionEventEntity::toView)
                        .toList();
        assertThat(eventList).extracting(CorrectionEventView::type)
                .containsExactly(CorrectionEventType.SUBMITTED, CorrectionEventType.OVERDUE);
    }

    private int countOverdueReminders(long recipientUserId) {
        return jdbc.queryForObject("""
                select count(*) from notifications
                where recipient_user_id = ?
                  and notification_type = 'SYSTEM'
                  and title = 'Correction request overdue'
                """, Integer.class, recipientUserId);
    }

    private int countNotifications(long recipientUserId, String title) {
        return jdbc.queryForObject("""
                select count(*) from notifications
                where recipient_user_id = ? and title = ?
                """, Integer.class, recipientUserId, title);
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
}
