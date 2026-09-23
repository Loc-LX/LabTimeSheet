# Attendance reports Spec

**Version:** 1.1.0 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-21

**Module:** `reporting` · **Shared contract:** [MODULE.md](../../MODULE.md)

## 1. Context & Goal

Read authorized attendance history and reports, including the Intern own-date view.

This is one cohesive planning scope. Operations below are sections of this feature,
not separate modules or mandatory branches. The split changes document ownership only.

## 2. Actors & Roles

Active Admin and Mentor within RPT-004; Intern sees their own history only.

The [platform permission matrix](../../../platform/MODULE.md#52-permission-matrix)
and the module's shared authorization rules apply to every operation.

## 3. Functional Requirements

The operation summaries below are navigation. The numbered rules remain authoritative;
they do not acquire additional behavior from a heading or summary.

### Attendance and compliance report

RPT-002 and RPT-004 define filters, fields and equal Admin/Mentor detailed dataset boundaries.

### Own-date attendance

RPT-015 resolves the Intern from the authenticated account and keeps this view in-app without export.

### Canonical feature rules

| ID | Requirement |
|---|---|
| RPT-002 | THE system SHALL filter an attendance and compliance report by date range and authorized Intern scope, and SHALL include the daily classification, the applied schedule, the raw and effective checkout, the late, early-departure and missing-checkout flags with whether each late arrival or early departure is excused, the attendance rate, and the compliance score. |
| RPT-004 | WHILE a Mentor or Admin account is active, THE system SHALL permit it to view detailed Intern attendance for any target account whose immutable role is `INTERN`, with the same target scope, navigation, HTML page, XLSX and PDF export, and report dataset. That dataset SHALL consist of the Intern's display name and Student Code, the selected period, the expected, present and absent workday counts, the attendance rate and compliance score, and for each day the work date, check-in, raw checkout, effective checkout, applied schedule, daily classification with its late, early-departure and missing-checkout flags and whether each late arrival or early departure is excused, and elapsed time. THE system SHALL show an Admin no attendance field outside that dataset. THE system SHALL permit an Intern to view their own history only. THE system SHALL NOT grant a Project Leader another Intern's attendance on account of leadership; as an Intern their Attendance scope remains their own history. Admin Attendance scope SHALL NOT imply any Project, Task, or Daily Project Work Report scope. |
| RPT-015 | WHILE an Intern account is active, THE system SHALL permit it to view its own attendance for a selected business date: the date, the raw check-in, the raw and effective checkout, the counted minutes, the late-arrival and early-departure flags, and whether each is excused. THE system SHALL resolve the Intern from the authenticated account rather than from a supplied identifier, and SHALL NOT include another Intern's data, Task effort, estimates, Current Work, variance, or any Project total. THE system SHALL keep this view in the application without an export, which limits the first version rather than protecting the data: it is the Intern's own. |



## 4. Non-functional Requirements

Inherit [module constraints](../../MODULE.md#4-non-functional-requirements) and
[platform constraints](../../../platform/MODULE.md#4-non-functional-requirements),
including authorization, history, concurrency, server time and test evidence.

## 5. Data

Authorized read datasets from attendance, internship and identity; no report-owned domain tables.

The [module data contract](../../MODULE.md#5-data) retains ownership, constraints and
checks across features. The full physical model stays in platform §19. A feature folder
does not grant repository/entity access across the module boundaries of ARC-006.

### Required contracts

These are required facts or behaviors, not prescribed cross-module imports or an
automatic implementation order. PLAN resolves delivery predecessors against what
already exists and preserves ARC-005/ARC-006.

| Provider | Required fact or behavior | Rule trace |
|---|---|---|
| [attendance](../../../attendance/MODULE.md) | Authorized daily/period classifications and totals | [ATT-013](../../../attendance/features/punches-and-results/SPEC.md), [ATT-014](../../../attendance/features/punches-and-results/SPEC.md), [ATT-015](../../../attendance/features/punches-and-results/SPEC.md), [ATT-016](../../../attendance/features/punches-and-results/SPEC.md), [ATT-017](../../../attendance/features/punches-and-results/SPEC.md), [ATT-018](../../../attendance/features/punches-and-results/SPEC.md) |
| [internship/lifecycle](../../../internship/features/lifecycle/SPEC.md) | Retained Intern name/code and interval | [ACC-019](../../../internship/features/lifecycle/SPEC.md) |
| [reporting](../../MODULE.md) | Shared authorized export dataset, formats and resource cleanup | [RPT-001](../../MODULE.md), [RPT-006](../../MODULE.md), [RPT-007](../../MODULE.md), [RPT-008](../../MODULE.md), [RPT-009](../../MODULE.md), [RPT-010](../../MODULE.md), [ERR-006](../../MODULE.md) |

### Related workflows and joint checks

- [Project and Task reports](../project-task-report/SPEC.md): Format parity and cross-family scope scenarios remain shared in MODULE.md.

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
| [Attendance and compliance report](#attendance-and-compliance-report) | Authorized Admin/Mentor or the owning Intern receives the permitted attendance dataset | [RPT-001](../../MODULE.md), [RPT-002](SPEC.md), [RPT-004](SPEC.md), [RPT-006](../../MODULE.md), [RPT-007](../../MODULE.md), [RPT-008](../../MODULE.md), [RPT-009](../../MODULE.md), [RPT-010](../../MODULE.md) | [AC-RPT-001](../../MODULE.md), [AC-RPT-002](../../MODULE.md), [AC-RPT-003](../../MODULE.md) | Use independently calculated expected totals, not only format parity; preserve exact role/target scope. |
| [Own-date attendance](#own-date-attendance) | Intern sees only their own selected-date attendance without export | [RPT-015](SPEC.md) | [AC-RPT-007](SPEC.md) | Cover corrected/excused data, empty date and a forged target identifier. |

### Canonical acceptance scenarios

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-RPT-007 | RPT-015, EXC-005, COR-006, AUTH-002 | An Intern opens their own attendance for a date carrying an excused late arrival and an approved missed-checkout correction, then for a date with no attendance row, and finally tries another Intern's identifier | The first date shows the check-in, the effective checkout, the counted minutes and both flags with the late arrival marked excused; the empty date shows an explicit empty state; no estimate, Current Work or variance appears anywhere; the attempt at another Intern's data discloses nothing. |

Shared and cross-feature scenarios in [MODULE.md](../../MODULE.md#7-acceptance-criteria)
also apply. Scenario ownership follows the behavior exercised, not every prerequisite
named by its Requirements cell. Relocation preserves every existing expected result.

## 8. Out of Scope

Inherit [module exclusions](../../MODULE.md#8-out-of-scope). Adjacent feature behavior
is a dependency, not a duplicated contract. This split creates no new product behavior,
schema, dependency, PLAN.md or TASKS.md.

## Notes / Open Questions

AC-RPT-001 and AC-RPT-002 remain shared because they compare report families and formats; they still apply here.

Read [shared open questions](../../MODULE.md#notes--open-questions) before approving
the technical plan. [plan.md](../../../../../plan.md) is the only progress tracker.
