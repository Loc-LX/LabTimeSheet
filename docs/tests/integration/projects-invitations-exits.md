# Test Evidence: Project invitations, pending membership exits, and notification consumers

- **Test type:** Integration
- **Requirement IDs:** `AUTH-001`, `AUTH-002`, `AUTH-006`, `AUTH-009`, `AUTH-011`, `PRJ-017`–`PRJ-022`, `NOT-001`, `NOT-002`, `NOT-004`, `NOT-010`, and delivery milestone `I2-PRJ-06`
- **Scenario IDs:** `AC-AUTH-010`; the executed invitation/exit slice of `AC-PRJ-011`–`AC-PRJ-013`; the Project recipient/in-app slice of `AC-NOT-004`
- **Test class/method:** `com.lab.labtimesheet.feature.project.service.ProjectInvitationExitIntegrationTest`, `ProjectLifecycleLockIntegrationTest`, `ProjectTaskMutationContextTest`
- **Implementation commit:** `91f74146a15e24bac3d22aae05e052cdfec8c76c`

## Protected behavior

Project invitations are non-expiring, tied to the issuing leadership term, and answerable only by the intended authenticated Intern. Mentor direct-add supersedes a matching pending invitation; leader-owned and Mentor-owned revocation retain distinct codes. Pending membership exits retain the membership interval and existing rights, enforce the request participant shape, and can be cancelled only by a current, eligible requester while the Project is open. Authorization is established before terminal-state evaluation, and every lifecycle mutation locks Account rows and Intern profiles in ascending order before the Project row. Mentor-owned decision paths include retained historical requesters and every pending notification recipient in that lock set, then resolve immutable Account-owned notification facts before acquiring the Project lock. The DTO-only Task context exposes current Leader, joined-at member intervals, and pending target membership IDs while the Project write lock is held. The immutable `ProjectMembershipIntervalView` query retains current and closed membership intervals across Projects without hydrating a filtered Project aggregate. Project History exposes the retained membership add/remove and leadership appointment/end actor identifiers instead of deriving attribution from current state.

The same Project domain transactions publish the four reviewed Project notification families. Invitation creation reaches only the invitee; invitation response reaches the issuing Leader and owning Mentor; revocation or supersession reaches the invitee, issuing Leader, and owning Mentor; and exit request/decision recipients follow the request type and requester/target/current-Leader rules with duplicate account IDs collapsed. Recipients are resolved through the Account-owned identity DTO and actions use safe relative Project routes.

## Test method

The Spring Boot integration tests create active account/internship fixtures and Project aggregates through the public Project service. PostgreSQL 18.4 assertions inspect only retained domain fields: invitation status/code/term/membership provenance, pending membership intervals, exit request resolution, and stored membership/leadership actor attribution. They read pending target IDs from both the locked `ProjectService.taskMutationContext` handoff and the read-only `ProjectQueryService.taskContext` DTO, then verify joined-at/left-at through `ProjectTaskMemberView` and `ProjectMembershipIntervalView`. The interval regression reads retained membership projections and then reads the same Project context in one transaction to prove no filtered fetch-join aggregate is left in the persistence context. Negative cases use wrong actors, blank reasons, duplicate pending targets, terminal invitations, completed Interns, removed requesters, closed Projects, and unrelated actors. `ProjectLifecycleLockIntegrationTest` uses independent transactions, real `FOR UPDATE` probes, lock waits, and stale routing/account-state and notification-recipient races to verify Account and Intern-profile lock retention, recipient-before-Project ordering, and post-lock authorization through an outer Project mutation. Its historical-requester regression builds the reachable state through public services: a Leader requests another member's removal, the Mentor removes that Leader while leaving the request pending, and the Mentor later decides the retained request while the former requester's Account is held.

## Hand-derived expected result

One current invitation exists per Project/Intern pair. Acceptance creates one membership whose `added_by_user_id` and invitation resolver are the accepting Intern; direct Mentor addition creates one membership and resolves the invitation as `SUPERSEDED`/`MENTOR_DIRECT_ADD`. A leadership-term change resolves that term's pending invitations as `REVOKED`/`LEADER_CHANGED`. Pending exit requests leave `left_at` null, appear as the target membership ID in the Task DTO context, and leave the member's joined/left view as joined/non-left; only the current requester may cancel them. Completed Interns and completed Projects are read-only, and an unrelated actor cannot distinguish completed/open lifecycle state through exit mutations.

