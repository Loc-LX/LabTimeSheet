package com.lab.labtimesheet.feature.notification.model.dto;

import java.time.Instant;

import com.lab.labtimesheet.feature.notification.model.NotificationEmailStatus;

/**
 * Safe Admin operational projection for ordinary-email delivery.
 *
 * <p>Email body and adapter diagnostics are intentionally omitted; the Admin can inspect
 * delivery state and invoke a retry without receiving secret payload material. The recipient
 * address ({@code emailTo}) and most-recent failure reason ({@code lastError}) are included
 * so the Admin can identify the failing mailbox without reading the message content
 * (NOT-007, AC-NOT-002, AC-NOT-007).</p>
 *
 * @param id notification identifier
 * @param recipientUserId recipient account identifier
 * @param title notification title
 * @param status delivery status
 * @param attempts total attempts for the current bounded retry cycle
 * @param nextAttemptAt next retry instant, or {@code null}
 * @param updatedAt last delivery-state update instant
 * @param emailTo recipient email address, or {@code null} when status is {@code NOT_REQUIRED} or {@code UNAVAILABLE}
 * @param lastError most-recent delivery failure class name, or {@code null} when no attempt has failed
 */
public record NotificationDeliveryView(
        long id,
        long recipientUserId,
        String title,
        NotificationEmailStatus status,
        int attempts,
        Instant nextAttemptAt,
        Instant updatedAt,
        String emailTo,
        String lastError) {
}
