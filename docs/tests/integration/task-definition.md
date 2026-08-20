# Test Evidence: Task definition edit, soft-delete, and historical inspection on PostgreSQL

- **Test type:** Integration
- **Requirement IDs:** TSK-010, TSK-019
- **Scenario IDs:** AC-TSK-005, AC-TSK-011
- **Test class/method:**
  - `com.lab.labtimesheet.feature.task.service.TaskDefinitionIntegrationTest`
    - `leaderEditsUnfinishedTaskAndSelfCreatorEditsOwnWhileAssigned`
    - `leaderEditsTaskCreatedForAnotherMember`
    - `selfCreatorLosesDefinitionControlAfterReassignmentAway`
    - `doneTaskRejectsEditAndDeleteUntilReopened`
    - `leaderSoftDeleteLeavesNormalProgressButRemainsHistoricallyQueryable`
    - `selfCreatorMayInspectOwnSoftDeletedTaskHistorically`
    - `editRejectsDueDateOutsideProjectOrOnGlobalDayOff`
- **Implementation commit:** pending

## Protected behavior

End-to-end on PostgreSQL 18.4: a current Leader edits/soft-deletes any unfinished Task and may edit a
Task created for another member; a self-creator edits only their own unfinished Task while still
assigned. Reassigning away removes the original creator's definition control. A `DONE` Task rejects
both edit and delete until reopened. Soft deletion removes the row from `list` (progress/normal lists)
yet `historicalDetails` still returns the full Task with comments and work logs to the Leader and
self-creator, with all mutation capabilities false; an unrelated member gets a non-disclosing 404.
Editing validates due date against Project dates and global day-off calendar.

## Test method

Testcontainers `postgres:18.4`, `@SpringBootTest` + `@Transactional`. Seeds a mentor, Leader, member,
and second member via `JdbcClient`, with an active leadership term. Exercises `TaskService` directly
with real transactions, then asserts on the returned `TaskView`/`TaskDetails` and re-queries rows for
history retention. Comments and work logs are inserted through their owning tables to confirm they
survive soft deletion.

## Hand-derived expected result

- Self-creator edit trims title/description, keeps creator/assigner/assignee, TODO status.
- Leader may edit a Task assigned to another member.
- After reassign away, original creator's `edit`/`softDelete` throw `TaskNotFoundException` and the
  title is unchanged.
- `DONE` Task: `edit`/`softDelete` both throw `TaskNotFoundException`.
- After Leader soft-delete: `list` excludes the row; `historicalDetails` returns `deleted=true`, the
  title, 1 comment, 1 work log, and all capabilities false; `historicalDetails` for an unrelated
  member throws `TaskNotFoundException`.
- Self-creator can read their own soft-deleted Task historically.
- Due date outside Project dates or on a global day-off throws `TaskValidationException` with the
  configured message.

## RED

**Command**

```text
.\mvnw.cmd "-Dtest=TaskDefinitionIntegrationTest" test
```

**Observed result**

```text
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

No RED recorded: the tests were written against the already-implemented production methods and
confirmed the boundaries directly. The RED for the underlying behavior was captured in the unit
boundary evidence while the methods were still missing.

## GREEN

**Command**

```text
.\mvnw.cmd "-Dtest=TaskDefinitionIntegrationTest" test
```

**Observed result**

```text
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
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

Uses real PostgreSQL 18.4 via Testcontainers; does not depend on the developer database or a real
SMTP server. Concurrent deletion/reassignment races are out of scope for this item and are guarded by
the Project-then-Task lock order verified by the existing concurrency tests. Browser rendering of the
edit form, deleted banner, and POST redirects is covered by `TaskControllerTest`.
