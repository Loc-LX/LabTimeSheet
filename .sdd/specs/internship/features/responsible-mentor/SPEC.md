# Responsible Mentor Spec

**Version:** 1.1.0 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-21

**Module:** `internship` · **Shared contract:** [MODULE.md](../../MODULE.md)

## 1. Context & Goal

Assign or replace the Mentor responsible for an Intern without rewriting historical decisions.

This is one cohesive planning scope. Operations below are sections of this feature,
not separate modules or mandatory branches. The split changes document ownership only.

## 2. Actors & Roles

Admin assigns an active Mentor; the assigned Mentor receives actionable requests.

The [platform permission matrix](../../../platform/features/authorization/SPEC.md#52-permission-matrix)
and the module's shared authorization rules apply to every operation.

## 3. Functional Requirements

The operation summaries below are navigation. The numbered rules remain authoritative;
they do not acquire additional behavior from a heading or summary.

### Assign Mentor

ACC-026 and DB-021 require an active Mentor account with the immutable MENTOR role.

### Replace unavailable Mentor

Pending and overdue attendance requests follow the new Mentor; retained decisions keep the original actor.

### Canonical feature rules

| ID | Requirement |
|---|---|
| ACC-026 | THE system SHALL let an Admin assign one active Mentor as an Intern's responsible Mentor and replace that assignment. WHEN the assignment changes, THE system SHALL move every pending or overdue leave, correction, and attendance exception request of that Intern to the new Mentor, SHALL leave every earlier decision attributed to the Mentor who made it, and SHALL NOT make the Admin an approver. WHILE an Intern's responsible Mentor is `LOCKED` or `DEACTIVATED`, THE system SHALL show that Intern to Admins as needing a new responsible Mentor, and SHALL NOT let an Admin or any other Mentor decide the Intern's requests. |
| DB-021 | THE schema SHALL let an Intern profile reference at most one responsible Mentor, whose account's global role is `MENTOR`. |



## 4. Non-functional Requirements

Inherit [module constraints](../../MODULE.md#4-non-functional-requirements) and
[platform constraints](../../../platform/MODULE.md#4-non-functional-requirements),
including authorization, history, concurrency, server time and test evidence.

## 5. Data

`intern_profiles.responsible_mentor_id`; attendance owns request and decision records.

The [module data contract](../../MODULE.md#5-data) retains ownership, constraints and
checks across features. The full physical model stays in platform §19. A feature folder
does not grant repository/entity access across the module boundaries of ARC-006.

### Required contracts

These are required facts or behaviors, not prescribed cross-module imports or an
automatic implementation order. PLAN resolves delivery predecessors against what
already exists and preserves ARC-005/ARC-006.

| Provider | Required fact or behavior | Rule trace |
|---|---|---|
| [identity/account-lifecycle](../../../identity/features/account-lifecycle/SPEC.md) | Immutable Mentor role and active account eligibility | [ACC-008](../../../identity/features/account-lifecycle/SPEC.md), [ACC-014](../../../identity/features/account-lifecycle/SPEC.md), [ACC-015](../../../identity/features/account-lifecycle/SPEC.md), [ACC-016](../../../identity/features/account-lifecycle/SPEC.md) |

### Related workflows and joint checks

Attendance consumes the current responsible Mentor under [NOT-011](../../../attendance/MODULE.md).
Verify pending/overdue request routing and historical actor retention with attendance;
Mentor assignment does not read or move attendance request records (`D28`, resolution R2).

- [Internship lifecycle](../lifecycle/SPEC.md): Activation waits for a responsible Mentor.

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
| [Assign Mentor](#assign-mentor) | Admin assigns an eligible active Mentor to an Intern | [ACC-026](SPEC.md), [DB-021](SPEC.md) | [AC-ACC-013](../../MODULE.md), [AC-DB-008](SPEC.md) | Activation is a joint lifecycle scenario; role integrity alone does not prove active-account eligibility. |
| [Replace unavailable Mentor](#replace-unavailable-mentor) | Admin replaces an unavailable Mentor and actionable requests follow the replacement | [ACC-026](SPEC.md) | [AC-ACC-014](SPEC.md), [AC-ACC-013](../../MODULE.md) | Retain the original actor of earlier decisions and include pending/overdue request types. |

### Canonical acceptance scenarios

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-ACC-014 | ACC-026 | An Intern's responsible Mentor is locked while the Intern has a pending leave request, a pending correction, and an overdue exception request | The Intern appears to Admins as needing a new responsible Mentor; neither an Admin nor another Mentor can decide the requests; after an Admin assigns a new Mentor all three move to that Mentor, and decisions made earlier still name the original Mentor. |
| AC-DB-008 | DB-021 | SQL probes set an Admin account, then an Intern account, then a Mentor account as an Intern's responsible Mentor | The first two are refused; the Mentor commits. |

Shared and cross-feature scenarios in [MODULE.md](../../MODULE.md#7-acceptance-criteria)
also apply. Scenario ownership follows the behavior exercised, not every prerequisite
named by its Requirements cell. Relocation preserves every existing expected result.

## 8. Out of Scope

Inherit [module exclusions](../../MODULE.md#8-out-of-scope). Adjacent feature behavior
is a dependency, not a duplicated contract. This split creates no new product behavior,
schema, dependency, PLAN.md or TASKS.md.

## Notes / Open Questions

AC-ACC-013 stays in MODULE.md because it exercises assignment together with lifecycle activation. ACC-026 has no dedicated use case; attendance UC-15 and UC-17 cover its consumers.

Read [shared open questions](../../MODULE.md#notes--open-questions) before approving
the technical plan. [plan.md](../../../../../plan.md) is the only progress tracker.
