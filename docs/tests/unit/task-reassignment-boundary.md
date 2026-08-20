# Test Evidence: Task reassignment authorization and preservation boundaries

- **Test type:** Unit
- **Requirement IDs:** TSK-009, TSK-019
- **Scenario IDs:** AC-TSK-004, AC-TSK-011
- **Test class/method:**
  - `com.lab.labtimesheet.feature.task.service.TaskMutationBoundaryTest.reassignmentRequiresCurrentLeaderInActiveProject`
  - `com.lab.labtimesheet.feature.task.service.TaskMutationBoundaryTest.nonLeaderAndNonActiveProjectCannotReassign`
  - `com.lab.labtimesheet.feature.task.service.TaskMutationBoundaryTest.doneTaskMustBeReopenedBeforeReassignment`
- **Implementation commit:** pending

## Protected behavior

Only the current Leader of an ACTIVE Project may reassign an unfinished Task. A `DONE` Task must first
be reopened to `IN_PROGRESS` by its current assignee. Reassignment updates `assignee_membership_id`,
`assigned_by_membership_id`, and `assigned_at` while preserving creator attribution and never touching
status, comments, or work logs. Non-Leaders and non-ACTIVE Projects are denied with the non-disclosing
`TaskNotFoundException`.

## Test method

Mock `TaskService` dependencies with the mutable Project task context and a `Task` mock. Invoke
`reassign` and verify the Project is locked before the Task row, that `Task.reassign(assignee, actor,
now)` receives the Leader membership and the fixed server clock instant, and that the returned view
carries the new assignee. A non-Leader actor and a COMPLETED Project each throw `TaskNotFoundException`
before the entity mutation. A `DONE` Task routes the entity's rejection into `TaskValidationException`
with the exact reopen message and records one entity call. These are the narrowest production-shaped
boundaries: they prove authorization, lock ordering, and the DONE gate without a browser.

## Hand-derived expected result

- ACTIVE Project, actor == current Leader: `Task.reassign(70L, 70L, NOW)` called once; view assignee 70L.
- Non-Leader actor or non-ACTIVE Project: `TaskNotFoundException`, no entity call.
- DONE Task under a Leader: `TaskValidationException("A DONE Task must be reopened before reassignment")`.

## RED

**Command**

```text
.\mvnw.cmd "-Dtest=TaskMutationBoundaryTest" test
```

**Observed result**

```text
[ERROR] Tests run: 6, Failures: 1, Errors: 2, Skipped: 0 <<< FAILURE!
[ERROR] doneTaskMustBeReopenedBeforeReassignment ... java.lang.AssertionError
[ERROR] nonLeaderAndNonActiveProjectCannotReassign ... UnnecessaryStubbingException
[ERROR] reassignmentRequiresCurrentLeaderInActiveProject ... UnnecessaryStubbingException
```

The failing assertions and strict-stub findings appeared because `TaskService.reassign` did not yet
exist; fixing the stubs to match the intended service contract removed the stub noise and kept the
missing-behavior failures.

## GREEN

**Command**

```text
.\mvnw.cmd "-Dtest=TaskMutationBoundaryTest" test
```

**Observed result**

```text
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
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

Mocks cannot prove the PostgreSQL foreign-key constraint that the new assignee must be an active
same-Project membership, nor that persisted history rows survive a reassignment round trip. Those
boundaries are covered by `TaskReassignmentIntegrationTest` on PostgreSQL 18.4 Testcontainers. Browser
rendering of the reassign form is covered by `TaskControllerTest`.
