package com.lab.labtimesheet.feature.internship.service;

import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.identity.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.internship.model.dto.LockedAccountMutationEligibility;
import com.lab.labtimesheet.platform.authorization.AuthorizationColumn;
import com.lab.labtimesheet.platform.authorization.AuthorizationRequest;
import com.lab.labtimesheet.platform.model.GlobalRole;
import java.util.Set;

/** Builds Internship-owned policy facts from identity or transaction-locked account state. */
final class InternshipAuthorizationRequests {

    private InternshipAuthorizationRequests() {}

    /**
     * Resolves the Admin column only for a stored active Admin account.
     *
     * @param identity stored account identity, or {@code null} when the identifier is unknown
     * @return account-lifecycle request with an empty actor set when Admin scope is absent
     */
    static AuthorizationRequest activeAdmin(AccountIdentity identity) {
        return activeAdmin(identity == null ? null : identity.role(), identity == null ? null : identity.status());
    }

    /**
     * Resolves the Admin column from locked identity facts retained through lifecycle mutation.
     *
     * @param identity transaction-locked actor snapshot
     * @return account-lifecycle request with an empty actor set when Admin scope is absent
     */
    static AuthorizationRequest activeAdmin(LockedAccountMutationEligibility identity) {
        return activeAdmin(identity.role(), identity.accountStatus());
    }

    private static AuthorizationRequest activeAdmin(GlobalRole role, AccountStatus status) {
        boolean activeAdmin = role == GlobalRole.ADMIN && status == AccountStatus.ACTIVE;
        return new AuthorizationRequest(
                activeAdmin ? Set.of(AuthorizationColumn.ADMIN) : Set.of(), null, null, null);
    }
}
