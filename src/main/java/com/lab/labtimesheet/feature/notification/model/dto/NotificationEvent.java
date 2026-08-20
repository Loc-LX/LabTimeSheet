package com.lab.labtimesheet.feature.notification.model.dto;

import java.util.Objects;

import com.lab.labtimesheet.feature.notification.model.NotificationType;

/**
 * Immutable scalar notification facts and the domain transition being presented.
 *
 * <p>The transition keeps attendance outcomes such as {@code REVERTED} and
 * {@code AUTO_REJECTED} explicit while the persisted event family remains one of the reviewed V1
 * notification types.
 */
public record NotificationEvent(NotificationType type, String transition, String title, String body) {
    /** Validates the event values before they enter the persistence or delivery boundary. */
    public NotificationEvent {
        Objects.requireNonNull(type, "type");
        transition = requireText(transition, "transition");
        title = requireText(title, "title");
        body = requireText(body, "body");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }
}
