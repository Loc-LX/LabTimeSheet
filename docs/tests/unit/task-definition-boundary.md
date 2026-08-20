# Test Evidence: Task definition edit, soft-delete, and historical inspection boundaries

- **Test type:** Unit
- **Requirement IDs:** TSK-010, TSK-019
- **Scenario IDs:** AC-TSK-005, AC-TSK-011
- **Test class/method:**
  - `com.lab.labtimesheet.feature.task.service.TaskMutationBoundaryTest.leaderEditsUnfinishedTaskAndSelfCreatorEditsOwnWhileAssigned`
  - `com.lab.labtimesheet.feature.task.service.TaskMutationBoundaryTest.nonCreatorOrReassignedAwayMemberCannotEditOrDelete`
  - `com.lab.labtimesheet.feature.task.service.TaskMutationBoundaryTest.doneTaskCannotBeEditedOrSoftDeleted`
  - `com.lab.labtimesheet.feature.task.service.TaskMutationBoundaryTest.leaderSoftDeletesAndEntityRecordsActorAndNow`
  - `com.lab.labtimesheet.feature.task.service.TaskMutationBoundaryTest.historicalDetailsRequireLeaderOrSelfCreatorIncludingDeleted`
  - `com.lab.labtimesheet.feature.task.service.TaskMutationBoundaryTest.historicalDetailsRejectUnrelatedMember`
- **Implementation commit:** pending

## Protected behavior

The current Leader may edit or soft-delete any unfinished Task; a non-Leader creator may edit or
soft-delete only while creator and current assignee remain the same active membership and the Task is
unfinished. A `DONE` Task must first be reopened. Soft-deleted Tasks are excluded from progress and
normal lists but remain queryable historically to the Leader and the self-Task creator through
`historicalDetails`, which exposes every mutation capability as false.

## Test method

Mock `TaskService` dependencies. For edit, verify the Project context is checked before the locked
Task row and that `Task.edit(normalizedTitle, description, dueDate, NOW)` receives normalized values.
A member who is neither Leader nor self-creator (different current Leader, creator not the actor)
throws `TaskNotFoundException` without touching the entity, as does a DONE Task. For soft-delete,
verify `Task.softDelete(actorMembership, NOW)`. Historical inspection loads the row through the
inclusive repository query and returns a `deleted=true` read-only projection for the Leader or
self-creator, while an unrelated member receives a non-disclosing 404.

## Hand-derived expected result

- Leader or self-creator edit: `Task.edit` called with normalized values; view reflects them.
- Non-Leader/non-creator or DONE: `TaskNotFoundException`, no entity mutation.
- Soft-delete: `Task.softDelete(70L, NOW)`.
- Historical: Leader/self-creator see `deleted=true` with all capabilities false; unrelated member 404.

## RED

**Command**

```text
.\mvnw.cmd "-Dtest=TaskMutationBoundaryTest" test
```

**Observed result**

```text
[ERROR] Tests run: 12, Failures: 0, Errors: 6, Skipped: 0 <<< FAILURE!
[ERROR] leaderEditsUnfinishedTask... TaskNotFound Task or Project was not found
[ERROR] leaderSoftDeletes... TaskNotFound Task or Project was not found
[ERROR] doneTaskCannotBeEditedOrSoftDeleted ... UnnecessaryStubbingException
[ERROR] historicalDetailsRejectUnrelatedMember ... UnnecessaryStubbingException
[ERROR] nonCreatorOrReassignedAwayMemberCannotEditOrDelete ... UnnecessaryStubbingException
[ERROR] historicalDetailsRequireLeaderOrSelfCreatorIncludingDeleted ... UnnecessaryStubbingException
```

The `TaskNotFound` errors showed `view()` could not resolve the assignee name because the members
query was not stubbed; the stub findings were fixture noise from strict Mockito. Tightening the mocks
to only the getters the service actually reads left only the intended missing-behavior failures.

## GREEN

**Command**

```text
.\mvnw.cmd "-Dtest=TaskMutationBoundaryTest" test
```

**Observed result**

```text
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
.\mvnw.cmd "-Dtest=Task*" test
[INFO] Tests run: 105, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## External-test boundaries

Mocks cannot prove that soft-deleted rows disappear from normal lists/progress while remaining
historically queryable against real PostgreSQL, nor that history (comments/work logs) survives
deletion. Those boundaries are covered by `TaskDefinitionIntegrationTest` on PostgreSQL 18.4
Testcontainers. Browser form/redirect rendering is covered by `TaskControllerTest`.
