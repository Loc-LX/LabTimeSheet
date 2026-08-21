package com.lab.labtimesheet.feature.notification.model;

/** Persisted ordinary-email delivery states for one in-app notification row. */
public enum NotificationEmailStatus {
    NOT_REQUIRED,
    PENDING,
    SENT,
    FAILED,
    UNAVAILABLE
}
