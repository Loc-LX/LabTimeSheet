package com.lab.labtimesheet.feature.task.model;

/**
 * Fixed v1 Task workflow states.
 *
 * <p>The graph is intentionally not configurable: TODO can move to IN_PROGRESS or BLOCKED;
 * IN_PROGRESS can move to DONE or BLOCKED; BLOCKED can move to TODO or IN_PROGRESS; and DONE can
 * only reopen to IN_PROGRESS.
 */
public enum TaskStatus {
    /** Work has not started. */
    TODO,
    /** Work is actively progressing. */
    IN_PROGRESS,
    /** Work cannot currently proceed. */
    BLOCKED,
    /** Work is complete and may only be reopened to IN_PROGRESS. */
    DONE;

    /**
     * Tests whether the fixed workflow permits a direct edge to {@code target}.
     *
     * @param target requested next state
     * @return {@code true} only for one of the specified v1 edges
     */
    public boolean canTransitionTo(TaskStatus target) {
        return switch (this) {
            case TODO -> target == IN_PROGRESS || target == BLOCKED;
            case IN_PROGRESS -> target == DONE || target == BLOCKED;
            case BLOCKED -> target == TODO || target == IN_PROGRESS;
            case DONE -> target == IN_PROGRESS;
        };
    }
}
