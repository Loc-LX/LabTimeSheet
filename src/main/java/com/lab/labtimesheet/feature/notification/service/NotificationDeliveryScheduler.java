package com.lab.labtimesheet.feature.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the bounded ordinary-email retry worker once per minute. */
@Component
@RequiredArgsConstructor
public class NotificationDeliveryScheduler {
    private final NotificationService notifications;

    /**
     * Attempts due rows; state transitions are idempotent and guarded by notification row locks.
     */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    public void retryDueEmails() {
        notifications.retryDueEmails();
    }
}
