package com.lab.labtimesheet.feature.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.entity.AppUser;
import com.lab.labtimesheet.feature.account.repository.AppUserRepository;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.account.service.BootstrapService;
import com.lab.labtimesheet.feature.integration.model.SecurityMode;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpConnection;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpDraft;
import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import com.lab.labtimesheet.feature.integration.service.SmtpProbe;
import com.lab.labtimesheet.feature.notification.model.EmailDeliveryStatus;
import com.lab.labtimesheet.feature.notification.model.NotificationType;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationCommand;
import com.lab.labtimesheet.feature.notification.model.entity.Notification;
import com.lab.labtimesheet.feature.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@Import({TestcontainersConfiguration.class, NotificationServiceIntegrationTest.ProbeConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class NotificationServiceIntegrationTest {
    private static final Instant INSTANT = Instant.parse("2026-08-14T00:00:00Z");

    @Autowired
    private BootstrapService bootstrap;

    @Autowired
    private AccountService accounts;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private NotificationService notifications;

    @Autowired
    private NotificationRepository repository;

    @Autowired
    private SmtpConfigurationService smtp;

    @Autowired
    private RecordingProbe smtpProbe;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private Clock clock;

    private long mentorId;
    private long internId;

    @BeforeEach
    void setUp() {
        bootstrap.bootstrap("admin@example.com", "Admin", "correct horse battery staple");
        mentorId = createRecipient("mentor@example.com", "Mentor", GlobalRole.MENTOR);
        internId = createRecipient("intern@example.com", "Intern", GlobalRole.INTERN);
    }

    @Test
    void deduplicatesRecipientsAndPersistsProjectWorkflowTypesWithoutSmtp() {
        List<NotificationType> types = List.of(
                NotificationType.PROJECT_INVITATION_CREATED,
                NotificationType.PROJECT_INVITATION_RESOLVED,
                NotificationType.MEMBERSHIP_EXIT_REQUESTED,
                NotificationType.MEMBERSHIP_EXIT_RESOLVED);
        List<Long> ids = new ArrayList<>();

        for (NotificationType type : types) {
            ids.addAll(notifications.publish(NotificationCommand.inAppWithEmail(
                    List.of(mentorId, internId, mentorId, internId),
                    type,
                    "Project workflow update",
                    "A Project workflow event requires your attention.",
                    "/projects/7",
                    "Project workflow update",
                    "A Project workflow event requires your attention.")));
        }

        List<Notification> saved = repository.findAllById(ids);
        assertThat(ids).hasSize(8);
        assertThat(saved).hasSize(8);
        assertThat(saved).extracting(Notification::getRecipientUserId)
                .containsExactlyInAnyOrderElementsOf(List.of(
                        mentorId, internId, mentorId, internId,
                        mentorId, internId, mentorId, internId));
        assertThat(saved).extracting(Notification::getNotificationType)
                .containsExactlyInAnyOrderElementsOf(List.of(
                        types.get(0), types.get(0), types.get(1), types.get(1),
                        types.get(2), types.get(2), types.get(3), types.get(3)));
        assertThat(saved).extracting(Notification::getEmailStatus)
                .containsOnly(EmailDeliveryStatus.UNAVAILABLE);
        assertThat(smtpProbe.messages).isEmpty();
    }

    @Test
    void emptyRecipientSetDoesNotCreateSelfTaskNotification() {
        List<Long> ids = notifications.publish(NotificationCommand.inApp(
                List.of(),
                NotificationType.TASK_STATUS_CHANGED,
                "Task updated",
                "Your self-Task changed.",
                "/tasks/9"));

        assertThat(ids).isEmpty();
        assertThat(repository.count()).isZero();
        assertThat(smtpProbe.messages).isEmpty();
    }

    @Test
    void smtpSuccessIsRecordedAfterCommittedNotification() {
        activateTestSmtp();

        List<Long> ids = notifications.publish(NotificationCommand.inAppWithEmail(
                List.of(mentorId),
                NotificationType.MEMBERSHIP_EXIT_RESOLVED,
                "Membership exit resolved",
                "The membership exit was approved.",
                "/projects/7/membership-exits",
                "Membership exit resolved",
                "The membership exit was approved."));

        Notification saved = repository.findById(ids.getFirst()).orElseThrow();
        assertThat(saved.getEmailStatus()).isEqualTo(EmailDeliveryStatus.SENT);
        assertThat(saved.getEmailAttempts()).isEqualTo(1);
        assertThat(saved.getEmailSentAt()).isEqualTo(INSTANT);
        assertThat(saved.getEmailNextAttemptAt()).isNull();
        assertThat(smtpProbe.messages).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isEqualTo("mentor@example.com");
            assertThat(message.subject()).isEqualTo("Membership exit resolved");
            assertThat(message.body()).isEqualTo("The membership exit was approved.");
        });
    }

    @Test
    void smtpFailureLeavesCommittedNotificationPendingWithoutLeakingProviderDiagnostic() {
        activateTestSmtp();
        smtpProbe.failureMessage = "provider secret diagnostic";

        List<Long> ids = notifications.publish(NotificationCommand.inAppWithEmail(
                List.of(mentorId),
                NotificationType.MEMBERSHIP_EXIT_REQUESTED,
                "Membership exit requested",
                "A member requested to leave.",
                "/projects/7/membership-exits",
                "Membership exit requested",
                "A member requested to leave."));

        Notification saved = repository.findById(ids.getFirst()).orElseThrow();
        assertThat(saved.getEmailStatus()).isEqualTo(EmailDeliveryStatus.PENDING);
        assertThat(saved.getEmailAttempts()).isEqualTo(1);
        assertThat(saved.getEmailNextAttemptAt()).isEqualTo(INSTANT.plusSeconds(60));
        assertThat(saved.getEmailLastError()).doesNotContain("provider secret diagnostic");
    }

    @Test
    void rolledBackDomainTransactionDoesNotSendEmailOrLeaveNotification() {
        activateTestSmtp();

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            notifications.publish(NotificationCommand.inAppWithEmail(
                    List.of(mentorId),
                    NotificationType.PROJECT_INVITATION_RESOLVED,
                    "Invitation resolved",
                    "The invitation was accepted.",
                    "/projects/7/invitations",
                    "Invitation resolved",
                    "The invitation was accepted."));
            throw new IllegalStateException("rollback domain action");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessage("rollback domain action");

        assertThat(repository.count()).isZero();
        assertThat(smtpProbe.messages).isEmpty();
    }

    private long createRecipient(String email, String displayName, GlobalRole role) {
        AppUser admin = users.findByNormalizedEmail("admin@example.com").orElseThrow();
        AppUser recipient = AppUser.pending(email, displayName, role, admin, clock.instant());
        recipient.activate("{noop}test-password", clock.instant());
        return users.saveAndFlush(recipient).getId();
    }

    private void activateTestSmtp() {
        long adminId = accounts.requireActiveAdminId("admin@example.com");
        long draftId = smtp.saveDraft(adminId, new SmtpDraft(
                "mailpit", 1025, SecurityMode.NONE, "smtp-user", "smtp-password",
                "notifications@example.com", "Lab Timesheet"));
        smtp.testDraft(draftId, adminId, "admin@example.com");
        smtp.activate(draftId, adminId);
        smtpProbe.messages.clear();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {
        @Bean
        @Primary
        RecordingProbe recordingProbe() {
            return new RecordingProbe();
        }
    }

    static final class RecordingProbe implements SmtpProbe {
        private final List<Message> messages = new ArrayList<>();
        private String failureMessage;

        @Override
        public void send(SmtpConnection connection, String recipient, String subject, String body) {
            if (failureMessage != null) {
                throw new IllegalStateException(failureMessage);
            }
            messages.add(new Message(recipient, subject, body));
        }
    }

    private record Message(String recipient, String subject, String body) {
    }
}
