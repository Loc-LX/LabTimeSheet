# Project and Task reports Spec

**Version:** 1.1.0 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-21

**Module:** `reporting` · **Shared contract:** [MODULE.md](../../MODULE.md)

## 1. Context & Goal

Read authorized Project progress and Task work with explicit per-member visibility.

This is one cohesive planning scope. Operations below are sections of this feature,
not separate modules or mandatory branches. The split changes document ownership only.

## 2. Actors & Roles

Owning Mentor, current Leader, ordinary member and active Admin within the distinct scopes of RPT-005.

The [platform permission matrix](../../../platform/MODULE.md#52-permission-matrix)
and the module's shared authorization rules apply to every operation.

## 3. Functional Requirements

The operation summaries below are navigation. The numbered rules remain authoritative;
they do not acquire additional behavior from a heading or summary.

### Filter Project and Task data

RPT-003 retains completed/cancelled Projects and defines status, date, assignee and logged-minute fields.

### Apply per-member visibility

RPT-005 distinguishes aggregate member visibility, owning Mentor/current Leader access and Admin read-only access.

### Canonical feature rules

| ID | Requirement |
|---|---|
| RPT-003 | THE system SHALL filter a Project and Task report by Project, member, Task status, and work-date range, and SHALL include the completion percentage, status counts, current assignees, due dates, logged minutes, and blocked Tasks. THE system SHALL keep `COMPLETED` and `CANCELLED` Projects in the report, each labelled with its status. |
| RPT-005 | THE system SHALL show per-member Project hours to an owning Mentor within their owned-Project scope, and to a current Leader only for a Project they currently lead. THE system SHALL show an ordinary member aggregate Project progress and hours only. THE system SHALL give an active Admin read-only Project and Task report scope for every Project, including the Project option list, the report dataset, per-member hours, and XLSX and PDF export. |



## 4. Non-functional Requirements

Inherit [module constraints](../../MODULE.md#4-non-functional-requirements) and
[platform constraints](../../../platform/MODULE.md#4-non-functional-requirements),
including authorization, history, concurrency, server time and test evidence.

## 5. Data

Authorized Project, membership, Task and work-log datasets; no report-owned domain tables.

The [module data contract](../../MODULE.md#5-data) retains ownership, constraints and
checks across features. The full physical model stays in platform §19. A feature folder
does not grant repository/entity access across the module boundaries of ARC-006.

### Required contracts

These are required facts or behaviors, not prescribed cross-module imports or an
automatic implementation order. PLAN resolves delivery predecessors against what
already exists and preserves ARC-005/ARC-006.

| Provider | Required fact or behavior | Rule trace |
|---|---|---|
| [project/lifecycle](../../../project/features/lifecycle/SPEC.md) | Retained Project status and progress | [PRJ-015](../../../project/features/lifecycle/SPEC.md), [PRJ-016](../../../project/features/lifecycle/SPEC.md), [PRJ-023](../../../project/features/lifecycle/SPEC.md) |
| [project/work-logs-and-effort](../../../project/features/work-logs-and-effort/SPEC.md) | Work totals and effort semantics | [TSK-013](../../../project/features/work-logs-and-effort/SPEC.md), [TSK-014](../../../project/features/work-logs-and-effort/SPEC.md), [TSK-015](../../../project/features/work-logs-and-effort/SPEC.md), [TSK-016](../../../project/features/work-logs-and-effort/SPEC.md), [TSK-021](../../../project/features/work-logs-and-effort/SPEC.md) |
| [project](../../../project/MODULE.md) | Stored Project/member/Leader scope | [AUTH-004](../../../project/MODULE.md), [AUTH-005](../../../project/MODULE.md), [AUTH-006](../../../project/MODULE.md), [AUTH-007](../../../project/MODULE.md), [AUTH-008](../../../project/MODULE.md), [AUTH-009](../../../project/MODULE.md), [AUTH-010](../../../platform/MODULE.md), [AUTH-011](../../../project/MODULE.md) |
| [reporting](../../MODULE.md) | Shared export pipeline and cleanup | [RPT-001](../../MODULE.md), [RPT-006](../../MODULE.md), [RPT-007](../../MODULE.md), [RPT-008](../../MODULE.md), [RPT-009](../../MODULE.md), [RPT-010](../../MODULE.md), [ERR-006](../../MODULE.md) |

### Related workflows and joint checks

- [Daily Project Work Report](../daily-work-report/SPEC.md): Selected-date minutes are a distinct report concept.

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
| [Filter Project and Task data](#filter-project-and-task-data) | Authorized reader filters retained Project/Task progress and work | [RPT-001](../../MODULE.md), [RPT-003](SPEC.md), [RPT-006](../../MODULE.md), [RPT-007](../../MODULE.md), [RPT-008](../../MODULE.md), [RPT-009](../../MODULE.md), [RPT-010](../../MODULE.md) | [AC-RPT-001](../../MODULE.md), [AC-RPT-003](../../MODULE.md) | Shared format checks need explicit Project/member/status/work-date filter fixtures and zero-denominator totals. |
| [Apply per-member visibility](#apply-per-member-visibility) | Reader receives per-member or aggregate data according to stored scope | [RPT-005](SPEC.md), [AUTH-010](../../../platform/MODULE.md) | [AC-RPT-002](../../MODULE.md), [AC-AUTH-008](../../../platform/MODULE.md) | Cover owning/non-owning Mentor, current/former Leader, member and read-only Admin, including export attempts. |

### Canonical acceptance scenarios

Use shared `AC-RPT-001` and `AC-RPT-002` in [MODULE.md](../../MODULE.md); copying their rows here would create two canonical definitions.

Shared and cross-feature scenarios in [MODULE.md](../../MODULE.md#7-acceptance-criteria)
also apply. Scenario ownership follows the behavior exercised, not every prerequisite
named by its Requirements cell. Relocation preserves every existing expected result.

## 8. Out of Scope

Inherit [module exclusions](../../MODULE.md#8-out-of-scope). Adjacent feature behavior
is a dependency, not a duplicated contract. This split creates no new product behavior,
schema, dependency, PLAN.md or TASKS.md.

## Notes / Open Questions

Acceptance is covered by the shared AC-RPT-001 and AC-RPT-002 scenarios in MODULE.md; there is no separate dedicated scenario to copy or fabricate.

Read [shared open questions](../../MODULE.md#notes--open-questions) before approving
the technical plan. [plan.md](../../../../../plan.md) is the only progress tracker.
