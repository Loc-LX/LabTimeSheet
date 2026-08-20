package com.lab.labtimesheet.feature.notification.model;

/**
 * Lifecycle of the optional ordinary-email channel attached to a notification.
 */
public enum EmailDeliveryStatus {
    NOT_REQUIRED,
    PENDING,
    SENT,
    FAILED,
    UNAVAILABLE
}
