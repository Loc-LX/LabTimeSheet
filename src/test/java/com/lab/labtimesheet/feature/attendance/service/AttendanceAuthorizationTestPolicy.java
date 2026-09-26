package com.lab.labtimesheet.feature.attendance.service;

import com.lab.labtimesheet.platform.authorization.AuthorizationCatalogue;
import com.lab.labtimesheet.platform.authorization.AuthorizationPolicy;

/** Loads the production §5.2 catalogue for Attendance service unit tests. */
final class AttendanceAuthorizationTestPolicy {

    private AttendanceAuthorizationTestPolicy() {}

    /** @return policy backed by the repository's production authorization catalogue */
    static AuthorizationPolicy create() {
        return new AuthorizationPolicy(AuthorizationCatalogue.loadDefault());
    }
}
