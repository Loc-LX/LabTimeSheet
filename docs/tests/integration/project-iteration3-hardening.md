# Test Evidence: Iteration 3 Project hardening

- **Test type:** Integration
- **Requirement IDs:** `AUTH-001`, `AUTH-004`, `AUTH-006`, `AUTH-007`, `AUTH-009`, `AUTH-011`, `PRJ-017`–`PRJ-022`, `DB-003`, `DB-006`, `DB-007`, `DB-011`, `DB-012`
- **Scenario IDs:** `AC-AUTH-001`, `AC-AUTH-007`, `AC-AUTH-009`, `AC-AUTH-010`, `AC-PRJ-010`–`AC-PRJ-013`
- **Test class/method:** `ProjectInvitationExitIntegrationTest` (route, terminal, matrix, stale-ID, stale-version, and null-version cases), `ProjectLifecycleLockIntegrationTest#concurrentExitApprovalAndTransferLeaveOnlyAReadyOrPendingState`, `ProjectControllerTest#nestedWorkflowPostsPassTheirRouteProjectToEveryMutationBoundary`, `#projectListExposesPageTwoContinuationForMoreThanOneBoundedPage`, `#exitTransferBindsCompleteTaskVersionPairsAndPassesThemToProjectService`, `#exitTransferRejectsAnIncompleteTaskVersionMapBeforeCallingProjectService`, and `#staleExitTransferReturnsSafeConflictWithoutProjectRedirect`, and `ProjectQueryIndexIntegrationTest#roleScopedProjectListsUseBoundedPagesAndEligibleDashboardTotals`
- **Implementation commit:** `fb41fe909480bce68f4384f5b5d143da4ebf4f93` (on merged Task producer `a6261eb0ccad46ee6eac028d7202b5c06967fe13` at `1b3b1df843ed253e5a5fabbdf82d1c9af6e390cf`)

## Protected behavior

Project mutations keep authorization and state checks inside the locked Project boundary. Every nested invitation/exit POST binds both the route Project and nested identifier before mutation; a cross-Project or stale identifier fails without protected-record disclosure or state change. A terminal exit request cannot transfer Tasks even when a new pending request for the same target makes the source membership eligible. The exit-transfer POST now requires one complete repeated `taskVersions=taskId:version` pair for every selected Task, and the Project service passes an immutable exact-key map into the reviewed six-argument Task transfer contract. The public map-bearing Project service overload rejects null or invalid complete maps before entering Task mutation; only explicit legacy overloads call the Task producer's no-version compatibility overload. A stale pair becomes the Task-owned safe HTTP 409 boundary before assignment, request, or notification mutation. The authorization matrix covers the scoped Project role/context/operation denials and asserts unchanged retained state. Admin, owning Mentor, and Intern Project-list reads use real repository slices with a bounded page of 50, stable continuation links, and representative PostgreSQL plans. Dashboard active-Project totals use dedicated aggregate queries, while the Mentor distinct-member total starts from the complete Project-owned scalar ID set and applies the established public `AccountService#isEligibleIntern` boundary rather than counting the first page or joining Account persistence. Existing Project membership, invitation/exit queue, leadership, and Task-progress/transfer index checks remain intact.

## Test method

The tests create real PostgreSQL 18.4 account, internship, Project, membership, invitation, exit-request, leadership, and Task rows. Route-binding regressions use an existing invitation or exit request owned by Project B while supplying Project A, then assert generic access denial, unchanged terminal/request state, and unchanged notification count. The terminal regression rejects request R1, creates pending request R2 for the same target, submits R1 through the six-argument transfer boundary, and checks Task assignment plus both request statuses. The new MVC tests post repeated `taskVersions` pairs, reject missing/extra/duplicate or malformed coverage before calling `ProjectService`, and map a Task-owned conflict to the safe generic 409 response. The PostgreSQL stale-version regression observes Task version `0`, advances the stored version to `1`, submits the stale pair through the route-bound Project service, and asserts the target assignment, current version, pending request, and notification count are unchanged. The direct-service null-map regression advances a stored Task version, calls the map-bearing overload with a null map, and asserts `ProjectRuleViolationException` plus unchanged assignment, version, pending request, and notification count. The AC-AUTH-009 table-driven test covers Admin, owning/non-owning Mentor, current/former Leader, ordinary member, requester, target, wrong invitee, removed member, completed Project, and unrelated contexts; every denied operation compares a public database-state snapshot before and after. Race tests run service calls in independent `TransactionTemplate` transactions with latches and PostgreSQL row-lock probes. The list-plan test creates 51 active Projects through `ProjectService`, each with an eligible Leader and a non-Leader whose internship is then completed with no unfinished Tasks; it verifies that all 51 membership intervals remain current while page one/page two reachability for Admin, Mentor, and Leader Intern, complete `51` active-Project totals, and the Mentor distinct eligible-member total of `1` are asserted. It analyzes the relevant tables and explains the Admin, Mentor, and Intern SQL shapes with sequential scans disabled only for deterministic capability proof. The MVC test requests `page=2` and verifies the route passes zero-based page one to the service and renders a previous continuation link.

