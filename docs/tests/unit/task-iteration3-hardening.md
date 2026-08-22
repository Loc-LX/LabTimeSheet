# Test Evidence: Task optimistic-version and conflict-response contracts

- **Test type:** Unit
- **Requirement IDs:** `TSK-004`, `TSK-006`, `TSK-009`, `TSK-013`–`TSK-019`, `DB-004`, `DB-007`–`DB-008`
- **Scenario IDs:** `AC-TSK-002`, `AC-TSK-004`, `AC-TSK-007`, `AC-TSK-008`, `AC-TSK-011`
- **Test class/method:** `TaskMutationBoundaryTest#statusChangeTurnsAnOptimisticTaskRaceIntoAnExplicitConflict`; `TaskMutationBoundaryTest#correctionRejectsAStaleClientWorkLogVersionBeforeMutation`; `TaskTransferServiceTest#batchTransferTurnsAnOptimisticTaskRaceIntoAnExplicitConflict`; `TaskTransferServiceTest#batchTransferRejectsAStaleClientTaskVersionBeforeMutation`; `TaskControllerTest#taskConflictReturnsExplicitReloadResponse`
- **Implementation commit:** `dbd93fd79b660e7e6d0db506c0ccac23b1e54ec1`

## Protected behavior

Browser Task and work-log mutations carry the version observed in their rendered form. The
Task-owned service validates that value after authorization and the existing lock order, before
mutating or publishing notifications. A stale Task or work-log submission becomes a stable
`TaskConflictException`; the global Task advice renders a safe HTTP 409 reload response.

The transfer producer contract also requires an immutable Task ID/version map. This branch exposes
that contract for the Projects owner but does not modify the Project route or shared workflow
fragment.

## Test method

Mockito supplies locked Task/work-log rows with known versions and verifies stale values stop before
domain mutation, persistence, and notification calls. MVC coverage posts an observed version and
asserts the shared `error/generic` view, status 409, and reload copy. The tests are deliberately
narrow: PostgreSQL transaction ordering and database planner behavior are covered by the companion
integration evidence.

## Hand-derived expected result

A client observing Task version `4` must not update a row at version `5`; a client observing work-log
version `3` must not correct a row at version `4`. No `save`, reassignment, or notification call is
allowed after either mismatch. The HTTP consumer receives 409 and only safe reload instructions.

## RED

**Command** (run before `dbd93fd`, after the review-fix tests were written)

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -Dtest='TaskControllerTest#taskConflictReturnsExplicitReloadResponse,Iteration2ProjectWorkflowWebTest#taskTransferConflictReturnsExplicitReloadResponse,TaskWorkLogIntegrationTest#twoTransactionsUsingOneObservedTaskVersionRejectTheStaleStatusWithoutPartialState' test
```

**Observed result**

```text
Compilation failed as expected: the expected-version TaskService overload and the Map-bearing
Project transfer contract were absent. The compiler reported the missing changeStatus and
transferTasks signatures; this was a production-contract RED, not a fixture or environment error.
The exploratory Project test and all Project/shared-fragment edits were removed before the Task
milestone commit because those paths belong to the Projects/Reports owners. The remaining Task
RED is therefore not evidence that the live Project route is fixed.
```

## GREEN

**Command and result**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -Dtest='TaskControllerTest,Iteration2TaskWorkflowWebTest,TaskMutationBoundaryTest,TaskTransferServiceTest' -DargLine='-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar' test
```

```text
Tests run: 38, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS; Java 25.0.4.
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

## Final static and source verification

The review-fix implementation was compiled and documented before the final evidence head. These
are the exact commands and observed results; the docs-only evidence commit did not change Java,
test, template, configuration, migration, dependency, or generated-asset paths.

**Compile**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -DskipTests compile
```

```text
BUILD SUCCESS; Java 25.0.4.
```

**Javadoc/doclint**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -DskipTests -Ddoclint=all javadoc:javadoc
```

```text
BUILD SUCCESS; doclint reported no errors. Maven emitted 100 pre-existing repository-wide
warnings; no new Task-specific warning was treated as a failure.
```

**Whitespace/diff**

```text
git diff --check
```

```text
Passed with no output.
```

**Targeted Lombok audit**

```text
task_component_boilerplate=$(rg -n 'public (TaskController|TaskService|TaskQueryService|TaskDashboardService)\(' src/main/java/com/lab/labtimesheet/feature/task | wc -l | tr -d ' ')
task_entity_boilerplate=$(rg -n 'protected (Task|TaskComment)\(\)|public (Long|long|String|Instant|LocalDate|TaskStatus) get[A-Z][A-Za-z0-9]*\(\)' src/main/java/com/lab/labtimesheet/feature/task/model/entity | wc -l | tr -d ' ')
printf 'component_boilerplate=%s entity_boilerplate=%s\n' "$task_component_boilerplate" "$task_entity_boilerplate"
test "$task_component_boilerplate" -eq 0 -a "$task_entity_boilerplate" -eq 0
```

```text
component_boilerplate=0 entity_boilerplate=0
```

No additional eligible mechanical boilerplate remained. Existing targeted Lombok constructors and
entity getters were retained; explicit constructors and mutation methods remain because they set
attribution/timestamps or enforce Task lifecycle invariants. No entity equality, hashCode,
toString, or setter generation was added.

## External-test boundaries

This unit evidence does not prove the live Project exit-transfer POST consumes version pairs; that
is an explicit Projects-owner handoff. It also does not prove production-scale planner choices or
an external browser race. The PostgreSQL two-transaction stale-status regression, authorization
matrix, and production-shaped EXPLAIN evidence are recorded in the companion integration file.
