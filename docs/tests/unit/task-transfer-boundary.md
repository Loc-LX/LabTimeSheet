# Test Evidence: Atomic unfinished-Task transfer boundary for Project membership removal

- **Test type:** Unit
- **Requirement IDs:** PRJ-009, PRJ-010
- **Scenario IDs:** PRJ-010.1, PRJ-010.2
- **Test class/method:** `com.lab.labtimesheet.feature.task.service.TaskTransferServiceTest` (both methods) and `com.lab.labtimesheet.feature.task.service.TaskQueryServiceTest.reportsCurrentAndDoneCountsForCompletionGate`
- **Implementation commit:** pending

## Protected behavior

`TaskTransferService.transferUnfinishedTasks` is a producer boundary for the Project feature: it reassigns every non-deleted unfinished Task of a departing member to a receiving Leader membership, returns how many were transferred, and performs no work at all when nothing qualifies. It must never touch DONE or soft-deleted Tasks, and reassignment must preserve creator attribution while recording the receiving Leader as the new assigner.

## Test method

Mocked `TaskRepository` + injectable `Clock`. One test feeds two unfinished Tasks (TODO, BLOCKED) and asserts the returned count is 2, each `Task.reassign(9L, 9L, NOW)` is invoked, and `flush()` is called (needed so the caller's Project transaction sees the writes before closing leadership/membership intervals). A second test feeds an empty result and asserts `0` and no `flush()`. The completion-gate test asserts `countCurrentTasks`/`countDoneTasks` delegate to the two non-deleted count queries. These are the narrowest production-shaped tests because they pin the boundary contract without exercising Project-owned transactions.

## Hand-derived expected result

- 2 unfinished Tasks ΓåÆ count `2`, both reassigned to membership `9` by assigner `9` at `NOW`, flushed once.
- Empty unfinished set ΓåÆ count `0`, repository never flushed.
- Completion gate: current = `4`, done = `4`.

## RED

**Command**

```text
.\mvnw.cmd "-Dtest=TaskTransferServiceTest" test
```

**Observed result**

```text
[ERROR] COMPILATION ERROR :
[ERROR] ... cannot find symbol: class TaskTransferService
```

Compilation failed because the boundary service did not exist yet; this is the missing-behavior RED.

## GREEN

**Command**

```text
.\mvnw.cmd "-Dtest=TaskTransferServiceTest,TaskQueryServiceTest,TaskProjectWorkSummaryScopeTest" test
```

**Observed result**

```text
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

(Initial run surfaced an `UnnecessaryStubbingException` from leftover Task stubs that `reassign` never reads; those stubs were removed and the suite passed.)

## Affected suite

**Command and result**

```text
.\mvnw.cmd "-Dtest=Task*" test
Tests run: 119, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## External-test boundaries

Proves the transfer boundary logic, not the Project write lock, leadership/interval closing, or the PostgreSQL lock ordering. Atomicity across the removal transaction and DONE/soft-deleted exclusion at the data level are proven in `docs/tests/integration/task-transfer.md`.
