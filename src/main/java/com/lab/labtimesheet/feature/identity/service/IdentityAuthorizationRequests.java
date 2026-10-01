package com.lab.labtimesheet.feature.identity.service;

import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.identity.model.dto.AccountIdentity;
import com.lab.labtimesheet.platform.authorization.AuthorizationColumn;
import com.lab.labtimesheet.platform.authorization.AuthorizationRequest;
import com.lab.labtimesheet.platform.model.GlobalRole;
import java.util.Set;

/** Builds identity-owned policy facts from persisted account identity. */
public final class IdentityAuthorizationRequests {

    private IdentityAuthorizationRequests() {}

    /**
     * Resolves the Admin actor column only for a stored active Admin identity.
     *
     * @param identity persisted identity, or {@code null} when no account exists
     * @return policy request with null scope and state facts
     */
    public static AuthorizationRequest activeAdmin(AccountIdentity identity) {
        boolean activeAdmin = identity != null
                && identity.role() == GlobalRole.ADMIN
                && identity.status() == AccountStatus.ACTIVE;
        return new AuthorizationRequest(
                activeAdmin ? Set.of(AuthorizationColumn.ADMIN) : Set.of(), null, null, null);
    }
}
