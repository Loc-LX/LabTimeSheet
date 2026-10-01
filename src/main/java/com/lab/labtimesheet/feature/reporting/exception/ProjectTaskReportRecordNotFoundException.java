package com.lab.labtimesheet.feature.reporting.exception;

/** Indicates that the selected Project is absent or outside the report reader's scope. */
public final class ProjectTaskReportRecordNotFoundException extends RuntimeException {

    /** Creates a non-disclosing selected-Project failure. */
    public ProjectTaskReportRecordNotFoundException() {
        super("Project unavailable");
    }
}
