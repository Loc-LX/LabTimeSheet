package com.lab.labtimesheet.feature.notification.model.dto;

import java.time.Instant;
import java.util.Objects;

import com.lab.labtimesheet.feature.notification.model.NotificationType;

/**
 * Immutable, non-secret presentation data for one notification belonging to the authenticated
 * recipient.
 *
 * <p>Email delivery state, recipient addresses, provider diagnostics, and retry metadata are
 * deliberately absent. The action route has already passed the notification boundary's safe-route
 * validation before it is persisted.
 *
 * @param id retained notification identifier
 * @param type reviewed notification family
 * @param title nonblank in-app title
 * @param body nonblank in-app body
 * @param actionUrl safe relative action route, or {@code null} when no action is available
 * @param createdAt server creation instant
 * @param read whether this recipient has marked the notification read
 */
public record NotificationInboxItem(
        long id,
        NotificationType type,
        String title,
        String body,
        String actionUrl,
        Instant createdAt,
        boolean read) {

    /**
     * Validates the stable scalar notification view without exposing its persistence entity.
     *
     * @param id retained notification identifier
     * @param type reviewed notification family
     * @param title nonblank in-app title
     * @param body nonblank in-app body
     * @param actionUrl safe relative action route, or {@code null}
     * @param createdAt server creation instant
     * @param read whether this recipient has marked the notification read
     */
    public NotificationInboxItem {
        if (id <= 0) {
            throw new IllegalArgumentException("Notification id must be positive");
        }
        Objects.requireNonNull(type, "type");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Notification title must not be blank");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Notification body must not be blank");
        }
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
