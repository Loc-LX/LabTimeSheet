# Daily Project Work Report Spec

**Version:** 1.1.0 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-21

**Module:** `reporting` · **Shared contract:** [MODULE.md](../../MODULE.md)

## 1. Context & Goal

Show selected-date Task contributions and the distinct whole-Task planning context.

This is one cohesive planning scope. Operations below are sections of this feature,
not separate modules or mandatory branches. The split changes document ownership only.

## 2. Actors & Roles

Owning Mentor; current Leader with one exact eligible Project; active Admin with read-only report scope.

Use cases defined here: UC-16.

The [platform permission matrix](../../../platform/features/authorization/SPEC.md#52-permission-matrix)
and the module's shared authorization rules apply to every operation.

## 3. Functional Requirements

The operation summaries below are navigation. The numbered rules remain authoritative;
they do not acquire additional behavior from a heading or summary.

### Resolve scope and selected date

RPT-011 defines navigation, selector/redirect behavior, Project eligibility and authorization before downstream reads.

### Build Task and Intern views

RPT-012 and RPT-014 distinguish selected-date minutes from whole-Task planning and keep totals meaningful.

### Export the same dataset

RPT-013 requires identical authorized content across HTML, XLSX and PDF.

### Canonical feature rules

| ID | Requirement |
|---|---|
| RPT-011 | THE system SHALL permit an owning Mentor to request a Daily Project Work Report for all owned Projects or one selected owned Project, and SHALL keep the global Daily entry available to Mentors. WHILE an active Intern currently leads at least one `PLANNED` or `ACTIVE` Project, THE system SHALL show them a conditional Daily entry: WHERE exactly one Project is eligible it SHALL redirect to that locked report, WHERE several are eligible it SHALL show an authorized selector, and WHERE none is eligible it SHALL return `Project unavailable`. THE system SHALL permit a current Leader to request HTML, XLSX, or PDF for one mandatory exact currently-led `PLANNED` or `ACTIVE` Project, for today or a permitted past Report date, covering the whole retained Project history including the period before the current leadership term. THE system SHALL re-authorize the exact `projectId` on every Leader request and SHALL carry a valid selected date through selection and redirection. THE system SHALL permit an active Admin to request, read-only, a Daily Project Work Report for all Projects or one selected Project in HTML, XLSX, or PDF, and SHALL keep the global Daily entry available to Admins. WHERE a Mentor or Admin requests all Projects, THE system SHALL leave `CANCELLED` Projects out unless the request includes them through a filter, and SHALL keep a cancelled Project's retained work available when it is selected. WHERE the caller is an ordinary Intern without current leadership, a former Leader, the Leader of another Project, or names a completed Project, a missing `projectId`, or a guessed identifier, THE system SHALL deny before reading Attendance context or Tasks and before invoking an exporter. |
| RPT-012 | THE system SHALL keep the Daily Project Work Report's selected-date minutes distinct from lifetime Actual Task effort, the estimate, the current Remaining effort, Current Work, and the variance of `TSK-021`. WHERE the mode is all-Projects, THE system SHALL omit an empty Project; WHERE one empty Project is selected, THE system SHALL show an explicit empty state. THE system SHALL NOT assert attendance, completion on that date, productivity, or efficiency. |
| RPT-013 | THE system SHALL build the HTML, XLSX, and PDF Daily Project Work Reports from one authorized immutable dataset, and SHALL expose identical rows, descriptions, statuses, planning values, and hand-checkable totals in all three. |
| RPT-014 | THE system SHALL present the Daily Project Work Report in two perspectives in every format. The Task view SHALL show each Task with work on the selected date once, with its current status, estimate, Actual Task effort, current Remaining effort and the date of the forecast it derives from, Current Work, and variance, followed by each contributor and their selected-date minutes. The Intern view SHALL show each work-log author with their Tasks, work descriptions, and selected-date minutes, and SHALL NOT repeat a Task's planning values under an author. THE system SHALL total selected-date minutes only. |

#### UC-16 — Review the Daily Project Work Report

| Field | Specification |
|---|---|
| Primary actor(s) | Owning Mentor; current Leader of an eligible Project; Admin, read-only |
| Trigger | A user opens the Daily Project Work Report for a Report date, or exports it. |
| Preconditions | The user is authorized for every Project in the requested scope under `RPT-011`. |
| Postconditions | The date's work is shown in a Task view and an Intern view, identically in HTML, XLSX, and PDF, without any attendance, completion, or productivity claim. |
| Traced requirements | RPT-011–RPT-014, TSK-021 |

**Main success flow**

1. Choose a Report date and either all authorized Projects or one Project.
2. Read the Task view: each Task worked that day once, with its status, estimate, Actual Task effort, current Remaining effort and the date of its forecast, Current Work, variance, and each contributor's minutes.
3. Read the Intern view: each author's Tasks, work descriptions, and minutes for that date, without Task-level planning values.
4. Export the same dataset to XLSX or PDF.

**Alternatives and exceptions**

- A current Leader reaches only the `PLANNED` or `ACTIVE` Projects they lead; one eligible Project opens directly, several show a selector.
- An all-Projects request leaves cancelled Projects out unless the filter includes them.
- An ordinary Intern, a former Leader, or a guessed identifier is refused without revealing the Project.

## 4. Non-functional Requirements

Inherit [module constraints](../../MODULE.md#4-non-functional-requirements) and
[platform constraints](../../../platform/MODULE.md#4-non-functional-requirements),
including authorization, history, concurrency, server time and test evidence.

## 5. Data

Immutable authorized dataset from retained Project/Task work, with attendance date context that does not assert attendance or productivity.

The [module data contract](../../MODULE.md#5-data) retains ownership, constraints and
checks across features. The full physical model stays in platform §19. A feature folder
does not grant repository/entity access across the module boundaries of ARC-006.

### Required contracts

These are required facts or behaviors, not prescribed cross-module imports or an
automatic implementation order. PLAN resolves delivery predecessors against what
already exists and preserves ARC-005/ARC-006.

| Provider | Required fact or behavior | Rule trace |
|---|---|---|
| [project/work-logs-and-effort](../../../project/features/work-logs-and-effort/SPEC.md) | Retained dated contributions and whole-Task effort | [TSK-013](../../../project/features/work-logs-and-effort/SPEC.md), [TSK-014](../../../project/features/work-logs-and-effort/SPEC.md), [TSK-015](../../../project/features/work-logs-and-effort/SPEC.md), [TSK-016](../../../project/features/work-logs-and-effort/SPEC.md), [TSK-021](../../../project/features/work-logs-and-effort/SPEC.md) |
| [project](../../../project/MODULE.md) | Exact Project and current Leader scope | [AUTH-004](../../../project/MODULE.md), [AUTH-005](../../../project/MODULE.md), [AUTH-006](../../../project/MODULE.md), [AUTH-007](../../../project/MODULE.md), [AUTH-008](../../../project/MODULE.md), [AUTH-009](../../../project/MODULE.md), [AUTH-010](../../../platform/features/authorization/SPEC.md), [AUTH-011](../../../project/MODULE.md) |
| [calendar](../../../calendar/MODULE.md) | Business-date context; work on days off remains valid | [ATT-001](../../../calendar/features/attendance-policy/SPEC.md), [CAL-006](../../../calendar/features/global-calendar/SPEC.md), [CAL-007](../../../calendar/features/global-calendar/SPEC.md), [CAL-008](../../../calendar/features/global-calendar/SPEC.md), [CAL-009](../../../calendar/features/global-calendar/SPEC.md) |
| [reporting](../../MODULE.md) | Authorized formats and bounded export | [RPT-001](../../MODULE.md), [RPT-006](../../MODULE.md), [RPT-007](../../MODULE.md), [RPT-008](../../MODULE.md), [RPT-009](../../MODULE.md), [RPT-010](../../MODULE.md), [ERR-006](../../MODULE.md) |

### Related workflows and joint checks

- [Project and Task reports](../project-task-report/SPEC.md): Shares retained Project scope, not selected-date interpretation.

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
| [Resolve scope and selected date](#resolve-scope-and-selected-date) | Eligible Mentor, exact current Leader or Admin reaches the authorized selected-date scope | [RPT-011](SPEC.md) | [AC-RPT-004](SPEC.md) | Verify zero/one/multiple Leader choices, guessed identifiers and authorization before downstream reads. |
| [Build Task and Intern views](#build-task-and-intern-views) | Reader sees Task and Intern perspectives without double-counting planning values | [RPT-012](SPEC.md), [RPT-014](SPEC.md), [TSK-021](../../../project/features/work-logs-and-effort/SPEC.md) | [AC-RPT-006](SPEC.md) | Hand-calculate selected-date minutes separately from lifetime effort and forecasts. |
| [Export the same dataset](#export-the-same-dataset) | Authorized exporter produces the same immutable Daily dataset in each format | [RPT-013](SPEC.md), [RPT-007](../../MODULE.md), [RPT-008](../../MODULE.md), [RPT-009](../../MODULE.md), [RPT-010](../../MODULE.md), [ERR-006](../../MODULE.md) | [AC-RPT-005](SPEC.md), [AC-RPT-003](../../MODULE.md), [AC-ERR-006](../../MODULE.md) | Keep Unicode, safe filename, bounded date and failed-export cleanup cases. |

### Canonical acceptance scenarios

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-RPT-004 | RPT-011–RPT-012 | Owning Mentor requests Daily Project Work Report for all owned or one selected owned Project; current Leader requests one mandatory exact currently-led `PLANNED`/`ACTIVE` Project in HTML, XLSX, or PDF for a past, current, policy non-workday, global day off, empty scope, or soft-deleted Task; an active Intern follows the conditional Leader navigation with zero, one, or multiple eligible Projects; Admin requests Daily HTML/XLSX/PDF | Authorized retained logs remain grouped by historical author and described with current status/deletion marker, including work before the current leadership term; date labels never filter valid Task work; one eligible Leader Project redirects to its locked report, multiple show only an authorized selector, none returns `Project unavailable`, and selected date survives selection/redirect. An active Admin receives a read-only report for all Projects or a selected one. Ordinary Intern without current leadership, former Leader, other-Project Leader, completed Project, missing `projectId`, and guessed-ID requests are denied before downstream reads or exporter invocation. A cancelled Project is left out of an all-Projects request unless the filter includes it. |
| AC-RPT-005 | RPT-013 | The same Daily dataset is rendered as HTML, XLSX, and PDF | All formats contain identical authorized Projects, authors, Tasks, descriptions, statuses, planning fields, and totals; no format adds attendance/completion/productivity claims. |
| AC-RPT-006 | RPT-014, TSK-021 | On one date a `DONE` estimated Task is logged by two authors, and an unfinished Task with a forecast by one of them | The Task view shows each Task once with its planning values and both contributors' minutes; the Intern view shows each author's minutes and descriptions with no estimate, forecast, Current Work, or variance; totals equal the sum of selected-date minutes in HTML, XLSX, and PDF. |

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
