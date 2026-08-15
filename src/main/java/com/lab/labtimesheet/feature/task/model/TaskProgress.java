package com.lab.labtimesheet.feature.task.model;

import java.util.Collection;
import java.util.OptionalDouble;

/**
 * Counts current non-deleted Tasks by fixed status for a single Project.
 *
 * @param todo Tasks not yet started
 * @param inProgress Tasks actively in progress
 * @param blocked Tasks currently blocked
 * @param done completed Tasks
 */
public record TaskProgress(int todo, int inProgress, int blocked, int done) {

    /**
     * Counts the supplied current Task statuses.
     *
     * @param statuses statuses already filtered to the caller's current Task scope
     * @return immutable counts for all four statuses
     */
    public static TaskProgress from(Collection<TaskStatus> statuses) {
        int todo = 0;
        int inProgress = 0;
        int blocked = 0;
        int done = 0;
        for (TaskStatus status : statuses) {
            switch (status) {
                case TODO -> todo++;
                case IN_PROGRESS -> inProgress++;
                case BLOCKED -> blocked++;
                case DONE -> done++;
            }
        }
        return new TaskProgress(todo, inProgress, blocked, done);
    }

    /**
     * Returns the denominator used for Project completion progress.
     *
     * @return total number of counted Tasks
     */
    public int total() {
        return todo + inProgress + blocked + done;
    }

    /**
     * Returns the count for one fixed status.
     *
     * @param status status to inspect
     * @return number of Tasks in that status
     */
    public int count(TaskStatus status) {
        return switch (status) {
            case TODO -> todo;
            case IN_PROGRESS -> inProgress;
            case BLOCKED -> blocked;
            case DONE -> done;
        };
    }

    /**
     * Computes DONE Tasks as a percentage of all counted Tasks.
     *
     * @return an empty value when the Project has no current Tasks, otherwise a value from 0 to 100
     */
    public OptionalDouble completionPercentage() {
        return total() == 0 ? OptionalDouble.empty() : OptionalDouble.of(done * 100.0 / total());
    }
}
