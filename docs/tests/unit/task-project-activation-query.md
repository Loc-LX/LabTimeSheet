# Test Evidence: Project activation Task-assignment query

- **Test type:** Unit
- **Requirement IDs:** `PRJ-012`
- **Scenario IDs:** `I1-PRJ-04`, `AC-PRJ-006`
- **Test class/method:** `com.lab.labtimesheet.feature.task.service.TaskQueryServiceTest`
- **Implementation commit:** `511ee81a91a79a61cc6afb00097e1b38577c1968`

## Protected behavior

The Project feature can ask the public Task service whether any current non-deleted Task is assigned outside the Project's active membership set, without accessing Task repositories or entities.

## Test method

Two focused Mockito tests exercise the concrete public service. An empty active-membership set counts every current Task without issuing an invalid `NOT IN ()` query. A non-empty set delegates to the filtered Spring Data repository query.

## Hand-derived expected result

With no active memberships, all three current Tasks are invalid assignments. With active memberships 7 and 9, the repository-derived count of assignments outside that set is two.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=TaskQueryServiceTest test
```

**Observed result**

```text
[ERROR] TaskQueryServiceTest.java:[22,13] cannot find symbol
  symbol: class TaskQueryService
[INFO] BUILD FAILURE
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=TaskQueryServiceTest test
```

Run with approved sandbox escalation for Mockito Java 25 self-attach.

**Observed result**

```text
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=TaskPersistenceStructureTest,TaskDomainRulesTest,TaskControllerTest,TaskQueryServiceTest,TaskDashboardServiceTest,TaskMutationBoundaryTest,TaskCreationIntegrationTest test

[INFO] Tests run: 51, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## External-test boundaries

This unit test does not prove the JPQL query against PostgreSQL or Project activation integration. The Task PostgreSQL suite and the Project feature's own integration tests cover those boundaries after dependency merge.
