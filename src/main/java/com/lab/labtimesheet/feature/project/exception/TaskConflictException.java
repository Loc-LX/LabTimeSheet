package com.lab.labtimesheet.feature.project.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Signals that a Task or work-log mutation lost an optimistic-concurrency race.
 *
 * <p>The stable conflict response tells the actor to reload without exposing persistence details
 * or allowing a stale mutation to publish notifications.</p>
 */
@ResponseStatus(HttpStatus.CONFLICT)
public final class TaskConflictException extends RuntimeException {

    /**
     * Creates an operator-safe Task conflict with its persistence cause retained for diagnostics.
     *
     * @param message stable user-facing reload instruction
     * @param cause underlying optimistic-lock failure
     */
    public TaskConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