For notifications, creation yields exactly the invitee; accept/decline yields the issuing Leader and owning Mentor; revocation/supersession yields invitee, issuing Leader, and owning Mentor; Leader-removal requests yield Mentor and target; member-leave requests yield Mentor and current Leader; and each resolution/cancellation yields requester, target, and current Leader once per account.

## RED

**Commands and observed results**

The initial production-shaped test suite proved the required behavior was absent:

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=ProjectInvitationExitIntegrationTest test

[ERROR] cannot find symbol: method issueInvitation(long,long,long)
[ERROR] cannot find symbol: method respondToInvitation(long,long,InvitationResponse)
[ERROR] cannot find symbol: method requestMemberRemoval(long,long,long,String)
[ERROR] Tests were not executable because the required Project service behavior was absent.
```

Review regressions were isolated against PostgreSQL 18.4. Before actor authorization was restored,
a wrong actor received terminal-state `ProjectRuleViolationException` instead of
`ProjectAccessDeniedException`; with the completed-Intern guard temporarily removed,
`completedInternCannotCreateOrCancelExitRequests` failed because no denial was thrown. Before the
reviewed locked Account consumer, the lock-retention test failed at the Account `FOR UPDATE` timeout
assertion (`Expecting code to raise a throwable`, 1 test, BUILD FAILURE).

The following additional REDs were captured before their fixes:

```text
./mvnw -Dtest='ProjectInvitationExitIntegrationTest#intervalProjectionDoesNotPartiallyHydrateAProjectUsedByTheNextTaskContextRead' test
[ERROR] expected active member IDs [2, 3] but was [2]

./mvnw -Dtest='ProjectLifecycleLockIntegrationTest#staleRoutingAndAccountReadsCannotIssueAfterLeadershipAndAccountStateChange' test
[ERROR] ObjectOptimisticLockingFailureException: Query result contains conflicting version of entity already held in persistence context for ProjectEntity id 1

./mvnw -Dtest=ProjectInvitationExitIntegrationTest#completedCurrentLeaderCannotIssueAnInvitation+ProjectInvitationExitIntegrationTest#removedFormerRequesterCannotCancelItsPendingExitRequest+ProjectInvitationExitIntegrationTest#pendingExitCannotBeCancelledAfterProjectCompletion test
[ERROR] Tests run: 3, Failures: 3, Errors: 0
[ERROR] completed leader expected ProjectAccessDeniedException but nothing was thrown
[ERROR] removed requester expected ProjectAccessDeniedException but nothing was thrown
[ERROR] closed Project expected ProjectRuleViolationException but nothing was thrown

./mvnw -Dtest='ProjectInvitationExitIntegrationTest#unrelatedActorCannotLearnCompletedProjectStateThroughExitMutations' test
[ERROR] Unexpected exception type: expected ProjectAccessDeniedException but was ProjectRuleViolationException
[ERROR] Caused by: ProjectRuleViolationException: Completed Projects are read-only
[ERROR] at ProjectService.requestOwnLeave(ProjectService.java:364)
```

Before adding scalar invitation-route actor preauthorization, the interaction regression below
failed because a mismatched actor reached Account-lock snapshot construction instead of being
denied before any Account/profile or Project lock:

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$PATH ./mvnw -Dtest='ProjectTaskMutationContextTest#rejectsInvitationActorMismatchBeforeTakingLifecycleOrProjectLocks' test
[ERROR] Unexpected exception type: expected ProjectAccessDeniedException but was java.lang.IllegalStateException
[ERROR] Caused by: Locked Account snapshot is missing
```

The regression was then fixed by comparing the authenticated actor with the scalar invitation
route's invited Intern ID before Account locking, while retaining the authoritative post-lock
invitation-row comparison.

The interval and stale-routing REDs were produced by temporarily restoring the rejected
implementations and then removed; they are review artifacts, not acceptance claims. The final RED
above was produced by temporarily restoring the pre-fix `requireOpenProject` ordering and then
restored. No RED was fabricated. The attempted proactive invitation sweep/`updated_at` cursor was
also rejected and removed because it corrupted lifecycle metadata and wrote unchanged rows.
PRJ-019/AC-PRJ-011 proactive pending-invitation cleanup remains incomplete until the reviewed
Platform lifecycle boundary is available.

