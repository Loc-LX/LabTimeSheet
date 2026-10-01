package com.lab.labtimesheet.feature.reporting.exception;

import com.lab.labtimesheet.feature.reporting.controller.ProjectTaskReportController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

/** Maps selected Projects outside report scope to the stable non-disclosing HTML 404 page. */
@ControllerAdvice(assignableTypes = ProjectTaskReportController.class)
public class ProjectTaskReportControllerAdvice {

    /** Returns one generic not-found response for absent and inaccessible selected Projects. */
    @ExceptionHandler(ProjectTaskReportRecordNotFoundException.class)
    public ModelAndView notFound() {
        ModelAndView error = new ModelAndView("error/generic");
        error.setStatus(HttpStatus.NOT_FOUND);
        error.addObject("errorStatus", HttpStatus.NOT_FOUND.value());
        error.addObject("errorTitle", "Project unavailable");
        error.addObject("errorMessage", "The requested Project could not be found or is not available to you.");
        return error;
    }
}
