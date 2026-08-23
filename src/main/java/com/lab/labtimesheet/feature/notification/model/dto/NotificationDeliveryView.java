package com.lab.labtimesheet.feature.notification.model.dto;

import java.time.Instant;

import com.lab.labtimesheet.feature.notification.model.NotificationEmailStatus;

/**
 * Safe Admin operational projection for ordinary-email delivery.
 *
 * <p>Email body, recipient address, and adapter diagnostics are intentionally omitted; the Admin can inspect
 * delivery state and invoke a retry without receiving internal or secret payload material.</p>
 *
 * @param id notification identifier
 * @param recipientUserId recipient account identifier
 * @param title notification title
 * @param status delivery status
 * @param attempts total attempts for the current bounded retry cycle
 * @param nextAttemptAt next retry instant, or {@code null}
 * @param updatedAt last delivery-state update instant
 */
public record NotificationDeliveryView(
        long id,
        long recipientUserId,
        String title,
        NotificationEmailStatus status,
        int attempts,
        Instant nextAttemptAt,
        Instant updatedAt) {
}
