# Authentication Spec

**Version:** 1.3.0 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-21

**Module:** `identity` · **Shared contract:** [MODULE.md](../../MODULE.md)

## 1. Context & Goal

Sign in and manage authenticated access, including login throttling and session invalidation.

This is one cohesive planning scope. Operations below are sections of this feature,
not separate modules or mandatory branches. The original split changed document ownership only; `D35` records the subsequent approved behavior decisions.

## 2. Actors & Roles

Account holder; Spring Security session handling. Account and internship state constrain access.

The [platform permission matrix](../../../platform/MODULE.md#52-permission-matrix)
and the module's shared authorization rules apply to every operation.

## 3. Functional Requirements

The operation summaries below are navigation. The numbered rules remain authoritative;
they do not acquire additional behavior from a heading or summary.

### Login

Use normalized email, the generic response of SEC-005, and ACC-030, which authenticates only an `ACTIVE` account; UC-02 describes the existing end-to-end flow.

### Logout

ACC-027 ends only the current authenticated session and redirects to the login page. Security-triggered invalidation remains governed by ACC-018 and the existing lifecycle rules.

### Session invalidation

ACC-018 owns invalidation after password, email and account-state changes. Internship completion and withdrawal also constrain access through ACC-023 and ACC-024.

### Failed login and throttling

SEC-006 and SEC-007 define the email/IP key, failure window, restart behavior and distinction from manual lock.

### Canonical feature rules

| ID | Requirement |
|---|---|
| ACC-027 | WHEN an authenticated account holder submits logout with valid CSRF protection, THE system SHALL invalidate only the current authenticated session and redirect the browser to the login page. THE system SHALL refuse subsequent authenticated access using that invalidated session. Other independently authenticated sessions of the same account SHALL remain valid subject to the existing account and lifecycle rules. Logout SHALL NOT change account state or retained business data. Security-triggered session invalidation SHALL continue to follow `ACC-018` and the existing lifecycle rules. |
| ACC-030 | THE system SHALL authenticate an account only WHILE it is `ACTIVE`. WHILE it is `PENDING_ACTIVATION`, `LOCKED` or `DEACTIVATED`, THE system SHALL refuse authentication even for a correct password and SHALL create no session, answering with the generic response of `SEC-005` so that the refusal does not reveal which of those states applies. |
| SEC-006 | THE system SHALL key login throttling on the normalized email together with the source IP. WHERE five failures occur inside 15 minutes, THE system SHALL throttle that key for 15 minutes. WHEN a login succeeds, THE system SHALL clear the applicable throttle state. |
| SEC-007 | THE system MAY hold throttle state in bounded memory in v1. A restart therefore resets it, and multi-node coordination is unsupported because production runs one application instance. THE system SHALL keep manual account lock persisted separately. |



## 4. Non-functional Requirements

Inherit [module constraints](../../MODULE.md#4-non-functional-requirements) and
[platform constraints](../../../platform/MODULE.md#4-non-functional-requirements),
including authorization, history, concurrency, server time and test evidence.

## 5. Data

`app_users`; authenticated sessions and bounded login-throttle state.

The [module data contract](../../MODULE.md#5-data) retains ownership, constraints and
checks across features. The full physical model stays in platform §19. A feature folder
does not grant repository/entity access across the module boundaries of ARC-006.

### Required contracts

These are required facts or behaviors, not prescribed cross-module imports or an
automatic implementation order. PLAN resolves delivery predecessors against what
already exists and preserves ARC-005/ARC-006.

| Provider | Required fact or behavior | Rule trace |
|---|---|---|
| [identity](../../MODULE.md) | Normalized identity, account eligibility, credential and session-change rules | [ACC-009](../../MODULE.md), [ACC-014](../account-lifecycle/SPEC.md), [ACC-015](../account-lifecycle/SPEC.md), [ACC-016](../account-lifecycle/SPEC.md), [ACC-017](../../MODULE.md), [ACC-018](../../MODULE.md), [SEC-002](../../MODULE.md), [SEC-005](../../MODULE.md) |
| [internship/lifecycle](../../../internship/features/lifecycle/SPEC.md) | Completed read-only and withdrawn denied access | [ACC-023](../../../internship/features/lifecycle/SPEC.md), [ACC-024](../../../internship/features/lifecycle/SPEC.md) |
| [platform](../../../platform/MODULE.md) | Session/CSRF and stored-context authorization | [SEC-001](../../../platform/MODULE.md), [AUTH-001](../../../platform/MODULE.md), [AUTH-002](../../../platform/MODULE.md) |

### Related workflows and joint checks

- [Account lifecycle](../account-lifecycle/SPEC.md): Account eligibility and immutable identity.
- [Password management](../password-management/SPEC.md): Credential changes invalidate sessions.

## 6. Error Handling

Apply each refusal, deadline, conflict and delivery-failure clause in the numbered rules
above, together with [module error handling](../../MODULE.md#6-error-handling).
Platform §21 supplies common failure behavior; an operation summary does not override it.

## 7. Acceptance Criteria

### Operation and acceptance map

This map traces existing behavior; its gap column does not define a new rule or
claim full test coverage. Actors and outcomes are summaries of the canonical rules.

| Operation | Actor and observable outcome | Canonical rules | Existing acceptance scenarios | Acceptance boundary or open decision |
|---|---|---|---|---|
| [Login](#login) | Account holder obtains authorized access after sign-in | [ACC-009](../../MODULE.md), [ACC-014](../account-lifecycle/SPEC.md), [ACC-015](../account-lifecycle/SPEC.md), [ACC-016](../account-lifecycle/SPEC.md), [ACC-030](SPEC.md), [SEC-005](../../MODULE.md) | [AC-ACC-007](../account-lifecycle/SPEC.md), [AC-ACC-009](../account-lifecycle/SPEC.md), [AC-ACC-022](SPEC.md) | Covered by `AC-ACC-022`: a correct and a wrong password, and every state other than `ACTIVE` refused behind one generic response. |
| [Logout](#logout) | Signed-in account holder ends the current session and returns to login | [ACC-027](SPEC.md), [SEC-001](../../../platform/MODULE.md) | [AC-ACC-016](SPEC.md), [AC-ACC-017](SPEC.md) | `D35` settles session scope and redirect; verify independent sessions, old-session rejection and CSRF refusal. |
| [Session invalidation](#session-invalidation) | Affected account loses existing authenticated sessions after an identity-security change | [ACC-018](../../MODULE.md), [ACC-023](../../../internship/features/lifecycle/SPEC.md), [ACC-024](../../../internship/features/lifecycle/SPEC.md) | [AC-ACC-009](../account-lifecycle/SPEC.md), [AC-ACC-012](../account-lifecycle/SPEC.md) | Existing cases cover lock/deactivation/email changes; password/reset and terminal-internship combinations need explicit session checks. |
| [Failed login and throttling](#failed-login-and-throttling) | Failed sign-in is throttled for the correct email/IP pair | [SEC-006](SPEC.md), [SEC-007](SPEC.md) | [AC-SEC-003](SPEC.md) | Include exact failure/window boundaries and the distinction from persisted manual lock. |

### Canonical acceptance scenarios

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-SEC-003 | SEC-006–SEC-007 | Same normalized email/IP fails login five times inside window | Sixth attempt is throttled for 15 minutes; restart may clear throttle but does not unlock a manually locked account. |
| AC-ACC-016 | ACC-027, ACC-018, AUTH-002 | An eligible account has independent authenticated sessions A and B; it logs out from A with a valid CSRF token, then uses A's old session identifier and B to request a protected page | A is invalidated and redirected to login; its old identifier cannot authenticate and a protected-page request goes to sign-in. B remains authenticated subject to the existing account/lifecycle guards. Account state and business data are unchanged. In separate security-change cases, the existing ACC-018 invalidation scope still applies. |
| AC-ACC-017 | ACC-027, SEC-001 | An authenticated user attempts logout using a state-changing request with a missing or invalid CSRF token, and attempts to cause logout through a GET request | None invalidates the authenticated session or changes account/business data; missing/invalid-CSRF requests are refused. A subsequent valid CSRF-protected logout ends the current session and redirects to login under ACC-027. |
| AC-ACC-022 | ACC-030, SEC-005 | Sign-in is attempted with the correct password for an account in each of `ACTIVE`, `LOCKED` and `DEACTIVATED`, with any password for a `PENDING_ACTIVATION` account, and with a wrong password for the `ACTIVE` one | Only the `ACTIVE` account with its correct password is authenticated. The other four attempts are refused with one identical generic response, so the caller cannot tell a wrong password from a locked, deactivated or pending account, and none of them creates a session. |

Shared and cross-feature scenarios in [MODULE.md](../../MODULE.md#7-acceptance-criteria)
also apply. Scenario ownership follows the behavior exercised, not every prerequisite
named by its Requirements cell. The original relocation preserved existing expected results; `D35` records the approved changes below.

## 8. Out of Scope

Inherit [module exclusions](../../MODULE.md#8-out-of-scope). Adjacent feature behavior
is a dependency, not a duplicated contract. `D35` changes only the stated behavior contracts. This revision adds no schema, dependency, PLAN.md or TASKS.md.

## Notes / Open Questions

`D35` settles logout through ACC-027 and AC-ACC-016/017, and `D38` settles the account transition table this feature gates sign-in on (`ACC-014`). This decision adds no all-devices logout screen; security-triggered invalidation remains under the existing rules.

Read [shared open questions](../../MODULE.md#notes--open-questions) before approving
the technical plan. [plan.md](../../../../../plan.md) is the only progress tracker.
