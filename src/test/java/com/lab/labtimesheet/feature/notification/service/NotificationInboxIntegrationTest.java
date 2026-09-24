package com.lab.labtimesheet.feature.notification.service;

import com.lab.labtimesheet.feature.internship.service.InternshipService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.identity.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.identity.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.identity.service.BootstrapService;
import com.lab.labtimesheet.feature.notification.model.NotificationType;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationAction;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationEvent;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationInbox;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationInboxItem;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationRecipient;
import com.lab.labtimesheet.feature.notification.repository.NotificationRepository;
import com.lab.labtimesheet.platform.model.GlobalRole;
import com.lab.labtimesheet.platform.model.SecurityMode;
import com.lab.labtimesheet.platform.model.dto.SmtpConnection;
import com.lab.labtimesheet.platform.model.dto.SmtpDraft;
import com.lab.labtimesheet.platform.service.MailDeliveryService;
import com.lab.labtimesheet.platform.service.SmtpConfigurationService;
import com.lab.labtimesheet.platform.service.SmtpProbe;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL proof that notification inbox reads and mark-read actions remain recipient-scoped. */
@Import({TestcontainersConfiguration.class, NotificationInboxIntegrationTest.MailProbeConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class NotificationInboxIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-21T00:00:00Z");

    @Autowired
    private BootstrapService bootstrap;

    @Autowired
    private AccountService accounts;

    @Autowired
    private InternshipService internships;

    @Autowired
    private SmtpConfigurationService smtp;

    @Autowired
    private RecordingSmtpProbe mail;

    @Autowired
    private NotificationService notifications;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private MailDeliveryService mailDelivery;

    @Autowired
    private Clock clock;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void listsOnlyTheAuthenticatedRecipientAndMarksOwnRowsReadIdempotently() {
        List<AccountIdentity> recipients = provisionRecipients();
        AccountIdentity recipientA = recipients.get(0);
        AccountIdentity recipientB = recipients.get(1);

        publish(recipientA.id(), recipientA.email(), "First A", "/tasks/1");
        publish(recipientA.id(), recipientA.email(), "Second A", "/tasks/2");
        publish(recipientB.id(), recipientB.email(), "Only B", "/tasks/3");

        List<Long> recipientAIds = idsFor(recipientA.id());
        List<Long> recipientBIds = idsFor(recipientB.id());
        NotificationInbox inbox = notifications.inboxFor(recipientA.id());

        assertThat(inbox.notifications()).extracting(NotificationInboxItem::id)
                .containsExactlyElementsOf(recipientAIds);
        assertThat(inbox.notifications()).extracting(NotificationInboxItem::title)
                .containsExactly("Second A", "First A");
        assertThat(inbox.notifications()).allMatch(item -> !item.read());
        assertThat(inbox.unreadCount()).isEqualTo(2);
        assertThat(inbox.notifications()).extracting(NotificationInboxItem::id)
                .doesNotContainAnyElementsOf(recipientBIds);
        assertThat(notifications.inboxFor(recipientB.id()).notifications())
                .extracting(NotificationInboxItem::id)
                .containsExactlyElementsOf(recipientBIds);

        assertThat(NotificationInbox.class.isRecord()).isTrue();
        assertThat(NotificationInboxItem.class.isRecord()).isTrue();
        assertThat(NotificationInbox.class.getDeclaredFields())
                .allMatch(field -> Modifier.isPrivate(field.getModifiers()) && Modifier.isFinal(field.getModifiers()));
        assertThat(NotificationInboxItem.class.getDeclaredFields())
                .allMatch(field -> Modifier.isPrivate(field.getModifiers()) && Modifier.isFinal(field.getModifiers()));
        assertThat(Arrays.stream(NotificationInboxItem.class.getRecordComponents())
                .map(RecordComponent::getName))
                .doesNotContain("emailStatus", "emailTo", "emailSubject", "emailBody", "emailAttempts",
                        "emailNextAttemptAt", "emailSentAt", "emailLastError");
        assertThatThrownBy(() -> inbox.notifications().clear())
                .isInstanceOf(UnsupportedOperationException.class);

        long ownNewestId = recipientAIds.getFirst();
        StepClock markReadClock = new StepClock(NOW.plusSeconds(1));
        NotificationService sequentialMarkReadService = new NotificationService(
                notificationRepository, mailDelivery, markReadClock, transactionManager);
        transactions.executeWithoutResult(status -> sequentialMarkReadService.markRead(
                recipientA.id(), ownNewestId));
        Object readAt = readAt(ownNewestId);
        assertThat(readAt).isNotNull();
        assertThat(notifications.inboxFor(recipientA.id()).unreadCount()).isEqualTo(1);
        assertThat(notifications.inboxFor(recipientA.id()).notifications().getFirst().read()).isTrue();

        markReadClock.set(NOW.plusSeconds(2));
        transactions.executeWithoutResult(status -> sequentialMarkReadService.markRead(
                recipientA.id(), ownNewestId));
        assertThat(readAt(ownNewestId)).isEqualTo(readAt);
        assertThat(notifications.inboxFor(recipientA.id()).unreadCount()).isEqualTo(1);

        notifications.markRead(recipientA.id(), recipientBIds.getFirst());
        notifications.markRead(recipientA.id(), Long.MAX_VALUE);
        assertThat(readAt(recipientBIds.getFirst())).isNull();
        assertThat(notifications.inboxFor(recipientA.id()).unreadCount()).isEqualTo(1);
    }

    @Test
    void concurrentOwnMarkReadRequestsAreIdempotent() throws Exception {
        AccountIdentity recipient = provisionRecipients().getFirst();
        publish(recipient.id(), recipient.email(), "Concurrent row", "/tasks/4");
        long notificationId = idsFor(recipient.id()).getFirst();

        MarkReadBarrier barrier = new MarkReadBarrier();
        NotificationService concurrentService = new NotificationService(
                gated(notificationRepository, barrier), mailDelivery, clock, transactionManager);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> transactions.executeWithoutResult(status ->
                    concurrentService.markRead(recipient.id(), notificationId)));
            Future<?> second = executor.submit(() -> transactions.executeWithoutResult(status ->
                    concurrentService.markRead(recipient.id(), notificationId)));

            assertThat(barrier.bothReadsCompleted.await(5, TimeUnit.SECONDS)).isTrue();
            barrier.releaseReads.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);

            assertThat(readAt(notificationId)).isNotNull();
            assertThat(notifications.inboxFor(recipient.id()).unreadCount()).isZero();
        } finally {
            barrier.releaseReads.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static NotificationRepository gated(NotificationRepository delegate, MarkReadBarrier barrier) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getName().equals("findByIdAndRecipientUserId")) {
                barrier.bothReadsCompleted.countDown();
                try {
                    if (!barrier.releaseReads.await(10, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting to release concurrent mark-read reads");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("Interrupted while waiting to release concurrent mark-read reads",
                            exception);
                }
            }
            Object result;
            try {
                result = method.invoke(delegate, args);
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            }
            return result;
        };
        return (NotificationRepository) Proxy.newProxyInstance(
                NotificationRepository.class.getClassLoader(), new Class<?>[] {NotificationRepository.class}, handler);
    }

    private static final class MarkReadBarrier {
        private final CountDownLatch bothReadsCompleted = new CountDownLatch(2);
        private final CountDownLatch releaseReads = new CountDownLatch(1);
    }

    private void publish(long recipientId, String email, String title, String actionUrl) {
        transactions.executeWithoutResult(status -> notifications.publish(
                new NotificationEvent(NotificationType.TASK_COMMENTED, "COMMENTED", title, title + " body"),
                new NotificationAction(actionUrl, false),
                List.of(new NotificationRecipient(recipientId, email))));
    }

    private List<AccountIdentity> provisionRecipients() {
        bootstrap.bootstrap("inbox-a@example.com", "Inbox A", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("inbox-a@example.com");
        activateSmtp(adminId);
        mail.clear();

        var creation = internships.create(new CreateAccountCommand(
                "inbox-b@example.com", "Inbox B", GlobalRole.MENTOR, null, null, null), adminId);
        assertThat(accounts.activate(mail.activationToken(), "inbox mentor password")).isTrue();
        return List.of(accounts.requireIdentityById(adminId), accounts.requireIdentityById(creation.userId()));
    }

    private void activateSmtp(long adminId) {
        String adminEmail = accounts.requireIdentityById(adminId).email();
        long draftId = smtp.saveDraft(adminId,
                new SmtpDraft("mailpit", 1025, SecurityMode.NONE, null, null, adminEmail, "Lab Timesheet"));
        smtp.testDraft(draftId, adminId, adminEmail);
        smtp.activate(draftId, adminId);
    }

    private List<Long> idsFor(long recipientId) {
        return jdbc.queryForList(
                "select id from notifications where recipient_user_id = ? order by id desc", Long.class, recipientId);
    }

    private Object readAt(long notificationId) {
        return jdbc.queryForObject(
                "select read_at from notifications where id = ?", Object.class, notificationId);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MailProbeConfiguration {
        @Bean
        @Primary
        RecordingSmtpProbe recordingSmtpProbe() {
            return new RecordingSmtpProbe();
        }
    }

    static final class RecordingSmtpProbe implements SmtpProbe {
        private final List<String> activationTokens = new java.util.ArrayList<>();

        @Override
        public synchronized void send(SmtpConnection connection, String recipient, String subject, String body) {
            int marker = body.indexOf("token=");
            if (marker >= 0) {
                activationTokens.add(body.substring(marker + "token=".length()).trim());
            }
        }

        synchronized String activationToken() {
            return activationTokens.getLast();
        }

        synchronized void clear() {
            activationTokens.clear();
        }
    }

    private static final class StepClock extends Clock {
        private volatile Instant current;

        private StepClock(Instant initial) {
            current = initial;
        }

        private void set(Instant instant) {
            current = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Ho_Chi_Minh");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