## Hand-derived expected result

- A route Project ID from A paired with an invitation or exit-request ID from B produces `ProjectAccessDeniedException`; the nested row, membership, leadership, Task, and notification state remain unchanged.
- A terminal R1 followed by pending R2 for the same target rejects a stale R1 transfer with `ProjectRuleViolationException`; the Task remains assigned and R1/R2 remain `REJECTED`/`PENDING`.
- The AC-AUTH-009 denial rows return the expected access exception without changing Project status, membership intervals, leadership terms, invitation/exit statuses, Task assignment/status, or Project notifications.
- Concurrent exit transfer and approval use the six-argument request-bound service API and leave a valid ready/pending outcome without a partial transfer.
- A transfer POST with selected IDs `{101,102}` and pairs `{101:4,102:9}` reaches ProjectService as an immutable map; a missing pair is rejected before the service call, and a stale Task version yields HTTP 409 with only safe reload copy.
- In PostgreSQL, a stale transfer leaves the target assignee, Task version, pending exit request, and notification count unchanged.
- A direct call to the map-bearing Project service overload with a null map is rejected before Task mutation; the Task assignment, advanced version, pending exit request, and notification count remain unchanged. Explicit legacy overloads retain their separate no-version Task producer path.
- Admin, owning Mentor, and Intern list paths each return at most 50 rows. Representative PostgreSQL plans contain a `Limit`; the Mentor path uses `ix_projects_mentor_status` and the Intern path uses an index scan for the membership join. No speculative index is introduced.
- With 51 active visible Projects, each role reaches a one-row page two and the MVC route renders a stable previous link. Admin, Mentor, and Intern dashboard active-Project totals are `51`; a completed non-Leader retains all 51 current membership intervals but is excluded by the public eligibility boundary, leaving the Mentor distinct eligible-member total at `1`, independent of the first-page row count.
- Existing Project/membership/invitation/exit/Task indexes are present, and PostgreSQL selects the established Task progress and transfer indexes.

## RED

### Route-bound mutation APIs and bounded list API

**Command**

```text
env MAVEN_OPTS='-XX:+EnableDynamicAgentLoading' JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw -Dtest=ProjectControllerTest,ProjectInvitationExitIntegrationTest,ProjectQueryIndexIntegrationTest test
```

**Observed result**

```text
COMPILATION ERROR (17 compiler errors):
ProjectQueryService.listVisible required long, found long, PageRequest;
ProjectService.revokeInvitation required 2 arguments, found 3;
ProjectService.cancelExit required 2 arguments, found 3;
ProjectService.approveExit/rejectExit required 3 arguments, found 4.
BUILD FAILURE
```

This is the production-shaped RED for the new route-bound controller calls, service boundaries, and pageable Project-list test: the required overloads and bounded repository contract did not yet exist.

### Terminal request recheck

To verify that the terminal-status branch was load-bearing, only the `!request.isPending()` guard was temporarily removed from `ProjectService#transferTasksInternal`; it was restored immediately after the RED run.