The notification consumer RED was then captured before adding the Project calls to the reviewed
Platform service:

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/local/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -Dtest=ProjectInvitationExitIntegrationTest test
[ERROR] Tests run: 22, Failures: 3, Errors: 0, Skipped: 0
[ERROR] membershipExitNotificationsUseRequestTypeAndCollapseDecisionRecipients expected recipient rows but was []
[ERROR] invitationResponseRetainsTerminalDecisionAndLeaderCanRevokeOnlyOwnPendingInvitation expected recipient rows but was []
[ERROR] leaderInvitationAcceptsOnlyTheIntendedInternAndMentorDirectAddSupersedesIt expected recipient rows but was []
[INFO] BUILD FAILURE
```

The three failures were the expected missing Project notification behavior; the Project state
assertions and PostgreSQL fixture were otherwise executable. Account IDs varied with the isolated
container sequence, so the evidence records the stable empty-row failure rather than a generated
identifier.

The NOT-002 membership/leadership consumer RED was then captured before adding those two event
families to the Project mutation paths:

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/usr/local/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=ProjectInvitationExitIntegrationTest test
[ERROR] Tests run: 22, Failures: 6, Errors: 0, Skipped: 0
[ERROR] initial membership/leadership, direct-add, invitation-acceptance, explicit leader-change, exit approval, direct Leader removal, and completion recipient assertions observed empty rows
[INFO] BUILD FAILURE
```

The six failures were expected missing `MEMBERSHIP_CHANGED`/`LEADERSHIP_CHANGED` rows; the
existing invitation/exit assertions remained green. The first unprivileged attempt was rejected by
the OrbStack socket policy and is not counted as RED; the command above is the elevated PostgreSQL
18.4 Testcontainers result.

After those notification consumers were added, the affected PostgreSQL suite exposed a real
recipient-lock ordering regression:

```text
git diff --check && JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/local/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest='Project*Test' test
[ERROR] Tests run: 80, Failures: 0, Errors: 1, Skipped: 0
[ERROR] ProjectLifecycleLockIntegrationTest.staleRoutingAndAccountReadsCannotIssueAfterLeadershipAndAccountStateChange
[ERROR] PostgreSQL PessimisticLockingFailureException: deadlock detected at ProjectLifecycleLockIntegrationTest.java:138
[INFO] BUILD FAILURE
```

The failure was the expected old implementation hazard: notification recipient Account rows were
resolved after the Project lock on a leadership transition, while another transaction held the
recipient Account row. The fix moved scalar recipient routing and ascending Account/profile locks
before the Project lock, retained the post-lock recipient-set recheck, and added the focused
`leaderChangeLocksNotificationRecipientsBeforeProject` regression. The stale-routing regression
was retained and still proves that a stale Leader/account state cannot issue an invitation.

The follow-up Mentor-recipient regression then exposed the same ordering hazard on the member-leave
path: the owning Mentor was a notification recipient but was not included in the pre-Project lock
set. Before the fix, a held Mentor Account row blocked the mutation while a separate Project lock
probe timed out, proving that the Project row had been acquired first:

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/local/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest='ProjectLifecycleLockIntegrationTest#exitRequestLocksMentorNotificationRecipientBeforeProject' test
[ERROR] Tests run: 1, Failures: 0, Errors: 1, Skipped: 0
[ERROR] java.util.concurrent.TimeoutException at ProjectLifecycleLockIntegrationTest.java:195 from projectProbe.get(1, TimeUnit.SECONDS)
[INFO] BUILD FAILURE
```

This RED was the expected pre-fix behavior, not a fixture or environment failure. The request
paths now include the owning Mentor in the ascending Account/profile lock set before the Project
lock. The regression holds that Mentor Account, proves the independent Project `FOR UPDATE` probe
still succeeds while the mutation waits, then releases the Account and verifies the request and
both `MEMBERSHIP_EXIT_REQUESTED` recipient rows.

Independent review then exposed a reachable retained-requester variant: a former Leader could
remain the requester of another member's pending exit after direct removal, so an exit decision
could acquire the Project lock and then block while resolving that historical Account. Temporarily
restoring that post-Project resolver produced the expected real PostgreSQL lock failure:

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=ProjectLifecycleLockIntegrationTest#exitDecisionLocksHistoricalRequesterBeforeProject test
[ERROR] Tests run: 1, Failures: 0, Errors: 1, Skipped: 0
[ERROR] java.util.concurrent.TimeoutException at ProjectLifecycleLockIntegrationTest.java:311
[INFO] BUILD FAILURE
```

