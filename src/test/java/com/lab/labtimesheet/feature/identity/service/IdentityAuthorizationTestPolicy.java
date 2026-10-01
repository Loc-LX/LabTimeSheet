package com.lab.labtimesheet.feature.identity.service;

import com.lab.labtimesheet.platform.authorization.AuthorizationCatalogue;
import com.lab.labtimesheet.platform.authorization.AuthorizationPolicy;

/** Loads the production permission catalogue for Identity tests. */
final class IdentityAuthorizationTestPolicy {

    private IdentityAuthorizationTestPolicy() {}

    /** @return a real authorization policy backed by the production catalogue */
    static AuthorizationPolicy create() {
        return new AuthorizationPolicy(AuthorizationCatalogue.loadDefault());
    }
}
