# Reporting Spec

**Version:** 1.1.0 · **Owner:** Loc-LX · **Status:** APPROVED · **Date:** 2026-09-14

Part of the Lab Timesheet specification. [`.sdd/requirements.md`](../../requirements.md) indexes every
spec, rule prefix, and original section number. Rules every feature shares, including the
glossary, the authorization model, the domain model, and failure handling, are in
[the platform spec](../feature-platform/SPEC.md). Section numbers marked `§` are the
numbers the rules carried in the single-file specification and are kept so existing
references still resolve.

## 1. Context & Goal

Authorized dashboards and consistent HTML, Excel, and PDF reports, the fourth area named by the product objective (§1.2 of the platform spec).
In code this is `feature/reporting`: the Attendance, Project/Task, and Daily Project Work reports and their exports. It owns no table and reads through other features' services.

## 2. Actors & Roles

Primary actors named by this feature's use cases:

- **UC-12 — Review and export reports:** Admin for detailed Intern Attendance reports only; Mentor; authorized Intern/Leader for their own/current scope. An Admin has no Project/Task report scope under `RPT-005` and no Daily Project Work Report scope under `RPT-011`.

Every capability by role is in the permission matrix, [platform spec](../feature-platform/SPEC.md) §5.2. Authorization is resolved from stored context, never from the global role alone (§5.1).

## 3. Functional Requirements

### §14. Reports and exports

| ID | Requirement |
|---|---|
| RPT-001 | THE system SHALL feed HTML, Excel, and PDF output from one shared report dataset and query layer, so that filters, classifications, totals, rounding, and authorization cannot drift between formats. |
| RPT-002 | THE system SHALL filter an attendance and compliance report by date range and authorized Intern scope, and SHALL include the daily classification, the applied schedule, the raw and effective checkout, the late, early-departure and missing-checkout flags, the attendance rate, and the compliance score. |
| RPT-003 | THE system SHALL filter a Project and Task report by Project, member, Task status, and work-date range, and SHALL include the completion percentage, status counts, current assignees, due dates, logged minutes, and blocked Tasks. |
| RPT-004 | WHILE a Mentor or Admin account is active, THE system SHALL permit it to view detailed Intern attendance for any target account whose immutable role is `INTERN`, with the same target scope, navigation, HTML page, XLSX and PDF export, and report dataset. That dataset SHALL consist of the Intern's display name and Student Code, the selected period, the expected, present and absent workday counts, the attendance rate and compliance score, and for each day the work date, check-in, raw checkout, effective checkout, applied schedule, daily classification with its late, early-departure and missing-checkout flags, and elapsed time. THE system SHALL show an Admin no attendance field outside that dataset. THE system SHALL permit an Intern to view their own history only. THE system SHALL NOT grant a Project Leader another Intern's attendance on account of leadership; as an Intern their Attendance scope remains their own history. Admin Attendance scope SHALL NOT imply any Project, Task, or Daily Project Work Report scope. |
| RPT-005 | THE system SHALL show per-member Project hours to an owning Mentor within their owned-Project scope, and to a current Leader only for a Project they currently lead. THE system SHALL show an ordinary member aggregate Project progress and hours only. THE system SHALL give an active Admin read-only Project and Task report scope for every Project, including the Project option list, the report dataset, per-member hours, and XLSX and PDF export. |
| RPT-006 | THE system SHALL provide Excel and PDF export for the attendance and compliance, Project and Task, and Daily Project Work Reports. THE system SHALL keep the leave, correction, notification, and integration queues in-app only. |
| RPT-007 | THE system SHALL produce Excel through Apache POI XSSF, and PDF through OpenPDF HTML using a dedicated print-safe Thymeleaf XHTML and CSS template with an embedded Unicode-capable font. THE system SHALL NOT pass the modern Tailwind application stylesheet to the PDF renderer. |
| RPT-008 | THE system MAY perform an export synchronously in v1, and SHALL enforce bounded date ranges and authorized filters so that a request cannot demand unbounded memory or response work. |
| RPT-009 | THE system SHALL produce identical hand-checkable totals in HTML, Excel, and PDF. WHERE Project progress has no non-deleted Task, or an attendance or compliance denominator is zero, THE system SHALL render `N/A`. |
| RPT-010 | THE system SHALL compose an export filename from the report family and the requested date range, and SHALL NOT include a user-controlled path character in it. |
| RPT-011 | THE system SHALL permit an owning Mentor to request a Daily Project Work Report for all owned Projects or one selected owned Project, and SHALL keep the global Daily entry available to Mentors. WHILE an active Intern currently leads at least one `PLANNED` or `ACTIVE` Project, THE system SHALL show them a conditional Daily entry: WHERE exactly one Project is eligible it SHALL redirect to that locked report, WHERE several are eligible it SHALL show an authorized selector, and WHERE none is eligible it SHALL return `Project unavailable`. THE system SHALL permit a current Leader to request HTML, XLSX, or PDF for one mandatory exact currently-led `PLANNED` or `ACTIVE` Project, for today or a permitted past Report date, covering the whole retained Project history including the period before the current leadership term. THE system SHALL re-authorize the exact `projectId` on every Leader request and SHALL carry a valid selected date through selection and redirection. THE system SHALL permit an active Admin to request, read-only, a Daily Project Work Report for all Projects or one selected Project in HTML, XLSX, or PDF, and SHALL keep the global Daily entry available to Admins. WHERE the caller is an ordinary Intern without current leadership, a former Leader, the Leader of another Project, or names a completed Project, a missing `projectId`, or a guessed identifier, THE system SHALL deny before reading Attendance context or Tasks and before invoking an exporter. |
| RPT-012 | THE system SHALL keep the Daily Project Work Report's selected-date minutes distinct from lifetime Actual Task effort, the estimate, the current Remaining effort, Current Work, and the variance of `TSK-021`. WHERE the mode is all-Projects, THE system SHALL omit an empty Project; WHERE one empty Project is selected, THE system SHALL show an explicit empty state. THE system SHALL NOT assert attendance, completion on that date, productivity, or efficiency. |
| RPT-013 | THE system SHALL build the HTML, XLSX, and PDF Daily Project Work Reports from one authorized immutable dataset, and SHALL expose identical rows, descriptions, statuses, planning values, and hand-checkable totals in all three. |
| RPT-014 | THE system SHALL present the Daily Project Work Report in two perspectives in every format. The Task view SHALL show each Task with work on the selected date once, with its current status, estimate, Actual Task effort, current Remaining effort and the date of the forecast it derives from, Current Work, and variance, followed by each contributor and their selected-date minutes. The Intern view SHALL show each work-log author with their Tasks, work descriptions, and selected-date minutes, and SHALL NOT repeat a Task's planning values under an author. THE system SHALL total selected-date minutes only. |

