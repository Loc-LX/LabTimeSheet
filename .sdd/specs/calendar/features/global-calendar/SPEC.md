# Global calendar Spec

**Version:** 1.1.0 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-21

**Module:** `calendar` · **Shared contract:** [MODULE.md](../../MODULE.md)

## 1. Context & Goal

Manage local calendar events and their effects on attendance, leave and Task due dates.

This is one cohesive planning scope. Operations below are sections of this feature,
not separate modules or mandatory branches. The split changes document ownership only.

## 2. Actors & Roles

Admin creates or edits events; other modules consume retained local data.

The [platform permission matrix](../../../platform/MODULE.md#52-permission-matrix)
and the module's shared authorization rules apply to every operation.

## 3. Functional Requirements

The operation summaries below are navigation. The numbered rules remain authoritative;
they do not acquire additional behavior from a heading or summary.

### Create and edit events

CAL-001 and CAL-007 define Admin-only scope, history, optimistic locking and impact preview.

### Apply day-off effects

CAL-006, CAL-008 and CAL-009 distinguish observances from global days off and preserve frozen leave allocations.

### Canonical feature rules

| ID | Requirement |
|---|---|
| CAL-001 | THE system SHALL permit only an Admin to create or edit a global calendar event, whether custom or imported. THE system SHALL NOT provide project-level calendar overrides. |
| CAL-006 | THE system SHALL treat a calendar date as globally exempt WHERE at least one local event on that date carries `is_day_off=true`. THE system SHALL display a non-day-off observance without letting it affect attendance or quota. |
| CAL-007 | WHILE a global event's calendar date has passed, THE system SHALL treat that event as immutable. THE system SHALL permit a future event to change under optimistic locking with an impact preview. THE system SHALL expose retained past and current event metadata through a Calendar History view without exposing any integration secret. |
| CAL-008 | WHEN a leave request is submitted, THE system SHALL treat a global day off as waiving attendance obligation, absence classification, compliance penalty, and quota consumption. THE system SHALL leave already-materialized leave-day allocations frozen under `ATT-006`, and SHALL disclose any such reservation in the future-calendar impact preview rather than rewriting it silently. |
| CAL-009 | WHILE a date is a global day off, THE system SHALL refuse attendance check-in on it and SHALL refuse creating or moving a Task due date onto it. THE system SHALL continue to permit voluntary Task comments, status changes, and work logs on that date. |



## 4. Non-functional Requirements

Inherit [module constraints](../../MODULE.md#4-non-functional-requirements) and
[platform constraints](../../../platform/MODULE.md#4-non-functional-requirements),
including authorization, history, concurrency, server time and test evidence.

## 5. Data

`global_calendar_events`; leave allocations and Task due dates stay owned by their modules.

The [module data contract](../../MODULE.md#5-data) retains ownership, constraints and
checks across features. The full physical model stays in platform §19. A feature folder
does not grant repository/entity access across the module boundaries of ARC-006.

### Required contracts

These are required facts or behaviors, not prescribed cross-module imports or an
automatic implementation order. PLAN resolves delivery predecessors against what
already exists and preserves ARC-005/ARC-006.

| Provider | Required fact or behavior | Rule trace |
|---|---|---|
| [calendar/attendance-policy](../attendance-policy/SPEC.md) | Effective schedule and eligibility context | [ATT-001](../attendance-policy/SPEC.md), [ATT-002](../attendance-policy/SPEC.md), [ATT-003](../attendance-policy/SPEC.md) |
| [attendance/leave](../../../attendance/features/leave/SPEC.md) | Frozen leave reservations exposed to impact preview | [LEV-003](../../../attendance/features/leave/SPEC.md) |
| [project/task-management](../../../project/features/task-management/SPEC.md) | Existing due dates exposed to impact preview | [TSK-005](../../../project/features/task-management/SPEC.md), [TSK-006](../../../project/features/task-management/SPEC.md) |

### Related workflows and joint checks

- [Holiday import](../holiday-import/SPEC.md): Imported candidates become local events.
- [Attendance policy](../attendance-policy/SPEC.md): Policy and calendar jointly determine date eligibility.

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
| [Create and edit events](#create-and-edit-events) | Admin manages eligible events with impact preview and retained past history | [CAL-001](SPEC.md), [CAL-007](SPEC.md) | [AC-CAL-004](SPEC.md), [AC-CAL-005](SPEC.md) | Include stale-version conflicts and the preview of affected work; non-Admin denial alone is insufficient. |
| [Apply day-off effects](#apply-day-off-effects) | Date consumers apply day-off effects while retaining frozen leave reservations | [CAL-006](SPEC.md), [CAL-008](SPEC.md), [CAL-009](SPEC.md), [ATT-006](../../../attendance/MODULE.md) | [AC-CAL-003](SPEC.md), [AC-ATT-001](../attendance-policy/SPEC.md) | Add a joint calendar/leave/Task-due-date case; format or date classification alone does not prove frozen allocations. |

### Canonical acceptance scenarios

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-CAL-003 | CAL-006–CAL-009 | Admin marks observed holiday as display-only, then as day off before date | Display-only date remains eligible; day-off version suppresses new attendance/quota and new due dates. |
| AC-CAL-004 | CAL-003, CAL-007, UI-019 | Admin attempts to edit an event after its date and opens Calendar History | Mutation is rejected; retained event/provenance metadata remains read-only and historical daily classification remains unchanged. |
| AC-CAL-005 | CAL-001 | A Mentor and an Intern each attempt to create and to edit a global calendar event, and a client attempts to attach a calendar override to one Project | All non-Admin attempts are refused before any write; no schema path or endpoint accepts a project-scoped calendar override; Admin succeeds for both a custom and an imported event. |

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
