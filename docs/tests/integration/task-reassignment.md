# Test Evidence: Leader reassignment preserves history and gates DONE on reopen

- **Test type:** Integration
- **Requirement IDs:** TSK-009, TSK-019
- **Scenario IDs:** AC-TSK-004, AC-TSK-011
- **Test class/method:**
  - `com.lab.labtimesheet.feature.task.service.TaskReassignmentIntegrationTest.doneTaskRejectsReassignmentUntilAssigneeReopensThenPreservesStateAndLogs`
  - `com.lab.labtimesheet.feature.task.service.TaskReassignmentIntegrationTest.nonLeaderCannotReassignAndReassignmentAwayRevokesCreatorControlUntilReturn`
- **Implementation commit:** pending

## Protected behavior

A `DONE` Task rejects Leader reassignment until its current assignee reopens it to `IN_PROGRESS`;
after reopening, reassignment preserves the `IN_PROGRESS` state, creator attribution, the comment, and
the work log including the log's original author. Reassigning a member-created unfinished Task away
then back never changes creator attribution or prior history; while assigned away the creator loses
definition control (status change) and regains it only when creator and current assignee match again
while the Task is unfinished. A non-Leader cannot reassign.

## Test method

Seed a PLANNED Project with Mentor, current Leader, creator member, and second member, activate it,
create a member Task, and drive real JPA/PostgreSQL transitions. The first test marks the Task DONE,
asserts `details(...).canReassign()` is false and the reassign call fails with the reopen message, then
reopens via `changeStatus`, reassigns to the second member, and checks status, assignee, assigner,
creator, comment count, work-log count, and the log's original author membership via JDBC. The second
test reassigns away, asserts the creator cannot change status while away and no history changed, then
reassigns back and completes the Task to prove control returned. No browser is involved.

## Hand-derived expected result

- DONE gate: reassign rejected; after reopen the Task stays `IN_PROGRESS` with one comment, one work
  log, and the log still attributed to the creator membership.
- Away round trip: assignee toggles 70L -> 71L -> 70L; creator stays 70L; creator status change fails
  while away and succeeds after return; final status `DONE`.

## RED

**Command**

```text
.\mvnw.cmd "-Dtest=TaskReassignmentIntegrationTest" test
```

**Observed result**

```text
[ERROR] Tests run: 2, Failures: 1, Errors: 0, Skipped: 0 <<< FAILURE!
[ERROR] nonLeaderCannotReassign... FAILURE!
  expected: IN_PROGRESS
   but was: "IN_PROGRESS"
```

The only failure was a strict type comparison (`TaskStatus` vs `String`) in the test itself after the
production behavior already worked; fixing the assertion type made both scenarios green.

## GREEN

**Command**

```text
.\mvnw.cmd "-Dtest=TaskReassignmentIntegrationTest" test
```

**Observed result**

```text
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
.\mvnw.cmd "-Dtest=Task*" test
[INFO] Tests run: 88, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## External-test boundaries

The tests use real PostgreSQL 18.4 via Testcontainers and JPA, so lock ordering, FK constraints, and
history persistence are proven end to end. They do not cover the SMTP/in-app notification request that
NOT-002 defines for reassignment; that delivery depends on the platform notification feature
(I2-PLAT-06) and is deferred, not stubbed. Browser navigation and CSRF-protected form posts are covered
by `TaskControllerTest`.
