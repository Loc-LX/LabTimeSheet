# Test Evidence: Task notification consumers

- **Test type:** Integration
- **Requirement IDs:** `NOT-002`, `NOT-003`, `NOT-004`, `NOT-010`, `TSK-003`, `TSK-009`, `TSK-012`, `TSK-018`
- **Scenario IDs:** `AC-TSK-003`, `AC-TSK-004`, `AC-TSK-006`, `AC-TSK-010`
- **Test class/method:** `com.lab.labtimesheet.feature.task.service.TaskCreationIntegrationTest`; `com.lab.labtimesheet.feature.notification.service.NotificationServiceIntegrationTest`
- **Implementation commit:** `a6b257f7f23a9db07da964093000d96a51d0a0c0`

## Protected behavior

Task mutations publish through the Platform-owned mandatory notification transaction boundary. A non-self assignment, single reassignment, pending-exit/direct-transfer reassignment, and each transferred unfinished Task create one row for each distinct previous/new assignee with a safe `/projects/{id}/tasks/{id}` action and designated email state. A self-Task invokes the validated self-silence marker and persists no notification. Status changes notify the current Leader excluding the actor and request no ordinary email. Comments notify the current assignee and current Leader excluding the author, with duplicate users collapsed. If a DONE Task retains a closed historical assignee, the comment still commits but that inaccessible recipient and action link are omitted; current authorized recipients remain notified.

The existing Platform integration contract also proves that publication outside a caller transaction is rejected, caller domain changes and notification rows roll back together, absent SMTP records `UNAVAILABLE` without later replay, and in-app-only events record `NOT_REQUIRED`.

## Test method

`TaskCreationIntegrationTest` runs against PostgreSQL 18.4 Testcontainers and creates real Projects, memberships, Tasks, comments, status changes, and transfer batches through `TaskService` and `TaskTransferService`. It queries persisted notification rows in insertion order and independently asserts recipient IDs, event types, email states, and action routes. The scenarios cover member self-creation, Leader assignment, assignee status change, member/Mentor comments including same-user assignee/Leader deduplication, one-to-one reassignment, and direct transfer of two unfinished Tasks while a DONE Task remains untouched.

`NotificationServiceIntegrationTest` is the existing PostgreSQL proof for the shared mandatory caller transaction, rollback, `UNAVAILABLE`, `NOT_REQUIRED`, and no-retroactive-delivery boundary consumed by these Task producers.

## Hand-derived expected result

- Self-Task: zero notification rows.
- Leader assignment: one `TASK_ASSIGNED` row for the new member, `UNAVAILABLE` when SMTP is absent.
- Status change: one `TASK_STATUS_CHANGED` row for the current Leader, `NOT_REQUIRED`, never the actor.
- Comments: current assignee and current Leader, author excluded and same account emitted once; all `TASK_COMMENTED` rows are `NOT_REQUIRED`. A closed retained assignee is omitted rather than receiving an inaccessible action.
- Reassignment: previous and new assignee once each, `TASK_REASSIGNED`, `UNAVAILABLE` when SMTP is absent.
- Two-Task transfer: four rows total (two recipients per changed Task), no row for the untouched DONE Task.
- Every Task notification action is `/projects/<projectId>/tasks/<taskId>`.

## RED

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=TaskCreationIntegrationTest test
```

**Observed result**

```text
Tests run: 21, Failures: 5, Errors: 0, Skipped: 0
Failures: leaderAssignmentNotifiesOnlyNewAssigneeWithUnavailableEmail; onlyCurrentAssigneeChangesStatusOnAnActiveProject; activeMemberAndOwningMentorAppendCommentsUntilProjectCompletion; reassignmentNotifiesPreviousAndNewAssigneeExactlyOnce; directTransferAllUnfinishedUsesOrderedIdProjectionAndLeavesDoneTasksUntouched.
Observed notification recipient lists were empty where the production-shaped assertions expected the specified recipients; this was the missing Task notification publication behavior, not an infrastructure failure.
```

The focused historical-assignee regression was then added and failed before the fix:

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=TaskCreationIntegrationTest#authorizedMentorCanCommentOnDoneTaskRetainingClosedAssigneeHistory test
```

