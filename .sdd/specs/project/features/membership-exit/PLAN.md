# Membership Exit and Transfer Plan

**Owner:** Loc-LX · technical design for the [Membership exit and transfer SPEC](SPEC.md). Progress belongs in [`plan.md`](../../../../../plan.md); tasks are in [TASKS.md](TASKS.md). This draft inherits the module boundaries and constraints of [`project/MODULE.md`](../../MODULE.md). Where this plan and a SPEC disagree, the SPEC wins.

| Part | Subject | Rules | State |
|---|---|---|---|
| MX | Membership exit traceability | `AC-PRJ-005`, `AC-AUTH-007` | Drafted on 30 September 2026 against current code |

The rule `AC-AUTH-007` is defined in [`project/MODULE.md`](../../MODULE.md).

## Part MX — Membership exit traceability

This part establishes traceability for membership departure, direct mentor removal, access boundary termination, and post-completion historical read access. It identifies candidate tests and required evidence without altering production behavior or schema definitions.

### What the current code offers

The status below is based on the implementation at the draft's starting revision. It is a rule-by-rule scope inventory, not a claim that existing code or tests fully satisfy each rule.

| Rule | Current implementation and evidence | Planned outcome |
|---|---|---|
| `AC-PRJ-005` | Mentor removes ordinary member with no unfinished Tasks; interval closes, active access lost without deleting history: `ProjectService.java:1150-1220`, `ProjectMembershipEntity.java:70-125`. Candidate tests to confirm in the task: `ProjectInvitationExitIntegrationTest#directMentorRemovalTransfersAllUnfinishedTasksAtomicallyAndKeepsDoneAssignment`, `ProjectInvitationExitIntegrationTest#directMentorRemovalRejectsWorkedUnfinishedTasksBeforeAnyMutation`. | Confirm candidate tests demonstrate interval closure, immediate loss of active access, and retention of historical memberships and completed task attributions; cite `AC-PRJ-005` in test Javadoc. |
| `AC-AUTH-007` | Removed member denied open Project access; after completion/cancellation receives authorized read-only history, no mutations: `ProjectAuthorizationRequests.java:89-112`, `ProjectController.java:200-240`. Candidate tests to confirm in the task: `ProjectInvitationExitIntegrationTest#removedMemberCannotReadOpenHistoryButReadsRetainedHistoryAfterCompletion`. | Confirm removed members cannot view or mutate an open active project, but can access read-only history once the project is completed or cancelled; cite `AC-AUTH-007` in test Javadoc. |

### Order of work

1. **MX-01 — Membership exit traceability.** Review candidate tests for `AC-PRJ-005` and `AC-AUTH-007`. Attach rule IDs to test Javadocs where assertions prove the rule. Implement targeted test scenarios where coverage gaps exist without weakening assertions.

### Tests

Attach rule IDs to the Javadoc of existing candidate tests when the Given/When/Expected structure proves the corresponding rule per `TST-005`. Write new focused tests only where candidate tests leave an acceptance scenario unproved. Never weaken or delete existing assertions. Tests run against PostgreSQL via Testcontainers.

### When done

- Every rule in `AC-PRJ-005` and `AC-AUTH-007` is cited in the Javadoc of at least one passing test that proves it.
- Focused integration tests, full `./mvnw test`, UI test suite, and disposable E2E suite pass cleanly.

### Risks

| Risk | Handling |
|---|---|
| Historical retention vs access denial verification in candidate tests | Verify that test assertions separately confirm denial during active state and read-only access after terminal transition. |
| Test does not verify interval timestamp boundaries | Confirm assertions inspect membership `closed_at` timestamps and attribution details. |

### Not in this part

No production code changes, no Flyway schema migrations, and no dependency additions.
