# Test Evidence: Role-correct Task dashboard query

- **Test type:** Unit
- **Requirement IDs:** `AUTH-003`–`AUTH-005`, `AUTH-009`, `TSK-001`, `TSK-002`, `TSK-004`, `PRJ-016`
- **Scenario IDs:** `I1-UI-03`
- **Test class/method:** `com.lab.labtimesheet.feature.task.service.TaskDashboardServiceTest`
- **Implementation commit:** `511ee81a91a79a61cc6afb00097e1b38577c1968`

## Protected behavior

The public Task dashboard service reports blocked Tasks only for a Mentor's active owned Projects. For an Intern, it excludes former/completed memberships, counts current assigned Tasks, and returns at most five priority Tasks ordered by due date with null dates last and Task ID as the stable tie-breaker.

## Test method

Two focused Mockito tests provide Project service DTOs and verify the Task service result. The repository remains mocked so the test isolates role/project/member filtering and the Task-owned dashboard DTO boundary; PostgreSQL query ordering is verified by the affected integration suite.

## Hand-derived expected result

A Mentor with one active and one planned Project receives the active Project's four blocked Tasks only. An Intern with one current active membership, one former membership, and one completed Project receives six assigned Tasks and the due-first Task from the current Project.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=TaskDashboardServiceTest test
```

**Observed result**

```text
[ERROR] TaskDashboardServiceTest.java:[34,13] cannot find symbol
  symbol: class TaskDashboardService
[INFO] BUILD FAILURE
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=TaskDashboardServiceTest test
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

This test does not prove the shared dashboard controller/template, which belongs to `work/reports-ui`. PostgreSQL ordering, soft-delete filtering, and repository query syntax remain integration-test concerns.
