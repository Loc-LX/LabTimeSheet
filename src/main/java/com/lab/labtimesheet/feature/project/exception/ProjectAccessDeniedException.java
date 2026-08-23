package com.lab.labtimesheet.feature.project.exception;

/**
 * Signals a Project lookup or operation that must fail without revealing resource existence.
 */
public final class ProjectAccessDeniedException extends RuntimeException {

    /** Creates the internal denial signal; controllers replace its message with generic copy. */
    public ProjectAccessDeniedException() {
        super("Project access denied");
    }
}
