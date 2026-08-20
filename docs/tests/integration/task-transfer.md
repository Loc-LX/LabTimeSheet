# Test Evidence: Atomic unfinished-Task transfer on PostgreSQL

- **Test type:** Integration
- **Requirement IDs:** PRJ-009, PRJ-010, PRJ-011
- **Scenario IDs:** PRJ-010.1, PRJ-010.2, PRJ-011.1
- **Test class/method:** `com.lab.labtimesheet.feature.task.service.TaskTransferIntegrationTest` (all three methods)
- **Implementation commit:** pending

## Protected behavior

`TaskTransferService.transferUnfinishedTasks` reassigns every non-deleted unfinished Task of a departing membership to the receiving Leader membership and returns the transferred count, running inside the caller's Project transaction. DONE Tasks stay with the departing member; creator attribution, comments, and work logs survive reassignment; the receiving Leader is recorded as the new assigner. Completion-gate counts exclude soft-deleted Tasks, and a transfer with nothing eligible returns 0.

## Test method

Real PostgreSQL (Testcontainers 18.4) with seeded Mentor/Leader/member/second-member, an active Project, and a leadership term. Tasks are created and status-changed through the production `TaskService`, comments and work logs are inserted directly, then the transfer runs through the production service. Assertions read the persisted rows back via `JdbcClient`: assignee/creator/assigner columns, comment count, and summed minutes. One test soft-deletes the only unfinished Task and asserts both `countCurrentTasks`/`countDoneTasks` are 0 and the transfer returns 0. A final test transfers to the same membership to confirm no duplicate assignment side effects.

## Hand-derived expected result

- 2 unfinished (TODO, BLOCKED) + 1 DONE ΓåÆ transfer returns 2; unfinished assignees become the Leader membership; DONE stays with member; creator stays member; assigner = Leader; 1 comment and 90 minutes preserved.
- Soft-deleted only Task ΓåÆ current and done counts 0, transfer returns 0.
- Transfer to same membership ΓåÆ returns 1, exactly one row assigned to that membership.

## RED

**Command**

```text
.\mvnw.cmd "-Dtest=TaskTransferIntegrationTest" test
```

**Observed result**

```text
Tests run: 3, Failures: 0, Errors: 1
TaskValidation Task status transition is not allowed
```

First run used an illegal direct TODOΓåÆDONE transition when setting up the DONE Task (the fixed status graph forbids it); the fixture was corrected to route through IN_PROGRESS. This is a fixture error, not a production defect.

## GREEN

**Command**

```text
.\mvnw.cmd "-Dtest=TaskTransferIntegrationTest,TaskWorkSummaryIntegrationTest" test
```

**Observed result**

```text
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
.\mvnw.cmd "-Dtest=Task*" test
Tests run: 119, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## External-test boundaries

The transfer service intentionally does not open its own transaction; the Project feature owns the removal/leadership transaction and its write lock, which is out of scope for the Task branch. No browser journey is exercised here.