### Use cases

#### UC-12 — Review and export reports

| Field | Specification |
|---|---|
| Primary actor(s) | Admin, read-only, for every report; Mentor; authorized Intern/Leader for their own/current scope. |
| Trigger | A user opens a report or requests Excel/PDF export. |
| Preconditions | The user is authorized for every row in the requested dataset. |
| Postconditions | HTML, XLSX, and PDF expose identical authorized totals without persisting a Report entity. |
| Traced requirements | RPT-001–RPT-010, AUTH-010 |

**Main success flow**

1. Choose date, Project, member, or status filters.
2. Build one authorized report dataset.
3. Render HTML totals and accessible chart/table alternatives.
4. Export the same dataset to XLSX or PDF.
5. Compare totals and no-data behavior across formats.

**Alternatives and exceptions**

- Zero denominators render N/A.
- Ordinary members cannot see per-member work breakdowns.
- Unauthorized identifiers fail without leaking record existence.

**Not covered by this flow.** The Daily Project Work Report shipped after this
use case was written, and its scope rules differ: the audience is the owning
Mentor, whoever currently leads an eligible Project, which is a stored
leadership term rather than a role, and, read-only, an Admin. Read `RPT-011` through `RPT-014` instead of
inferring it from the steps above.

## 4. Non-functional Requirements

System-wide non-functional rules apply unchanged: architecture §3 (`ARC`), authentication and security §13 (`SEC`), interface and accessibility §15 (`UI`), delivery §16 (`OPS`), and test evidence §17 (`TST`), all in the [platform spec](../feature-platform/SPEC.md).

## 5. Data

