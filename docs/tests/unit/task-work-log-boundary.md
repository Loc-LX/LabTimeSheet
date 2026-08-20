# Test Evidence: Task work log membership and date boundaries

- **Test type:** Unit
- **Requirement IDs:** `TSK-013`, `TSK-014`, `TSK-016`, `TSK-017`
- **Scenario IDs:** `I2-TSK-01`, `AC-TSK-007`, `AC-TSK-009`
- **Test class/method:** `com.lab.labtimesheet.feature.task.service.TaskWorkLogBoundaryTest`
- **Implementation commit:** `pending`

## Protected behavior

Only the Task's current assignee can create a work log for the ACTIVE Project. The work date must be in the past or today, within the Project dates, and on or after the actor's membership join date. Minutes must be between 1 and 1440 and the note, when present, must be non-blank. The log author may correct minutes and note after a Task reassignment while still a member; any other member is rejected for that log. A global day off never blocks work logging and logging never creates attendance.

## Test method

Six focused Mockito tests drive `TaskService.logWork` and `TaskService.correctWorkLog` through mocked Project and Calendar boundaries. `currentAssigneeLogsWorkAndOnlyCurrentAssigneeMayLog` proves the actor-membership check gates the write; `workDateRejectedOutsideProjectDatesBeforeMembershipOrInFuture` derives today from the injected clock and proves past-today/interval/date-window rejection without writing; `rejectsInvalidMinutesAndBlankNoteWithoutWriting` proves validation precedes persistence; `globalDayOffDoesNotBlockWorkLogsAndCreatesNoAttendance` proves the calendar service is never invoked; `authorCorrectsOwnLogAfterTaskReassignment` proves correction permission follows the author's membership, not current assignee; `nonAuthorCannotCorrectAnotherMembersLog` proves another active member is rejected. A reassigned task with the author as an active member is the narrowest production-shaped scope for `AC-TSK-009`.

## Hand-derived expected result

A log is written only when the actor's current active membership equals the Task assignee, the Project is ACTIVE, and the work date is not after today (server clock), within `[project.startDate, project.endDate]`, and not before the membership join date. Minutes 1..1440; blank note becomes null. Correction keeps the immutable work date and updates only minutes/note when the actor still holds an active membership whose id matches the log's author membership; otherwise `TaskNotFoundException`.

## RED

**Command**

```text
.\mvnw.cmd "-Dtest=TaskWorkLogBoundaryTest" test
```

**Observed result**

```text
[ERROR] cannot find symbol
  symbol:   class TaskWorkLog
[ERROR] cannot find symbol
  symbol:   class LogWorkCommand
[ERROR] cannot find symbol
  symbol:   class TaskWorkLogRepository
[ERROR] cannot find symbol
  symbol:   method logWork
[INFO] BUILD FAILURE
```

The RED was the missing work-log behavior; no fixture or environment failure.

## GREEN

**Command**

```text
.\mvnw.cmd "-Dtest=TaskWorkLogBoundaryTest" test
```

**Observed result**

```text
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
.\mvnw.cmd "-Dtest=Task*,TaskCreationIntegrationTest,TaskWorkLogIntegrationTest,TaskWorkLogDailyTotalConcurrencyTest" test

[INFO] Tests run: 81, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## External-test boundaries

These unit tests establish authorization order, date/interval validation, and no-calendar/no-attendance guarantees with mocks. They do not simulate two concurrent transactions; PostgreSQL execution of the locked work-log lookup and correction path is covered by `TaskWorkLogIntegrationTest`. The 1440-minute daily total across Projects is the separate Iteration 2 scope `I2-TSK-02` (`TSK-015`).
