package com.lab.labtimesheet.feature.attendance.service;

import static com.lab.labtimesheet.feature.attendance.model.entity.CorrectionEntityFixtures.approved;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.account.service.BootstrapService;
import com.lab.labtimesheet.feature.attendance.exception.CorrectionException;
import com.lab.labtimesheet.feature.attendance.exception.CorrectionRejection;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceHistoryItem;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionDecisionCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionSubmission;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionSubmissionCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionsOverview;
import com.lab.labtimesheet.feature.attendance.model.dto.MentorCorrectionDecision;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceCorrectionEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceCorrectionEventEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceRecordEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceCorrectionEventRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceCorrectionRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceRecordRepository;
import com.lab.labtimesheet.feature.integration.model.SecurityMode;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpDraft;
import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import com.lab.labtimesheet.feature.integration.service.SmtpProbe;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Import(CorrectionPersistenceIntegrationTest.IntegrationConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CorrectionPersistenceIntegrationTest {

    @Autowired
    private AttendanceApplicationService attendance;

    @Autowired
    private CorrectionService corrections;

    @Autowired
    private AccountService accounts;

    @Autowired
    private BootstrapService bootstrap;

    @Autowired
    private SmtpConfigurationService smtp;

    @Autowired
    private RecordingSmtpProbe mail;

    @Autowired
    private AttendanceRecordRepository records;

    @Autowired
    private AttendanceCorrectionRepository correctionRows;

    @Autowired
    private AttendanceCorrectionEventRepository correctionEvents;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MutableClock clock;

    private long internId;
    private long adminId;

    @BeforeEach
    void seedUsers() {
        clock.set(Instant.parse("2026-08-14T00:00:00Z"));
        bootstrap.bootstrap("admin@example.test", "Admin", "correct horse battery staple");
        adminId = accounts.requireActiveAdminId("admin@example.test");
        long draftId = smtp.saveDraft(adminId, new SmtpDraft(
                "mailpit", 1025, SecurityMode.NONE, null, null, "admin@example.test", "Lab Timesheet"));
        smtp.testDraft(draftId, adminId, "admin@example.test");
        smtp.activate(draftId, adminId);
        mail.clear();

        var creation = accounts.create(new CreateAccountCommand(
                "intern@example.test",
                "Intern",
                GlobalRole.INTERN,
                "INT-001",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 12, 31)), adminId);
        assertThat(creation.deliverySucceeded()).isTrue();
        assertThat(accounts.activate(mail.onlyActivationToken(), "new secure intern password")).isTrue();
        accounts.activateInternship(creation.userId(), adminId);
        internId = creation.userId();
    }

    @Test
    void submitsJustAfterCutoffPersistingDeadlinesAndSubmittedEvent() {
        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        attendance.checkIn(internId);

        clock.set(Instant.parse("2026-08-14T09:00:01Z"));
        CorrectionSubmission submission = corrections.submit(
                internId, new CorrectionSubmissionCommand(LocalDate.of(2026, 8, 14), LocalTime.of(15, 45), "Forgot"));

        assertThat(submission.status()).isEqualTo("PENDING");
        assertThat(submission.proposedCheckoutAt()).isEqualTo(Instant.parse("2026-08-14T08:45:00Z"));
        assertThat(submission.submissionDeadline()).isEqualTo(Instant.parse("2026-08-15T08:30:00Z"));
        assertThat(submission.decisionDeadline()).isEqualTo(Instant.parse("2026-08-15T09:00:01Z"));

        AttendanceRecordEntity record = records.findByInternUserIdAndWorkDate(internId, LocalDate.of(2026, 8, 14))
                .orElseThrow();
        assertThat(record.checkOutAt()).isNull();

        AttendanceCorrectionEntity persisted = correctionRows.findById(submission.id()).orElseThrow();
        assertThat(persisted.status()).isEqualTo("PENDING");
        assertThat(persisted.requestedCheckoutAt()).isEqualTo(Instant.parse("2026-08-14T08:45:00Z"));
        assertThat(persisted.submittedAt()).isEqualTo(Instant.parse("2026-08-14T09:00:01Z"));
        assertThat(persisted.submissionDeadline()).isEqualTo(Instant.parse("2026-08-15T08:30:00Z"));
        assertThat(persisted.decisionDeadline()).isEqualTo(Instant.parse("2026-08-15T09:00:01Z"));

        List<AttendanceCorrectionEventEntity> events = correctionEvents
                .findByCorrectionIdOrderByOccurredAtAscIdAsc(submission.id());
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().eventType()).isEqualTo("SUBMITTED");
        assertThat(events.getFirst().fromStatus()).isNull();
        assertThat(events.getFirst().toStatus()).isEqualTo("PENDING");
        assertThat(events.getFirst().actorUserId()).isEqualTo(internId);
        assertThat(events.getFirst().occurredAt()).isEqualTo(Instant.parse("2026-08-14T09:00:01Z"));
    }

    @Test
    void rejectsBeforeAndAtInclusiveCutoffWithoutPersisting() {
        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        attendance.checkIn(internId);

        clock.set(Instant.parse("2026-08-14T09:00:00Z"));
        assertRejected(CorrectionRejection.TOO_EARLY);
        clock.set(Instant.parse("2026-08-14T08:59:59.999Z"));
        assertRejected(CorrectionRejection.TOO_EARLY);

        assertThat(correctionRows.findAll()).isEmpty();
    }

    @Test
    void acceptsAtInclusiveDeadlineAndRejectsTheFirstLaterInstant() {
        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        attendance.checkIn(internId);

        clock.set(Instant.parse("2026-08-15T08:30:00Z"));
        assertThat(corrections.submit(
                internId, new CorrectionSubmissionCommand(LocalDate.of(2026, 8, 14), LocalTime.of(15, 45), "On deadline"))
                .status()).isEqualTo("PENDING");

        clock.set(Instant.parse("2026-08-17T02:00:00Z"));
        attendance.checkIn(internId);
        clock.set(Instant.parse("2026-08-18T08:30:00.001Z"));
        assertRejected(LocalDate.of(2026, 8, 17), CorrectionRejection.DEADLINE_PASSED);
        assertThat(correctionRows.findAll()).hasSize(1);
    }

    @Test
    void enforcesAtMostOneCorrectionPerRecordAndLeavesRawCheckoutNull() {
        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        attendance.checkIn(internId);
        clock.set(Instant.parse("2026-08-14T09:00:01Z"));
        CorrectionSubmission first = corrections.submit(
                internId, new CorrectionSubmissionCommand(LocalDate.of(2026, 8, 14), LocalTime.of(15, 45), "First"));

        assertThatThrownBy(() -> corrections.submit(
                internId, new CorrectionSubmissionCommand(LocalDate.of(2026, 8, 14), LocalTime.of(16, 0), "Second")))
                .isInstanceOfSatisfying(CorrectionException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(CorrectionRejection.ALREADY_SUBMITTED));

        assertThat(records.findByInternUserIdAndWorkDate(internId, LocalDate.of(2026, 8, 14))
                .orElseThrow().checkOutAt()).isNull();
        assertThat(correctionRows.findAll()).hasSize(1);
        assertThat(correctionRows.findAll().getFirst().id()).isEqualTo(first.id());
    }

    @Test
    void rejectsProposedValuesWithoutCreatingACorrection() {
        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        attendance.checkIn(internId);
        clock.set(Instant.parse("2026-08-14T09:00:01Z"));

        assertRejected(
                new CorrectionSubmissionCommand(LocalDate.of(2026, 8, 14), LocalTime.of(8, 30), "At check-in time"),
                CorrectionRejection.PROPOSED_BEFORE_CHECKIN);
        assertRejected(
                new CorrectionSubmissionCommand(LocalDate.of(2026, 8, 14), LocalTime.of(23, 0), "Future time"),
                CorrectionRejection.PROPOSED_IN_FUTURE);
        assertRejected(
                new CorrectionSubmissionCommand(LocalDate.of(2026, 8, 14), LocalTime.of(15, 45), "   "),
                CorrectionRejection.INVALID_REQUEST);

        assertThat(correctionRows.findAll()).isEmpty();
        assertThat(records.findByInternUserIdAndWorkDate(internId, LocalDate.of(2026, 8, 14))
                .orElseThrow().checkOutAt()).isNull();
    }

    @Test
    void approvedCorrectionDerivesEffectiveCheckoutInHistoryWithoutEditingRaw() {
        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        attendance.checkIn(internId);

        AttendanceRecordEntity record = records.findByInternUserIdAndWorkDate(internId, LocalDate.of(2026, 8, 14))
                .orElseThrow();
        AttendanceCorrectionEntity approved = approved(
                record,
                Instant.parse("2026-08-14T08:00:00Z"),
                Instant.parse("2026-08-14T09:00:01Z"),
                Instant.parse("2026-08-15T08:30:00Z"),
                Instant.parse("2026-08-15T09:00:01Z"),
                adminId,
                Instant.parse("2026-08-14T10:00:00Z"));
        entityManager.persist(approved);
        entityManager.flush();

        clock.set(Instant.parse("2026-08-14T12:00:00Z"));
        AttendanceHistoryItem item = attendance.history(
                        new AttendanceActor(internId, AttendanceRole.INTERN),
                        internId,
                        LocalDate.of(2026, 8, 14),
                        LocalDate.of(2026, 8, 14))
                .getFirst();

        assertThat(item.checkOutAt()).isNull();
        assertThat(item.effectiveCheckOutAt()).isEqualTo(Instant.parse("2026-08-14T08:00:00Z"));
        assertThat(item.checkOutTimeDisplay()).isEqualTo("15:00");
        assertThat(item.violations().missingCheckout()).isFalse();
        assertThat(item.violations().earlyDeparture()).isTrue();
    }

    @Test
    void overviewListsOwnCorrectionsNewestFirst() {
        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        attendance.checkIn(internId);
        clock.set(Instant.parse("2026-08-14T09:00:01Z"));
        corrections.submit(
                internId, new CorrectionSubmissionCommand(LocalDate.of(2026, 8, 14), LocalTime.of(15, 45), "Monday"));

        clock.set(Instant.parse("2026-08-17T02:00:00Z"));
        attendance.checkIn(internId);
        clock.set(Instant.parse("2026-08-17T09:00:01Z"));
        corrections.submit(
                internId, new CorrectionSubmissionCommand(LocalDate.of(2026, 8, 17), LocalTime.of(15, 45), "Second"));

        CorrectionsOverview overview = corrections.overview(internId, LocalDate.of(2026, 8, 1));

        assertThat(overview.month()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(overview.corrections()).hasSize(2);
        assertThat(overview.corrections().getFirst().workDate()).isEqualTo(LocalDate.of(2026, 8, 17));
        assertThat(overview.corrections().getFirst().reason()).isEqualTo("Second");
        assertThat(overview.corrections().get(1).workDate()).isEqualTo(LocalDate.of(2026, 8, 14));
        assertThat(overview.corrections().get(1).reason()).isEqualTo("Monday");
    }

    @Test
    void mentorApprovesPendingCorrectionPersistingStateAndAppendOnlyEvents() {
        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        attendance.checkIn(internId);
        clock.set(Instant.parse("2026-08-14T09:00:01Z"));
        CorrectionSubmission submission = corrections.submit(
                internId, new CorrectionSubmissionCommand(LocalDate.of(2026, 8, 14), LocalTime.of(15, 45), "Forgot"));
        long mentorId = seedMentor();

        clock.set(Instant.parse("2026-08-15T08:00:00Z"));
        MentorCorrectionDecision decided = corrections.decide(
                mentorId, submission.id(), new CorrectionDecisionCommand(true, "Verified in person"));

        assertThat(decided.status()).isEqualTo("APPROVED");
        assertThat(decided.decidedByMentorUserId()).isEqualTo(mentorId);
        assertThat(decided.decidedAt()).isEqualTo(Instant.parse("2026-08-15T08:00:00Z"));
        assertThat(decided.internDisplayName()).isEqualTo("Intern");

        AttendanceCorrectionEntity persisted = correctionRows.findById(submission.id()).orElseThrow();
        assertThat(persisted.status()).isEqualTo("APPROVED");
        assertThat(persisted.decidedByMentorUserId()).isEqualTo(mentorId);
        assertThat(persisted.decidedAt()).isEqualTo(Instant.parse("2026-08-15T08:00:00Z"));
        assertThat(persisted.decisionNote()).isEqualTo("Verified in person");

        List<AttendanceCorrectionEventEntity> events = correctionEvents
                .findByCorrectionIdOrderByOccurredAtAscIdAsc(submission.id());
        assertThat(events).hasSize(2);
        assertThat(events.getFirst().eventType()).isEqualTo("SUBMITTED");
        AttendanceCorrectionEventEntity decisionEvent = events.get(1);
        assertThat(decisionEvent.eventType()).isEqualTo("APPROVED");
        assertThat(decisionEvent.fromStatus()).isEqualTo("PENDING");
        assertThat(decisionEvent.toStatus()).isEqualTo("APPROVED");
        assertThat(decisionEvent.actorUserId()).isEqualTo(mentorId);
        assertThat(decisionEvent.note()).isEqualTo("Verified in person");
        assertThat(decisionEvent.occurredAt()).isEqualTo(Instant.parse("2026-08-15T08:00:00Z"));

        AttendanceRecordEntity record = records.findByInternUserIdAndWorkDate(internId, LocalDate.of(2026, 8, 14))
                .orElseThrow();
        assertThat(record.checkOutAt()).isNull();

        clock.set(Instant.parse("2026-08-15T12:00:00Z"));
        AttendanceHistoryItem item = attendance.history(
                        new AttendanceActor(internId, AttendanceRole.INTERN),
                        internId,
                        LocalDate.of(2026, 8, 14),
                        LocalDate.of(2026, 8, 14))
                .getFirst();
        assertThat(item.effectiveCheckOutAt()).isEqualTo(Instant.parse("2026-08-14T08:45:00Z"));
        assertThat(item.violations().missingCheckout()).isFalse();
    }

    @Test
    void mentorRejectsThenRevertsThenApprovesAppendingEveryImmutableEvent() {
        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        attendance.checkIn(internId);
        clock.set(Instant.parse("2026-08-14T09:00:01Z"));
        CorrectionSubmission submission = corrections.submit(
                internId, new CorrectionSubmissionCommand(LocalDate.of(2026, 8, 14), LocalTime.of(15, 45), "Forgot"));
        long mentorId = seedMentor();

        clock.set(Instant.parse("2026-08-15T08:00:00Z"));
        corrections.decide(mentorId, submission.id(), new CorrectionDecisionCommand(false, "No evidence"));
        corrections.revert(mentorId, submission.id(), "Mentor reopened after reviewing the log");
        corrections.decide(mentorId, submission.id(), new CorrectionDecisionCommand(true, "Evidence confirmed"));

        List<AttendanceCorrectionEventEntity> events = correctionEvents
                .findByCorrectionIdOrderByOccurredAtAscIdAsc(submission.id());
        assertThat(events).extracting(AttendanceCorrectionEventEntity::eventType)
                .containsExactly("SUBMITTED", "REJECTED", "REOPENED", "APPROVED");
        assertThat(events).extracting(AttendanceCorrectionEventEntity::fromStatus)
                .containsExactly(null, "PENDING", "REJECTED", "PENDING");
        assertThat(events).extracting(AttendanceCorrectionEventEntity::toStatus)
                .containsExactly("PENDING", "REJECTED", "PENDING", "APPROVED");

        AttendanceCorrectionEntity persisted = correctionRows.findById(submission.id()).orElseThrow();
        assertThat(persisted.status()).isEqualTo("APPROVED");
        assertThat(persisted.decidedAt()).isEqualTo(Instant.parse("2026-08-15T08:00:00Z"));
        assertThat(persisted.decisionNote()).isEqualTo("Evidence confirmed");
    }

    @Test
    void mentorCannotDecideAfterDecisionWindowPassedWithoutAppendingAnEvent() {
        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        attendance.checkIn(internId);
        clock.set(Instant.parse("2026-08-14T09:00:01Z"));
        CorrectionSubmission submission = corrections.submit(
                internId, new CorrectionSubmissionCommand(LocalDate.of(2026, 8, 14), LocalTime.of(15, 45), "Forgot"));
        long mentorId = seedMentor();

        clock.set(Instant.parse("2026-08-15T09:00:01.001Z"));
        assertThatThrownBy(() -> corrections.decide(
                mentorId, submission.id(), new CorrectionDecisionCommand(true, "Too late")))
                .isInstanceOfSatisfying(CorrectionException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(CorrectionRejection.DECISION_WINDOW_PASSED));

        List<AttendanceCorrectionEventEntity> events = correctionEvents
                .findByCorrectionIdOrderByOccurredAtAscIdAsc(submission.id());
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().eventType()).isEqualTo("SUBMITTED");
    }

    @Test
    void mentorDecisionsListAllCorrectionsNewestFirstWithDerivedFlags() {
        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        attendance.checkIn(internId);
        clock.set(Instant.parse("2026-08-14T09:00:01Z"));
        CorrectionSubmission first = corrections.submit(
                internId, new CorrectionSubmissionCommand(LocalDate.of(2026, 8, 14), LocalTime.of(15, 45), "Monday"));
        long mentorId = seedMentor();

        clock.set(Instant.parse("2026-08-15T08:00:00Z"));
        corrections.decide(mentorId, first.id(), new CorrectionDecisionCommand(true, "Confirmed"));

        clock.set(Instant.parse("2026-08-17T02:00:00Z"));
        attendance.checkIn(internId);
        clock.set(Instant.parse("2026-08-17T09:00:01Z"));
        corrections.submit(
                internId, new CorrectionSubmissionCommand(LocalDate.of(2026, 8, 17), LocalTime.of(15, 45), "Second"));

        List<MentorCorrectionDecision> decisions = corrections.decisions();

        assertThat(decisions).hasSize(2);
        assertThat(decisions.getFirst().id()).isNotEqualTo(first.id());
        assertThat(decisions.getFirst().status()).isEqualTo("PENDING");
        assertThat(decisions.getFirst().internDisplayName()).isEqualTo("Intern");
        assertThat(decisions.getFirst().approvedCompliant()).isTrue();
        assertThat(decisions.getFirst().revertable()).isFalse();
        MentorCorrectionDecision decided = decisions.get(1);
        assertThat(decided.id()).isEqualTo(first.id());
        assertThat(decided.status()).isEqualTo("APPROVED");
        assertThat(decided.approvedCompliant()).isTrue();
        assertThat(decided.revertable()).isFalse();
        assertThat(decided.rawCheckoutPresent()).isFalse();
    }

    private long seedMentor() {
        mail.clear();
        var creation = accounts.create(new CreateAccountCommand(
                "mentor@example.test", "Mentor", GlobalRole.MENTOR, null, null, null), adminId);
        assertThat(creation.deliverySucceeded()).isTrue();
        assertThat(accounts.activate(mail.onlyActivationToken(), "new secure mentor password")).isTrue();
        return creation.userId();
    }

    private void assertRejected(CorrectionRejection rejection) {
        assertRejected(LocalDate.of(2026, 8, 14), rejection);
    }

    private void assertRejected(LocalDate workDate, CorrectionRejection rejection) {
        assertRejected(
                new CorrectionSubmissionCommand(workDate, LocalTime.of(15, 45), "Should fail"),
                rejection);
    }

    private void assertRejected(CorrectionSubmissionCommand command, CorrectionRejection rejection) {
        assertThatThrownBy(() -> corrections.submit(internId, command))
                .isInstanceOfSatisfying(CorrectionException.class,
                        exception -> assertThat(exception.rejection()).isEqualTo(rejection));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class IntegrationConfiguration {

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgresContainer() {
            return new PostgreSQLContainer(DockerImageName.parse("postgres:18.4"));
        }

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(Instant.parse("2026-08-14T00:00:00Z"));
        }

        @Bean
        @Primary
        RecordingSmtpProbe recordingSmtpProbe() {
            return new RecordingSmtpProbe();
        }
    }

    static final class RecordingSmtpProbe implements SmtpProbe {

        private final List<String> messages = new ArrayList<>();

        @Override
        public void send(
                com.lab.labtimesheet.feature.integration.model.dto.SmtpConnection connection,
                String recipient,
                String subject,
                String body) {
            messages.add(body);
        }

        void clear() {
            messages.clear();
        }

        String onlyActivationToken() {
            assertThat(messages).hasSize(1);
            String body = messages.getFirst();
            int tokenStart = body.indexOf("token=");
            assertThat(tokenStart).isGreaterThanOrEqualTo(0);
            return body.substring(tokenStart + "token=".length()).trim();
        }
    }

    static final class MutableClock extends Clock {

        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}