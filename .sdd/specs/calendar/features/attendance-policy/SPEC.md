# Attendance policy Spec

**Version:** 1.1.0 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-21

**Module:** `calendar` · **Shared contract:** [MODULE.md](../../MODULE.md)

## 1. Context & Goal

Maintain the effective-dated global schedule and policy history.

This is one cohesive planning scope. Operations below are sections of this feature,
not separate modules or mandatory branches. The split changes document ownership only.

## 2. Actors & Roles

Admin writes future policy; attendance and reporting read the policy applicable to a date.

The [platform permission matrix](../../../platform/MODULE.md#52-permission-matrix)
and the module's shared authorization rules apply to every operation.

## 3. Functional Requirements

The operation summaries below are navigation. The numbered rules remain authoritative;
they do not acquire additional behavior from a heading or summary.

### Seed policy

ATT-002 and DB-009 define the initial timeline and ISO weekdays.

### Schedule and validate a version

ATT-001 and ATT-003 define future-month scheduling, numeric limits and effective-version immutability.

### Read history

ATT-001 retains non-secret version and effective metadata.

### Canonical feature rules

| ID | Requirement |
|---|---|
| ATT-001 | THE system SHALL maintain one effective-dated global attendance-policy timeline managed by an Admin. THE system SHALL store, in each version, the timezone, scheduled start and end, check-in grace minutes, checkout grace minutes, monthly leave quota, violation penalty, and configured ISO weekdays. THE system SHALL expose a read-only policy History built from those retained versions and their non-secret creator and effective metadata. |
| ATT-002 | THE system SHALL seed one policy version effective `1970-01-01` with timezone `Asia/Ho_Chi_Minh`, Monday through Friday, 08:30 to 15:30, 30-minute check-in grace, 30-minute checkout grace, 3 leave workdays per month, and a 0.25 penalty per applicable violation. |
| ATT-003 | THE system SHALL require each grace value to be an integer from 0 through 720 minutes, and SHALL require scheduled end plus checkout grace to fall strictly before the next local midnight, so that an overnight schedule cannot be configured. THE system SHALL require the monthly leave quota to be an integer from 0 through 4, because four days is the largest whole number that stays within one fifth of the shortest twenty-workday month. THE system SHALL require a new policy version to begin on the first day of a future calendar month, SHALL collect that month in the Admin form and derive its first day on the server before applying the same validation, SHALL permit a not-yet-effective version to be replaced, and SHALL treat an effective version as immutable. |
| DB-009 | THE schema seed SHALL create the `1970-01-01` policy version and ISO workdays 1 through 5. |



## 4. Non-functional Requirements

Inherit [module constraints](../../MODULE.md#4-non-functional-requirements) and
[platform constraints](../../../platform/MODULE.md#4-non-functional-requirements),
including authorization, history, concurrency, server time and test evidence.

## 5. Data

`attendance_policy_versions`, `attendance_policy_workdays`.

The [module data contract](../../MODULE.md#5-data) retains ownership, constraints and
checks across features. The full physical model stays in platform §19. A feature folder
does not grant repository/entity access across the module boundaries of ARC-006.

### Required contracts

These are required facts or behaviors, not prescribed cross-module imports or an
automatic implementation order. PLAN resolves delivery predecessors against what
already exists and preserves ARC-005/ARC-006.

| Provider | Required fact or behavior | Rule trace |
|---|---|---|
| [platform](../../../platform/MODULE.md) | Historical stability and business-time semantics | [GOV-005](../../../platform/MODULE.md), [GOV-011](../../../platform/MODULE.md) |

### Related workflows and joint checks

- [Global calendar](../global-calendar/SPEC.md): Local days off complement the global workweek.

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
| [Seed policy](#seed-policy) | Installation obtains the prescribed baseline global policy | [ATT-002](SPEC.md), [DB-009](SPEC.md) | [AC-DB-001](../../../platform/MODULE.md) | The shared schema replay is broader than this operation; include explicit seed values and weekdays in the policy test design. |
| [Schedule and validate a version](#schedule-and-validate-a-version) | Admin schedules a valid future-month policy without changing prior snapshots | [ATT-001](SPEC.md), [ATT-003](SPEC.md) | [AC-ATT-001](SPEC.md) | Cover each numeric/date boundary, including rejected crafted form input. |
| [Read history](#read-history) | Admin reads retained non-secret policy history | [ATT-001](SPEC.md) | [AC-ATT-001](SPEC.md) | History is part of the same scenario; verify role denial through the shared authorization cases. |

### Canonical acceptance scenarios

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-ATT-001 | ATT-001–ATT-006, UI-014, UI-019 | Admin selects a future month, schedules different workdays/penalty and zero checkout grace, then opens Policy History and submits crafted arbitrary-day, out-of-range-grace, or midnight-crossing requests | The server derives day 1 from the selected month; every invalid direct request is rejected; History shows non-secret version/effective metadata; past/current policy, an older attendance row's cutoff, reports, and existing leave allocations do not drift. |

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
