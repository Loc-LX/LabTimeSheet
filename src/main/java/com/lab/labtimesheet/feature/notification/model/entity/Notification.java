package com.lab.labtimesheet.feature.notification.model.entity;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.lab.labtimesheet.feature.notification.model.EmailDeliveryStatus;
import com.lab.labtimesheet.feature.notification.model.NotificationType;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationCommand;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Durable recipient-scoped in-app notification with optional ordinary-email state.
 *
 * <p>The entity contains no account relationship so notification reads cannot
 * accidentally widen their recipient scope through a JPA association. Email
 * delivery transitions are explicit and retain only sanitized failure state.
 */
@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {
    private static final List<Duration> RETRY_DELAYS = List.of(
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(30),
            Duration.ofHours(2),
            Duration.ofHours(12));
    private static final String SANITIZED_DELIVERY_FAILURE = "SMTP delivery failed";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_user_id", nullable = false)
    private long recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 32)
    private NotificationType notificationType;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "action_url", length = 500)
    private String actionUrl;

    @Column(name = "read_at")
    private Instant readAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_status", nullable = false, length = 24)
    private EmailDeliveryStatus emailStatus;

    @Column(name = "email_to", length = 320)
    private String emailTo;

    @Column(name = "email_subject", length = 255)
    private String emailSubject;

    @Column(name = "email_body", columnDefinition = "text")
    private String emailBody;

    @Column(name = "email_attempts", nullable = false)
    private int emailAttempts;

    @Column(name = "email_next_attempt_at")
    private Instant emailNextAttemptAt;

    @Column(name = "email_sent_at")
    private Instant emailSentAt;

    @Column(name = "email_last_error", length = 1000)
    private String emailLastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    private Notification(
            long recipientUserId,
            NotificationCommand command,
            String recipientEmail,
            boolean smtpAvailable,
            Instant now) {
        this.recipientUserId = recipientUserId;
        this.notificationType = command.type();
        this.title = command.title();
        this.body = command.body();
        this.actionUrl = command.actionUrl();
        this.createdAt = now;
        this.updatedAt = now;

        if (!command.emailRequested()) {
            this.emailStatus = EmailDeliveryStatus.NOT_REQUIRED;
            this.emailAttempts = 0;
            return;
        }

        this.emailTo = requireEmail(recipientEmail);
        this.emailSubject = command.emailSubject();
        this.emailBody = command.emailBody();
        this.emailAttempts = 0;
        if (smtpAvailable) {
            this.emailStatus = EmailDeliveryStatus.PENDING;
            this.emailNextAttemptAt = now;
        } else {
            this.emailStatus = EmailDeliveryStatus.UNAVAILABLE;
        }
    }

    /**
     * Creates a notification row from a validated ordinary domain event.
     *
     * @param recipientUserId recipient account identifier
     * @param command validated notification command
     * @param recipientEmail recipient address resolved by the account feature
     * @param smtpAvailable whether an active tested SMTP revision exists
     * @param now server creation timestamp
     * @return new unsaved notification entity
     */
    public static Notification create(
            long recipientUserId,
            NotificationCommand command,
            String recipientEmail,
            boolean smtpAvailable,
            Instant now) {
        if (recipientUserId <= 0) {
            throw new IllegalArgumentException("Recipient user ID must be positive");
        }
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(now, "now");
        return new Notification(recipientUserId, command, recipientEmail, smtpAvailable, now);
    }

    /**
     * Marks the notification read. Repeating the operation is idempotent.
     *
     * @param at server read timestamp
     */
    public void markRead(Instant at) {
        Objects.requireNonNull(at, "at");
        if (readAt == null) {
            readAt = at;
            updatedAt = at;
        }
    }

    /**
     * Records a successful ordinary-email attempt.
     *
     * @param at server delivery timestamp
     * @throws IllegalStateException when the notification is not pending delivery
     */
    public void markSent(Instant at) {
        requirePending();
        Objects.requireNonNull(at, "at");
        requireAttemptAvailable();
        emailAttempts++;
        emailStatus = EmailDeliveryStatus.SENT;
        emailSentAt = at;
        emailNextAttemptAt = null;
        emailLastError = null;
        updatedAt = at;
    }

    /**
     * Records a failed ordinary-email attempt and schedules the next bounded retry,
     * or moves the row to terminal failure after the sixth total attempt.
     *
     * @param at server failure timestamp
     * @throws IllegalStateException when the notification is not pending delivery
     */
    public void markDeliveryFailure(Instant at) {
        requirePending();
        Objects.requireNonNull(at, "at");
        requireAttemptAvailable();
        emailAttempts++;
        emailLastError = SANITIZED_DELIVERY_FAILURE;
        if (emailAttempts >= 6) {
            emailStatus = EmailDeliveryStatus.FAILED;
            emailNextAttemptAt = null;
        } else {
            emailStatus = EmailDeliveryStatus.PENDING;
            emailNextAttemptAt = at.plus(RETRY_DELAYS.get(emailAttempts - 1));
        }
        updatedAt = at;
    }

    /**
     * Permanently records that ordinary email was unavailable before an attempt.
     *
     * @param at server state-transition timestamp
     * @throws IllegalStateException when the notification is not pending delivery
     */
    public void markUnavailable(Instant at) {
        requirePending();
        Objects.requireNonNull(at, "at");
        emailStatus = EmailDeliveryStatus.UNAVAILABLE;
        emailNextAttemptAt = null;
        updatedAt = at;
    }

    private void requirePending() {
        if (emailStatus != EmailDeliveryStatus.PENDING) {
            throw new IllegalStateException("Notification email is not pending");
        }
    }

    private void requireAttemptAvailable() {
        if (emailAttempts >= 6) {
            throw new IllegalStateException("Notification email attempt limit reached");
        }
    }

    private static String requireEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Recipient email is required for ordinary email");
        }
        return email.trim();
    }
}
