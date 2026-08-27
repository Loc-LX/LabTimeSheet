package com.lab.labtimesheet.feature.reporting.exception;

import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.reporting.controller.DailyProjectWorkReportController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

/**
 * Maps Daily report authorization and date-validation failures to a non-disclosing HTML error.
 *
 * <p>The dataset service remains responsible for authorization; this adapter prevents an
 * unauthorized Project identifier or unsupported role from becoming a server error or an empty
 * report that could be mistaken for a valid scope.</p>
 */
@ControllerAdvice(assignableTypes = DailyProjectWorkReportController.class)
public class DailyProjectWorkReportControllerAdvice {

    /**
     * Hides whether a guessed Project exists outside the actor's authorized scope.
     *
     * @return shared generic 404 response
     */
    @ExceptionHandler(ProjectAccessDeniedException.class)
    public ModelAndView accessDenied() {
        return genericError(
                HttpStatus.NOT_FOUND,
                "Project unavailable",
                "The requested Project could not be found or is not available to you.");
    }

    /**
     * Reports future or conflicting date parameters without exposing persistence details.
     *
     * @return shared generic 400 response
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ModelAndView invalidRequest() {
        return genericError(
                HttpStatus.BAD_REQUEST,
                "Report request is invalid",
                "Choose one past or current Report date and try again.");
    }

    private static ModelAndView genericError(HttpStatus status, String title, String message) {
        ModelAndView error = new ModelAndView("error/generic");
        error.setStatus(status);
        error.addObject("errorStatus", status.value());
        error.addObject("errorTitle", title);
        error.addObject("errorMessage", message);
        return error;
    }
}
