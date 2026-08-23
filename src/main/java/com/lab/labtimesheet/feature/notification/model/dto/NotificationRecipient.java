package com.lab.labtimesheet.feature.notification.model.dto;

import java.util.Locale;
import java.util.Objects;

/**
 * Immutable scalar recipient facts supplied by an owning domain feature.
 *
 * <p>The Account identifier is the deduplication key. Account persistence is deliberately not
 * reached by the notification producer, so the caller supplies the already-authorized address.
 */
public record NotificationRecipient(long userId, String email) {
    /** Validates and canonicalizes the scalar recipient boundary. */
    public NotificationRecipient {
        if (userId <= 0) {
            throw new IllegalArgumentException("Notification recipient ID must be positive");
        }
        Objects.requireNonNull(email, "email");
        email = email.trim().toLowerCase(Locale.ROOT);
        if (email.isBlank() || email.length() > 320 || !email.contains("@")) {
            throw new IllegalArgumentException("Notification recipient email is invalid");
        }
    }
}
