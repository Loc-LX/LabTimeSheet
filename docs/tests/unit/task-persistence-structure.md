# Test Evidence: Task feature persistence structure

- **Test type:** Unit
- **Requirement IDs:** `TSK-001`–`TSK-005`, `TSK-007`, `TSK-011`, `TSK-012`
- **Scenario IDs:** `I1-TSK-01`–`I1-TSK-04`
- **Test class/method:** `com.lab.labtimesheet.feature.task.repository.TaskPersistenceStructureTest#taskPersistenceUsesJpaEntitiesAndSpringDataRepositories`
- **Implementation commit:** `511ee81a91a79a61cc6afb00097e1b38577c1968`

## Protected behavior

Task persistence uses JPA entities in `feature.task.model.entity` and Spring Data repositories in `feature.task.repository`. Status/comment mutation lookup is protected by `PESSIMISTIC_WRITE`. This prevents a regression to business-level JDBC access, unlocked mutation reads, or a global layer package.

## Test method

Four focused tests load the production `Task` and `TaskComment` classes, verify their `@Entity` annotations, verify that both production repository interfaces extend `JpaRepository`, reject direct JDBC imports in Task business code, and inspect the locked lookup's `@Lock(PESSIMISTIC_WRITE)` annotation.

## Hand-derived expected result

Exactly two Task-owned persisted aggregates are required for Iteration 1: `Task` and append-only `TaskComment`. Each must be a JPA entity, and each repository must be a Spring Data JPA repository under the Task feature package.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=TaskPersistenceStructureTest test
```

**Observed result**

```text
[ERROR] TaskPersistenceStructureTest.java:[5,54] package com.lab.labtimesheet.feature.task.model.entity does not exist
[ERROR] TaskPersistenceStructureTest.java:[6,54] package com.lab.labtimesheet.feature.task.model.entity does not exist
[INFO] BUILD FAILURE
```

The final feature-first package contract did not yet exist.

After that package move reached GREEN, the business-persistence boundary was tightened with a second test and separately observed RED:

```text
[ERROR] Tests run: 3, Failures: 2, Errors: 0, Skipped: 0
Expecting [org.springframework.jdbc.core.simple.JdbcClient]
to contain [TaskRepository, TaskCommentRepository]
Expecting empty but was: [src/main/java/com/lab/labtimesheet/feature/task/service/TaskService.java]
[INFO] BUILD FAILURE
```

The second failure proves that `TaskService` still depended on direct JDBC instead of the two Task-owned Spring Data repositories.

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=TaskPersistenceStructureTest test
```

**Observed result**

```text
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
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

The suite ran with approved escalation for PostgreSQL 18.4 Testcontainers and Mockito Java 25 self-attach.

## External-test boundaries

This structure test does not prove persistence mappings against PostgreSQL, transactional authorization, cross-feature service contracts, or rendered behavior. Those remain protected by the Task integration and web evidence after the dependency foundations are merged.
