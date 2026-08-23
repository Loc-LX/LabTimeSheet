package com.lab.labtimesheet.feature.task.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

/**
 * Maps stale Task mutations from server-rendered consumers to a safe conflict response.
 *
 * <p>The response deliberately avoids persistence details and gives the browser a clear reload
 * instruction. The same exception remains available to service callers for transaction rollback
 * and diagnostics.</p>
 */
@ControllerAdvice
public class TaskControllerAdvice {

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
