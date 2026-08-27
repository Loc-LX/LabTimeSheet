package com.lab.labtimesheet.feature.task.model;

/** Planning variance state derived from estimate, retained effort, and current Task status. */
public enum TaskVarianceState {
    /** DONE Task with an estimate and signed actual-minus-estimate value. */
    VALUE,
    /** Estimated Task that is unfinished or reopened; variance is pending. */
    PENDING,
    /** No estimate exists; presentation is N/A. */
    NOT_ESTIMATED
}
