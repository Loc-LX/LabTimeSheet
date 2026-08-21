package com.lab.labtimesheet.feature.notification.model.dto;

import java.time.Instant;

import com.lab.labtimesheet.feature.notification.model.EmailDeliveryStatus;
import com.lab.labtimesheet.feature.notification.model.NotificationType;

/**
 * Recipient-scoped, non-secret notification projection for list and unread views.
 *
 * @param id notification identifier
 * @param recipientUserId owning recipient account identifier
 * @param type stable notification type
 * @param title in-app title
 * @param body in-app body
 * @param actionUrl optional authenticated application route
 * @param readAt read timestamp, or null while unread
 * @param emailStatus ordinary email state
 * @param emailTo resolved recipient address, or null for in-app-only records
 * @param emailSubject ordinary email subject, or null when email is not required
 * @param emailAttempts number of ordinary-email attempts
 * @param emailNextAttemptAt next retry timestamp, or null when no retry is scheduled
 * @param emailSentAt successful send timestamp, or null before delivery
 * @param emailLastError sanitized delivery failure detail, or null when absent
 * @param createdAt notification creation timestamp
 */
public record NotificationView(
        long id,
        long recipientUserId,
        NotificationType type,
        String title,
        String body,
        String actionUrl,
        Instant readAt,
        EmailDeliveryStatus emailStatus,
        String emailTo,
        String emailSubject,
        int emailAttempts,
        Instant emailNextAttemptAt,
        Instant emailSentAt,
        String emailLastError,
        Instant createdAt) {
}