This feature owns no table. It reads through the services and DTOs of the features whose data it reports.

The conceptual model, the table inventory, the integrity rules (`DB`), and both diagrams are §19 of the [platform spec](../feature-platform/SPEC.md).

## 6. Error Handling

Rules in section 3 whose text names a refusal, rejection, denial, or failure: `RPT-011`.

System-wide failure behavior is §21 (`ERR-001`–`ERR-007`), and the interface message families are Appendix F, both in the [platform spec](../feature-platform/SPEC.md).

## 7. Acceptance Criteria

Scenarios from the §20 acceptance catalogue whose identifiers start with `AC-RPT`. The catalogue's introduction, including the rules deliberately written without a scenario, is in the [platform spec](../feature-platform/SPEC.md).

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-RPT-001 | RPT-001–RPT-009 | Same filter is rendered as HTML, XLSX, and PDF | Row classifications, counts, rates, progress, minutes, rounding, and `N/A` values match exactly. |
| AC-RPT-002 | RPT-002–RPT-005 | An Intern requests another Intern's Attendance, an active Mentor requests authorized detailed Attendance, an owning Mentor/current Leader requests authorized Project/Task detail, an ordinary member requests broader detail, an active Admin requests detailed Intern Attendance HTML/XLSX/PDF, and an Admin requests Project/Task HTML/XLSX/PDF | Intern scope remains own history; active Mentor and authorized owning-Mentor/current-Leader scopes remain available; ordinary members receive aggregate-only Project/Task results; the active Admin receives the same detailed Intern Attendance result an active Mentor receives, and a read-only Project/Task result for every Project including per-member hours. |
| AC-RPT-003 | RPT-007–RPT-010 | Vietnamese names and safe date filter are exported | Unicode renders in XLSX/PDF; filename is deterministic and path-safe. |
| AC-RPT-004 | RPT-011–RPT-012 | Owning Mentor requests Daily Project Work Report for all owned or one selected owned Project; current Leader requests one mandatory exact currently-led `PLANNED`/`ACTIVE` Project in HTML, XLSX, or PDF for a past, current, policy non-workday, global day off, empty scope, or soft-deleted Task; an active Intern follows the conditional Leader navigation with zero, one, or multiple eligible Projects; Admin requests Daily HTML/XLSX/PDF | Authorized retained logs remain grouped by historical author and described with current status/deletion marker, including work before the current leadership term; date labels never filter valid Task work; one eligible Leader Project redirects to its locked report, multiple show only an authorized selector, none returns `Project unavailable`, and selected date survives selection/redirect. An active Admin receives a read-only report for all Projects or a selected one. Ordinary Intern without current leadership, former Leader, other-Project Leader, completed Project, missing `projectId`, and guessed-ID requests are denied before downstream reads or exporter invocation. |
| AC-RPT-005 | RPT-013 | The same Daily dataset is rendered as HTML, XLSX, and PDF | All formats contain identical authorized Projects, authors, Tasks, descriptions, statuses, planning fields, and totals; no format adds attendance/completion/productivity claims. |
| AC-RPT-006 | RPT-014, TSK-021 | On one date a `DONE` estimated Task is logged by two authors, and an unfinished Task with a forecast by one of them | The Task view shows each Task once with its planning values and both contributors' minutes; the Intern view shows each author's minutes and descriptions with no estimate, forecast, Current Work, or variance; totals equal the sum of selected-date minutes in HTML, XLSX, and PDF. |

## 8. Out of Scope

System-wide exclusions are §1.3 of the [platform spec](../feature-platform/SPEC.md): `GOV-007` through `GOV-010` and `GOV-015`.

No rule in this spec states an exclusion of its own.

## Notes / Open Questions

- `D1` was confirmed by the instructor on 14 September 2026 and widened the same day: an Admin views every report and its data read-only, and exports them, but edits no Project or Task and decides nothing. The instructor expects to withdraw part of this later; `AUTH-012` and `ADR-005` make each capability withdrawable alone. `RPT-004` lists the Attendance fields.
- `D15`: the Daily Project Work Report is mainly for the owning Mentor and the current Leader. It is not opened to ordinary Interns, who see their own work logs in their Task pages. Its two perspectives are `RPT-014`.
