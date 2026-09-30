# Project Invitations Plan

**Owner:** Loc-LX · technical design for the [Project invitations SPEC](SPEC.md). Progress belongs in [`plan.md`](../../../../../plan.md); tasks are in [TASKS.md](TASKS.md). This draft inherits the module boundaries and constraints of [`project/MODULE.md`](../../MODULE.md). Where this plan and a SPEC disagree, the SPEC wins.

| Part | Subject | Rules | State |
|---|---|---|---|
| IN | Project invitations traceability | `PRJ-019`, `AC-PRJ-010`, `AC-PRJ-011`, `NOT-010`, `AC-AUTH-010`, `AC-DB-002` | Drafted on 30 September 2026 against current code |

The rules `NOT-010`, `AC-AUTH-010`, and `AC-DB-002` are defined in [`project/MODULE.md`](../../MODULE.md).

## Part IN — Project invitations traceability

This part establishes traceability for project invitation issuance, acceptance/rejection semantics, revocation, notifications, authorization bounds, and database integrity. It identifies candidate tests and required evidence without modifying production behavior or database schema.

### What the current code offers

The status below is based on the implementation at the draft's starting revision. It is a rule-by-rule scope inventory, not a claim that existing code or tests fully satisfy each rule.

| Rule | Current implementation and evidence | Planned outcome |
|---|---|---|
| `PRJ-019` | Only intended Intern accepts/declines; acceptance rechecks state and creates membership atomically; Leader revokes own, Mentor revokes any; leadership end/completion/ineligibility revokes pending invite: `ProjectService.java:700-850`, `ProjectInvitationEntity.java:99-195`. Candidate tests to confirm in the task: `ProjectInvitationExitIntegrationTest#leaderInvitationAcceptsOnlyTheIntendedInternAndMentorDirectAddSupersedesIt`, `ProjectInvitationExitIntegrationTest#invitationResponseRetainsTerminalDecisionAndLeaderCanRevokeOnlyOwnPendingInvitation`. | Confirm candidate tests prove intended invitee identity, atomic membership creation, revocation permissions, and automatic revocation triggers; cite `PRJ-019` in test Javadoc. |
| `AC-PRJ-010` | Leader invites, Intern accepts while Mentor direct-add races; one active membership commits, winning is ACCEPTED/SUPERSEDED, losing returns conflict: `ProjectService.java:95-125`, `750-810`. Candidate tests to confirm in the task: `ProjectLifecycleLockIntegrationTest#invitationAcceptanceAndMentorDirectAddHaveOneCommittedWinner`, `ProjectInvitationExitIntegrationTest#leaderInvitationAcceptsOnlyTheIntendedInternAndMentorDirectAddSupersedesIt`. | Confirm concurrency controls commit exactly one active membership and transition the invitation to `ACCEPTED` or `SUPERSEDED`; cite `AC-PRJ-010` in test Javadoc. |
| `AC-PRJ-011` | Decline, revoke, leadership change, completion, ineligibility transition invitations to correct terminal status without deleting history: `ProjectService.java:720-770`, `820-855`. Candidate tests to confirm in the task: `ProjectInvitationExitIntegrationTest#invitationResponseRetainsTerminalDecisionAndLeaderCanRevokeOnlyOwnPendingInvitation`, `ProjectInvitationExitIntegrationTest#pendingInvitationIsRevokedWhenInviteeBecomesIneligibleBeforeResponse`, `ProjectInvitationExitIntegrationTest#owningMentorMayRevokeAnyPendingInvitationAndLeaderChangeRevokesTheOldTermQueue`. | Confirm terminal statuses preserve historical records without physical deletion across all revocation triggers; cite `AC-PRJ-011` in test Javadoc. |
| `NOT-010` | Notifications on create, answer, revoke/supersede (collapsing duplicates), removal request, member leave, decision/cancellation; self-Task is silent: `ProjectService.java:710-745`, `770-810`, `830-865`, `1250-1320`. Candidate tests to confirm in the task: `ProjectInvitationExitIntegrationTest#membershipExitNotificationsUseRequestTypeAndCollapseDecisionRecipients`, `ProjectInvitationExitIntegrationTest#leaderInvitationAcceptsOnlyTheIntendedInternAndMentorDirectAddSupersedesIt`. | Confirm notifications emit for invitation lifecycle transitions and collapse duplicate recipients; cite `NOT-010` in test Javadoc. |
| `AC-AUTH-010` | Stale former Leader or unrelated member submits invitation/exit/self-Task/Task-management; authorization re-evaluated inside transaction, denied without leakage or partial mutation: `ProjectService.java:715-740`, `TaskService.java:140-165`. Candidate tests to confirm in the task: `ProjectInvitationExitIntegrationTest#acAuth009ProjectRoleContextOperationMatrixDeniesWithoutStateDisclosureOrMutation`, `ProjectInvitationExitIntegrationTest#nestedWorkflowRoutesRejectForeignProjectIdsWithoutChangingRetainedState`. | Confirm transactional re-evaluation of authorization prevents stale mutations or data leakage; cite `AC-AUTH-010` in test Javadoc. |
| `AC-DB-002` | SQL probes attempt duplicate pending invitations/exits, cross-Project references, invalid participants, unsupported resolution combinations; PostgreSQL rejects: `V1__baseline.sql:80-120`, `V3__platform_schema_expansion.sql:40-70`. Candidate tests to confirm in the task: `ProjectInvitationExitIntegrationTest#aSecondPendingInvitationForTheSameInternIsRefused`, `SchemaExpansionProbeIntegrationTest`. | Confirm database schema enforces uniqueness, foreign key validity, and status constraints against invalid states; cite `AC-DB-002` in test Javadoc. |

### Order of work

1. **IN-01 — Project invitations traceability.** Review candidate tests for `PRJ-019`, `AC-PRJ-010`, `AC-PRJ-011`, `NOT-010`, `AC-AUTH-010`, and `AC-DB-002`. Attach rule IDs to test Javadocs where assertions demonstrate the requirement. Write targeted tests if any scenario lacks definitive proof.

### Tests

Attach rule IDs to the Javadoc of existing candidate tests when their assertions prove the rule per `TST-005`. Write new tests only when existing coverage does not fully assert the acceptance criteria. Never weaken or delete assertions. PostgreSQL-backed via Testcontainers.

### When done

- Every rule in `PRJ-019`, `AC-PRJ-010`, `AC-PRJ-011`, `NOT-010`, `AC-AUTH-010`, and `AC-DB-002` is named in the Javadoc of at least one passing test that proves it.
- Focused integration tests, full `./mvnw test`, UI test suite, and disposable E2E suite pass cleanly.

### Risks

| Risk | Handling |
|---|---|
| Candidate test only covers happy path without exercising boundary/concurrency conditions | Verify concurrency race assertions and database refusal tests explicitly before citing. |
| Incorrect requirement attribution in test Javadoc | Ensure hand-derived expected values and observable break match the specific requirement. |

### Not in this part

No production code changes, no Flyway schema migrations, and no dependency additions.
