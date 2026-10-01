package com.lab.labtimesheet.feature.attendance.exception;

import com.lab.labtimesheet.feature.attendance.controller.AttendanceController;
import com.lab.labtimesheet.feature.attendance.controller.AttendanceRequestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

/** Maps record-level Attendance denials to a stable response that does not disclose existence. */
@ControllerAdvice(assignableTypes = {AttendanceController.class, AttendanceRequestController.class})
public class AttendanceRecordControllerAdvice {

    /** Returns the shared generic not-found page for absent or inaccessible Attendance records. */
    @ExceptionHandler(AttendanceRecordNotFoundException.class)
    public ModelAndView notFound() {
        ModelAndView error = new ModelAndView("error/generic");
        error.setStatus(HttpStatus.NOT_FOUND);
        error.addObject("errorStatus", HttpStatus.NOT_FOUND.value());
        error.addObject("errorTitle", "Record unavailable");
        error.addObject("errorMessage", "The requested record could not be found or is not available to you.");
        return error;
    }
}
