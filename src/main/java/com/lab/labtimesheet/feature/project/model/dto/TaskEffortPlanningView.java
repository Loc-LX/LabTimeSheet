package com.lab.labtimesheet.feature.project.model.dto;

import com.lab.labtimesheet.feature.project.model.TaskVarianceState;

/** Authorized whole-Task planning facts; all durations are minutes independent of attendance time.
 * @param estimatedMinutes optional original whole-Task estimate
 * @param actualMinutes lifetime sum of retained work logs
 * @param varianceState whether variance has a value, is pending, or is unavailable
 * @param varianceMinutes signed DONE variance, when {@code VALUE}; otherwise null
 * @param canEditEstimate whether the viewer may mutate the estimate
 */
public record TaskEffortPlanningView(Integer estimatedMinutes, long actualMinutes,
        TaskVarianceState varianceState, Long varianceMinutes, boolean canEditEstimate) {}
