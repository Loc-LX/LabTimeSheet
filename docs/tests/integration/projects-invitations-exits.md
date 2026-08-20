# Test Evidence: Project invitations and pending membership exits

- **Test type:** Integration
- **Requirement IDs:** `AUTH-001`, `AUTH-002`, `AUTH-011`, `PRJ-017`, `PRJ-018`, `PRJ-020`, and the request/cancel/interval-retention slice of `PRJ-021`
- **Scenario IDs:** `AC-AUTH-010`; the decline/revoke/ineligible-response slice of `AC-PRJ-011`
- **Test class/method:** `com.lab.labtimesheet.feature.project.service.ProjectInvitationExitIntegrationTest`, `ProjectLifecycleLockIntegrationTest`
- **Implementation commit:** `pending`

## Protected behavior

Project invitations are non-expiring, tied to the issuing leadership term, and answerable only by the intended authenticated Intern. Mentor direct-add supersedes a matching pending invitation; leader-owned and Mentor-owned revocation retain distinct codes. Pending membership exits retain the membership interval and existing rights, enforce the request participant shape, and can be cancelled only by a current, eligible requester while the Project is open. Authorization is established before terminal-state evaluation, and every lifecycle mutation locks Account rows and Intern profiles in ascending order before the Project row. The DTO-only Task context exposes current Leader, joined-at member intervals, and pending target membership IDs while the Project write lock is held. The immutable `ProjectMembershipIntervalView` query retains current and closed membership intervals across Projects without hydrating a filtered Project aggregate.

## Test method

The Spring Boot integration tests create active account/internship fixtures and Project aggregates through the public Project service. PostgreSQL 18.4 assertions inspect only retained domain fields: invitation status/code/term/membership provenance, pending membership intervals, and exit request resolution. They read pending target IDs from both the locked `ProjectService.taskMutationContext` handoff and the read-only `ProjectQueryService.taskContext` DTO, then verify joined-at/left-at through `ProjectTaskMemberView` and `ProjectMembershipIntervalView`. The interval regression reads retained membership projections and then reads the same Project context in one transaction to prove no filtered fetch-join aggregate is left in the persistence context. Negative cases use wrong actors, blank reasons, duplicate pending targets, terminal invitations, completed Interns, removed requesters, closed Projects, and unrelated actors. `ProjectLifecycleLockIntegrationTest` uses independent transactions, real `FOR UPDATE` probes, lock waits, and a stale routing/account-state race to verify Account and Intern-profile lock retention and post-lock authorization through an outer Project mutation.

## Hand-derived expected result

One current invitation exists per Project/Intern pair. Acceptance creates one membership whose `added_by_user_id` and invitation resolver are the accepting Intern; direct Mentor addition creates one membership and resolves the invitation as `SUPERSEDED`/`MENTOR_DIRECT_ADD`. A leadership-term change resolves that term's pending invitations as `REVOKED`/`LEADER_CHANGED`. Pending exit requests leave `left_at` null, appear as the target membership ID in the Task DTO context, and leave the member's joined/left view as joined/non-left; only the current requester may cancel them. Completed Interns and completed Projects are read-only, and an unrelated actor cannot distinguish completed/open lifecycle state through exit mutations.

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

## GREEN

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=ProjectTaskMutationContextTest,ProjectInvitationExitIntegrationTest,ProjectLifecycleLockIntegrationTest test

[INFO] Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

This focused green run covers immutable interval projection, same-transaction context integrity,
stale route/account-state denial, completed-Leader denial, completed-Intern read-only behavior,
removed-requester denial, closed-Project denial, unrelated-actor denial before terminal-state
checks, and Account/profile lock retention. The focused Project invitation/lifecycle classes alone
pass 16/16; the DTO/lock-order unit class contributes 4/4, including the invitation actor
preauthorization interaction check. Response-time eligibility is rechecked immediately before invitation acceptance. No
proactive invitation cleanup or rotation is claimed.

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest='Project*Test' test

[INFO] Tests run: 71, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## External-test boundaries

The tests prove Project-owned invitation and pending-exit persistence and authorization against
PostgreSQL 18.4, including the consumed Platform Account-owned lock contract and stale lifecycle
rechecks. They do not prove proactive pending-invitation cleanup, ordinary notification delivery,
pending-target Task assignment exclusion, assisted transfer batches, direct-removal transfer,
Mentor exit approval/rejection, Project completion orchestration, browser pages, or the Task-owned
retained-history projection; those require the reviewed Task/notification service contracts. No
Platform callback into Project, email bearer token, invitation sweep/rotation, or new schema object
is used.

## Full branch verification

```text
./mvnw test
[INFO] Tests run: 263, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

`./mvnw -Dtest=LayerStructureTest,AttendanceLayerStructureTest,ReportingArchitectureTest,ProjectPersistenceStructureTest,TaskPersistenceStructureTest test`
passed 10/10. `./mvnw -DskipTests compile` and
`./mvnw -DskipTests -Ddoclint=all javadoc:javadoc` both passed. The full run used Java 25 and
PostgreSQL 18.4 Testcontainers through OrbStack; no schema migration was added.
