# Work logs and effort Spec

**Version:** 1.1.0 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-21

**Module:** `project` · **Shared contract:** [MODULE.md](../../MODULE.md)

## 1. Context & Goal

Record work and derive whole-Task effort without turning it into attendance or individual productivity.

This is one cohesive planning scope. Operations below are sections of this feature,
not separate modules or mandatory branches. The split changes document ownership only.

## 2. Actors & Roles

Authorized log author; current Leader manages estimates and remaining-effort forecasts.

The [platform permission matrix](../../../platform/features/authorization/SPEC.md#52-permission-matrix)
and the module's shared authorization rules apply to every operation.

## 3. Functional Requirements

The operation summaries below are navigation. The numbered rules remain authoritative;
they do not acquire additional behavior from a heading or summary.

### Record and correct work

TSK-013 through TSK-017 define author scope, date/minute limits and corrections after reassignment.

### Set estimate

TSK-020 governs the optional whole-Task estimate and freezes it after the first retained log.

### Forecast remaining effort

TSK-022 and TSK-024 govern append-only forecasts and worked-Task transfer snapshots.

### Calculate effort and variance

TSK-021 distinguishes lifetime actual, current remaining, Current Work and variance; it defines Pending and N/A.

### Canonical feature rules

| ID | Requirement |
|---|---|
| TSK-013 | THE system SHALL permit only the current assignee to create a work log for a Task, and SHALL require each entry to carry a work date, minutes from 1 through 1440, and an optional nonblank note. |
| TSK-014 | THE system SHALL refuse a work date that is in the future, outside the Project dates, or outside the logging member's membership interval. |
| TSK-015 | THE system SHALL refuse a work log that would take an Intern's combined Task work across all Projects above 1440 minutes on one local date, and SHALL serialize that check on the Intern so that concurrent submissions cannot over-allocate. |
| TSK-016 | WHILE the Project is active and the author remains a member, THE system SHALL permit the log author to correct their own work log, even after the Task has been reassigned. THE system SHALL refuse any other user's attempt to edit that log. |
| TSK-017 | Day-off effects on Task activity are defined by `CAL-009`, and the separation of Task work from attendance by `GOV-004`. This entry exists so that a reader of the Task domain reaches those rules; it adds nothing of its own. |
| TSK-020 | THE system SHALL permit a Task to carry one optional whole-Task estimate in integer minutes from 1 through 527040. THE system SHALL permit only the current Project Leader to set, replace, or clear it, and only before the first retained work log. WHEN the first work log is retained, THE system SHALL make the estimate immutable. THE system SHALL treat estimate mutation as separate from ordinary Task editing and SHALL reject it from any other actor. |
| TSK-021 | THE system SHALL compute Actual Task effort as the lifetime sum of retained work-log minutes across every author and assignment. THE system SHALL compute the current Remaining effort as zero for a `DONE` Task and otherwise as the latest effective Remaining effort forecast, which is the most recently recorded forecast that no correction has superseded, less the Actual Task effort added since that forecast's actual-effort snapshot, never below zero, and SHALL compute Current Work as Actual Task effort plus current Remaining effort. WHERE a Task carries an estimate and is `DONE` or has a Remaining effort forecast, THE system SHALL compute variance as Current Work minus the estimate. WHERE a Task carries an estimate, is unfinished, and has no forecast, THE system SHALL leave variance undefined and render it as `Pending`. WHERE a Task carries no estimate, THE system SHALL leave variance undefined and render it as `N/A`. Earlier forecasts SHALL remain as history and SHALL NOT feed the current value. THE system SHALL NOT derive an efficiency or productivity score from any of these. |
| TSK-022 | WHERE an unfinished Task carrying retained work is reassigned, THE system SHALL require an append-only Remaining effort forecast from the current Leader containing the reassignment snapshot. THE system SHALL accept a correcting successor only before the incoming assignee's first newly created work log. WHILE a member owns a worked unfinished Task, THE system SHALL refuse direct Mentor removal, and SHALL NOT fabricate Leader provenance for a forecast. |
| TSK-024 | WHILE a Project is `ACTIVE`, THE system SHALL permit its current Leader to append a Remaining effort forecast for any unfinished Task whenever the prediction of the effort still needed changes. THE system SHALL record with each forecast the Actual Task effort at that moment, SHALL NOT edit or replace an earlier forecast, and SHALL use the latest one under `TSK-021`. A forecast SHALL NOT change the estimate. |
| DB-013 | THE schema SHALL store a Task estimate as nullable whole-Task integer minutes constrained to `1..527040`, with no backfill. THE schema SHALL store Remaining effort forecasts as append-only rows carrying same-Project Task and membership references, the start of the assignment the forecast applies to, remaining minutes, a nonnegative lifetime-actual snapshot, an optional initial note, the correction reason and supersession shape, linear successors, and indexes for Task history and latest lookup. THE schema SHALL NOT persist a derived forecast total. |



## 4. Non-functional Requirements

Inherit [module constraints](../../MODULE.md#4-non-functional-requirements) and
[platform constraints](../../../platform/MODULE.md#4-non-functional-requirements),
including authorization, history, concurrency, server time and test evidence.

## 5. Data

`task_work_logs`, `task_remaining_effort_forecasts`; estimate is stored on `tasks`.

The [module data contract](../../MODULE.md#5-data) retains ownership, constraints and
checks across features. The full physical model stays in platform §19. A feature folder
does not grant repository/entity access across the module boundaries of ARC-006.

### Required contracts

These are required facts or behaviors, not prescribed cross-module imports or an
automatic implementation order. PLAN resolves delivery predecessors against what
already exists and preserves ARC-005/ARC-006.

| Provider | Required fact or behavior | Rule trace |
|---|---|---|
| [project/task-management](../task-management/SPEC.md) | Task status, current assignment and reassignment context | [TSK-007](../task-management/SPEC.md), [TSK-009](../task-management/SPEC.md) |
| [project/membership-and-leadership](../membership-and-leadership/SPEC.md) | Current Leader and member intervals | [PRJ-005](../membership-and-leadership/SPEC.md) |
| [project/lifecycle](../lifecycle/SPEC.md) | Active Project and work-date bounds | [PRJ-002](../lifecycle/SPEC.md), [PRJ-024](../lifecycle/SPEC.md) |
| [internship](../../../internship/MODULE.md) | Intern interval and per-Intern lock owner under DB-008 | [ACC-019](../../../internship/features/lifecycle/SPEC.md) |
| [platform](../../../platform/MODULE.md) | Daily-work serialization and independence from attendance | [DB-008](../../../platform/features/data-model/SPEC.md), [GOV-004](../../../platform/MODULE.md) |

### Related workflows and joint checks

- [Task management](../task-management/SPEC.md): Reassignment/status changes affect effort guards.
- [Membership exit and transfer](../membership-exit/SPEC.md): Worked-task exit transfer requires Leader provenance.

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
| [Record and correct work](#record-and-correct-work) | Eligible log author records and corrects their own work without creating attendance | [TSK-013](SPEC.md), [TSK-014](SPEC.md), [TSK-015](SPEC.md), [TSK-016](SPEC.md), [TSK-017](SPEC.md) | [AC-TSK-007](SPEC.md), [AC-TSK-008](SPEC.md), [AC-TSK-009](SPEC.md) | Include concurrent totals across Projects and correction after reassignment. |
| [Set estimate](#set-estimate) | Current Leader changes the optional whole-Task estimate only before retained work | [TSK-020](SPEC.md) | [AC-TSK-012](SPEC.md) | Test value boundaries, clearing and the first-log race; ordinary Task editing grants no estimate permission. |
| [Forecast remaining effort](#forecast-remaining-effort) | Current Leader appends a forecast or permitted correction with retained provenance | [TSK-022](SPEC.md), [TSK-024](SPEC.md), [DB-013](SPEC.md) | [AC-TSK-013](SPEC.md), [AC-TSK-017](SPEC.md), [AC-DB-003](../../MODULE.md) | Distinguish the correction cutoff from a later independent forecast; keep all earlier rows. |
| [Calculate effort and variance](#calculate-effort-and-variance) | Authorized reader sees hand-calculable actual, remaining, Current Work and variance | [TSK-021](SPEC.md) | [AC-TSK-012](SPEC.md), [AC-TSK-017](SPEC.md) | Cover DONE, reopened, no-estimate and no-forecast states; selected-date report minutes remain distinct. |

### Canonical acceptance scenarios

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-TSK-007 | TSK-013–TSK-016 | Assignee logs work, Task is reassigned, former assignee corrects own prior log | Log remains attributed; correction succeeds only while former assignee is still active member and Project active. |
| AC-TSK-008 | TSK-015, DB-008 | Concurrent logs would total 1,441 minutes across Projects | Serialization permits at most totals through 1,440; one transaction is rejected without partial write. |
| AC-TSK-009 | GOV-004, TSK-017 | Intern logs work on global day off without attendance | Log succeeds; no attendance record or implied presence is created. |
| AC-TSK-012 | TSK-020–TSK-021 | Leader creates and edits an estimate before work, then a retained log is created and the Task is completed | Valid optional estimate persists, ordinary-member forged mutation is denied, first retained log freezes the estimate, lifetime actual spans authors/assignments, and a DONE Task's variance is signed actual minus estimate. |
| AC-TSK-013 | TSK-022 | Leader reassigns worked and unworked unfinished Tasks, then corrects the latest forecast | Worked reassignment requires an atomic forecast snapshot; unworked reassignment rejects unsolicited forecast; correction appends one successor before the incoming member's first new log and rejects stale/late/unauthorized corrections. |
| AC-TSK-017 | TSK-021, TSK-024 | A Task estimated at 600 minutes has 200 logged; the Leader records 300 remaining; 120 more are logged; the Leader records 250 remaining; 220 more are logged and the Task is completed | After the first forecast Current Work is 500 and variance −100; after the next 120 minutes current Remaining is 180 and Current Work stays 500; after the second forecast Current Work is 570 and variance −30; once `DONE`, Current Work is 540 and variance −60. The estimate never changes and both forecasts remain in history. |

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
