# Test Evidence: Iteration 3 Project hardening

- **Test type:** Integration
- **Requirement IDs:** `AUTH-001`, `AUTH-004`, `AUTH-006`, `AUTH-007`, `AUTH-009`, `AUTH-011`, `PRJ-017`–`PRJ-022`, `DB-003`, `DB-006`, `DB-007`, `DB-011`, `DB-012`
- **Scenario IDs:** `AC-AUTH-001`, `AC-AUTH-007`, `AC-AUTH-009`, `AC-AUTH-010`, `AC-PRJ-010`–`AC-PRJ-013`
- **Test class/method:** `ProjectInvitationExitIntegrationTest` (route, terminal, matrix, and stale-ID cases), `ProjectLifecycleLockIntegrationTest#concurrentExitApprovalAndTransferLeaveOnlyAReadyOrPendingState`, `ProjectControllerTest#nestedWorkflowPostsPassTheirRouteProjectToEveryMutationBoundary` and `#projectListExposesPageTwoContinuationForMoreThanOneBoundedPage`, and `ProjectQueryIndexIntegrationTest#roleScopedProjectListsUseBoundedPagesAndRepresentativePostgresPlans`
- **Implementation commit:** `0c6ed0caf18da715bb082676e5e6faf9e4dde746`

## Protected behavior

Project mutations keep authorization and state checks inside the locked Project boundary. Every nested invitation/exit POST binds both the route Project and nested identifier before mutation; a cross-Project or stale identifier fails without protected-record disclosure or state change. A terminal exit request cannot transfer Tasks even when a new pending request for the same target makes the source membership eligible. The authorization matrix covers the scoped Project role/context/operation denials and asserts unchanged retained state. Admin, owning Mentor, and Intern Project-list reads use real repository slices with a bounded page of 50, stable continuation links, and representative PostgreSQL plans. Dashboard active-Project and Mentor distinct-member totals use dedicated aggregate queries rather than a display page. Existing Project membership, invitation/exit queue, leadership, and Task-progress/transfer index checks remain intact.

## Test method

The tests create real PostgreSQL 18.4 account, internship, Project, membership, invitation, exit-request, leadership, and Task rows. Route-binding regressions use an existing invitation or exit request owned by Project B while supplying Project A, then assert generic access denial, unchanged terminal/request state, and unchanged notification count. The terminal regression rejects request R1, creates pending request R2 for the same target, submits R1 through the six-argument transfer boundary, and checks Task assignment plus both request statuses. The AC-AUTH-009 table-driven test covers Admin, owning/non-owning Mentor, current/former Leader, ordinary member, requester, target, wrong invitee, removed member, completed Project, and unrelated contexts; every denied operation compares a public database-state snapshot before and after. Race tests run service calls in independent `TransactionTemplate` transactions with latches and PostgreSQL row-lock probes. The list-plan test creates 51 active Projects through `ProjectService`, checks page one and page two for Admin, Mentor, and Intern, verifies the one-row continuation, asserts complete `51` active-Project totals and the complete Mentor distinct-member total, analyzes the relevant tables, and explains the Admin, Mentor, and Intern SQL shapes with sequential scans disabled only for deterministic capability proof. The MVC test requests `page=2` and verifies the route passes zero-based page one to the service and renders a previous continuation link.

## Hand-derived expected result

- A route Project ID from A paired with an invitation or exit-request ID from B produces `ProjectAccessDeniedException`; the nested row, membership, leadership, Task, and notification state remain unchanged.
- A terminal R1 followed by pending R2 for the same target rejects a stale R1 transfer with `ProjectRuleViolationException`; the Task remains assigned and R1/R2 remain `REJECTED`/`PENDING`.
- The AC-AUTH-009 denial rows return the expected access exception without changing Project status, membership intervals, leadership terms, invitation/exit statuses, Task assignment/status, or Project notifications.
- Concurrent exit transfer and approval use the six-argument request-bound service API and leave a valid ready/pending outcome without a partial transfer.
- Admin, owning Mentor, and Intern list paths each return at most 50 rows. Representative PostgreSQL plans contain a `Limit`; the Mentor path uses `ix_projects_mentor_status` and the Intern path uses an index scan for the membership join. No speculative index is introduced.
- With 51 active visible Projects, each role reaches a one-row page two and the MVC route renders a stable previous link. Admin, Mentor, and Intern dashboard active-Project totals are `51`; the Mentor distinct current-member total is `1`, independent of the first-page row count.
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

