# Test Evidence: Iteration 3 Project hardening

- **Test type:** Integration
- **Requirement IDs:** `AUTH-001`, `AUTH-004`, `AUTH-006`, `AUTH-011`, `PRJ-017`–`PRJ-022`, `DB-003`, `DB-006`, `DB-007`, `DB-011`, `DB-012`
- **Scenario IDs:** `AC-AUTH-001`, `AC-AUTH-007`, `AC-AUTH-009`, `AC-AUTH-010`, `AC-PRJ-010`–`AC-PRJ-013`
- **Test class/method:** `ProjectInvitationExitIntegrationTest` and `ProjectLifecycleLockIntegrationTest`; `ProjectQueryIndexIntegrationTest`
- **Implementation commit:** `pending`

## Protected behavior

Project mutations keep authorization and state checks inside the locked Project boundary. Exit transfer is bound to the route's pending request, cross-Project membership and replacement IDs fail without aggregate details, completed Projects reject stale invitation mutation, and concurrent invitation/direct-add, leadership, and exit transfer/approval races leave one valid winner and no partial history. PostgreSQL catalog and explain-plan checks protect the indexes used by Project membership, invitation/exit queues, and Task progress/transfer paths.

## Test method

The tests create real PostgreSQL 18.4 account, internship, Project, membership, invitation, exit-request, leadership, and Task rows. Race tests run each service call in an independent `TransactionTemplate` transaction with latches and PostgreSQL row-lock probes. Assertions inspect only public outcomes and retained database state: winner count, terminal/request status, membership interval, assignment, and current leadership count. The index test reads `pg_indexes` and runs `EXPLAIN (COSTS OFF)` with sequential scans disabled only for deterministic index capability proof.

## Hand-derived expected result

- A route request ID from another or terminal exit cannot transfer Tasks; the selected Task remains assigned and the pending request remains unchanged.
- A membership/replacement identifier from another Project produces `ProjectAccessDeniedException`; no exit request or leadership term changes.
- Completed Projects reject invitation revocation and retain the pending row in the crafted stale-state regression.
- Invitation acceptance versus Mentor direct-add commits exactly one current membership and resolves the invitation as `ACCEPTED` or `SUPERSEDED`.
- Concurrent leadership changes based on the same current-Leader snapshot produce one success, one conflict, and exactly one current term.
- Concurrent transfer and approval first make the target ready; the final state is either pending/current after an approval conflict or approved/closed after successful approval, with the Task transferred in both cases.
- Required Project/membership/invitation/exit/Task indexes exist, and PostgreSQL selects an index for progress and transfer paths.

## RED

**Command**

```text
MAVEN_OPTS='-XX:+EnableDynamicAgentLoading' JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH="/opt/homebrew/opt/openjdk@25/bin:$PATH" ./mvnw '-Dtest=ProjectInvitationExitIntegrationTest#exitTransferMustUseThePendingRequestFromTheRoute' test
```

**Observed result**

```text
COMPILATION ERROR: method transferTasks in class ProjectService cannot be applied to given types;
required: long,long,long,java.util.Set<java.lang.Long>,long
found: long,long,long,long,java.util.Set<java.lang.Long>,long
```

The production boundary had no request-bound transfer overload, so the route identifier could not be checked.

Additional behavior REDs were observed before their guards:

```text
leaderRemovalRejectsMembershipIdFromAnotherProjectWithoutRuleDetails: expected ProjectAccessDeniedException but was ProjectRuleViolationException: Membership is not in this Project
leadershipChangeRejectsAnInternFromAnotherProjectWithoutContextLeak: expected ProjectAccessDeniedException but was ProjectRuleViolationException: Leader must be a current same-Project member
completedProjectsRejectInvitationRevocationWithoutChangingRetainedHistory: Expected ProjectRuleViolationException to be thrown, but nothing was thrown
```

## GREEN

**Command and result**

```text
MAVEN_OPTS='-XX:+EnableDynamicAgentLoading' JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH="/opt/homebrew/opt/openjdk@25/bin:$PATH" ./mvnw '-Dtest=ProjectInvitationExitIntegrationTest#exitTransferMustUseThePendingRequestFromTheRoute+leaderRemovalRejectsMembershipIdFromAnotherProjectWithoutRuleDetails+leadershipChangeRejectsAnInternFromAnotherProjectWithoutContextLeak' test
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

```text
MAVEN_OPTS='-XX:+EnableDynamicAgentLoading' JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH="/opt/homebrew/opt/openjdk@25/bin:$PATH" ./mvnw '-Dtest=ProjectInvitationExitIntegrationTest#completedProjectsRejectInvitationRevocationWithoutChangingRetainedHistory' test
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

```text
MAVEN_OPTS='-XX:+EnableDynamicAgentLoading' JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH="/opt/homebrew/opt/openjdk@25/bin:$PATH" ./mvnw '-Dtest=ProjectLifecycleLockIntegrationTest#concurrentLeadershipChangesHaveOneWinnerFromTheSameLeaderSnapshot+invitationAcceptanceAndMentorDirectAddHaveOneCommittedWinner+concurrentExitApprovalAndTransferLeaveOnlyAReadyOrPendingState' test
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

```text
MAVEN_OPTS='-XX:+EnableDynamicAgentLoading' JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH="/opt/homebrew/opt/openjdk@25/bin:$PATH" ./mvnw '-Dtest=ProjectLifecycleLockIntegrationTest#concurrentLeaderChangeAndLeaderRemovalHaveOneWinnerFromTheSameSnapshot' test
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

```text
MAVEN_OPTS='-XX:+EnableDynamicAgentLoading' JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH="/opt/homebrew/opt/openjdk@25/bin:$PATH" ./mvnw -Dtest=ProjectQueryIndexIntegrationTest test
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
MAVEN_OPTS='-XX:+EnableDynamicAgentLoading' JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH="/opt/homebrew/opt/openjdk@25/bin:$PATH" ./mvnw -Dtest=ProjectServiceIntegrationTest,ProjectInvitationExitIntegrationTest,ProjectLifecycleLockIntegrationTest,ProjectEntityTest,ProjectControllerTest,Iteration2ProjectWorkflowWebTest,ProjectTaskMutationContextTest,ProjectPersistenceStructureTest,ProjectQueryIndexIntegrationTest test
Tests run: 86, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Branch-wide suite and static gates

```text
MAVEN_OPTS='-XX:+EnableDynamicAgentLoading' JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH="/opt/homebrew/opt/openjdk@25/bin:$PATH" ./mvnw test
Tests run: 453, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

```text
MAVEN_OPTS='-XX:+EnableDynamicAgentLoading' JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH="/opt/homebrew/opt/openjdk@25/bin:$PATH" ./mvnw -DskipTests compile
BUILD SUCCESS

MAVEN_OPTS='-XX:+EnableDynamicAgentLoading' JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH="/opt/homebrew/opt/openjdk@25/bin:$PATH" ./mvnw -DskipTests -Ddoclint=all javadoc:javadoc
BUILD SUCCESS

git diff --check
PASS
```

## External-test boundaries

This evidence does not prove browser rendering, production HTTPS/proxy behavior, reports/UI exports, account lifecycle services, Task-owned mutation internals beyond the Project transfer handoff, or a multi-node deployment. The schema remains V1-owned by Platform; this branch adds no migration or new table.
