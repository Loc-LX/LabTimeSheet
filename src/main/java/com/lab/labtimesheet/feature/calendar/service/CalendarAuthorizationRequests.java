package com.lab.labtimesheet.feature.calendar.service;

import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.identity.model.dto.AccountIdentity;
import com.lab.labtimesheet.platform.authorization.AuthorizationColumn;
import com.lab.labtimesheet.platform.authorization.AuthorizationRequest;
import com.lab.labtimesheet.platform.model.GlobalRole;
import java.util.Set;

/** Builds Calendar-owned policy facts from the persisted actor identity. */
final class CalendarAuthorizationRequests {

    private CalendarAuthorizationRequests() {}

    /**
     * Resolves the Admin column only for a stored active Admin account.
     *
     * @param identity stored account identity, or {@code null} when the identifier is unknown
     * @return global-configuration request with an empty actor set when Admin scope is absent
     */
    static AuthorizationRequest activeAdmin(AccountIdentity identity) {
        boolean activeAdmin = identity != null
                && identity.role() == GlobalRole.ADMIN
                && identity.status() == AccountStatus.ACTIVE;
        return new AuthorizationRequest(
                activeAdmin ? Set.of(AuthorizationColumn.ADMIN) : Set.of(), null, null, null);
    }
}
