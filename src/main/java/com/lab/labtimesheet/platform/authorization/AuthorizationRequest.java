package com.lab.labtimesheet.platform.authorization;

import java.util.Set;

/** Immutable actor scope and record-state facts supplied to the shared authorization policy. */
public record AuthorizationRequest(
        Set<AuthorizationColumn> actorColumns,
        String scopeState,
        String recordState,
        String targetState) {

    /** Copies the resolved scope so callers cannot change a decision after construction. */
    public AuthorizationRequest {
        actorColumns = Set.copyOf(actorColumns);
    }
}
