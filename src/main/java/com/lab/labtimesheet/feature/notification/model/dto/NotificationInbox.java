package com.lab.labtimesheet.feature.notification.model.dto;

import java.util.List;
import java.util.Objects;

/**
 * Immutable recipient-scoped notification list and unread total.
 *
 * <p>The list is copied at the service boundary so callers cannot mutate a retained view or
 * accidentally alter a later render. The count is calculated only for the same authenticated
 * recipient used for the list query.
 *
 * @param notifications newest-first notification views for one authenticated recipient
 * @param unreadCount number of unread rows belonging to that same recipient
 */
public record NotificationInbox(List<NotificationInboxItem> notifications, long unreadCount) {

    /**
     * Copies the list and validates the aggregate count.
     *
     * @param notifications newest-first notification views for one recipient
     * @param unreadCount unread rows for that recipient
     */
    public NotificationInbox {
        notifications = List.copyOf(Objects.requireNonNull(notifications, "notifications"));
        if (unreadCount < 0) {
            throw new IllegalArgumentException("Unread count must not be negative");
        }
    }
}
