# Test Evidence: Task optimistic conflict and impact-query contracts

- **Test type:** Unit
- **Requirement IDs:** `TSK-004`, `TSK-006`, `TSK-009`, `TSK-013`–`TSK-019`, `DB-004`, `DB-007`–`DB-008`
- **Scenario IDs:** `AC-TSK-002`, `AC-TSK-004`, `AC-TSK-007`, `AC-TSK-008`, `AC-TSK-011`
- **Test class/method:** `TaskMutationBoundaryTest#statusChangeTurnsAnOptimisticTaskRaceIntoAnExplicitConflict`; `TaskTransferServiceTest#batchTransferTurnsAnOptimisticTaskRaceIntoAnExplicitConflict`; `TaskIteration3QueryTest#dueDateImpactListsCurrentTasksWithoutRewritingTheirStoredDueDate`
- **Implementation commit:** `df7f2a7a59dfe8e8c7d9d9faa0c07c1fd00317d5`

## Protected behavior

Task and batch-transfer optimistic-lock failures become one safe HTTP 409 conflict instead of an
uncaught persistence exception, and no reassignment notification is published after the failed
flush. The Task query boundary exposes the stored due date and current Task facts for a later
calendar impact preview without mutating the Task.

## Test method

Mockito supplies a locked Task and forces the repository flush to throw Spring's optimistic-lock
failure. The tests assert the stable conflict message and the absence of notification side effects.
The query test supplies one Task projection, invokes the DTO-only query boundary, and independently
checks the due date and mapped fields.

## Hand-derived expected result

An optimistic race must be a conflict with message `Task changed concurrently; reload before trying
again`; a failed transfer cannot publish. A Task due on `20/08/2026` remains due on that date and is
returned with its Project, status, title, and current assignee membership.

## RED

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -Dtest='TaskMutationBoundaryTest#statusChangeTurnsAnOptimisticTaskRaceIntoAnExplicitConflict,TaskTransferServiceTest#batchTransferTurnsAnOptimisticTaskRaceIntoAnExplicitConflict,TaskIteration3QueryTest' test
```

**Observed result**

```text
Compilation failed as expected: TaskConflictException and TaskDueDateImpactView could not be found.
The failure was the missing production contracts, not a broken fixture or environment.
```

## GREEN

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -Dtest='TaskIteration3QueryTest,TaskMutationBoundaryTest,TaskTransferServiceTest' -DargLine='-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar' test
```

**Observed result**

```text
Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Java 25.0.4; Mockito agent supplied explicitly for the local JDK.
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

This unit evidence does not prove real JPA optimistic races, PostgreSQL lock waits, calendar-controller
authorization, or cross-feature rendering. Those boundaries are covered by the companion PostgreSQL
integration evidence and remain dependent on the Attendance consumer wiring the public Task query.
