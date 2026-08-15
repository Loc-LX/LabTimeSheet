# Test Evidence: Iteration 1 Task persistence and authorization

- **Test type:** Integration
- **Requirement IDs:** `AUTH-001`, `AUTH-002`, `AUTH-005`–`AUTH-009`, `AUTH-011`, `PRJ-013`, `PRJ-015`, `PRJ-016`, `TSK-001`–`TSK-005`, `TSK-007`, `TSK-008`, `TSK-011`, `TSK-012`, `TSK-018`
- **Scenario IDs:** `I1-TSK-01`–`I1-TSK-05`, `AC-AUTH-001`, `AC-AUTH-003`–`AC-AUTH-007`, `AC-AUTH-010`, `AC-PRJ-008`, `AC-TSK-002`, `AC-TSK-003`, `AC-TSK-006`, `AC-TSK-010`
- **Test class/method:** `com.lab.labtimesheet.feature.task.service.TaskCreationIntegrationTest`
- **Implementation commit:** `511ee81a91a79a61cc6afb00097e1b38577c1968`

## Protected behavior

PostgreSQL-backed Task operations preserve generic same-Project membership actors, limit ordinary members to self-Task creation, allow current Leaders to assign active same-Project members, validate due dates, restrict status changes to the active current assignee, append authorized comments, exclude deleted Tasks from current reads/progress, render assignee names and empty progress, deny guessed/cross-Project identifiers without writes, give former members read-only access only after completion, and execute the Project-activation and dashboard Task queries.

## Test method

Thirteen transactional Spring integration tests create real users, Intern profiles, Projects, memberships, leadership terms, calendar events, Tasks, and comments against the approved PostgreSQL 18.4 V1 schema. Assertions inspect returned behavior and persisted rows; there are no mocked domain or database operations.

## Hand-derived expected result

A self-Task stores one membership in creator, assigner, and assignee fields. A Leader-created Task retains the Leader membership as creator/assigner and the selected member as assignee. Project start/end due dates are valid; dates before, after, or on a current global day off are invalid. Only an active Project's current assignee can traverse an allowed status edge. Authorized member/Mentor comments append two rows. Four current Tasks with one in each status produce 25% and four unit counts; a deleted fifth Task is absent; zero Tasks has no percentage.

A former member cannot read Task data while the Project remains planned or active, but can read the completed Project history. List/detail views resolve the assignee display name from the Project service boundary, and their capability flags match current membership, assignment, role, and Project lifecycle.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=TaskCreationIntegrationTest test
```

**Observed result**

```text
[ERROR] TaskCreationIntegrationTest.java:[6,34] cannot find symbol
  symbol:   class CreateTaskCommand
[ERROR] TaskCreationIntegrationTest.java:[8,34] cannot find symbol
  symbol:   class TaskService
[INFO] BUILD FAILURE
```

After creation reached GREEN, the next cohesive workflow increment was separately observed RED:

```text
[ERROR] TaskCreationIntegrationTest.java:[7,34] cannot find symbol
  symbol:   class TaskCommentView
[ERROR] TaskCreationIntegrationTest.java:[8,34] cannot find symbol
  symbol:   class TaskDetails
[ERROR] TaskCreationIntegrationTest.java:[9,34] cannot find symbol
  symbol:   class TaskListView
[INFO] BUILD FAILURE
```

The review-hardening increment was also observed RED before its implementation. The former-member PostgreSQL regression reached the old list behavior instead of throwing, and the view-contract tests could not compile because `assigneeName`, `canCreate`, `canChangeStatus`, and `canComment` did not exist.

The second review then made the completed-history fixture production-shaped by closing the current leadership term and all memberships. That focused test was observed RED because the Project read boundary still required `currentLeader()` for a completed Project:

```text
[ERROR] TaskCreationIntegrationTest.formerMemberReadsOnlyCompletedProjectTaskHistory
  » TaskNotFound Task or Project was not found
[INFO] Tests run: 1, Failures: 0, Errors: 1, Skipped: 0
[INFO] BUILD FAILURE
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=TaskCreationIntegrationTest test
```

**Observed result**

```text
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw test

[INFO] Tests run: 107, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## External-test boundaries

This evidence does not prove browser behavior, shared-shell integration, notification delivery, Iteration 2 work logs/reassignment/edit/deletion, or Iteration 3 concurrency/index plans. The status edge matrix is separately protected by unit evidence. HTTP form, CSRF, template, and direct-route behavior require the companion web evidence.
