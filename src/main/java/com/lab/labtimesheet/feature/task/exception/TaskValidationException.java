package com.lab.labtimesheet.feature.task.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Signals that an authorized Task request violates a Task business rule.
 *
 * <p>Unadapted MVC uses map this exception to HTTP 400. The create-form controller handles this
 * exact type locally to associate due-date failures with the field while allowing access failures
 * to retain their separate HTTP 404 contract.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public final class TaskValidationException extends RuntimeException {

    /**
     * Creates a client-visible validation failure.
     *
     * @param message actionable rule violation without protected record details
     */
    public TaskValidationException(String message) {
        super(message);
    }
}
