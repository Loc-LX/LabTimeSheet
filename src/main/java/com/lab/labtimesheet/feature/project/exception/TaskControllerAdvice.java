package com.lab.labtimesheet.feature.project.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

/**
 * Maps inaccessible Task/Project reads and stale Task mutations from server-rendered consumers to
 * safe generic responses.
 *
 * <p>The response deliberately avoids persistence details and gives the browser a clear reload
 * instruction. The same exception remains available to service callers for transaction rollback
 * and diagnostics.</p>
 */
@ControllerAdvice
public class TaskControllerAdvice {

    /**
     * Returns the shared non-disclosing 404 page for a missing or unauthorized Task/Project.
     *
     * @return generic error view with HTTP 404 and safe access copy
     */
    @ExceptionHandler(TaskNotFoundException.class)
    public ModelAndView notFound() {
        var error = new ModelAndView("error/generic");
        error.setStatus(HttpStatus.NOT_FOUND);
        error.addObject("errorStatus", HttpStatus.NOT_FOUND.value());
        error.addObject("errorTitle", "Task or Project unavailable");
        error.addObject("errorMessage", "The requested Task or Project could not be found or is not available to you.");
        return error;
    }

    /**
     * Returns the stable reload response for a stale Task or work-log submission.
     *
     * @return generic error view with HTTP 409 and safe reload copy
     */
    @ExceptionHandler(TaskConflictException.class)
    public ModelAndView conflict() {
        var error = new ModelAndView("error/generic");
        error.setStatus(HttpStatus.CONFLICT);
        error.addObject("errorStatus", HttpStatus.CONFLICT.value());
        error.addObject("errorTitle", "Task changed");
        error.addObject("errorMessage", "The Task changed while you were working. Reload the page and try again.");
        return error;
    }
}
