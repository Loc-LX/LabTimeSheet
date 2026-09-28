package com.lab.labtimesheet.feature.internship.service;

import com.lab.labtimesheet.platform.authorization.AuthorizationCatalogue;
import com.lab.labtimesheet.platform.authorization.AuthorizationPolicy;

/** Loads the production permission catalogue for Internship tests. */
final class InternWorkAuthorizationTestPolicy {

    private InternWorkAuthorizationTestPolicy() {}

    /** @return a real authorization policy backed by the production catalogue */
    static AuthorizationPolicy create() {
        return new AuthorizationPolicy(AuthorizationCatalogue.loadDefault());
    }
}
