package com.lab.labtimesheet.feature.project.model;

/**
 * Participant shape of a pending membership-exit request.
 */
public enum ProjectExitRequestType {
    /** Current Leader requests removal of another current member. */
    LEADER_REMOVAL,
    /** Current member requests to leave the Project. */
    MEMBER_LEAVE
}
