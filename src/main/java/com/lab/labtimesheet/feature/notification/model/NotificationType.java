package com.lab.labtimesheet.feature.notification.model;

/**
 * Stored notification families permitted by the V1 schema.
 *
 * <p>Leave and correction decisions use the scalar transition in the event DTO to retain
 * {@code REVERTED} and {@code AUTO_REJECTED} outcomes without allocating a schema event type that
 * the reviewed migration does not contain.
 */
public enum NotificationType {
    LEAVE_SUBMITTED(true),
    LEAVE_DECIDED(true),
    CORRECTION_SUBMITTED(true),
    CORRECTION_DECIDED(true),
    MEMBERSHIP_CHANGED(true),
    LEADERSHIP_CHANGED(true),
    PROJECT_INVITATION_CREATED(true),
    PROJECT_INVITATION_RESOLVED(true),
    MEMBERSHIP_EXIT_REQUESTED(true),
    MEMBERSHIP_EXIT_RESOLVED(true),
    TASK_ASSIGNED(true),
    TASK_REASSIGNED(true),
    TASK_STATUS_CHANGED(false),
    TASK_COMMENTED(false),
    SYSTEM(false);

    private final boolean emailDesignated;

    NotificationType(boolean emailDesignated) {
        this.emailDesignated = emailDesignated;
    }

    /**
     * Indicates whether the requirement designates this event family for ordinary email.
     *
     * @return {@code true} for events that may request an ordinary-email attempt
     */
    public boolean emailDesignated() {
        return emailDesignated;
    }
}
