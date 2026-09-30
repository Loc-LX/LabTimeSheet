package com.lab.labtimesheet.feature.attendance.service;

import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.platform.authorization.AuthorizationColumn;
import com.lab.labtimesheet.platform.authorization.AuthorizationPolicy;
import com.lab.labtimesheet.platform.authorization.AuthorizationRequest;
import com.lab.labtimesheet.platform.authorization.AuthorizationCapability;
import com.lab.labtimesheet.platform.model.GlobalRole;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;

/** Builds policy facts from Attendance-owned identity and record scope. */
public final class AttendanceAuthorizationRequests {

    private AttendanceAuthorizationRequests() {}

    /**
     * Resolves one active actor column after the caller verifies persisted identity and record ownership.
     *
     * @param actor authenticated Attendance actor
     * @param activeAccount whether the persisted actor account is active and matches the actor role
     * @param recordOwnerId record owner or actor ID for an own-record operation
     * @param scopeState resolved generic scope state, or {@code null} when the capability has no scope predicate
     * @return immutable policy request with an empty actor-column set when the scope is not established
     */
    public static AuthorizationRequest request(
            AttendanceActor actor, boolean activeAccount, long recordOwnerId, String scopeState) {
        Set<AuthorizationColumn> columns = EnumSet.noneOf(AuthorizationColumn.class);
        if (activeAccount && actor != null && actor.role() != null) {
            switch (actor.role()) {
                case ADMIN -> columns.add(AuthorizationColumn.ADMIN);
                case MENTOR -> columns.add(AuthorizationColumn.OWNING_MENTOR);
                case INTERN -> {
                    if (actor.userId() == recordOwnerId) {
                        columns.add(AuthorizationColumn.ACTIVE_MEMBER_ASSIGNEE);
                    }
                }
            }
        }
        return new AuthorizationRequest(columns, scopeState, null, null);
    }

    /**
     * Resolves the policy request for deciding an attendance request.
     * Only an active Mentor who is the designated responsible Mentor for the intern receives the OWNING_MENTOR column.
     *
     * @param actor authenticated Attendance actor
     * @param activeAccount whether the persisted actor account is active and matches the actor role
     * @param responsible whether the actor is the responsible Mentor for the intern
     * @return policy request with OWNING_MENTOR column only when activeAccount, role=MENTOR, and responsible are all true
     */
    public static AuthorizationRequest decisionRequest(
            AttendanceActor actor, boolean activeAccount, boolean responsible) {
        Set<AuthorizationColumn> columns = EnumSet.noneOf(AuthorizationColumn.class);
        if (activeAccount && actor != null && actor.role() == GlobalRole.MENTOR && responsible) {
            columns.add(AuthorizationColumn.OWNING_MENTOR);
        }
        return new AuthorizationRequest(columns, null, null, null);
    }

    /**
     * Rejects an operation when the catalogue grants none of the module-resolved actor columns.
     *
     * @param policy shared platform policy
     * @param capability exact §5.2 capability
     * @param request Attendance-resolved actor and state facts
     */
    public static void requireAllowed(
            AuthorizationPolicy policy, AuthorizationCapability capability, AuthorizationRequest request) {
        if (!policy.allows(capability, request)) {
            throw new AccessDeniedException("Attendance operation is outside the requested scope");
        }
    }
}
