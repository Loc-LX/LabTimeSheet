# Project lifecycle Spec

**Version:** 1.2.0 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-21

**Module:** `project` · **Shared contract:** [MODULE.md](../../MODULE.md)

## 1. Context & Goal

Create, start, complete, cancel or delete a Project under its lifecycle and retention guards.

This is one cohesive planning scope. Operations below are sections of this feature,
not separate modules or mandatory branches. The split changes document ownership only.

## 2. Actors & Roles

Owning Mentor; authorized historical readers.

The [platform permission matrix](../../../platform/features/authorization/SPEC.md#52-permission-matrix)
and the module's shared authorization rules apply to every operation.

## 3. Functional Requirements

The operation summaries below are navigation. The numbered rules remain authoritative;
they do not acquire additional behavior from a heading or summary.

### Create and start

Project ownership, date range, initial state and start guards follow the PRJ rules below.

### Complete

Completion rechecks work and membership conditions before closing the Project.

### Cancel or delete

PRJ-002 and PRJ-023 distinguish deletion of an empty draft from retained cancellation history.

### Canonical feature rules

| ID | Requirement |
|---|---|
| PRJ-001 | WHEN an active Mentor creates a Project, THE system SHALL create the Project, one eligible initial Leader membership, and its first leadership term in a single transaction. THE system SHALL NOT commit a Project with no membership. |
| PRJ-002 | THE system SHALL permit only these Project transitions: `PLANNED → ACTIVE → COMPLETED`, and `PLANNED → CANCELLED` or `ACTIVE → CANCELLED` under `PRJ-023`. THE system SHALL NOT reopen a `COMPLETED` or `CANCELLED` Project. WHILE a Project is `PLANNED` and empty, holding exactly the one membership and one leadership term created with it and no Task (soft-deleted Tasks included), invitation, or exit request, THE system SHALL permit only its owning Mentor to delete it together with those rows and every notification raised for it. WHERE a Project is not empty, or is `ACTIVE`, `COMPLETED`, or `CANCELLED`, THE system SHALL refuse deletion and SHALL NOT offer a delete action. |
| PRJ-012 | WHEN a Mentor activates a Project, THE system SHALL require a current Leader, at least one active member, valid Project dates, and a valid active-member assignee on every non-deleted Task. |
| PRJ-013 | WHILE a Project is `PLANNED`, THE system SHALL permit the current Leader to prepare a Task for any active member and any active member to prepare a self-assigned Task, and SHALL refuse Task status changes and work logging until the Project is `ACTIVE`. |
| PRJ-014 | THE system SHALL permit only the owning Mentor to complete a Project, and only WHILE every non-deleted Task is `DONE`. WHEN completion commits, THE system SHALL close the current leadership and membership intervals, revoke pending invitations, supersede pending exit requests, and make the aggregate read-only. |
| PRJ-015 | THE system SHALL compute Project progress as `DONE non-deleted Tasks / all non-deleted Tasks`. WHERE a Project has no non-deleted Task, THE system SHALL render `N/A` rather than 0%. |
| PRJ-016 | THE system SHALL show, alongside Project progress, the counts of `TODO`, `IN_PROGRESS`, `BLOCKED`, and `DONE` Tasks and the total logged minutes. |
| PRJ-023 | WHEN the owning Mentor cancels a `PLANNED` or `ACTIVE` Project, THE system SHALL require a nonblank cancellation reason and SHALL record the Mentor, the server time, and the reason. In the same transaction THE system SHALL close the current leadership and membership intervals while retaining them, revoke pending invitations with `PROJECT_CANCELLED`, mark pending exit requests `SUPERSEDED`, leave every Task, comment, work log, estimate, and forecast unchanged, and make the aggregate read-only. THE system SHALL notify every member whose interval it closes under `NOT-002`. |
| PRJ-024 | WHEN a Mentor creates a Project, THE system SHALL accept a start date in the past, today, or in the future, and SHALL refuse an end date earlier than the start date. THE system SHALL NOT treat a past start date as permission to record Task work before a member joined; the dates a work log may carry are set by `TSK-014`. |
| DB-019 | THE schema SHALL constrain a Project's status to `PLANNED`, `ACTIVE`, `COMPLETED` or `CANCELLED`, and SHALL require a cancelled Project to carry the cancelling Mentor, the server time and a nonblank reason. THE schema SHALL require no activation timestamp WHILE a Project is `PLANNED` and one WHILE it is `ACTIVE` or `COMPLETED`, and SHALL accept a `CANCELLED` Project with or without one, because `PRJ-023` cancels both a `PLANNED` Project, which was never activated, and an `ACTIVE` one, which was. |

#### State transitions: Project

The transitions the rules above allow. The table adds nothing to them: where it and a rule differ, the rule wins.

| From | Action | To | Who | Rules |
|---|---|---|---|---|
| (none) | create, with the initial Leader membership and leadership term | `PLANNED` | active Mentor | `PRJ-001`, `PRJ-002`, `PRJ-024` |
| `PLANNED` | delete, while empty | (deleted) | owning Mentor | `PRJ-002` |
| `PLANNED` | activate, with a current Leader, an active member, valid dates and valid assignees | `ACTIVE` | owning Mentor | `PRJ-002`, `PRJ-012` |
| `ACTIVE` | complete, when every non-deleted Task is `DONE` | `COMPLETED` | owning Mentor | `PRJ-002`, `PRJ-014` |
| `PLANNED`, `ACTIVE` | cancel, with a reason | `CANCELLED` | owning Mentor | `PRJ-002`, `PRJ-023`, `DB-019` |

No transition leaves `COMPLETED` or `CANCELLED` (`PRJ-002`). `PLANNED` is the only status no permitted transition leads to, so a new Project starts in it.


## 4. Non-functional Requirements

Inherit [module constraints](../../MODULE.md#4-non-functional-requirements) and
[platform constraints](../../../platform/MODULE.md#4-non-functional-requirements),
including authorization, history, concurrency, server time and test evidence.

## 5. Data

`projects`; membership, Task and notification history retain their module contracts.

The [module data contract](../../MODULE.md#5-data) retains ownership, constraints and
checks across features. The full physical model stays in platform §19. A feature folder
does not grant repository/entity access across the module boundaries of ARC-006.

### Required contracts

These are required facts or behaviors, not prescribed cross-module imports or an
automatic implementation order. PLAN resolves delivery predecessors against what
already exists and preserves ARC-005/ARC-006.

| Provider | Required fact or behavior | Rule trace |
|---|---|---|
| [project/membership-and-leadership](../membership-and-leadership/SPEC.md) | Initial/current Leader and retained membership intervals | [PRJ-005](../membership-and-leadership/SPEC.md), [PRJ-006](../membership-and-leadership/SPEC.md), [PRJ-007](../membership-and-leadership/SPEC.md) |
| [project/task-management](../task-management/SPEC.md) | Task readiness for completion | [TSK-007](../task-management/SPEC.md) |
| [project/invitations](../invitations/SPEC.md) | Invitation terminal resolution | [PRJ-019](../invitations/SPEC.md) |
| [project/membership-exit](../membership-exit/SPEC.md) | Pending exit resolution | [PRJ-021](../membership-exit/SPEC.md) |

### Related workflows and joint checks

- [Membership and leadership](../membership-and-leadership/SPEC.md): Lifecycle guards current participants.
- [Membership exit and transfer](../membership-exit/SPEC.md): Completion/cancellation resolve pending workflows.
- [Task management](../task-management/SPEC.md): Tasks constrain completion and remain historical after cancellation.

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
| [Create and start](#create-and-start) | Owning Mentor creates a valid Project and starts it after readiness guards pass | [PRJ-001](SPEC.md), [PRJ-012](SPEC.md), [PRJ-013](SPEC.md), [PRJ-024](SPEC.md) | [AC-PRJ-006](SPEC.md), [AC-PRJ-009](SPEC.md), [AC-PRJ-016](SPEC.md) | Include initial-Leader concurrency, valid dates and missing/ineligible participant refusal. |
| [Complete](#complete) | Owning Mentor completes a ready Project and retained readers see correct progress | [PRJ-002](SPEC.md), [PRJ-014](SPEC.md), [PRJ-015](SPEC.md), [PRJ-016](SPEC.md) | [AC-PRJ-007](SPEC.md), [AC-PRJ-008](SPEC.md), [AC-PRJ-014](SPEC.md) | Include terminal mutation refusal and retained membership/leadership history. |
| [Cancel or delete](#cancel-or-delete) | Owning Mentor deletes only an empty draft or cancels with retained work and reason | [PRJ-002](SPEC.md), [PRJ-023](SPEC.md), [DB-019](SPEC.md) | [AC-PRJ-014](SPEC.md), [AC-PRJ-015](SPEC.md), [AC-DB-007](../../MODULE.md), [AC-DB-012](SPEC.md) | `D35` settles internship readiness through ACC-022 and AC-ACC-018; cancellation itself still preserves every Task status and history. |

### Canonical acceptance scenarios

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-PRJ-006 | PRJ-012–PRJ-013 | Mentor activates a Project missing Leader or with invalid assignee | Activation is rejected atomically with specific validation; valid planned Tasks remain intact. |
| AC-PRJ-007 | PRJ-014 | Mentor completes Project with a BLOCKED Task | Completion is rejected; after all active Tasks reach DONE, completion succeeds and all mutation becomes read-only. |
| AC-PRJ-008 | PRJ-015–PRJ-016 | Project has zero, then four Tasks with two DONE | Progress changes from `N/A` to 50%, with accurate status counts and minutes. |
| AC-PRJ-009 | PRJ-001, PRJ-005 | Mentor creates a Project while two requests race | One transaction atomically commits Project, initial Leader membership, and first leadership term; no committed `PLANNED`/`ACTIVE` Project has zero or two current Leaders. |
| AC-PRJ-014 | PRJ-002 | An owning Mentor completes a Project, then attempts to move it back to `ACTIVE` or `PLANNED`, and a direct request attempts the same transition | Both attempts are refused; the Project remains `COMPLETED`; no state column value outside `PLANNED`, `ACTIVE`, `COMPLETED`, and `CANCELLED` can be written. Separately, the owning Mentor deletes an empty `PLANNED` Project and its initial membership, leadership term, and the notifications raised for it are removed with it; a `PLANNED` Project holding a Task, a second membership, an invitation, or an exit request refuses deletion and offers none; another Mentor's delete request is denied; a delete request for an `ACTIVE` Project is refused, the Project remains, and its page offers no delete action. |
| AC-PRJ-015 | PRJ-023, AUTH-006, DB-011 | The owning Mentor cancels an `ACTIVE` Project holding unfinished Tasks, a pending invitation, and a pending exit request, first without a reason and then with one, and another Mentor attempts the same | The reasonless attempt and the other Mentor's attempt are refused and change nothing; the cancellation records the Mentor, the time, and the reason, closes the leadership and membership intervals, revokes the invitation with `PROJECT_CANCELLED`, supersedes the exit request, leaves the Tasks and their statuses unchanged, and notifies each closed member; every later mutation of the Project or its Tasks is refused. |
| AC-DB-012 | DB-019, PRJ-023 | Bypassing the application, a `PLANNED` Project and an `ACTIVE` one are each set to `CANCELLED` with the cancelling Mentor, the server time and a reason; then rows are written for every status with and without an activation timestamp, and a `CANCELLED` row without its Mentor, time or a nonblank reason | Both cancellations are accepted: the formerly `PLANNED` row without an activation timestamp and the formerly `ACTIVE` row with one. A `PLANNED` row with an activation timestamp, an `ACTIVE` or `COMPLETED` row without one, and a `CANCELLED` row missing its Mentor, time or reason are each refused. |
| AC-PRJ-016 | PRJ-024, TSK-014 | On 15 September a Mentor creates a Project that started on 8 September and ends on 14 December, then tries another whose end date precedes its start; after activation the Leader, assigned a Task, logs work dated 10 September and 15 September | The first Project is created with its 8 September start; the reversed range is refused; the 10 September work log, dated before the Leader joined, is refused under `TSK-014`, and the 15 September one is accepted. |

Shared and cross-feature scenarios in [MODULE.md](../../MODULE.md#7-acceptance-criteria)
also apply. Scenario ownership follows the behavior exercised, not every prerequisite
named by its Requirements cell. Relocation preserves every existing expected result.

## 8. Out of Scope

Inherit [module exclusions](../../MODULE.md#8-out-of-scope). Adjacent feature behavior
is a dependency, not a duplicated contract. This split creates no new product behavior,
schema, dependency, PLAN.md or TASKS.md.

## Notes / Open Questions

`D35` settles ACC-022 versus PRJ-023: retained unfinished Tasks in CANCELLED Projects are excluded from internship readiness, while Project cancellation and immutability remain unchanged. See [the canonical readiness scenario](../../../internship/features/lifecycle/SPEC.md#canonical-acceptance-scenarios).

Read [shared open questions](../../MODULE.md#notes--open-questions) before approving
the technical plan. [plan.md](../../../../../plan.md) is the only progress tracker.
