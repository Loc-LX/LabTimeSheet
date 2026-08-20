# Test Evidence: Iteration 2 Task review regressions

- **Test type:** Integration and unit
- **Requirement IDs:** `PRJ-015`, `PRJ-016`, `PRJ-008`–`PRJ-011`, `PRJ-020`–`PRJ-022`, `TSK-004`, `TSK-009`, `TSK-010`, `DB-007`
- **Scenario IDs:** `AC-PRJ-004`, `AC-PRJ-008`, `AC-PRJ-012`, `AC-TSK-004`, `AC-TSK-011`
- **Test class/method:** `TaskCreationIntegrationTest#directTransferAllUnfinishedUsesOrderedIdProjectionAndLeavesDoneTasksUntouched`, `#projectProgressAggregateReturnsEmptyDenominatorForNoCurrentTasks`, `#creatorMayEditOnlyWhileStillCurrentAssigneeAndLeaderMayEditAnyUnfinishedTask`, `TaskProjectQueryTest#returnsHandCheckableStatusProgressAndMinutes`
- **Implementation commit:** `pending`

## Protected behavior

The review regressions protect direct-removal transfer from a non-empty source, one-snapshot
Project progress totals including the empty-project `N/A` denominator, and creator controls plus
final assignment actor/time after reassignment away and back.

## Test method

The PostgreSQL tests create real Project memberships and Tasks, invoke the public Project-context
and Task transfer/query services, and inspect persisted assignment/deletion data. The unit test
verifies the progress service invokes one aggregate repository projection and does not issue the
old per-status/work-log reads.

## Hand-derived expected result

- Two unfinished source Tasks transfer atomically to the Leader; one DONE Task remains with its
  original assignee.
- An empty Project returns four zero counts, zero minutes, and an empty completion percentage.
- A creator loses edit/delete after reassignment away, regains both after reassignment back, and
  the retained projection reports the final assignee, Leader assignment actor, persisted assignment
  instant, and creator deletion actor.
- The progress unit uses `1 + 2 + 1 + 2 = 6` current Tasks, `2 / 6 = 33.333...%`, and 450 minutes.

## RED

**Commands and observed results**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./mvnw -Dtest=TaskProjectQueryTest,TaskCreationIntegrationTest test
Compilation failed: TaskRepository had no aggregate projectProgress(...) method.
```

The pre-commit review also reproduced the non-empty direct-transfer failure: the derived
`findIdsBy...` declaration returned Task entities while the service expected `List<Long>`. This is
recorded in `.superpowers/sdd/PROJECT_PLAN/iteration-2/reviews/tasks-precommit-1.md`.

The reassignment-back finding was a coverage gap rather than a missing runtime rule; the new
production-shaped assertions were added without claiming a false RED. The first run exposed only
that the Testcontainers clock is intentionally fixed, so timestamp assertions were corrected to
compare the final DTO with the persisted assignment instant.

## GREEN

**Commands and results**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./mvnw -Dtest=TaskProjectQueryTest test
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

JAVA_HOME=/opt/homebrew/opt/openjdk@25 DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -Dtest=TaskCreationIntegrationTest#directTransferAllUnfinishedUsesOrderedIdProjectionAndLeavesDoneTasksUntouched,TaskCreationIntegrationTest#projectProgressAndHistoryReadPersistedWorkAndRetainedDeletedRows,TaskCreationIntegrationTest#creatorMayEditOnlyWhileStillCurrentAssigneeAndLeaderMayEditAnyUnfinishedTask test
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS
PostgreSQL 18.4 Testcontainers.

JAVA_HOME=/opt/homebrew/opt/openjdk@25 DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -Dtest=TaskCreationIntegrationTest#projectProgressAggregateReturnsEmptyDenominatorForNoCurrentTasks test
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS
PostgreSQL 18.4 Testcontainers.
```

## Affected suite

**Command and result**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -Dtest=TaskDefinitionRulesTest,TaskWorkLogRulesTest,TaskProjectQueryTest,TaskTransferServiceTest,TaskCreationIntegrationTest,TaskPersistenceStructureTest,TaskDomainRulesTest,TaskQueryServiceTest,TaskMutationBoundaryTest,TaskControllerTest test
Tests run: 72, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS
PostgreSQL 18.4 Testcontainers; Java 25.0.4.
```

## External-test boundaries

These regressions do not prove pending-exit exclusion or cross-Project 1,441-minute concurrency;
those remain pending the reviewed Project and Account DTO pins and their dedicated PostgreSQL tests.