The earlier review's three prose-only behavior observations are not claimed as independent RED evidence here; they are retained only as characterization context in the review record. The exact RED evidence above is the reproducible compile/API failure and the temporary guard-removal regression.

### Pagination continuation and dashboard aggregate boundary

**Command**

```text
env MAVEN_OPTS='-XX:+EnableDynamicAgentLoading' JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw -Dtest=ProjectControllerTest#projectListExposesPageTwoContinuationForMoreThanOneBoundedPage,ProjectQueryIndexIntegrationTest#roleScopedProjectListsUseBoundedPagesAndRepresentativePostgresPlans test
```

**Observed result**

```text
COMPILATION ERROR (2 compiler errors):
ProjectControllerTest.java: cannot find symbol: class ProjectListPage;
ProjectQueryIndexIntegrationTest.java: cannot find symbol: class ProjectListPage.
BUILD FAILURE
```

This RED is the missing production page DTO/boundary required by both the MVC continuation regression and the PostgreSQL page-two/complete-total regression. A subsequent first post-implementation run exposed only a fixture error (`ck_projects_activation`) because the test changed `status` without `activated_at`; the fixture was corrected to set both required lifecycle timestamps before the passing run below. That environment/data-shape failure is not counted as behavior RED.

## GREEN

**Command and result**

```text
env MAVEN_OPTS='-XX:+EnableDynamicAgentLoading' JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw -Dtest=ProjectQueryIndexIntegrationTest test
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The affected Project suite below includes the route-bound MockMvc calls, cross-Project nested-ID regressions, AC-AUTH-009 matrix, terminal R1/R2 regression, wrong-Project request regression, and six-argument concurrency boundary.

```text
env MAVEN_OPTS='-XX:+EnableDynamicAgentLoading' JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw -Dtest=ProjectControllerTest#projectListExposesPageTwoContinuationForMoreThanOneBoundedPage,ProjectQueryIndexIntegrationTest#roleScopedProjectListsUseBoundedPagesAndRepresentativePostgresPlans test
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Finished at: 2026-08-22T14:49:25+07:00
```

## Affected suite

**Command and result**

```text
env MAVEN_OPTS='-XX:+EnableDynamicAgentLoading' JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw -Dtest=ProjectServiceIntegrationTest,ProjectInvitationExitIntegrationTest,ProjectLifecycleLockIntegrationTest,ProjectEntityTest,ProjectControllerTest,Iteration2ProjectWorkflowWebTest,ProjectTaskMutationContextTest,ProjectPersistenceStructureTest,ProjectQueryIndexIntegrationTest test
Tests run: 93, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Branch-wide suite and static gates

```text
env MAVEN_OPTS='-XX:+EnableDynamicAgentLoading' JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw test
Tests run: 460, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Finished at: 2026-08-22T14:59:55+07:00
```

```text
env MAVEN_OPTS='-XX:+EnableDynamicAgentLoading' JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw -DskipTests compile
BUILD SUCCESS

env MAVEN_OPTS='-XX:+EnableDynamicAgentLoading' JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw -DskipTests -Ddoclint=all javadoc:javadoc
BUILD SUCCESS

git diff --check
PASS
```

## External-test boundaries

This evidence does not prove browser rendering, production HTTPS/proxy behavior, reports/UI exports, account lifecycle services, Task-owned mutation internals beyond the existing Project transfer handoff, or a multi-node deployment. The schema remains V1-owned by Platform; this branch adds no migration or new table. The versioned Task transfer producer contract is a pending cross-branch dependency and is deliberately not merged or claimed by this fix.
