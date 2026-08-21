package com.lab.labtimesheet.feature.notification.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.lab.labtimesheet.feature.integration.service.MailDeliveryService;
import com.lab.labtimesheet.feature.notification.model.NotificationEmailStatus;
import com.lab.labtimesheet.feature.notification.model.NotificationType;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationAction;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationEvent;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationInbox;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationInboxItem;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationRecipient;
import com.lab.labtimesheet.feature.notification.model.entity.NotificationEntity;
import com.lab.labtimesheet.feature.notification.repository.NotificationRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Publishes Platform-owned in-app notifications and one immediate ordinary-email attempt.
 *
 * <p>All recipient facts are scalar DTOs supplied by the already-authorized owning feature. The
 * caller must already own its domain transaction; rows are written in that transaction and
 * provider I/O is deferred until after commit and is run
 * with transaction resources suspended so SMTP cannot roll back the domain mutation. Missing SMTP
 * is recorded as {@code UNAVAILABLE}, and that state has no later delivery path. A transient
 * adapter failure retains bounded {@code PENDING} state for the later retry worker.
 */
@Service
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class NotificationService {
    private static final Set<String> SELF_TASK_TRANSITIONS = Set.of("SELF_TASK", "SELF_ASSIGNED", "SELF_CREATED");

    private final NotificationRepository notifications;
    private final MailDeliveryService mailDelivery;
    private final Clock clock;
    private final PlatformTransactionManager transactionManager;

    /**
     * Persists one notification per distinct recipient and schedules only designated ordinary-email delivery.
     *
     * <p>The method is mandatory inside the already-open domain transaction: the caller's mutation and this
     * notification write commit or roll back together. Duplicate recipient IDs are collapsed in encounter order. A
     * duplicate with a different canonical email is rejected rather than silently choosing an address. Ordinary
     * email is derived only from the event family; no action flag can suppress a designated requirement. Self-Task
     * actions are silent only for a validated {@code TASK_ASSIGNED} self-transition. Leave/correction transitions such
     * as {@code REVERTED} and {@code AUTO_REJECTED} remain in the immutable event DTO/body while their persisted family
     * uses the existing V1 {@code *_DECIDED} type.
     *
     * @param event immutable notification family, transition, title, and body
     * @param action immutable safe action route and self-Task marker
     * @param recipients already-authorized scalar recipient facts
     * @return number of in-app rows persisted
     * @throws org.springframework.transaction.IllegalTransactionStateException when no caller transaction is active
     *         or the publication is attempted outside the caller's domain transaction
     * @throws IllegalArgumentException when a self-Task marker conflicts with the event
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int publish(
            NotificationEvent event, NotificationAction action, Collection<NotificationRecipient> recipients) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(recipients, "recipients");
        validateSelfTaskShape(event, action);
        if (action.selfTask() || recipients.isEmpty()) {
            return 0;
        }

        Map<Long, NotificationRecipient> distinctRecipients = deduplicate(recipients);
        boolean emailDesignated = event.type().emailDesignated();
        boolean smtpAvailable = emailDesignated && mailDelivery.isAvailable();
        Instant now = clock.instant();
        String body = event.body() + "\n\nTransition: " + event.transition();
        String emailBody = action.actionUrl() == null ? body : body + "\n\nOpen: " + action.actionUrl();
        NotificationEmailStatus emailStatus = emailDesignated
                ? smtpAvailable ? NotificationEmailStatus.PENDING : NotificationEmailStatus.UNAVAILABLE
                : NotificationEmailStatus.NOT_REQUIRED;

        List<NotificationEntity> saved = distinctRecipients.values().stream()
                .map(recipient -> NotificationEntity.create(
                        recipient.userId(), event.type(), event.title(), body, action.actionUrl(), emailStatus,
                        smtpAvailable ? recipient.email() : null,
                        smtpAvailable ? event.title() : null,
                        smtpAvailable ? emailBody : null,
                        smtpAvailable ? now : null,
                        now))
                .toList();
        notifications.saveAll(saved);
        notifications.flush();
        if (smtpAvailable) {
            scheduleDelivery(saved.stream().map(NotificationEntity::getId).toList());
        }
        return saved.size();
    }

    /**
     * Reads the authenticated account's notification list and unread count without exposing
     * notification entities or email-delivery metadata.
     *
     * <p>The caller must derive and authorize {@code recipientUserId} from the authenticated
     * principal before invoking this boundary. This service intentionally does not look up an
     * Account, and every persistence query remains recipient-scoped. Results are newest-first and
     * carry no pagination contract for the current V1 inbox.
     *
     * @param recipientUserId already-authenticated recipient account identifier
     * @return immutable newest-first list and unread count for that recipient only
     */
    @Transactional(readOnly = true)
    public NotificationInbox inboxFor(long recipientUserId) {
        List<NotificationInboxItem> items = notifications
                .findByRecipientUserIdOrderByCreatedAtDescIdDesc(recipientUserId)
                .stream()
                .map(NotificationService::toInboxItem)
                .toList();
        return new NotificationInbox(items, notifications.countByRecipientUserIdAndReadAtIsNull(recipientUserId));
    }

    /**
     * Marks one notification read only when it belongs to the authenticated recipient.
     *
     * <p>Missing and foreign identifiers intentionally produce no error and no mutation, which
     * prevents this boundary from becoming an identifier-enumeration oracle. Repeating an own
     * mark-read is idempotent and preserves the original server timestamp.
     *
     * @param recipientUserId already-authenticated recipient account identifier
     * @param notificationId requested notification identifier
     */
    @Transactional
    public void markRead(long recipientUserId, long notificationId) {
        if (recipientUserId <= 0 || notificationId <= 0) {
            return;
        }
        notifications.findByIdAndRecipientUserId(notificationId, recipientUserId)
                .ifPresent(notification -> notification.markRead(clock.instant()));
    }

    private static NotificationInboxItem toInboxItem(NotificationEntity notification) {
        return new NotificationInboxItem(
                notification.getId(), notification.getNotificationType(), notification.getTitle(),
                notification.getBody(), notification.getActionUrl(), notification.getCreatedAt(),
                notification.getReadAt() != null);
    }

    private void validateSelfTaskShape(NotificationEvent event, NotificationAction action) {
        boolean selfTransition = SELF_TASK_TRANSITIONS.contains(event.transition().toUpperCase(Locale.ROOT));
        if (action.selfTask() && (event.type() != NotificationType.TASK_ASSIGNED || !selfTransition)) {
            throw new IllegalArgumentException("Self-Task silence requires a TASK_ASSIGNED self-transition");
        }
        if (!action.selfTask() && event.type() == NotificationType.TASK_ASSIGNED && selfTransition) {
            throw new IllegalArgumentException("A TASK_ASSIGNED self-transition requires the self-Task marker");
        }
    }

    private Map<Long, NotificationRecipient> deduplicate(Collection<NotificationRecipient> recipients) {
        Map<Long, NotificationRecipient> distinct = new LinkedHashMap<>();
        for (NotificationRecipient recipient : recipients) {
            Objects.requireNonNull(recipient, "recipient");
            NotificationRecipient previous = distinct.putIfAbsent(recipient.userId(), recipient);
            if (previous != null && !previous.email().equals(recipient.email())) {
                throw new IllegalArgumentException("Duplicate notification recipient has conflicting email");
            }
        }
        return distinct;
    }

    private void scheduleDelivery(List<Long> ids) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Notification publication requires an active transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                ids.forEach(NotificationService.this::deliver);
            }
        });
    }

    private void deliver(long notificationId) {
        try {
            Delivery delivery = executeInNewTransaction(status -> notifications.findById(notificationId)
                    .filter(notification -> notification.getEmailStatus() == NotificationEmailStatus.PENDING)
                    .map(notification -> new Delivery(
                            notification.getEmailTo(), notification.getEmailSubject(), notification.getEmailBody()))
                    .orElse(null));
            if (delivery == null) {
                return;
            }
            try {
                sendOutsideTransaction(delivery);
            } catch (RuntimeException failure) {
                Instant failedAt = clock.instant();
                String safeError = failure.getClass().getSimpleName();
                executeInNewTransaction(status -> {
                    notifications.findById(notificationId)
                            .ifPresent(notification -> notification.retainPendingRetry(
                                    failedAt, failedAt.plus(Duration.ofMinutes(1)), safeError));
                    return null;
                });
                return;
            }
            executeInNewTransaction(status -> {
                notifications.findById(notificationId)
                        .ifPresent(notification -> notification.markSent(clock.instant()));
                return null;
            });
        } catch (RuntimeException failure) {
            // A post-commit state/adapter failure must not starve later recipients in this callback.
            log.warn("Notification delivery attempt could not complete for id {} ({})",
                    notificationId, failure.getClass().getSimpleName());
        }
    }

    private <T> T executeInNewTransaction(org.springframework.transaction.support.TransactionCallback<T> callback) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return new TransactionTemplate(transactionManager, definition).execute(callback);
    }

    private void sendOutsideTransaction(Delivery delivery) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        new TransactionTemplate(transactionManager, definition)
                .executeWithoutResult(status -> mailDelivery.send(
                        delivery.recipient(), delivery.subject(), delivery.body()));
    }

    private record Delivery(String recipient, String subject, String body) {
    }
}
