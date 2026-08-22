# Test Evidence: PostgreSQL Task hardening and lifecycle boundaries

- **Test type:** Integration
- **Requirement IDs:** `TSK-005`–`TSK-006`, `TSK-013`–`TSK-019`, `DB-004`, `DB-006`–`DB-008`
- **Scenario IDs:** `AC-TSK-002`, `AC-TSK-007`, `AC-TSK-008`, `AC-TSK-009`, `AC-TSK-011`
- **Test class/method:** `TaskCreationIntegrationTest#laterDayOffKeepsExistingDueDateAndListsOnlyCurrentAffectedTasks`; `TaskCreationIntegrationTest#taskAuthorizationMatrixKeepsAdminReadOnlyAndRejectsGuessedOrCrossContextMutations`; `TaskCreationIntegrationTest#taskProgressAndDueDatePathsHaveTheirSupportingPostgresIndexes`; `TaskWorkLogIntegrationTest#concurrentProjectsRejectOneOfExactly1441AttemptedMinutes`; `TaskWorkLogIntegrationTest#correctionRejectsDeletedTaskHistoryEvenWhileAuthorAndProjectRemainActive`; `TaskWorkLogIntegrationTest#correctionRejectsFormerMemberWithoutChangingTheLog`; `TaskWorkLogIntegrationTest#correctionRejectsCompletedProjectWithoutChangingTheLog`
- **Implementation commit:** `df7f2a7a59dfe8e8c7d9d9faa0c07c1fd00317d5`

## Protected behavior

PostgreSQL preserves an existing Task due date when a later global day off is added and returns
only current matching Tasks for the impact preview. Admins can read but cannot mutate Task state;
guessed or cross-Project identifiers remain non-disclosing. The daily work-minute lock permits at
most 1,440 minutes across Projects. A former assignee may correct only while the author membership
and Project remain active; deleted Task history, former membership, and completed Project state are
read-only. Existing Task progress and due-date query paths have their production indexes.

## Test method

Each Spring test starts the real Flyway schema in a PostgreSQL 18.4 Testcontainer. Fixtures create
separate active/planned Projects, memberships, Tasks, and work logs. Concurrent work-log attempts
run in separate transactions against the same Intern profile. Lifecycle tests mutate only fixture
state with JDBC, invoke the public Task service, and assert both the safe exception and unchanged
persisted log. The index test reads PostgreSQL's `pg_indexes` catalog rather than inspecting source
text.

## Hand-derived expected result

For a later day off on `20/08/2026`, an existing non-deleted Task due that day remains due that day;
a soft-deleted Task is not an impact row. Two concurrent attempts of 900 and 541 minutes yield one
committed write and one `TaskValidationException`, never 1,441 minutes. Corrections after deletion,
membership closure, or Project completion leave the original 120-minute log unchanged. The catalog
contains the partial `(project_id, status, id)` and `(due_date, project_id)` Task indexes.

## RED

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -Dtest='TaskMutationBoundaryTest#statusChangeTurnsAnOptimisticTaskRaceIntoAnExplicitConflict,TaskTransferServiceTest#batchTransferTurnsAnOptimisticTaskRaceIntoAnExplicitConflict,TaskIteration3QueryTest' test
```

**Observed result**

```text
Compilation failed before the new Task conflict and impact DTO contracts existed.
This RED established the missing production behavior; the PostgreSQL container was not needed for
that compile-level contract failure.
```

## GREEN

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -Dtest='TaskCreationIntegrationTest,TaskWorkLogIntegrationTest' -DargLine='-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar' test
```

**Observed result**

```text
Tests run: 37, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS; PostgreSQL 18.4 Testcontainers, Flyway V1, Java 25.0.4.
```

## Affected suite

**Command and result**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -Dtest='TaskControllerTest,TaskCreationIntegrationTest,TaskDashboardServiceTest,TaskDomainRulesTest,TaskMutationBoundaryTest,TaskPersistenceStructureTest,TaskProjectQueryTest,TaskQueryServiceTest,TaskTransferServiceTest,TaskWorkLogIntegrationTest,TaskWorkLogRulesTest' -DargLine='-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar' test
```

```text
Tests run: 99, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS; PostgreSQL 18.4 Testcontainers and Java 25.0.4.
```

## Branch-wide suite

**Command and result**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw test -DargLine='-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar'
```

```text
Tests run: 453, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS; Java 25.0.4 and PostgreSQL 18.4 Testcontainers.
```

## External-test boundaries

The Attendance calendar controller does not yet consume this Task query on this branch; the public
DTO/service handoff is ready for the Attendance owner. Full cross-feature browser preview wiring,
production-size planner benchmarks, and an external stale-HTTP-request race remain outside this
Task branch. The unprivileged sandbox cannot access the OrbStack socket; the recorded PostgreSQL runs
used the approved elevated execution path.
