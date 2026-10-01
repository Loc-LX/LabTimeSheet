# Membership and Leadership Plan

**Owner:** Loc-LX · technical design for the [Membership and leadership SPEC](SPEC.md). Progress belongs in [`plan.md`](../../../../../plan.md); tasks are in [TASKS.md](TASKS.md). This draft inherits the module boundaries and constraints of [`project/MODULE.md`](../../MODULE.md). Where this plan and a SPEC disagree, the SPEC wins.

| Part | Subject | Rules | State |
|---|---|---|---|
| ML | Membership and leadership traceability | `PRJ-004`, `AC-PRJ-001`, `AC-PRJ-002`, `AUTH-004` | Drafted on 30 September 2026 against current code |

The rule `AUTH-004` is defined in [`project/MODULE.md`](../../MODULE.md).

## Part ML — Membership and leadership traceability

This part establishes traceability for membership intervals, leadership appointment, role constraints, and governance authority. It identifies candidate tests and required evidence without altering production behavior or schema definitions.

### What the current code offers

The status below is based on the implementation at the draft's starting revision. It is a rule-by-rule scope inventory, not a claim that existing code or tests fully satisfy each rule.

| Rule | Current implementation and evidence | Planned outcome |
|---|---|---|
| `PRJ-004` | Owning Mentor adds/removes directly, decides exits; Leader invites, requests removal, redistributes Tasks; Intern joins by accepting invite, leaves after approval: `ProjectService.java:95-125` (addMember), `700-750` (invite), `1050-1120` (remove/exit). Candidate tests to confirm in the task: `ProjectServiceIntegrationTest#ownerAddsEligibleMemberAndDuplicateCurrentMembershipIsRejected`, `ProjectInvitationExitIntegrationTest#leaderInvitationAcceptsOnlyTheIntendedInternAndMentorDirectAddSupersedesIt`. | Confirm candidate tests demonstrate each actor's authority; cite `PRJ-004` in test Javadoc with observable break and expected result; add focused tests if coverage is incomplete. |
| `AC-PRJ-001` | Same Intern in two Projects and Leader of one; both coexist; leadership affects selected Project only: `ProjectService.java:95-128`, `ProjectMembershipEntity.java:60-95`. Candidate tests to confirm in the task: `ProjectInvitationExitIntegrationTest#internMembershipIntervalsRetainCurrentAndClosedRowsAcrossProjects`, `ProjectServiceIntegrationTest#ownerAddsEligibleMemberAndDuplicateCurrentMembershipIsRejected`. | Confirm candidate tests prove multi-project membership coexistence and isolated leadership scope; cite `AC-PRJ-001` in test Javadoc. |
| `AC-PRJ-002` | Two transactions appoint different Leaders; database/transaction rules allow exactly one current term: `ProjectService.java:170-210`, `V1__baseline.sql:60-75`. Candidate tests to confirm in the task: `ProjectLifecycleLockIntegrationTest#concurrentLeadershipChangesHaveOneWinnerFromTheSameLeaderSnapshot`, `ProjectServiceIntegrationTest#leaderChangeClosesOneTermAndDoesNotMoveTaskAssignments`. | Confirm concurrency locking and database unique constraint permit exactly one active term; cite `AC-PRJ-002` in test Javadoc. |
| `AUTH-004` | Term grants Leader permissions; withdraws Task-management when term ends; pending Leader exit requires Mentor replacement before transfer; Tasks not moved merely because leadership changed: `ProjectService.java:170-215`, `TaskService.java:215-245`. Candidate tests to confirm in the task: `ProjectServiceIntegrationTest#formerLeaderLosesTaskManagementControlsAfterLeaderChange`, `ProjectInvitationExitIntegrationTest#leaderExitRequiresReplacementAndLeaderChangeDoesNotMoveAssignments`. | Confirm permissions withdrawal on term end, pending exit replacement requirement, and preservation of Task assignments; cite `AUTH-004` in test Javadoc. |

### Order of work

1. **ML-01 — Membership and leadership traceability.** Review candidate tests for `PRJ-004`, `AC-PRJ-001`, `AC-PRJ-002`, and `AUTH-004`. Attach rule IDs to test Javadocs where assertions prove the rule. Implement targeted test scenarios where coverage gaps exist without weakening assertions.

### Tests

Attach rule IDs to the Javadoc of existing candidate tests when the Given/When/Expected structure proves the corresponding rule per `TST-005`. Write new focused tests only where candidate tests leave an acceptance scenario unproved. Never weaken or delete existing assertions. Tests run against PostgreSQL via Testcontainers.

### When done

- Every rule in `PRJ-004`, `AC-PRJ-001`, `AC-PRJ-002`, and `AUTH-004` is cited in the Javadoc of at least one passing test that proves it.
- Focused integration tests, full `./mvnw test`, UI test suite, and disposable E2E suite pass cleanly.

### Risks

| Risk | Handling |
|---|---|
| Candidate test only partially covers the acceptance scenario | Inspect exact assertions before citing; supplement with focused test cases if edge conditions or invariants are untested. |
| False traceability citation without assertion verification | Verify that test failures directly observe rule breaks and that expected values are hand-derived before adding rule identifiers. |

### Not in this part

No production code changes, no Flyway schema migrations, and no dependency additions.
