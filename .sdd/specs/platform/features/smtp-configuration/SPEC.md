# SMTP configuration Spec

**Version:** 1.1.0 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-21

**Module:** `platform` · **Shared contract:** [MODULE.md](../../MODULE.md)

## 1. Context & Goal

Test and activate a stored SMTP revision while retaining the previous configuration until success.

This is one cohesive planning scope. Operations below are sections of this feature,
not separate modules or mandatory branches. The split changes document ownership only.

## 2. Actors & Roles

Active Admin; platform owns integration persistence and raw mail. Identity owns the administration screen under ARC-005.

Use cases defined here: UC-19.

The [platform permission matrix](../../../platform/MODULE.md#52-permission-matrix)
and the module's shared authorization rules apply to every operation.

## 3. Functional Requirements

The operation summaries below are navigation. The numbered rules remain authoritative;
they do not acquire additional behavior from a heading or summary.

### Save draft

INT-001 and INT-006 keep configuration in the application and distinguish a draft from the active revision.

### Test and activate

INT-007 and INT-008 govern the test, atomic activation and retained revision history.

### Canonical feature rules

| ID | Requirement |
|---|---|
| INT-006 | THE system SHALL move an SMTP revision through `DRAFT → ACTIVE → RETIRED`, and SHALL permit at most one draft and one active revision at a time. WHEN an Admin edits an active configuration, THE system SHALL create a draft and leave the active revision operational. THE system SHALL expose a read-only SMTP History carrying non-secret revision metadata, test, activation and retirement outcomes, and the responsible users. |
| INT-007 | THE system SHALL store, for SMTP, a host, a port from 1 through 65535, a security mode, an optional username and password, a From address, and a From name. WHILE running under the production profile, THE system SHALL permit `STARTTLS` or `TLS` and SHALL reject plaintext `NONE`. |
| INT-008 | WHEN an Admin tests SMTP, THE system SHALL send a message to that Admin. THE system SHALL permit activation of a draft only after a successful test, and WHEN a draft is activated SHALL retire the previous active revision in the same transaction. |

#### UC-19 — Configure SMTP

**Part F3.**

| Field | Specification |
|---|---|
| Primary actor(s) | Admin |
| Trigger | An Admin changes SMTP configuration. |
| Preconditions | The Admin is active and has the deployment-provided encryption master key available to the application. |
| Postconditions | Mail uses the newly activated revision; any previously active revision is retired and remains in SMTP History without its secrets. |
| Traced requirements | INT-001–INT-008 |

**Main success flow**

1. Create a draft SMTP revision.
2. Test the draft before activation.
3. Activate the tested draft.

**Alternatives and exceptions**

- A failed SMTP test cannot replace the working active revision.

#### State transitions: SMTP revision

The transitions the rules above allow. The table adds nothing to them: where it and a rule differ, the rule wins.

| From | Action | To | Who | Rules |
|---|---|---|---|---|
| (none) | save a draft, including by editing the active revision | `DRAFT` | Admin | `INT-006` |
| `DRAFT` | activate, after a successful test | `ACTIVE` | Admin | `INT-006`, `INT-008` |
| `ACTIVE` | another draft is activated, in the same transaction | `RETIRED` | system | `INT-006`, `INT-008` |

At most one draft and one active revision exist at a time (`INT-006`), and no transition leaves `RETIRED`.

## 4. Non-functional Requirements

Inherit [module constraints](../../MODULE.md#4-non-functional-requirements) and
[platform constraints](../../../platform/MODULE.md#4-non-functional-requirements),
including authorization, history, concurrency, server time and test evidence.

## 5. Data

`smtp_configurations`; encryption-envelope rules INT-002 through INT-005 and INT-010 stay in MODULE.md because HolidayAPI shares them.

The [module data contract](../../MODULE.md#5-data) retains ownership, constraints and
checks across features. The full physical model stays in platform §19. A feature folder
does not grant repository/entity access across the module boundaries of ARC-006.

### Required contracts

These are required facts or behaviors, not prescribed cross-module imports or an
automatic implementation order. PLAN resolves delivery predecessors against what
already exists and preserves ARC-005/ARC-006.

| Provider | Required fact or behavior | Rule trace |
|---|---|---|
| [platform](../../MODULE.md) | Encryption envelope, redaction and master-key requirements | [INT-002](../../MODULE.md), [INT-003](../../MODULE.md), [INT-004](../../MODULE.md), [INT-005](../../MODULE.md), [INT-010](../../MODULE.md) |

### Related workflows and joint checks

- Shared contracts and cross-module consumers are listed in [MODULE.md](../../MODULE.md).

Identity owns the Admin screen and supplies the authorized Admin email to the
platform contract. Platform does not read identity repositories/entities (`ARC-005`).

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
| [Save draft](#save-draft) | Active Admin saves an encrypted draft without replacing the working active revision | [INT-001](../../MODULE.md), [INT-002](../../MODULE.md), [INT-003](../../MODULE.md), [INT-004](../../MODULE.md), [INT-005](../../MODULE.md), [INT-006](SPEC.md) | [AC-INT-001](../../MODULE.md), [AC-INT-005](SPEC.md) | Verify secret redaction and at most one draft/active revision. |
| [Test and activate](#test-and-activate) | Active Admin tests delivery and atomically activates a successful revision | [INT-007](SPEC.md), [INT-008](SPEC.md) | [AC-INT-002](../../MODULE.md) | Failure retains the previous active revision; history stays non-secret and Admin-only. |

### Canonical acceptance scenarios

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-INT-005 | INT-001 | SMTP settings are supplied through deployment environment variables while none is configured in the Admin console, then an Admin edits SMTP in the console | The environment values are ignored and SMTP remains unconfigured; the console edit is the only change that takes effect, and it is stored through the application rather than requiring direct database editing. |

Shared and cross-feature scenarios in [MODULE.md](../../MODULE.md#7-acceptance-criteria)
also apply. Scenario ownership follows the behavior exercised, not every prerequisite
named by its Requirements cell. Relocation preserves every existing expected result.

## 8. Out of Scope

Inherit [module exclusions](../../MODULE.md#8-out-of-scope). Adjacent feature behavior
is a dependency, not a duplicated contract. This split creates no new product behavior,
schema, dependency, PLAN.md or TASKS.md.

## Notes / Open Questions

AC-INT-001, AC-INT-002 and AC-INT-004 stay shared because they exercise both SMTP and HolidayAPI. This document split does not move the Admin screen or change ARC-005.

Read [shared open questions](../../MODULE.md#notes--open-questions) before approving
the technical plan. [plan.md](../../../../../plan.md) is the only progress tracker.
