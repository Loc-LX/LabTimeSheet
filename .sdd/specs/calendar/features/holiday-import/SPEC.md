# Holiday import Spec

**Version:** 1.1.0 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-21

**Module:** `calendar` · **Shared contract:** [MODULE.md](../../MODULE.md)

## 1. Context & Goal

Preview Vietnam holidays and explicitly copy selected candidates into the local calendar.

This is one cohesive planning scope. Operations below are sections of this feature,
not separate modules or mandatory branches. The split changes document ownership only.

## 2. Actors & Roles

Admin invokes preview/import and manages HolidayAPI configuration.

The [platform permission matrix](../../../platform/MODULE.md#52-permission-matrix)
and the module's shared authorization rules apply to every operation.

## 3. Functional Requirements

The operation summaries below are navigation. The numbered rules remain authoritative;
they do not acquire additional behavior from a heading or summary.

### Configure and test HolidayAPI

INT-009 inherits the shared integration secret and revision contract.

### Preview and select

CAL-002 and CAL-003 constrain invocation, provenance and suggested day-off selection.

### Import and handle failure

CAL-004 and CAL-005 prohibit silent overwrite and preserve manual entry when the provider is unavailable.

### Canonical feature rules

| ID | Requirement |
|---|---|
| CAL-002 | THE system SHALL treat the HolidayAPI integration as optional and fixed to country `VN`, and SHALL call it only from an explicit Admin preview or import action. THE system SHALL NOT call it from attendance, leave, a dashboard, or a report. |
| CAL-003 | WHEN an import preview is produced, THE system SHALL preserve the source UUID, name, actual date, observed date, public-holiday marker, import timestamp, and the non-secret provenance the Admin Calendar History needs. WHERE a candidate carries `public=true`, THE system SHALL preselect the local `is_day_off` choice without forcing it. |
| CAL-004 | THE system SHALL require an Admin to review and explicitly select the rows to import, SHALL copy the selected data locally, and SHALL NOT silently overwrite an existing source UUID. |
| CAL-005 | WHERE the HolidayAPI key is absent, invalid, rate-limited, or the service is unavailable, THE system SHALL keep manual custom calendar entry available. |
| INT-009 | THE system SHALL apply the same draft, test, activate and retire behavior, the same encrypted key handling, and the fixed country code `VN` to the HolidayAPI configuration, and SHALL expose a read-only HolidayAPI History carrying non-secret revision metadata and outcomes. |



## 4. Non-functional Requirements

Inherit [module constraints](../../MODULE.md#4-non-functional-requirements) and
[platform constraints](../../../platform/MODULE.md#4-non-functional-requirements),
including authorization, history, concurrency, server time and test evidence.

## 5. Data

`holiday_api_configurations`, imported `global_calendar_events`.

The [module data contract](../../MODULE.md#5-data) retains ownership, constraints and
checks across features. The full physical model stays in platform §19. A feature folder
does not grant repository/entity access across the module boundaries of ARC-006.

### Required contracts

These are required facts or behaviors, not prescribed cross-module imports or an
automatic implementation order. PLAN resolves delivery predecessors against what
already exists and preserves ARC-005/ARC-006.

| Provider | Required fact or behavior | Rule trace |
|---|---|---|
| [calendar/global-calendar](../global-calendar/SPEC.md) | Local event mutation, day-off and history rules | [CAL-001](../global-calendar/SPEC.md), [CAL-006](../global-calendar/SPEC.md), [CAL-007](../global-calendar/SPEC.md) |
| [platform](../../../platform/MODULE.md) | Shared encrypted-secret and redaction contract | [INT-002](../../../platform/MODULE.md), [INT-003](../../../platform/MODULE.md), [INT-004](../../../platform/MODULE.md), [INT-005](../../../platform/MODULE.md), [INT-010](../../../platform/MODULE.md) |

### Related workflows and joint checks

- [Global calendar](../global-calendar/SPEC.md): Owns local day-off behavior.
- [Attendance policy](../attendance-policy/SPEC.md): Verify together that policy resolution never invokes HolidayAPI.

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
| [Configure and test HolidayAPI](#configure-and-test-holidayapi) | Admin tests and activates an encrypted HolidayAPI revision | [INT-009](SPEC.md), [INT-002](../../../platform/MODULE.md), [INT-003](../../../platform/MODULE.md), [INT-004](../../../platform/MODULE.md), [INT-005](../../../platform/MODULE.md), [INT-010](../../../platform/MODULE.md) | [AC-INT-001](../../../platform/MODULE.md), [AC-INT-002](../../../platform/MODULE.md), [AC-INT-004](../../../platform/MODULE.md) | Shared integration cases cover SMTP and HolidayAPI; exercise this provider without a live external service. |
| [Preview and select](#preview-and-select) | Admin sees Vietnam candidates with provenance and can override suggested selection | [CAL-002](SPEC.md), [CAL-003](SPEC.md) | [AC-CAL-002](SPEC.md) | Preserve source provenance and prove no provider request is triggered by attendance/report reads. |
| [Import and handle failure](#import-and-handle-failure) | Admin imports selected local events safely and can still enter events during provider failure | [CAL-004](SPEC.md), [CAL-005](SPEC.md) | [AC-CAL-001](SPEC.md), [AC-CAL-002](SPEC.md) | Include duplicate UUID handling and each declared provider failure mode. |

### Canonical acceptance scenarios

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-CAL-001 | CAL-002–CAL-005 | HolidayAPI unavailable or unconfigured | Preview reports actionable failure; Admin can add custom event; attendance/reporting continue from local data. |
| AC-CAL-002 | CAL-003–CAL-004 | Preview returns public and non-public Vietnam events | Public is preselected only; Admin can toggle either; selected rows preserve provenance and duplicate UUID import is rejected/idempotent. |

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