**Command**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw -Dtest=ProjectInvitationExitIntegrationTest#routeBoundExitTransferRejectsTerminalRequestWithoutChangingTask test
```

**Observed result**

```text
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
Expected ProjectRuleViolationException to be thrown, but nothing was thrown.
BUILD FAILURE
```

The failure used an existing rejected R1 and a new pending R2 for the same target, so it reached the request-specific terminal check rather than a missing-ID lookup. The guard was restored before GREEN verification.

The earlier review's three prose-only behavior observations are not claimed as independent RED evidence here; they are retained only as characterization context in the review record. The exact RED evidence above is the reproducible compile/API failure, temporary guard-removal regression, and complete eligible-member mismatch.

### Pagination continuation and dashboard aggregate boundary

**Command**

```text
env MAVEN_OPTS='-XX:+EnableDynamicAgentLoading' JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw -Dtest=ProjectControllerTest#projectListExposesPageTwoContinuationForMoreThanOneBoundedPage,ProjectQueryIndexIntegrationTest#roleScopedProjectListsUseBoundedPagesAndEligibleDashboardTotals test
```

**Observed result**

```text
COMPILATION ERROR (2 compiler errors):
ProjectControllerTest.java: cannot find symbol: class ProjectListPage;
ProjectQueryIndexIntegrationTest.java: cannot find symbol: class ProjectListPage.
BUILD FAILURE
```

This RED is the missing production page DTO/boundary required by both the MVC continuation regression and the PostgreSQL page-two/complete-total regression. A subsequent first post-implementation run exposed only a fixture error (`ck_projects_activation`) because the test changed `status` without `activated_at`; the fixture was corrected to set both required lifecycle timestamps before the passing run below. That environment/data-shape failure is not counted as behavior RED.

### Complete eligible-member aggregate

**Command**

```text
env MAVEN_OPTS='-XX:+EnableDynamicAgentLoading' JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw -Dtest=ProjectQueryIndexIntegrationTest#mentorDashboardExcludesCompletedInternFromDistinctMembersWhileMembershipIsRetained test
```

**Observed result**

```text
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
Expected: 1L but was: 2L
BUILD FAILURE
Finished at: 2026-08-22T15:09:15+07:00
```

This PostgreSQL RED proved that counting the complete current-membership set without the established Account eligibility boundary included a non-Leader Intern after `completeInternship` retained its membership interval. The temporary standalone fixture was consolidated into the 51-Project page-two test so the regression and complete-total proof share one isolated database fixture.

### Versioned Task transfer consumer boundary

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw -Dtest='ProjectControllerTest#exitTransferBindsCompleteTaskVersionPairsAndPassesThemToProjectService,ProjectControllerTest#exitTransferRejectsAnIncompleteTaskVersionMapBeforeCallingProjectService,ProjectControllerTest#staleExitTransferReturnsSafeConflictWithoutProjectRedirect,ProjectInvitationExitIntegrationTest#staleExitTransferVersionReturnsConflictWithoutPartialTaskOrRequestMutation' test
```

**Observed result**

```text
Test compilation reported 3 errors: no suitable method found for ProjectService.transferTasks(long,long,long,long,Set<Long>,Map<Long,Long>,long) at the two controller verifications and the PostgreSQL regression.
BUILD FAILURE
Finished at: 2026-08-22T15:51:56+07:00
```

This RED was the missing Project consumer boundary after the exact reviewed Task producer merge; it did not depend on a fixture, database, or browser failure.

### Mandatory version map at the public Project boundary

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -Dtest='ProjectInvitationExitIntegrationTest#versionedExitTransferRejectsNullTaskVersionsWithoutMutation' -DargLine='-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar' test
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
BUILD FAILURE
Finished at: 2026-08-22T16:22:07+07:00
```

The assertion failed with `Expected ProjectRuleViolationException to be thrown, but nothing was thrown.` The committed Task version advance was therefore accepted by the public map-bearing Project overload, proving the null bypass before the fix. An unprivileged retry at `2026-08-22T16:21:45+07:00` failed before test execution because the sandbox could not open the OrbStack socket; it is not counted as behavioral RED.

## GREEN

### Versioned Task transfer consumer

**Controller-focused command and result**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw -Dtest='ProjectControllerTest#exitTransferBindsCompleteTaskVersionPairsAndPassesThemToProjectService,ProjectControllerTest#exitTransferRejectsAnIncompleteTaskVersionMapBeforeCallingProjectService,ProjectControllerTest#staleExitTransferReturnsSafeConflictWithoutProjectRedirect' -DargLine='-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar' test
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Finished at: 2026-08-22T15:54:45+07:00
```

**PostgreSQL stale-transfer command and result**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -Dtest='ProjectInvitationExitIntegrationTest#staleExitTransferVersionReturnsConflictWithoutPartialTaskOrRequestMutation' -DargLine='-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar' test
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS
PostgreSQL 18.4 Testcontainers; finished at 2026-08-22T15:55:06+07:00.
```

The controller slice also passed with the existing Byte Buddy agent after the initial Java 25 self-attach attempt was correctly characterized as an environment limitation.

### Mandatory version map at the public Project boundary

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -Dtest='ProjectInvitationExitIntegrationTest#versionedExitTransferRejectsNullTaskVersionsWithoutMutation' -DargLine='-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar' test
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS; PostgreSQL 18.4 Testcontainers.
Finished at: 2026-08-22T16:23:30+07:00.
```

