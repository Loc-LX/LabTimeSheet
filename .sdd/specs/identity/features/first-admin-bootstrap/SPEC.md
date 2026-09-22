# First Admin bootstrap Spec

**Version:** 1.1.0 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-21

**Module:** `identity` · **Shared contract:** [MODULE.md](../../MODULE.md)

## 1. Context & Goal

Initialize an installation once and guide its first Admin through SMTP setup or explicit deferral.

This is one cohesive planning scope. Operations below are sections of this feature,
not separate modules or mandatory branches. The split changes document ownership only.

## 2. Actors & Roles

Installer becoming the first Admin.

Use cases defined here: UC-01.

The [platform permission matrix](../../../platform/MODULE.md#52-permission-matrix)
and the module's shared authorization rules apply to every operation.

## 3. Functional Requirements

The operation summaries below are navigation. The numbered rules remain authoritative;
they do not acquire additional behavior from a heading or summary.

### Initialize once

ACC-001 through ACC-004 define the bootstrap guard, concurrent initialization and permanent closure.

### Configure or defer SMTP

ACC-005 through ACC-007 define the five confirmations and persistent setup warning.

### Canonical feature rules

| ID | Requirement |
|---|---|
| ACC-001 | WHILE the installation is uninitialized, THE system SHALL expose only the health endpoints, the static bootstrap assets, and the one-time bootstrap workflow. |
| ACC-002 | WHEN a valid first-Admin bootstrap is submitted, THE system SHALL create that `ADMIN` with a password and mark the singleton system state initialized in one transaction. WHERE two bootstrap submissions arrive concurrently, exactly one SHALL create an Admin and the other SHALL be told setup is already complete. |
| ACC-003 | WHEN initialization completes, THE system SHALL make the bootstrap route unavailable, and it SHALL remain unavailable across restarts. |
| ACC-004 | Bootstrap runs on a private interface before the installation is exposed publicly. The temporary unguarded bootstrap route is an accepted and documented operational risk, not a defect. |
| ACC-005 | WHILE bootstrap is open, THE system SHALL offer SMTP setup. WHERE the Admin defers it, THE system SHALL require five sequential distinct confirmations, SHALL offer Back and Configure SMTP on every one, and SHALL permit Finish without SMTP only on the fifth. |
| ACC-006 | THE system SHALL present the five deferral warnings in this order: account onboarding disabled, activation resend disabled, password recovery disabled, reduced email immediacy for workflow events, and final acknowledgement of a restricted installation. |
| ACC-007 | WHILE no tested SMTP configuration is active, THE system SHALL keep a persistent warning visible to every Admin. |

#### UC-01 — Initialize the installation

| Field | Specification |
|---|---|
| Primary actor(s) | First Admin (installer) |
| Trigger | The uninitialized installation is opened on a private interface. |
| Preconditions | No bootstrap has completed; the singleton system state is uninitialized. |
| Postconditions | Exactly one first Admin exists and bootstrap cannot be reopened. |
| Traced requirements | ACC-001–ACC-007, INT-001–INT-003, SEC-001 |

**Main success flow**

1. Enter the first Admin identity and password.
2. Optionally configure and test an SMTP draft.
3. If SMTP is deferred, pass through five distinct warning screens in order.
4. Submit the final bootstrap action.
5. Create the first Admin and close bootstrap atomically.
6. Enter the initialized Admin workspace.

**Alternatives and exceptions**

- A competing bootstrap submission loses the atomic race and cannot create another first Admin.
- An SMTP test failure leaves the draft inactive and allows correction.
- Before the fifth warning, the user may go back or configure SMTP but may not finish the deferral.

## 4. Non-functional Requirements

Inherit [module constraints](../../MODULE.md#4-non-functional-requirements) and
[platform constraints](../../../platform/MODULE.md#4-non-functional-requirements),
including authorization, history, concurrency, server time and test evidence.

## 5. Data

`system_state`, first `app_users` row; SMTP revision storage remains a platform responsibility.

The [module data contract](../../MODULE.md#5-data) retains ownership, constraints and
checks across features. The full physical model stays in platform §19. A feature folder
does not grant repository/entity access across the module boundaries of ARC-006.

### Required contracts

These are required facts or behaviors, not prescribed cross-module imports or an
automatic implementation order. PLAN resolves delivery predecessors against what
already exists and preserves ARC-005/ARC-006.

| Provider | Required fact or behavior | Rule trace |
|---|---|---|
| [platform/smtp-configuration](../../../platform/features/smtp-configuration/SPEC.md) | SMTP test/activation when setup is selected | [INT-006](../../../platform/features/smtp-configuration/SPEC.md), [INT-007](../../../platform/features/smtp-configuration/SPEC.md), [INT-008](../../../platform/features/smtp-configuration/SPEC.md) |

### Related workflows and joint checks

- [Account lifecycle](../account-lifecycle/SPEC.md): Subsequent accounts follow the normal activation path.

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
| [Initialize once](#initialize-once) | Installer creates exactly one first Admin and permanently closes bootstrap | [ACC-001](SPEC.md), [ACC-002](SPEC.md), [ACC-003](SPEC.md), [ACC-004](SPEC.md) | [AC-ACC-001](SPEC.md), [AC-ACC-002](SPEC.md) | Exercise concurrent clients and post-restart closure together with the shared integrity constraints. |
| [Configure or defer SMTP](#configure-or-defer-smtp) | First Admin configures SMTP or completes the prescribed deferral confirmations | [ACC-005](SPEC.md), [ACC-006](SPEC.md), [ACC-007](SPEC.md) | [AC-ACC-003](SPEC.md), [AC-INT-002](../../../platform/MODULE.md) | Deferral and SMTP activation are separate cases; the combined first-run flow must preserve the warning and activation outcome. |

### Canonical acceptance scenarios

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-ACC-001 | ACC-001–ACC-004 | Two clients submit valid first-Admin bootstrap concurrently | Exactly one Admin and initialized singleton commit; the other request sees setup already complete; no second bootstrap Admin is created. |
| AC-ACC-002 | ACC-003 | A client requests bootstrap after initialization and restart | Route is unavailable and cannot mutate system state. |
| AC-ACC-003 | ACC-005–ACC-007 | Admin repeatedly chooses Defer SMTP | Five different confirmations appear sequentially; only the fifth permits completion; persistent warning remains. |

Shared and cross-feature scenarios in [MODULE.md](../../MODULE.md#7-acceptance-criteria)
also apply. Scenario ownership follows the behavior exercised, not every prerequisite
named by its Requirements cell. Relocation preserves every existing expected result.

## 8. Out of Scope

Inherit [module exclusions](../../MODULE.md#8-out-of-scope). Adjacent feature behavior
is a dependency, not a duplicated contract. This split creates no new product behavior,
schema, dependency, PLAN.md or TASKS.md.

## Notes / Open Questions

No additional decision is introduced by this split. No question affecting this feature is open; the cross-feature checks and shared contracts in MODULE.md still apply.

Read [shared open questions](../../MODULE.md#notes--open-questions) before approving
the technical plan. [plan.md](../../../../../plan.md) is the only progress tracker.
