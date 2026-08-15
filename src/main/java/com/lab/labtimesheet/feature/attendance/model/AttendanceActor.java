package com.lab.labtimesheet.feature.attendance.model;

import java.util.Objects;

/**
 * Attendance authorization context resolved from the authenticated account service identity.
 *
 * @param userId authoritative application user identifier
 * @param role immutable global role used for attendance route and history scope checks
 */
public record AttendanceActor(long userId, AttendanceRole role) {

    /**
     * Rejects an actor without a resolved global role.
     */
    public AttendanceActor {
        Objects.requireNonNull(role, "role");
    }
}