```text
Tests run: 1, Failures: 0, Errors: 1, Skipped: 0.
TaskService.notificationRecipients rejected the closed retained assignee because it was absent from the current activeMembers DTO; the comment was not persisted. PostgreSQL 18.4 Testcontainers and Flyway were green, so this was the missing historical-membership behavior rather than an environment failure.
```

The lock-order boundary assertion also failed before the final correction:

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=com.lab.labtimesheet.feature.task.service.TaskMutationBoundaryTest#commentLocksProjectThenTaskBeforeWriting' test
```

```text
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0.
Mockito observed an unexpected ProjectQueryService.members(5L, 10L) call from TaskService.projectMembers after the Project mutation context; this proved the post-Project historical Account-read path that the fix removes.
```

## GREEN

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=TaskCreationIntegrationTest test
```

**Observed result**

```text
Tests run: 21, Failures: 0, Errors: 0, Skipped: 0.
PostgreSQL 18.4 Testcontainers and Flyway migration completed successfully.
```

The historical-assignee regression is now green:

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0.
The comment committed on the open Project; the closed historical assignee was absent from the already locked active-member context and omitted, while the current Leader was notified once.
```

Final focused class command:

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=com.lab.labtimesheet.feature.task.service.TaskCreationIntegrationTest' test
```

Final focused `TaskCreationIntegrationTest` coverage, including the regression, is 22/22 green against PostgreSQL 18.4 Testcontainers.

The lock-order boundary assertion is also green:

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=com.lab.labtimesheet.feature.task.service.TaskMutationBoundaryTest#commentLocksProjectThenTaskBeforeWriting' test

Tests run: 1, Failures: 0, Errors: 0, Skipped: 0.
The comment path did not call `ProjectQueryService.members` after the mutation context; recipients were derived from the already locked active-member DTO and the closed historical assignee was omitted.
```

## Task package

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=com.lab.labtimesheet.feature.task.**' test
```

The complete Task package reported 94/94 tests, 0 failures, 0 errors, and 0 skipped, including Task controller, model, repository, service, and PostgreSQL integration coverage.

## Affected suite

**Command and result**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=com.lab.labtimesheet.feature.task.service.TaskCreationIntegrationTest,com.lab.labtimesheet.feature.task.service.TaskWorkLogIntegrationTest,com.lab.labtimesheet.feature.notification.service.NotificationServiceIntegrationTest,com.lab.labtimesheet.feature.task.service.TaskMutationBoundaryTest,com.lab.labtimesheet.feature.task.service.TaskTransferServiceTest' test

Reports: TaskCreationIntegrationTest 22/22, TaskWorkLogIntegrationTest 9/9, NotificationServiceIntegrationTest 8/8, TaskMutationBoundaryTest 9/9, TaskTransferServiceTest 5/5; total 53/53 green. Comments resolve only from the already Account-locked `ProjectTaskContext.activeMembers`; absent historical or ineligible assignees are omitted before Account identity conversion, preserving the Account/profile → Project → Task order. Assignment, status, and reassignment remain strict for absent active members.
```

## Full suite and source checks

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar test
```

The complete merged-tree Maven suite reported 323/323 tests, 0 failures, 0 errors, and 0 skipped. Java 25 `./mvnw -q -DskipTests compile` and `./mvnw -q -DskipTests -Ddoclint=all javadoc:javadoc` completed successfully. `git diff --check` passed after the final evidence edits. Same-reviewer re-review accepted the implementation with 0 Critical / 0 Important findings; this evidence pin is intentionally left unstaged for its separate docs-only review.

## External-test boundaries

These tests do not prove browser rendering or SMTP-provider delivery over a live external server. SMTP-absent and shared transaction behavior are covered through the existing Platform test probe and PostgreSQL persistence. Project-owned exit approval/direct-removal authorization remains outside this Task consumer slice; the Task transfer service consumes only the reviewed `ProjectTaskContext` DTO and does not import Project persistence.
