package com.lab.labtimesheet.feature.notification.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.lab.labtimesheet.feature.account.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.integration.service.MailDeliveryService;
import com.lab.labtimesheet.feature.notification.model.EmailDeliveryStatus;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationCommand;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationView;
import com.lab.labtimesheet.feature.notification.model.entity.Notification;
import com.lab.labtimesheet.feature.notification.repository.NotificationRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publishes durable recipient-scoped notifications and owns the immediate
 * ordinary-email transition after the originating transaction commits.
 *
 * <p>The service intentionally depends on the account and integration service
 * boundaries rather than their repositories. Domain callers can commit their
 * own action and the in-app row atomically while SMTP delivery remains a
 * post-commit side effect.
 */
@Service
public class NotificationService {
    private final NotificationRepository notifications;
    private final AccountService accounts;
    private final MailDeliveryService mailDelivery;
    private final Clock clock;
    private final TransactionTemplate deliveryTransactions;

    NotificationService(
            NotificationRepository notifications,
            AccountService accounts,
            MailDeliveryService mailDelivery,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.notifications = notifications;
        this.accounts = accounts;
        this.mailDelivery = mailDelivery;
        this.clock = clock;
        this.deliveryTransactions = new TransactionTemplate(transactionManager);
        this.deliveryTransactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Persists one notification per distinct recipient and registers ordinary
     * email for immediate delivery only after the surrounding transaction commits.
     *
     * @param command validated event content and candidate recipients
     * @return persisted notification identifiers in first-seen recipient order
     */
    @Transactional
    public List<Long> publish(NotificationCommand command) {
        Objects.requireNonNull(command, "command");
        List<Long> recipientIds = distinctRecipientIds(command.recipientUserIds());
        if (recipientIds.isEmpty()) {
            return List.of();
        }

        List<AccountIdentity> recipients = recipientIds.stream()
                .map(accounts::requireIdentityById)
                .toList();
        boolean smtpAvailable = command.emailRequested() && mailDelivery.isAvailable();
        Instant now = clock.instant();
        List<Notification> rows = new ArrayList<>(recipients.size());
        for (AccountIdentity recipient : recipients) {
            rows.add(Notification.create(
                    recipient.id(), command, recipient.email(), smtpAvailable, now));
        }

        List<Notification> saved = notifications.saveAll(rows);
        notifications.flush();
        if (smtpAvailable) {
            for (Notification notification : saved) {
                registerAfterCommit(notification.getId());
            }
        }
        return saved.stream().map(Notification::getId).toList();
    }

    /**
     * Lists notifications owned by one recipient, newest first.
     *
     * @param recipientUserId authenticated recipient account identifier
     * @return recipient-scoped notification projections
     */
    @Transactional(readOnly = true)
    public List<NotificationView> listForRecipient(long recipientUserId) {
        requirePositiveId(recipientUserId, "Recipient user ID");
        return notifications.findByRecipientUserIdOrderByCreatedAtDescIdDesc(recipientUserId)
                .stream()
                .map(NotificationService::view)
                .toList();
    }

    /**
     * Counts unread notifications owned by one recipient.
     *
     * @param recipientUserId authenticated recipient account identifier
     * @return unread count
     */
    @Transactional(readOnly = true)
    public long unreadCount(long recipientUserId) {
        requirePositiveId(recipientUserId, "Recipient user ID");
        return notifications.countByRecipientUserIdAndReadAtIsNull(recipientUserId);
    }

    /**
     * Marks a recipient-owned notification as read. Repeating the operation,
     * or supplying another recipient's notification ID, is an idempotent no-op.
     *
     * @param recipientUserId authenticated recipient account identifier
     * @param notificationId notification identifier
     */
    @Transactional
    public void markRead(long recipientUserId, long notificationId) {
        requirePositiveId(recipientUserId, "Recipient user ID");
        requirePositiveId(notificationId, "Notification ID");
        notifications.findForUpdateByIdAndRecipientUserId(notificationId, recipientUserId)
                .ifPresent(notification -> notification.markRead(clock.instant()));
    }

    private void registerAfterCommit(long notificationId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Notification publication requires an active transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deliverImmediately(notificationId);
            }
        });
    }

    private void deliverImmediately(long notificationId) {
        deliveryTransactions.executeWithoutResult(status -> {
            Optional<Notification> candidate = notifications.findForUpdateById(notificationId);
            if (candidate.isEmpty()) {
                return;
            }

            Notification notification = candidate.get();
            if (notification.getEmailStatus() != EmailDeliveryStatus.PENDING) {
                return;
            }
            if (!mailDelivery.isAvailable()) {
                notification.markUnavailable(clock.instant());
                return;
            }

            try {
                mailDelivery.send(
                        notification.getEmailTo(),
                        notification.getEmailSubject(),
                        notification.getEmailBody());
                notification.markSent(clock.instant());
            } catch (RuntimeException ignored) {
                notification.markDeliveryFailure(clock.instant());
            }
        });
    }

    private static List<Long> distinctRecipientIds(List<Long> candidates) {
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long candidate : candidates) {
            if (candidate == null || candidate <= 0) {
                throw new IllegalArgumentException("Recipient user IDs must be positive");
            }
            distinct.add(candidate);
        }
        return List.copyOf(distinct);
    }

    private static NotificationView view(Notification notification) {
        return new NotificationView(
                notification.getId(),
                notification.getRecipientUserId(),
                notification.getNotificationType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getActionUrl(),
                notification.getReadAt(),
                notification.getEmailStatus(),
                notification.getEmailTo(),
                notification.getEmailSubject(),
                notification.getEmailAttempts(),
                notification.getEmailNextAttemptAt(),
                notification.getEmailSentAt(),
                notification.getEmailLastError(),
                notification.getCreatedAt());
    }

    private static void requirePositiveId(long id, String label) {
        if (id <= 0) {
            throw new IllegalArgumentException(label + " must be positive");
        }
    }
}
