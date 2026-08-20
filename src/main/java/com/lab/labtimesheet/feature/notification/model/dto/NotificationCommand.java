package com.lab.labtimesheet.feature.notification.model.dto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.lab.labtimesheet.feature.notification.model.NotificationType;

/**
 * Non-secret notification input supplied by a domain application service.
 *
 * <p>Recipient IDs are intentionally retained in caller order so the notification
 * service can collapse duplicates deterministically. Email content is ordinary
 * event content; activation and password-reset links must not be supplied here.
 *
 * @param recipientUserIds candidate recipient account IDs
 * @param type stable notification type
 * @param title in-app title
 * @param body in-app body
 * @param actionUrl optional authenticated application route
 * @param emailRequested whether the event is designated for ordinary email
 * @param emailSubject ordinary email subject when requested
 * @param emailBody ordinary email body when requested
 */
public record NotificationCommand(
        List<Long> recipientUserIds,
        NotificationType type,
        String title,
        String body,
        String actionUrl,
        boolean emailRequested,
        String emailSubject,
        String emailBody) {

    /**
     * Validates and freezes command values at the feature boundary.
     */
    public NotificationCommand {
        recipientUserIds = recipientUserIds == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(recipientUserIds));
        type = Objects.requireNonNull(type, "type");
        title = requireText(title, "Notification title is required", 200);
        body = requireText(body, "Notification body is required", Integer.MAX_VALUE);
        actionUrl = normalizeOptional(actionUrl, 500);

        if (emailRequested) {
            if (!type.supportsOrdinaryEmail()) {
                throw new IllegalArgumentException("Notification type does not support ordinary email");
            }
            emailSubject = requireText(emailSubject, "Email subject is required", 255);
            emailBody = requireText(emailBody, "Email body is required", Integer.MAX_VALUE);
        } else if (emailSubject != null || emailBody != null) {
            throw new IllegalArgumentException("Email content requires an ordinary-email request");
        }
    }

    /**
     * Creates an in-app-only command.
     *
     * @param recipientUserIds candidate recipient account IDs
     * @param type stable notification type
     * @param title in-app title
     * @param body in-app body
     * @param actionUrl optional authenticated application route
     * @return validated in-app command
     */
    public static NotificationCommand inApp(
            Collection<Long> recipientUserIds,
            NotificationType type,
            String title,
            String body,
            String actionUrl) {
        return new NotificationCommand(
                copyRecipients(recipientUserIds), type, title, body, actionUrl, false, null, null);
    }

    /**
     * Creates a command with ordinary email attached to the in-app notification.
     *
     * @param recipientUserIds candidate recipient account IDs
     * @param type stable email-designated notification type
     * @param title in-app title
     * @param body in-app body
     * @param actionUrl optional authenticated application route
     * @param emailSubject ordinary email subject
     * @param emailBody ordinary email body
     * @return validated notification command
     */
    public static NotificationCommand inAppWithEmail(
            Collection<Long> recipientUserIds,
            NotificationType type,
            String title,
            String body,
            String actionUrl,
            String emailSubject,
            String emailBody) {
        return new NotificationCommand(
                copyRecipients(recipientUserIds), type, title, body, actionUrl, true, emailSubject, emailBody);
    }

    private static List<Long> copyRecipients(Collection<Long> recipientUserIds) {
        return recipientUserIds == null ? List.of() : List.copyOf(recipientUserIds);
    }

    private static String requireText(String value, String message, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(message + " exceeds the maximum length");
        }
        return normalized;
    }

    private static String normalizeOptional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("Notification action URL exceeds the maximum length");
        }
        return normalized;
    }
}
