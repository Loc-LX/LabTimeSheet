package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.account.service.BootstrapService;
import com.lab.labtimesheet.feature.attendance.exception.CorrectionException;
import com.lab.labtimesheet.feature.attendance.exception.CorrectionRejection;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionDecisionCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionSubmission;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionSubmissionCommand;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceCorrectionEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceCorrectionEventEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceCorrectionEventRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceCorrectionRepository;
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
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Proves AC-COR-005 against committed rows: the decision-window guard runs in an isolated REQUIRES_NEW
 * transaction, so a still-pending correction whose deadline passed is auto-rejected and locked atomically on the
 * first decide/revert access even when that access then fails with LOCKED. Because this scenario needs the
 * correction committed before the guard runs, every service call here commits independently (no test transaction),
 * which is why this scenario lives in its own container instead of the shared rollback-based integration class.
 */
@Import(CorrectionExpiryIntegrationTest.IntegrationConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class CorrectionExpiryIntegrationTest {

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
    private AttendanceCorrectionRepository correctionRows;

    @Autowired
    private AttendanceCorrectionEventRepository correctionEvents;

    @Autowired
    private MutableClock clock;

    private long internId;
    private long adminId;
    private long mentorId;

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

        mail.clear();
        var mentorCreation = accounts.create(new CreateAccountCommand(
                "mentor@example.test", "Mentor", GlobalRole.MENTOR, null, null, null), adminId);
        assertThat(mentorCreation.deliverySucceeded()).isTrue();
        assertThat(accounts.activate(mail.onlyActivationToken(), "new secure mentor password")).isTrue();
        mentorId = mentorCreation.userId();
    }

    @Test
    void expiredPendingCorrectionIsAutoRejectedAndLockedByFirstAccess() {
        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        attendance.checkIn(internId);
        clock.set(Instant.parse("2026-08-14T09:00:01Z"));
        CorrectionSubmission submission = corrections.submit(
                internId, new CorrectionSubmissionCommand(LocalDate.of(2026, 8, 14), LocalTime.of(15, 45), "Forgot"));

        clock.set(Instant.parse("2026-08-15T09:00:01.001Z"));
        assertThatThrownBy(() -> corrections.decide(
                mentorId, submission.id(), new CorrectionDecisionCommand(true, "Too late")))
                .isInstanceOfSatisfying(CorrectionException.class,
                        exception -> assertThat(exception.rejection()).isEqualTo(CorrectionRejection.LOCKED));

        AttendanceCorrectionEntity persisted = correctionRows.findById(submission.id()).orElseThrow();
        assertThat(persisted.status()).isEqualTo("REJECTED");
        assertThat(persisted.decidedAt()).isEqualTo(Instant.parse("2026-08-15T09:00:01.001Z"));
        assertThat(persisted.lockedAt()).isEqualTo(Instant.parse("2026-08-15T09:00:01.001Z"));
        assertThat(persisted.decidedByMentorUserId()).isNull();

        List<AttendanceCorrectionEventEntity> events = correctionEvents
                .findByCorrectionIdOrderByOccurredAtAscIdAsc(submission.id());
        assertThat(events).hasSize(2);
        assertThat(events.getFirst().eventType()).isEqualTo("SUBMITTED");
        AttendanceCorrectionEventEntity autoRejected = events.get(1);
        assertThat(autoRejected.eventType()).isEqualTo("AUTO_REJECTED");
        assertThat(autoRejected.fromStatus()).isEqualTo("PENDING");
        assertThat(autoRejected.toStatus()).isEqualTo("REJECTED");
        assertThat(autoRejected.actorUserId()).isNull();
        assertThat(autoRejected.occurredAt()).isEqualTo(Instant.parse("2026-08-15T09:00:01.001Z"));

        assertThatThrownBy(() -> corrections.revert(
                mentorId, submission.id(), "Try to reopen a locked correction"))
                .isInstanceOfSatisfying(CorrectionException.class,
                        exception -> assertThat(exception.rejection()).isEqualTo(CorrectionRejection.LOCKED));

        assertThatThrownBy(() -> corrections.decide(
                mentorId, submission.id(), new CorrectionDecisionCommand(false, "Still locked")))
                .isInstanceOfSatisfying(CorrectionException.class,
                        exception -> assertThat(exception.rejection()).isEqualTo(CorrectionRejection.LOCKED));

        assertThat(correctionEvents.findByCorrectionIdOrderByOccurredAtAscIdAsc(submission.id())).hasSize(2);
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