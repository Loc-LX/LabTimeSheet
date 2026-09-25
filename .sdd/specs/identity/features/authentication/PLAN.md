# Authentication Plan

**Version:** 1.0 · **Owner:** Loc-LX · **Status:** APPROVED · **Date:** 2026-09-26

This is a technical design for [SPEC.md](SPEC.md), not a progress tracker. Progress belongs in
[`plan.md`](../../../../../plan.md). It inherits the shared identity contract in
[`MODULE.md`](../../MODULE.md); it does not copy or replace that contract. Tasks are in
[TASKS.md](TASKS.md). The maintainer approved it on 26 September 2026.

| Scope | Rules | State |
|---|---|---|
| Sign-in only for active accounts | `ACC-030`; related `SEC-005` | Approved on 26 September 2026 |

## 1. Goal and boundary

Implement or confirm the outstanding authentication behavior: refuse sign-in unless an account is
`ACTIVE` (`ACC-030`). The acceptance scenario is `AC-ACC-022`.

The identity contract migration is task C-04 in the [data-model plan](../../../platform/features/data-model/PLAN.md).
It ships with the Account lifecycle and Authentication code named there. This plan points to C-04;
it does not define or duplicate the migration. C-04 remains authoritative for the exact `DB-022`
row shapes and timestamp triggers.

## 2. Constraints and dependencies

1. Inactive-state sign-in refusals return the generic response of `SEC-005` and create no session;
   the acceptance result is the one specified by `AC-ACC-022`.
2. Password-reset eligibility, token lifecycle, session invalidation, and generic responses retain
   the existing `SEC-003`–`SEC-005`, `SEC-015`, and `ACC-018` contracts.
3. Keep module boundaries (`ARC-005`, `ARC-006`).

## 3. Implementation order

Every code task starts with its smallest boundary test, run RED for the expected behavior gap.
Test Javadoc names the rule IDs and the expected observable result (`TST-001`–`TST-010`). Before
editing each production symbol, run GitNexus impact analysis; treat UNKNOWN as unresolved and
confirm callers with text search. Preserve all existing assertions.

### 3.1 Refuse authentication for every non-active state

Write the state matrix from `AC-ACC-022` first, including correct credentials for `LOCKED` and
`DEACTIVATED`, an attempted credential for `PENDING_ACTIVATION`, and a wrong password for
`ACTIVE`. If it fails, the failure names the gap, and the fix is the minimum change.
`DatabaseUserDetailsService` already marks every account that is not `ACTIVE` as disabled, so the
scenario may pass on the current code; then the evidence records that `ACC-030` holds, and no
production code changes.

### 3.2 Ship with the C-04 contract

Coordinate with account lifecycle and data-model C-04. The authentication and lifecycle changes
ship with C-04; its probes own `DB-022`, and this plan does not make a separate schema change.

## 4. Verification and completion

Run the focused test after any change, then the affected identity suite and full `./mvnw test`. Run
`npm run test:ui` and `git diff --check`. Completion requires `AC-ACC-022`, C-04's `AC-DB-009`, and
the full required suites to pass with existing assertions intact. Record commands and resolved tool
versions in `plan.md`.

## 5. Risks

| Risk | Handling |
|---|---|
| An inactive account receives an authenticated session | Verify every state and the generic response in `AC-ACC-022` |

## 6. Not in this part

Login throttle behavior, account creation, activation, deactivation/reinstatement, password-reset
completion and eligibility, token policy changes, UI redesign, and any change to the business rules
are outside this plan. Existing SPECs remain authoritative. The throttle clause of `SEC-015` changes
the reset-completion operation of Password management, whose SPEC holds that use case and
`AC-SEC-011`; under `D40` it is planned there, and it does not ship with C-04.

## 7. Open questions

No new business decision is required by `ACC-030`. Before approval, recheck the [shared identity
open questions](../../MODULE.md#notes--open-questions); record any newly found rule gap with its ID
instead of inferring a requirement.
