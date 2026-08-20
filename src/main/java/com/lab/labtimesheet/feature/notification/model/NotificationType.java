package com.lab.labtimesheet.feature.notification.model;

/**
 * Stable notification types enforced by the baseline notification schema.
 *
 * <p>The type also records whether the originating event is allowed to request
 * ordinary email. Task comments and status changes are intentionally in-app only.
 */
public enum NotificationType {
    LEAVE_SUBMITTED,
    LEAVE_DECIDED,
    CORRECTION_SUBMITTED,
    CORRECTION_DECIDED,
    MEMBERSHIP_CHANGED,
    LEADERSHIP_CHANGED,
    PROJECT_INVITATION_CREATED,
    PROJECT_INVITATION_RESOLVED,
    MEMBERSHIP_EXIT_REQUESTED,
    MEMBERSHIP_EXIT_RESOLVED,
    TASK_ASSIGNED,
    TASK_REASSIGNED,
    TASK_STATUS_CHANGED,
    TASK_COMMENTED,
    SYSTEM;

    /**
     * Reports whether this event may carry an ordinary email delivery request.
     *
     * @return {@code true} for events designated for ordinary email
     */
    public boolean supportsOrdinaryEmail() {
        return switch (this) {
            case LEAVE_SUBMITTED, LEAVE_DECIDED,
                    CORRECTION_SUBMITTED, CORRECTION_DECIDED,
                    MEMBERSHIP_CHANGED, LEADERSHIP_CHANGED,
                    PROJECT_INVITATION_CREATED, PROJECT_INVITATION_RESOLVED,
                    MEMBERSHIP_EXIT_REQUESTED, MEMBERSHIP_EXIT_RESOLVED,
                    TASK_ASSIGNED, TASK_REASSIGNED -> true;
            case TASK_STATUS_CHANGED, TASK_COMMENTED, SYSTEM -> false;
        };
    }
}
