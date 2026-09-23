# Membership and leadership Spec

**Version:** 1.1.0 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-21

**Module:** `project` · **Shared contract:** [MODULE.md](../../MODULE.md)

## 1. Context & Goal

Manage membership intervals and the current Project Leader without converting leadership into a global role.

This is one cohesive planning scope. Operations below are sections of this feature,
not separate modules or mandatory branches. The split changes document ownership only.

## 2. Actors & Roles

Owning Mentor manages membership/leadership; Intern participation requires current eligibility.

The [platform permission matrix](../../../platform/features/authorization/SPEC.md#52-permission-matrix)
and the module's shared authorization rules apply to every operation.

## 3. Functional Requirements

The operation summaries below are navigation. The numbered rules remain authoritative;
they do not acquire additional behavior from a heading or summary.

### Add eligible members

Membership and internship eligibility follow PRJ-003 through PRJ-007 and PRJ-017.

### Assign or replace Leader

PRJ-005 through PRJ-007 govern the current Leader, atomic replacement and unchanged
Task assignments. PRJ-011 preserves attribution and history when a membership closes.

### Canonical feature rules

| ID | Requirement |
|---|---|
| PRJ-003 | THE system SHALL permit an Intern to hold active memberships in several Projects at once, and SHALL represent each membership explicitly with a join timestamp and an optional leave timestamp. |
| PRJ-004 | THE system SHALL permit only the owning Mentor to add or remove a member directly and to decide a membership exit. THE system SHALL permit the current Leader to invite an eligible Intern, request another member's removal, and redistribute unfinished Tasks away from a pending exit target. THE system SHALL permit an Intern to join only by accepting their own invitation, and to leave only after the owning Mentor approves. |
| PRJ-005 | WHILE a Project is `PLANNED` or `ACTIVE`, THE system SHALL maintain exactly one current Leader who is an active member of that Project. WHEN the Project completes, THE system SHALL close the final leadership term rather than delete it. |
| PRJ-006 | WHEN leadership changes, THE system SHALL close the current term and open a new term for another active member in one transaction, so that the Project never exposes two current Leaders and never exposes an active period without one. |
| PRJ-007 | WHEN leadership changes and nothing else, THE system SHALL NOT reassign any Task. The former Leader SHALL remain a normal member and SHALL retain assignee rights for Tasks still assigned to them. |
| PRJ-011 | WHEN a membership closes, THE system SHALL leave completed Tasks, Task creator attribution, comments, work logs, invitations, exit requests, and leadership history unchanged. A completed Task SHALL remain assigned to the closed historical membership and SHALL display that Intern's name in authorized history. |
| PRJ-017 | THE system SHALL treat an Intern as eligible for direct addition or invitation only WHILE their account and internship are `ACTIVE` and they hold no active membership in that Project. WHEN an owning Mentor adds a member directly, THE system SHALL require no acceptance and SHALL record that Mentor as `added_by_user_id`; WHEN an invitation is accepted, THE system SHALL record the accepting Intern. WHERE a matching invitation is pending at the moment of a direct add, THE system SHALL mark it `SUPERSEDED` with `MENTOR_DIRECT_ADD` in the same transaction. |



## 4. Non-functional Requirements

Inherit [module constraints](../../MODULE.md#4-non-functional-requirements) and
[platform constraints](../../../platform/MODULE.md#4-non-functional-requirements),
including authorization, history, concurrency, server time and test evidence.

## 5. Data

`project_memberships`, `project_leadership_terms`.

The [module data contract](../../MODULE.md#5-data) retains ownership, constraints and
checks across features. The full physical model stays in platform §19. A feature folder
does not grant repository/entity access across the module boundaries of ARC-006.

### Required contracts

These are required facts or behaviors, not prescribed cross-module imports or an
automatic implementation order. PLAN resolves delivery predecessors against what
already exists and preserves ARC-005/ARC-006.

| Provider | Required fact or behavior | Rule trace |
|---|---|---|
| [identity/account-lifecycle](../../../identity/features/account-lifecycle/SPEC.md) | Immutable role and account state | [ACC-008](../../../identity/features/account-lifecycle/SPEC.md), [ACC-014](../../../identity/features/account-lifecycle/SPEC.md) |
| [internship/lifecycle](../../../internship/features/lifecycle/SPEC.md) | Internship eligibility | [ACC-020](../../../internship/features/lifecycle/SPEC.md) |
| [project/lifecycle](../lifecycle/SPEC.md) | Project state | [PRJ-002](../lifecycle/SPEC.md) |
| [project/membership-exit](../membership-exit/SPEC.md) | Removal and replacement guards | [PRJ-008](../membership-exit/SPEC.md), [PRJ-009](../membership-exit/SPEC.md), [PRJ-010](../membership-exit/SPEC.md) |

### Related workflows and joint checks

- [Project lifecycle](../lifecycle/SPEC.md): Project state constrains membership.
- [Project invitations](../invitations/SPEC.md): Invitations can create membership.
- [Membership exit and transfer](../membership-exit/SPEC.md): Exit closes membership only after transfer guards.

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
| [Add eligible members](#add-eligible-members) | Owning Mentor adds only an eligible Intern membership in the intended Project | [PRJ-003](SPEC.md), [PRJ-004](SPEC.md), [PRJ-005](SPEC.md), [PRJ-017](SPEC.md) | [AC-PRJ-001](SPEC.md), [AC-PRJ-010](../invitations/SPEC.md) | Include inactive/terminal internship eligibility and direct-add racing invitation acceptance. |
| [Assign or replace Leader](#assign-or-replace-leader) | Owning Mentor replaces the one current Leader atomically without changing assignees | [PRJ-005](SPEC.md), [PRJ-006](SPEC.md), [PRJ-007](SPEC.md), [PRJ-011](SPEC.md) | [AC-PRJ-002](SPEC.md), [AC-PRJ-003](SPEC.md) | PRJ-011 protects attribution when membership closes; replacement behavior is PRJ-005 through PRJ-007. |

### Canonical acceptance scenarios

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-PRJ-001 | PRJ-003–PRJ-005 | Same Intern is added to two Projects and appointed Leader of one | Both active memberships coexist; leadership affects only the selected Project. |
| AC-PRJ-002 | PRJ-005, DB-003 | Two transactions appoint different current Leaders | Database/transaction rules allow exactly one current term; no double-Leader state commits. |
| AC-PRJ-003 | PRJ-006–PRJ-007 | Mentor changes Leader while former Leader remains a member | Old term closes, new term opens, every Task assignee remains unchanged, and former Leader loses Task-management controls. |

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
