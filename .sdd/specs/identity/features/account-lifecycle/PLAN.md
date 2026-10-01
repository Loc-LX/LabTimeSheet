# Account lifecycle Plan

**Version:** 1.0 · **Owner:** Loc-LX · **Status:** APPROVED · **Date:** 2026-09-26

This is a technical design for [SPEC.md](SPEC.md), not a progress tracker. Progress belongs in
[`plan.md`](../../../../../plan.md). It inherits the shared identity contract in
[`MODULE.md`](../../MODULE.md); it does not copy or replace that contract. Tasks are in
[TASKS.md](TASKS.md). The maintainer approved it on 26 September 2026.

| Scope | Rules | State |
|---|---|---|
| Deactivation that keeps the lock, pending-account deactivation, and reinstatement | `ACC-016` (lock kept on deactivation), `ACC-028`, `ACC-029`; `DB-022` through C-04; related `ACC-014`, `ACC-018`, `ACC-024` | Approved on 26 September 2026 |

## 1. Goal and boundary

Implement the already specified account lifecycle edges for deactivation and reinstatement. A pending
account can be deactivated without gaining credentials; a deactivated account can be reinstated to
the state allowed by its stored activation and lock history. Existing identity, role, attribution,
session and token rules remain in force. The acceptance boundaries are `AC-ACC-020` and
`AC-ACC-021`.

This plan does not define a new lifecycle rule or schema. The identity contract migration is task
C-04 in the [data-model plan](../../../platform/features/data-model/PLAN.md). The C-04 plan owns
`DB-022` and `AC-DB-009`; this feature's code ships with that contract as C.4 requires. The required
`DB-022` shapes are: `PENDING_ACTIVATION` has neither password hash nor activation timestamp;
`ACTIVE` and `LOCKED` have both; `DEACTIVATED` has both or neither. A lock timestamp is required
for `LOCKED`, is allowed for `DEACTIVATED` only beside an activation timestamp, and is otherwise
absent; a deactivation timestamp exists exactly for `DEACTIVATED`. An activation timestamp may be
set only on `PENDING_ACTIVATION → ACTIVE` and may never later change or clear. A lock timestamp may
be set only on `ACTIVE → LOCKED`, cleared only on `LOCKED → ACTIVE`, and otherwise remains
unchanged. C-04 owns the trigger implementation for both timestamp rules, which compares old and new values with `IS DISTINCT FROM`. Keeping the lock on deactivation is application code that this plan owns (`ACC-016`, section 3.1), because C-04's lock rule refuses the cleared timestamp that `AppUser#deactivate` writes today. This plan points to C-04 as the authority for the migration and does not restate its design.

## 2. Constraints and dependencies

1. The account state transitions and their outcomes are exactly those in `ACC-014`, `ACC-016`,
   `ACC-028`, and `ACC-029`; acceptance details remain in the linked SPEC scenarios.
2. Pending deactivation invalidates every unused activation and password-reset token and leaves the
   account without a password hash or activation timestamp (`AC-ACC-020`).
3. Reinstatement uses the retained activation and lock state. It does not restore an internship,
   Project membership, leadership term, or a withdrawn internship (`AC-ACC-021`).
4. A lock timestamp carried through deactivation survives and reinstates as `LOCKED`. Existing
   deactivated rows whose old lock timestamp was cleared cannot regain that history (`D38`).
5. Account-state changes continue to invalidate authenticated sessions under `ACC-018`; no access
   to another feature's repository or entity is introduced (`ARC-006`).

## 3. Implementation order

Every code task starts with the smallest PostgreSQL-backed or application-boundary test for its
acceptance behavior, run RED for the expected reason. Name the protected rule IDs and hand-derived
expected result in test Javadoc (`TST-001`–`TST-010`). Before editing each production symbol, run
GitNexus impact analysis; treat UNKNOWN as unresolved and confirm callers with text search. Do not
weaken existing assertions.

### 3.1 Keep the lock when a locked account is deactivated

`AppUser#deactivate` clears the lock timestamp today. Write the focused test first: deactivating a
`LOCKED` account keeps its lock timestamp. See it fail on the current code, then remove the clearing
and nothing else (`ACC-016`). Reinstatement to `LOCKED` in section 3.3 depends on it.

### 3.2 Deactivate a pending account

Write the focused case from `AC-ACC-020` first and see it fail because the specified transition is
not implemented. Then implement `ACC-028`: deactivate the pending account, invalidate its unused
activation and reset tokens, preserve identity/role/attribution, and create neither credentials nor
an activation timestamp. Verify the token links and later reset request are refused as the scenario
specifies.

### 3.3 Reinstate a deactivated account

Write the three-state scenario from `AC-ACC-021` first and see the missing or incorrect transition
fail. Then implement `ACC-029` for the activated-unlocked, previously locked, and never-activated
shapes. Verify existing credentials are reused where allowed, a carried lock remains effective,
pending activation requires explicit resend, and no internship or Project history is restored.

### 3.4 Ship with the C-04 contract

Coordinate both lifecycle changes with data-model C-04. Its `AC-DB-009` probes must demonstrate the
database boundary, including trigger behavior, while `AC-ACC-020` and `AC-ACC-021` demonstrate the
application behavior. Do not create a separate migration or duplicate C-04's schema specification
in this plan.

## 4. Verification and completion

Run each focused test after its implementation, then the identity feature/module suite and full
`./mvnw test`. Run `npm run test:ui` and `git diff --check`. The coordinated C-04 change is complete
only when the lifecycle acceptance scenarios and C-04's schema probes pass, existing assertions
remain intact, and the full required suites pass. Record actual commands and resolved tool versions
in `plan.md`.

## 5. Risks

| Risk | Handling |
|---|---|
| A previously locked account loses its lock during deactivation | Keep the timestamp as C-04 requires; prove reinstatement to `LOCKED` through `AC-ACC-021` |
| A pending account's unused link remains usable | Exercise both token purposes after deactivation under `AC-ACC-020` |
| Reinstatement restores unrelated internship or Project state | Assert those histories remain unchanged under `AC-ACC-021` |
| Application and database lifecycle rules drift | Ship application code with C-04 and run both application scenarios and `AC-DB-009` |

## 6. Not in this part

Account creation, activation, profile editing, email correction, lock/unlock behavior outside the
specified transitions, password reset implementation, login/throttle behavior, internship
lifecycle, Project membership/leadership restoration, and any change to the account contract are
outside this plan. Their existing SPECs and `MODULE.md` remain authoritative.

## 7. Open questions

No new business decision is required by the cited rules. Before approval, recheck the shared
[identity open questions](../../MODULE.md#notes--open-questions) and the SPEC's account-lifecycle
notes. If implementation discovers a missing or conflicting requirement, stop and record the
question with its rule ID; do not infer a transition or database shape.