The final boundary scalar-selects every pending requester, locks all recipient Accounts/profiles in
ascending order, resolves immutable `NotificationRecipient` facts, and only then acquires the
Project lock. The unchanged regression passed 1/1 after restoring that ordering.

The same review found that Project History dropped already-retained membership and leadership
actor IDs. Temporarily replacing only the history mapping with zero/null proved the new assertion
detects that regression:

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=ProjectInvitationExitIntegrationTest#projectHistoryExposesStoredMembershipAndLeadershipProvenance test
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
[ERROR] ProjectInvitationExitIntegrationTest.java:670 expected: <1> but was: <0>
[INFO] BUILD FAILURE
```

Restoring the direct stored-field mapping made the identical focused PostgreSQL command pass 1/1.

## GREEN

**Command and result**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=ProjectInvitationExitIntegrationTest,ProjectLifecycleLockIntegrationTest test

[INFO] Tests run: 28, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

The three notification-focused methods each passed 1/1 in their focused GREEN runs. The focused
`leaderChangeLocksNotificationRecipientsBeforeProject` and
`exitRequestLocksMentorNotificationRecipientBeforeProject` PostgreSQL regressions each passed 1/1:
while the outgoing-Leader or owning-Mentor Account was held with `FOR UPDATE`, the corresponding
Project mutation waited for that Account and an independent Project `FOR UPDATE` probe still
succeeded; after release, the transition completed and the expected notification rows persisted.
The focused classes pass 23/23 for invitation/exit/history behavior and 5/5 for lifecycle locks,
and also cover immutable interval projection,
same-transaction context integrity, stale
route/account-state denial, completed-Leader denial, completed-Intern read-only behavior,
removed-requester denial, closed-Project denial, unrelated-actor denial before terminal-state
checks, Account/profile lock retention, historical-requester-before-Project ordering, stored
membership/leadership provenance, repeated Task transfer batches, direct Mentor removal,
Project completion, and retained-history visibility. Response-time eligibility is rechecked
immediately before invitation acceptance. No proactive invitation cleanup or rotation is claimed.

## Affected suite

**Command and result**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/opt/homebrew/opt/openjdk@25/bin:/usr/local/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest='Project*Test,TaskProjectQueryTest' test

[INFO] Tests run: 86, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## External-test boundaries

The tests prove Project-owned invitation/exit persistence and authorization against PostgreSQL
18.4, the consumed Platform Account-owned lock contract, stale lifecycle rechecks, Task-backed
transfer/count/progress/history orchestration through DTO/service boundaries, and the Project
in-app notification recipient/action mapping. Detailed Task eligibility/batch/history semantics
remain Task-owned. The exact reviewed Task notification producer
`265fdab44091d378fe023146f2b60f4ef0f39125` is now present in the merged consumer tree; its
Task-owned transfer/direct-removal notification rows remain owned and tested by Task, rather than
duplicated in this Project evidence. Platform's notification integration evidence proves mandatory
transaction behavior and SMTP-absent `UNAVAILABLE` handling; this Project class does not re-test
email delivery, retry timing, browser pages, or UI action routes. No Platform callback into Project,
email bearer token, invitation sweep/rotation, generic audit store, or new schema object is used.

## Full branch verification

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/opt/homebrew/opt/openjdk@25/bin:/usr/local/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar test
[INFO] Tests run: 336, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

`./mvnw -Dtest=LayerStructureTest,AttendanceLayerStructureTest,ReportingArchitectureTest,ProjectPersistenceStructureTest,TaskPersistenceStructureTest test`
passed 11/11, and the focused Project/Task/Reporting persistence command passed 7/7. `./mvnw
-DskipTests compile` and `./mvnw -DskipTests -Ddoclint=all javadoc:javadoc` both passed. The full
run used Java 25 and PostgreSQL 18.4 Testcontainers through OrbStack; no schema migration was
added.
