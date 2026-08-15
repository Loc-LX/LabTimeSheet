package com.lab.labtimesheet.feature.project.exception;

import com.lab.labtimesheet.feature.project.controller.ProjectController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.ModelAndView;

/**
 * Maps uncaught Project authorization and lifecycle failures to the shared, non-disclosing
 * server-rendered error contract.
 *
 * <p>The Reporting/UI feature supplies {@code error/generic}. Its stable model contains
 * {@code errorStatus}, {@code errorTitle}, and {@code errorMessage}; none is populated from the
 * exception message.
 */
@ControllerAdvice(assignableTypes = ProjectController.class)
public class ProjectControllerAdvice {

    /** Creates the stateless Project exception-to-view adapter. */
    public ProjectControllerAdvice() {
    }

    /**
     * Hides whether a requested Project or nested resource exists.
     *
     * @return the shared generic error view with HTTP 404 and safe copy
     */
    @ExceptionHandler(ProjectAccessDeniedException.class)
    public ModelAndView accessDenied() {
        return genericError(
                HttpStatus.NOT_FOUND,
                "Project unavailable",
                "The requested Project could not be found or is not available to you.");
    }

    /**
     * Reports an uncaught stale or invalid Project request without exposing aggregate details.
     * Known form validation failures are handled by the controller before reaching this fallback.
     *
     * @return the shared generic error view with HTTP 409 and safe copy
     */
    @ExceptionHandler(ProjectRuleViolationException.class)
    public ModelAndView conflict() {
        return genericError(
                HttpStatus.CONFLICT,
                "Project request could not be completed",
                "Review the Project and try again.");
    }

    private static ModelAndView genericError(HttpStatus status, String title, String message) {
        var error = new ModelAndView("error/generic");
        error.setStatus(status);
        error.addObject("errorStatus", status.value());
        error.addObject("errorTitle", title);
        error.addObject("errorMessage", message);
        return error;
    }
}
