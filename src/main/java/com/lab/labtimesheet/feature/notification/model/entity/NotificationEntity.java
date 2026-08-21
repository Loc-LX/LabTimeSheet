package com.lab.labtimesheet.feature.notification.model.entity;

import java.time.Instant;

import com.lab.labtimesheet.feature.notification.model.NotificationEmailStatus;
import com.lab.labtimesheet.feature.notification.model.NotificationType;
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
 * In-app notification row with optional non-secret ordinary-email delivery state.
 *
 * <p>This entity stores only ordinary notification text and a safe relative action route. Raw
 * activation/password-reset links are intentionally outside this table and service.
 */
@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationEntity {
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
    private NotificationEmailStatus emailStatus;

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

    private NotificationEntity(
            long recipientUserId,
            NotificationType notificationType,
            String title,
            String body,
            String actionUrl,
            NotificationEmailStatus emailStatus,
            String emailTo,
            String emailSubject,
            String emailBody,
            Instant emailNextAttemptAt,
            Instant now) {
        this.recipientUserId = recipientUserId;
        this.notificationType = notificationType;
        this.title = title;
        this.body = body;
        this.actionUrl = actionUrl;
        this.emailStatus = emailStatus;
        this.emailTo = emailTo;
        this.emailSubject = emailSubject;
        this.emailBody = emailBody;
        this.emailNextAttemptAt = emailNextAttemptAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Creates one unsaved in-app notification and optional immediate ordinary-email attempt state.
     *
     * @param recipientUserId recipient account identifier
     * @param notificationType reviewed V1 notification family
     * @param title nonblank in-app and email subject
     * @param body nonblank in-app and email body
     * @param actionUrl safe relative action route, or null
     * @param emailStatus initial delivery state
     * @param emailTo recipient email when delivery is available, or null
     * @param emailSubject ordinary-email subject, or null
     * @param emailBody ordinary-email body, or null
     * @param emailNextAttemptAt initial pending-attempt instant, or null
     * @param now server creation instant
     * @return unsaved notification entity
     */
    public static NotificationEntity create(
            long recipientUserId,
            NotificationType notificationType,
            String title,
            String body,
            String actionUrl,
            NotificationEmailStatus emailStatus,
            String emailTo,
            String emailSubject,
            String emailBody,
            Instant emailNextAttemptAt,
            Instant now) {
        return new NotificationEntity(
                recipientUserId, notificationType, title, body, actionUrl, emailStatus,
                emailTo, emailSubject, emailBody, emailNextAttemptAt, now);
    }

    /**
     * Marks this notification read for its own recipient; repeating the operation is a no-op.
     *
     * <p>The service loads this entity through a recipient-qualified query, so this state
     * transition cannot be applied to another account's row through the inbox boundary.
     *
     * @param now server instant recorded for the first mark-read operation
     */
    public void markRead(Instant now) {
        if (readAt != null) {
            return;
        }
        readAt = now;
        updatedAt = now;
    }

    /**
     * Marks the one immediate ordinary-email attempt successful after external I/O has completed.
     *
     * @param now server delivery instant
     */
    public void markSent(Instant now) {
        if (emailStatus != NotificationEmailStatus.PENDING) {
            return;
        }
        emailStatus = NotificationEmailStatus.SENT;
        emailAttempts++;
        emailNextAttemptAt = null;
        emailSentAt = now;
        emailLastError = null;
        updatedAt = now;
    }

    /**
     * Retains a failed immediate attempt in bounded retry state for the later notification worker.
     *
     * @param now server failure instant
     * @param nextAttemptAt first retry due instant selected by the Platform boundary
     * @param safeError non-secret bounded diagnostic, never a provider message
     */
    public void retainPendingRetry(Instant now, Instant nextAttemptAt, String safeError) {
        if (emailStatus != NotificationEmailStatus.PENDING) {
            return;
        }
        emailAttempts++;
        emailNextAttemptAt = nextAttemptAt;
        emailLastError = safeError;
        updatedAt = now;
    }
}
