# Lab Timesheet

Lab Timesheet coordinates internship attendance and Project delivery while keeping attendance evidence separate from Task effort.

## Language

**Task estimate**:
The planned effort, expressed in minutes, for completing one Task. It belongs to the Task rather than an assignee and is independent of attendance, calendar duration, and elapsed time.
_Avoid_: Estimated hours, assignee estimate, time budget

**Actual Task effort**:
The lifetime sum of retained work-log minutes for one Task across all authors and assignments. It is independent of attendance and does not reset when the Task is reassigned.
_Avoid_: Current-assignee effort, attendance duration, selected-period effort

**Task effort variance**:
For a `DONE` Task with a Task estimate, Actual Task effort minus Task estimate. It is a signed planning difference, not a measure of quality or productivity; it is undefined for an unfinished or unestimated Task.
_Avoid_: Efficiency score, productivity score, remaining effort

**Remaining effort forecast**:
A Project Leader's dated prediction of the additional effort needed to finish an unfinished Task from a reassignment or replanning point. It does not replace the Task estimate and may be lower, equal to, or higher than the unused portion of that estimate.
_Avoid_: Replacement estimate, assignee estimate, revised Task estimate

**Report date**:
A local business date used to select dated Task work logs for reporting. Attendance-policy and calendar context may describe the date but never removes otherwise valid Task work from the report.
_Avoid_: Task workday, reporting workday, attendance date

**Daily Project Work Report**:
An authorized view of retained Task work logs for one Report date, grouped by Project and work-log author. It may show attendance context and each Task's current status, but it does not assert attendance or that a Task was completed on that date.
_Avoid_: Daily completion report, daily attendance report, daily productivity report
