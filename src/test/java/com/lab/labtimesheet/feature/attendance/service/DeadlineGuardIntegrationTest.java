package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.account.service.BootstrapService;
import com.lab.labtimesheet.feature.attendance.exception.LeaveException;
import com.lab.labtimesheet.feature.attendance.exception.LeaveRejection;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionDecisionCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionSubmission;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionSubmissionCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveDecisionCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveSubmission;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveSubmissionCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.MentorCorrectionDecision;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceCorrectionEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceCorrectionEventEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceCorrectionEventRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceCorrectionRepository;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Proves I2-ATT-07 (LEV-010, COR-008, ERR-003, ERR-004, AC-LEV-004, AC-COR-005, AC-TST-001) against committed
 * rows. Because the request-time guards and scheduled workers run idempotent REQUIRES_NEW transitions that must
 * commit independently of the rejecting mutation, every service call here commits separately (no test
 * transaction), which is why this scenario lives in its own container instead of the shared rollback-based class.
 */
@Import(DeadlineGuardIntegrationTest.IntegrationConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class DeadlineGuardIntegrationTest {

    @Autowired
    private AttendanceApplicationService attendance;

    @Autowired
    private LeaveService leave;

    @Autowired
    private CorrectionService corrections;

    @Autowired
    private AttendanceScheduler scheduler;

    @Autowired
    private AccountService accounts;

    @Autowired
    private BootstrapService bootstrap;

    @Autowired
    private SmtpConfigurationService smtp;

    @Autowired
    private RecordingSmtpProbe mail;

    @Autowired
    private LeaveRequestRepository requests;

    @Autowired
    private AttendanceCorrectionRepository correctionRows;

    @Autowired
    private AttendanceCorrectionEventRepository correctionEvents;

    @Autowired
    private MutableClock clock;

    @MockitoBean
    private AttendanceNotificationClient notifications;

    private static long internId;
    private static long adminId;
    private static long mentorId;
    private static boolean adminSeeded;
    private static boolean usersSeeded;

    @BeforeEach
    void seedUsers() {
        clock.set(Instant.parse("2026-08-14T00:00:00Z"));
        if (!adminSeeded) {
            bootstrap.bootstrap("deadline-admin@example.test", "Admin", "correct horse battery staple");
            adminId = accounts.requireActiveAdminId("deadline-admin@example.test");
            long draftId = smtp.saveDraft(adminId, new SmtpDraft(
                    "mailpit", 1025, SecurityMode.NONE, null, null, "deadline-admin@example.test", "Lab Timesheet"));
            smtp.testDraft(draftId, adminId, "deadline-admin@example.test");
            smtp.activate(draftId, adminId);
            adminSeeded = true;
        }
        if (!usersSeeded) {
            mail.clear();
            var mentorCreation = accounts.create(new CreateAccountCommand(
                    "deadline-mentor@example.test", "Deadline Mentor", GlobalRole.MENTOR, null, null, null), adminId);
            assertThat(mentorCreation.deliverySucceeded()).isTrue();
            assertThat(accounts.activate(mail.onlyActivationToken(), "new secure mentor password")).isTrue();
            mentorId = mentorCreation.userId();

            mail.clear();
            var internCreation = accounts.create(new CreateAccountCommand(
                    "deadline-intern@example.test",
                    "Deadline Intern",
                    GlobalRole.INTERN,
                    "INT-DEADLINE",
                    LocalDate.of(2026, 8, 1),
                    LocalDate.of(2026, 12, 31)), adminId);
            assertThat(internCreation.deliverySucceeded()).isTrue();
            assertThat(accounts.activate(mail.onlyActivationToken(), "new secure intern password")).isTrue();
            accounts.activateInternship(internCreation.userId(), adminId);
            internId = internCreation.userId();
            usersSeeded = true;
        }
        mail.clear();
    }

    @Test
    void pendingLeaveAutoRejectsThroughRequestAccessEvenIfSchedulerHasNotRun() {
        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        LeaveSubmission request = leave.submit(
                internId, new LeaveSubmissionCommand(
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 1),
                        "Pending block"));

        clock.set(Instant.parse("2026-09-01T01:30:00Z"));
        assertThatThrownBy(() -> leave.decide(
                mentorId, request.requestId(), new LeaveDecisionCommand(true, null)))
                .isInstanceOfSatisfying(LeaveException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(LeaveRejection.BOUNDARY_PASSED));

        LeaveRequestEntity persisted = requests.findById(request.requestId()).orElseThrow();
        assertThat(persisted.status()).isEqualTo("REJECTED");
        assertThat(persisted.decidedAt()).isEqualTo(Instant.parse("2026-09-01T01:30:00Z"));
        assertThat(persisted.decidedByMentorUserId()).isNull();
        assertThat(persisted.decisionNote()).contains("expired");
        assertThat(leave.overview(internId, LocalDate.of(2026, 9, 1)).reserved()).isZero();
        verify(notifications).leaveAutoRejected(
                internId, request.requestId(), Instant.parse("2026-09-01T01:30:00Z"));

        assertThatThrownBy(() -> leave.decide(
                mentorId, request.requestId(), new LeaveDecisionCommand(false, null)))
                .isInstanceOfSatisfying(LeaveException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(LeaveRejection.INVALID_STATE));
        assertThat(requests.findById(request.requestId()).orElseThrow().status()).isEqualTo("REJECTED");
        verify(notifications).leaveAutoRejected(
                internId, request.requestId(), Instant.parse("2026-09-01T01:30:00Z"));

        scheduler.expireLeaveDeadlines();
        assertThat(requests.findById(request.requestId()).orElseThrow().status()).isEqualTo("REJECTED");
        verify(notifications).leaveAutoRejected(
                internId, request.requestId(), Instant.parse("2026-09-01T01:30:00Z"));
    }

    @Test
    void schedulerAutoRejectsPendingLeaveExactlyOnceAcrossRepeatedInvocations() {
        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        LeaveSubmission request = leave.submit(
                internId, new LeaveSubmissionCommand(
                        LocalDate.of(2026, 9, 3),
                        LocalDate.of(2026, 9, 3),
                        "Worker block"));

        clock.set(Instant.parse("2026-09-03T01:30:00Z"));
        scheduler.expireLeaveDeadlines();

        LeaveRequestEntity persisted = requests.findById(request.requestId()).orElseThrow();
        assertThat(persisted.status()).isEqualTo("REJECTED");
        assertThat(persisted.decidedAt()).isEqualTo(Instant.parse("2026-09-03T01:30:00Z"));
        verify(notifications).leaveAutoRejected(
                internId, request.requestId(), Instant.parse("2026-09-03T01:30:00Z"));

        scheduler.expireLeaveDeadlines();
        assertThat(requests.findById(request.requestId()).orElseThrow().status()).isEqualTo("REJECTED");
        verify(notifications).leaveAutoRejected(
                internId, request.requestId(), Instant.parse("2026-09-03T01:30:00Z"));
    }

    @Test
    void schedulerAutoRejectsAndLocksExpiredCorrectionsWithoutDuplicatingTransitions() {
        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        attendance.checkIn(internId);
        clock.set(Instant.parse("2026-08-14T09:00:01Z"));
        CorrectionSubmission submission = corrections.submit(
                internId,
                new CorrectionSubmissionCommand(LocalDate.of(2026, 8, 14), LocalTime.of(15, 45), "Forgot"));

        clock.set(Instant.parse("2026-08-15T09:00:01.001Z"));
        scheduler.expireCorrectionDeadlines();

        AttendanceCorrectionEntity persisted = correctionRows.findById(submission.id()).orElseThrow();
        assertThat(persisted.status()).isEqualTo("REJECTED");
        assertThat(persisted.decidedAt()).isEqualTo(Instant.parse("2026-08-15T09:00:01.001Z"));
        assertThat(persisted.lockedAt()).isEqualTo(Instant.parse("2026-08-15T09:00:01.001Z"));
        assertThat(correctionEvents.findByCorrectionIdOrderByOccurredAtAscIdAsc(submission.id()))
                .extracting(AttendanceCorrectionEventEntity::eventType)
                .containsExactly("SUBMITTED", "AUTO_REJECTED");
        verify(notifications).correctionAutoRejected(
                internId, submission.id(), Instant.parse("2026-08-15T09:00:01.001Z"));

        scheduler.expireCorrectionDeadlines();
        assertThat(correctionEvents.findByCorrectionIdOrderByOccurredAtAscIdAsc(submission.id())).hasSize(2);
        verify(notifications).correctionAutoRejected(
                internId, submission.id(), Instant.parse("2026-08-15T09:00:01.001Z"));
    }

    @Test
    void readPathGuardClosesExpiredCorrectionsBeforeRenderingDecisions() {
        clock.set(Instant.parse("2026-08-17T02:00:00Z"));
        attendance.checkIn(internId);
        clock.set(Instant.parse("2026-08-17T09:00:01Z"));
        CorrectionSubmission submission = corrections.submit(
                internId,
                new CorrectionSubmissionCommand(LocalDate.of(2026, 8, 17), LocalTime.of(15, 45), "Forgot"));

        clock.set(Instant.parse("2026-08-18T09:00:01.001Z"));
        List<MentorCorrectionDecision> decisions = corrections.decisions();

        MentorCorrectionDecision row = decisions.stream()
                .filter(decision -> decision.id() == submission.id())
                .findFirst()
                .orElseThrow();
        assertThat(row.status()).isEqualTo("REJECTED");
        assertThat(row.lockedAt()).isNotNull();
        verify(notifications).correctionAutoRejected(
                internId, submission.id(), Instant.parse("2026-08-18T09:00:01.001Z"));
    }

    @Test
    void readPathGuardLocksDecidedOutcomeAfterDeadline() {
        clock.set(Instant.parse("2026-08-18T02:00:00Z"));
        attendance.checkIn(internId);
        clock.set(Instant.parse("2026-08-18T09:00:01Z"));
        CorrectionSubmission submission = corrections.submit(
                internId,
                new CorrectionSubmissionCommand(LocalDate.of(2026, 8, 18), LocalTime.of(15, 45), "Forgot"));

        clock.set(Instant.parse("2026-08-19T08:00:00Z"));
        corrections.decide(mentorId, submission.id(), new CorrectionDecisionCommand(true, "Confirmed"));

        clock.set(Instant.parse("2026-08-19T09:00:01.001Z"));
        List<MentorCorrectionDecision> decisions = corrections.decisions();

        MentorCorrectionDecision row = decisions.stream()
                .filter(decision -> decision.id() == submission.id())
                .findFirst()
                .orElseThrow();
        assertThat(row.status()).isEqualTo("APPROVED");
        assertThat(row.lockedAt()).isNotNull();
        assertThat(correctionEvents.findByCorrectionIdOrderByOccurredAtAscIdAsc(submission.id()))
                .extracting(AttendanceCorrectionEventEntity::eventType)
                .containsExactly("SUBMITTED", "APPROVED", "LOCKED");
        verify(notifications).correctionLocked(
                internId, submission.id(), Instant.parse("2026-08-19T09:00:01.001Z"));
        verify(notifications, never()).correctionAutoRejected(
                internId, submission.id(), Instant.parse("2026-08-19T09:00:01.001Z"));
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