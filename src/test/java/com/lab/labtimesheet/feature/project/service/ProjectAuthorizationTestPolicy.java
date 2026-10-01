package com.lab.labtimesheet.feature.project.service;

import com.lab.labtimesheet.platform.authorization.AuthorizationCatalogue;
import com.lab.labtimesheet.platform.authorization.AuthorizationPolicy;

/** Loads the production permission catalogue for Project tests. */
final class ProjectAuthorizationTestPolicy {

    private ProjectAuthorizationTestPolicy() {}

    /** @return a real authorization policy backed by the production catalogue */
    static AuthorizationPolicy create() {
        return new AuthorizationPolicy(AuthorizationCatalogue.loadDefault());
    }
}
