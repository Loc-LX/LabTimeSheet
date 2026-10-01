package com.lab.labtimesheet.platform.authorization;

import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Applies generic catalogue predicates to facts resolved by the record-owning module.
 *
 * <p>Terms such as Own, Assigned, Membership scope, and Own history only are resolved by that
 * module; it supplies an actor column only after confirming the requested scope. This policy
 * evaluates only the structured scope, record-state, and transition values and performs no lookup.
 */
@Component
public final class AuthorizationPolicy {

    private final AuthorizationCatalogue catalogue;

    /** @param catalogue validated immutable §5.2 catalogue */
    public AuthorizationPolicy(AuthorizationCatalogue catalogue) {
        this.catalogue = Objects.requireNonNull(catalogue, "catalogue");
    }

    /**
     * Returns the union of granted cells for all scopes the record-owning module resolved.
     *
     * @param capability exact matrix capability
     * @param request actor columns and generic state facts resolved by the owning module
     * @return whether at least one supplied actor column grants this operation in the supplied state
     */
    public boolean allows(AuthorizationCapability capability, AuthorizationRequest request) {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(request, "request");
        for (AuthorizationColumn column : request.actorColumns()) {
            if (catalogue.cell(capability, column).allows(request)) {
                return true;
            }
        }
        return false;
    }
}
