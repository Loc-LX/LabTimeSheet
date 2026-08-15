package com.lab.labtimesheet.feature.task.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Signals a non-disclosing Task or Project lookup/authorization failure.
 *
 * <p>MVC maps this exception to HTTP 404 so guessed identifiers do not reveal whether the record
 * exists or merely falls outside the authenticated actor's current or historical scope.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public final class TaskNotFoundException extends RuntimeException {

    /** Creates the fixed, non-identifying HTTP 404 failure. */
    public TaskNotFoundException() {
        super("Task or Project was not found");
    }
}
