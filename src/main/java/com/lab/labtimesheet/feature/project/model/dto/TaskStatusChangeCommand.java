package com.lab.labtimesheet.feature.project.model.dto;

import com.lab.labtimesheet.feature.project.model.TaskStatus;

/** Requested Task status edge and its optional reopen explanation. */
public record TaskStatusChangeCommand(
        /** Requested next state in the fixed Task workflow. */
        TaskStatus target,
        /** Explanation retained with a DONE-to-IN_PROGRESS reopen, when supplied. */
        String reason) {

    /** Creates an immutable status request for TaskService validation. */
    public TaskStatusChangeCommand {
    }
}
