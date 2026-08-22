# Test Evidence: PostgreSQL Task concurrency, authorization, and query-plan boundaries

- **Test type:** Integration
- **Requirement IDs:** `TSK-005`–`TSK-006`, `TSK-013`–`TSK-019`, `DB-004`, `DB-006`–`DB-008`
- **Scenario IDs:** `AC-TSK-002`, `AC-TSK-007`, `AC-TSK-008`, `AC-TSK-009`, `AC-TSK-011`
- **Test class/method:** `TaskWorkLogIntegrationTest#twoTransactionsUsingOneObservedTaskVersionRejectTheStaleStatusWithoutPartialState`; `TaskCreationIntegrationTest#authorizationMatrixKeepsEveryDeniedActorNonDisclosingAndStateUnchanged`; `TaskCreationIntegrationTest#taskProgressAndDueDateQueriesUseTheirSupportingPostgresIndexes`; `TaskCreationIntegrationTest#laterDayOffKeepsExistingDueDateAndListsOnlyCurrentAffectedTasks`; `TaskWorkLogIntegrationTest#concurrentProjectsRejectOneOfExactly1441AttemptedMinutes`; `TaskWorkLogIntegrationTest#correctionRejectsDeletedTaskHistoryEvenWhileAuthorAndProjectRemainActive`; `TaskWorkLogIntegrationTest#correctionRejectsFormerMemberWithoutChangingTheLog`; `TaskWorkLogIntegrationTest#correctionRejectsCompletedProjectWithoutChangingTheLog`
- **Implementation commit:** `dbd93fd79b660e7e6d0db506c0ccac23b1e54ec1`

## Protected behavior

PostgreSQL serializes two stale browser submissions that carry one observed Task version: exactly
one valid status decision commits, the stale loser raises `TaskConflictException`, the Task version
increments once, and no failed notification state remains. The authorization matrix keeps Admin,
owning/non-owning Mentor, current/former Leader, creator/current assignee, same-Project unassigned
member, former member, and unrelated actors non-disclosing where required; every denied mutation
leaves Task fields, version, deletion state, comments, and work-log counts unchanged.

The planner regression explains the production-shaped `projectProgress` aggregate: four conditional
Task status sums plus the correlated retained-work-log minutes subquery, filtered by Project and
soft-deletion. It asserts Task and work-log index plan nodes, then explains the due-date impact
predicate/order and asserts the due-date index. Existing stored due dates remain unchanged.

## Test method

Each Spring test starts Flyway V1 against a real PostgreSQL 18.4 Testcontainer. The stale test
launches two service transactions with the same database-observed version and independent targets.
The matrix creates active, former, current-Leader, unassigned, mentor, Admin, and unrelated actors,
then snapshots persisted Task state and child-row counts around each denied mutation. The planner
fixture creates one Task and retained work log, disables sequential scans, and runs `EXPLAIN (costs
off)` against the SQL-equivalent production aggregate and due-date query shapes.

## Hand-derived expected result

For one observed version, the final Task version is the original plus one, the status is exactly one
of the two submitted valid targets, and notification count is unchanged by the losing transaction.
The authorization snapshot before and after each denied operation is equal. The aggregate plan uses
an `ix_tasks_*` current-Task index and `ix_task_work_logs_project_date` for retained minutes; the
due-date plan uses `ix_tasks_due_date_active`. A later day off never rewrites the stored due date and
excludes soft-deleted Tasks from impact rows.

## RED

**Review-fix RED (run before `dbd93fd`, after the focused tests were written)**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -Dtest='TaskControllerTest#taskConflictReturnsExplicitReloadResponse,Iteration2ProjectWorkflowWebTest#taskTransferConflictReturnsExplicitReloadResponse,TaskWorkLogIntegrationTest#twoTransactionsUsingOneObservedTaskVersionRejectTheStaleStatusWithoutPartialState' test
```

```text
Compilation failed because the expected-version TaskService and Map-bearing Project transfer
contracts were not yet present. This RED established missing production contracts; it did not run a
PostgreSQL container. The authorization and planner checks are characterization/regression checks,
not retroactively attributed to that compile RED.
```

## GREEN

**Focused PostgreSQL regressions and result**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -Dtest='TaskCreationIntegrationTest#authorizationMatrixKeepsEveryDeniedActorNonDisclosingAndStateUnchanged,TaskCreationIntegrationTest#taskProgressAndDueDateQueriesUseTheirSupportingPostgresIndexes' -DargLine='-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar' test

JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -Dtest='TaskWorkLogIntegrationTest#twoTransactionsUsingOneObservedTaskVersionRejectTheStaleStatusWithoutPartialState' -DargLine='-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar' test
```

```text
First command: Tests run: 2, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS.
Second command: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS.
PostgreSQL 18.4 Testcontainers and Java 25.0.4 for both commands.
```

## Affected suite

**Command and result**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -Dtest='TaskControllerTest,Iteration2TaskWorkflowWebTest,TaskCreationIntegrationTest,TaskDashboardServiceTest,TaskDomainRulesTest,TaskMutationBoundaryTest,TaskPersistenceStructureTest,TaskProjectQueryTest,TaskQueryServiceTest,TaskTransferServiceTest,TaskWorkLogIntegrationTest,TaskWorkLogRulesTest' -DargLine='-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar' test
```

```text
Tests run: 109, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS; PostgreSQL 18.4 Testcontainers and Java 25.0.4.
```

## Branch-wide suite

**Command and result**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw test -DargLine='-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar'
```

```text
Tests run: 459, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS; PostgreSQL 18.4 Testcontainers and Java 25.0.4; total time 05:53 min.
Finished at 2026-08-22T14:44:02+07:00.
```

## External-test boundaries

The Task producer exposes `TaskTransferService#transferBatch(ProjectTaskContext, long, long,
Set<Long>, Map<Long,Long>, long)` and keeps its compatibility overload for existing programmatic
callers. The live Project exit-transfer controller/service and Reports/UI hidden version inputs are
outside this owner’s branch; the Projects and Reports/UI owners must consume the map and add their
MockMvc/PostgreSQL stale-transfer regression. No Project repository/entity/table remapping or shared
fragment edit is claimed here. The unprivileged sandbox cannot access OrbStack; recorded PostgreSQL
runs use the approved elevated socket execution path.