The focused post-fix set of three controller cases, the existing stale PostgreSQL case, and this direct-service null-map case passed `5/5` at `2026-08-22T16:24:35+07:00`. The map-bearing overload now rejects null/incomplete maps before the locked transfer workflow, while no-map Project overloads call the Task producer's separate legacy overload.

**Command and result**

```text
env MAVEN_OPTS='-XX:+EnableDynamicAgentLoading' JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw -Dtest=ProjectQueryIndexIntegrationTest test
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Finished at: 2026-08-22T15:15:00+07:00
```

```text
env MAVEN_OPTS='-XX:+EnableDynamicAgentLoading' JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw -Dtest=ProjectQueryIndexIntegrationTest#roleScopedProjectListsUseBoundedPagesAndEligibleDashboardTotals test
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Finished at: 2026-08-22T15:15:45+07:00
```

The affected Project suite below includes the route-bound MockMvc calls, cross-Project nested-ID regressions, AC-AUTH-009 matrix, terminal R1/R2 regression, wrong-Project request regression, and six-argument concurrency boundary.

## Affected suite

**Command and result**

```text
env MAVEN_OPTS='-XX:+EnableDynamicAgentLoading' JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw -Dtest=ProjectServiceIntegrationTest,ProjectInvitationExitIntegrationTest,ProjectLifecycleLockIntegrationTest,ProjectEntityTest,ProjectControllerTest,Iteration2ProjectWorkflowWebTest,ProjectTaskMutationContextTest,ProjectPersistenceStructureTest,ProjectQueryIndexIntegrationTest test
Tests run: 93, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Finished at: 2026-08-22T15:17:37+07:00
```

### Post-merge affected Project/Task suite

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -Dtest='ProjectServiceIntegrationTest,ProjectInvitationExitIntegrationTest,ProjectLifecycleLockIntegrationTest,ProjectEntityTest,ProjectControllerTest,Iteration2ProjectWorkflowWebTest,ProjectTaskMutationContextTest,ProjectPersistenceStructureTest,ProjectQueryIndexIntegrationTest,TaskControllerTest,Iteration2TaskWorkflowWebTest,TaskCreationIntegrationTest,TaskDashboardServiceTest,TaskDomainRulesTest,TaskMutationBoundaryTest,TaskPersistenceStructureTest,TaskProjectQueryTest,TaskQueryServiceTest,TaskTransferServiceTest,TaskWorkLogIntegrationTest,TaskWorkLogRulesTest' -DargLine='-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar' test
Tests run: 207, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS; PostgreSQL 18.4 Testcontainers and Java 25.0.4.
Finished at 2026-08-22T16:25:47+07:00.
```

### Post-merge architecture and static gates

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw -Dtest='LayerStructureTest,ProjectPersistenceStructureTest,TaskPersistenceStructureTest' -DargLine='-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar' test
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS; finished at 2026-08-22T16:25:55+07:00.

JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw -DskipTests compile
BUILD SUCCESS; finished at 2026-08-22T16:26:03+07:00.

JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw -DskipTests -Ddoclint=all javadoc:javadoc
BUILD SUCCESS; finished at 2026-08-22T16:26:11+07:00.

git diff --check
PASS
```

## Branch-wide suite and static gates

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw test -DargLine='-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar'
Tests run: 480, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Finished at: 2026-08-22T16:31:56+07:00
```

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw -DskipTests compile
BUILD SUCCESS

JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw -DskipTests -Ddoclint=all javadoc:javadoc
BUILD SUCCESS

git diff --check
PASS
```

## External-test boundaries

This evidence does not prove browser rendering of the Reports/UI-owned hidden version inputs, production HTTPS/proxy behavior, reports/UI exports, account lifecycle services, Task-owned mutation internals beyond the public versioned transfer boundary, or a multi-node deployment. The schema remains V1-owned by Platform; this branch adds no migration or new table. The Project-owned scalar ID query uses the existing public Account eligibility service one ID at a time; a future batch boundary is not justified by this requirement. The exact reviewed Task producer `a6261eb0ccad46ee6eac028d7202b5c06967fe13` is merged at `1b3b1df843ed253e5a5fabbdf82d1c9af6e390cf`; Project does not import Task repositories/entities or edit the shared workflow fragment.
