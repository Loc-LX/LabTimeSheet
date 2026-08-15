# Test Evidence: Task mutation authorization and locking boundary

- **Test type:** Unit
- **Requirement IDs:** `AUTH-011`, `TSK-003`, `TSK-007`, `TSK-012`, `TSK-018`
- **Scenario IDs:** `I1-TSK-01`, `I1-TSK-03`, `I1-TSK-04`, `AC-AUTH-010`, `AC-TSK-003`, `AC-TSK-006`, `AC-TSK-010`
- **Test class/method:** `com.lab.labtimesheet.feature.task.service.TaskMutationBoundaryTest`
- **Implementation commit:** `511ee81a91a79a61cc6afb00097e1b38577c1968`

## Protected behavior

Every Task create/status/comment mutation first asks the concrete Project service for a current authorization context while holding the Project row lock. Status and comment mutations then load the Task with `PESSIMISTIC_WRITE` before checking or changing Task state.

## Test method

Three focused Mockito tests verify call order for create, status, and comment. They prove the Project mutation context precedes the Task write, the unlocked Project query is not used for create, and status/comment use the locked Task lookup before mutation. `TaskPersistenceStructureTest` separately inspects the real repository method's lock annotation, while the PostgreSQL workflow suite executes the query.

## Hand-derived expected result

Create calls `ProjectService.taskMutationContext(5, 10)` before saving. Status and comment call that same Project boundary, then `TaskRepository.findLockedByIdAndProjectIdAndDeletedAtIsNull(25, 10)`, before changing status or appending the comment. The Project service joins the outer Task transaction, so both locks remain through commit or rollback.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=TaskMutationBoundaryTest test
```

**Observed result**

```text
[ERROR] constructor TaskService ... cannot be applied to given types
  required: TaskRepository,TaskCommentRepository,ProjectQueryService,CalendarApplicationService,Clock
  found:    TaskRepository,TaskCommentRepository,ProjectQueryService,ProjectService,CalendarApplicationService,Clock
[ERROR] cannot find symbol
  symbol: method findLockedByIdAndProjectIdAndDeletedAtIsNull(long,long)
[INFO] BUILD FAILURE
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=TaskMutationBoundaryTest test
```

Run with approved sandbox escalation for Mockito Java 25 self-attach.

**Observed result**

```text
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
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

These unit tests establish service call order and repository lock metadata; they do not simulate two concurrent database transactions. PostgreSQL execution of the locked Task lookup is covered by `TaskCreationIntegrationTest`, and the producing Project feature separately proves its locked DTO boundary. Broader concurrency stress remains the explicit Iteration 3 hardening scope.
