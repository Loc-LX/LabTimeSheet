# Reporting Module

<a id="reporting-spec"></a>

**Version:** 1.6.0 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-21

Part of the Lab Timesheet specification. Rules every feature shares, including the
glossary, the authorization model, the domain model, and failure handling, are in
[the platform spec](../platform/MODULE.md). Section numbers marked `§` are one
numbering shared by all the specs, so a reference such as §5.2 names the same section
wherever it appears.

## 1. Context & Goal

Authorized dashboards and consistent HTML, Excel, and PDF reports, the fourth area named by the product objective (§1.2 of the platform spec).
In code this is `feature/reporting`: the Attendance, Project/Task, and Daily Project Work reports and their exports. It owns no table and reads through other features' services.


### Feature index

Read this shared contract together with the relevant feature SPEC. Rules are canonical
in exactly one of these documents; references do not create copies. Cross-feature use
cases, shared data constraints and unresolved decisions remain here (`D34`).

| Feature | Operations |
|---|---|
| [Attendance reports](features/attendance-report/SPEC.md) | Attendance and compliance report; Own-date attendance |
| [Project and Task reports](features/project-task-report/SPEC.md) | Filter Project and Task data; Apply per-member visibility |
| [Daily Project Work Report](features/daily-work-report/SPEC.md) | Resolve scope and selected date; Build Task and Intern views; Export the same dataset |


## 2. Actors & Roles

Primary actors named by this feature's use cases:

- **UC-12 — Review and export reports:** Admin, read-only, for every report under `RPT-004`, `RPT-005` and `RPT-011`; Mentor; authorized Intern/Leader for their own or current scope; an active Intern for their own attendance under `RPT-015`. Read-only means what `D1` says it means: an Admin reads every report and its exports, edits no Project or Task, and decides no leave, correction or attendance exception.
- **UC-16 — Review the Daily Project Work Report:** Owning Mentor for owned Projects; current Leader of an eligible currently led `PLANNED` or `ACTIVE` Project; Admin, read-only. Its scope rules are `RPT-011`, which is why it is a use case of its own rather than part of `UC-12`.

Every capability by role is in the permission matrix, [platform spec](../platform/MODULE.md) §5.2. Authorization is resolved from stored context, never from the global role alone (§5.1).

## 3. Functional Requirements

### §14. Reports and exports

| ID | Requirement |
|---|---|
| RPT-001 | THE system SHALL feed HTML, Excel, and PDF output from one shared report dataset and query layer, so that filters, classifications, totals, rounding, and authorization cannot drift between formats. |
| RPT-006 | THE system SHALL provide Excel and PDF export for the attendance and compliance, Project and Task, and Daily Project Work Reports. THE system SHALL keep the leave, correction, notification, and integration queues in-app only. |
| RPT-007 | THE system SHALL produce Excel through Apache POI XSSF, and PDF through OpenPDF HTML using a dedicated print-safe Thymeleaf XHTML and CSS template with an embedded Unicode-capable font. THE system SHALL NOT pass the modern Tailwind application stylesheet to the PDF renderer. |
| RPT-008 | THE system MAY perform an export synchronously in v1, and SHALL enforce bounded date ranges and authorized filters so that a request cannot demand unbounded memory or response work. |
| RPT-009 | THE system SHALL produce identical hand-checkable totals in HTML, Excel, and PDF. WHERE Project progress has no non-deleted Task, or an attendance or compliance denominator is zero, THE system SHALL render `N/A`. |
| RPT-010 | THE system SHALL compose an export filename from the report family and the requested date range, and SHALL NOT include a user-controlled path character in it. |

Feature contracts: [Attendance reports](features/attendance-report/SPEC.md), [Project and Task reports](features/project-task-report/SPEC.md), [Daily Project Work Report](features/daily-work-report/SPEC.md).

### Failure handling (from platform §21)

| ID | Requirement |
|---|---|
| ERR-006 | WHERE report generation fails, THE system SHALL return an error, SHALL persist no partial report, and SHALL close the response stream and any temporary resource. |

### Use cases

#### UC-12 — Review and export reports

| Field | Specification |
|---|---|
| Primary actor(s) | Admin, read-only, for every report; Mentor; authorized Intern/Leader for their own/current scope. |
| Trigger | A user opens a report or requests Excel/PDF export. |
| Preconditions | The user is authorized for every row in the requested dataset. |
| Postconditions | HTML, XLSX, and PDF expose identical authorized totals without persisting a Report entity. |
| Traced requirements | RPT-001–RPT-010, RPT-015, AUTH-010 |

**Main success flow**

1. Choose date, Project, member, or status filters.
2. Build one authorized report dataset.
3. Render HTML totals and accessible chart/table alternatives.
4. Export the same dataset to XLSX or PDF.
5. Compare totals and no-data behavior across formats.

**Alternatives and exceptions**

- Zero denominators render N/A.
- An active Intern views their own attendance for one date, resolved from the signed-in account, with no export.
- Ordinary members cannot see per-member work breakdowns.
- Unauthorized identifiers fail without leaking record existence.

The Daily Project Work Report has its own scope rules and is `UC-16`.

#### UC-16 — Review the Daily Project Work Report

Canonical workflow: [feature contract](features/daily-work-report/SPEC.md).

## 4. Non-functional Requirements

System-wide non-functional rules apply unchanged: architecture §3 (`ARC`), authentication and security §13 (`SEC`), interface and accessibility §15 (`UI`), delivery §16 (`OPS`), and test evidence §17 (`TST`), all in the [platform spec](../platform/MODULE.md).

## 5. Data

This feature owns no table. It reads through the services and DTOs of the features whose data it reports.

The conceptual model, the table inventory, the integrity rules (`DB`), and both diagrams are §19 of the [platform spec](../platform/MODULE.md).

## 6. Error Handling

Rules in section 3 whose text names a refusal, rejection, denial, or failure: `RPT-011`, `ERR-006`.

System-wide failure behavior is §21 (`ERR-001`–`ERR-007`), and the interface message families are Appendix F, both in the [platform spec](../platform/MODULE.md).

## 7. Acceptance Criteria

Scenarios from the §20 acceptance catalogue whose identifiers start with `AC-RPT` or `AC-ERR`. The catalogue's introduction, including the rules deliberately written without a scenario, is in the [platform spec](../platform/MODULE.md).

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-RPT-001 | RPT-001–RPT-009 | Same filter is rendered as HTML, XLSX, and PDF | Row classifications, counts, rates, progress, minutes, rounding, and `N/A` values match exactly. |
| AC-RPT-002 | RPT-002–RPT-005 | An Intern requests another Intern's Attendance, an active Mentor requests authorized detailed Attendance, an owning Mentor/current Leader requests authorized Project/Task detail, an ordinary member requests broader detail, an active Admin requests detailed Intern Attendance HTML/XLSX/PDF, and an Admin requests Project/Task HTML/XLSX/PDF | Intern scope remains own history; active Mentor and authorized owning-Mentor/current-Leader scopes remain available; ordinary members receive aggregate-only Project/Task results; the active Admin receives the same detailed Intern Attendance result an active Mentor receives, and a read-only Project/Task result for every Project including per-member hours. |
| AC-RPT-003 | RPT-007–RPT-010 | Vietnamese names and safe date filter are exported | Unicode renders in XLSX/PDF; filename is deterministic and path-safe. |
| AC-ERR-006 | ERR-006 | An XLSX export fails midway through writing the workbook | The response reports the failure rather than a truncated file; no report artifact is persisted; the workbook stream and any temporary file are closed, verified by the absence of leaked handles after the request. |

Feature contracts: [Daily Project Work Report](features/daily-work-report/SPEC.md), [Attendance reports](features/attendance-report/SPEC.md).

## 8. Out of Scope

System-wide exclusions are §1.3 of the [platform spec](../platform/MODULE.md): `GOV-007` through `GOV-010` and `GOV-015`.

No rule in this spec states an exclusion of its own.

## Notes / Open Questions

The feature split is organizational; read the feature-specific notes as well as the shared
notes below. No question affecting this module is open (`D38`). Should one be recorded here
later, this module and every feature it affects return to the inherited-baseline status.


- `D1`: an Admin views and exports every report read-only, edits no Project or Task, and decides nothing. The instructor expects to withdraw part of this later; `AUTH-012` and `ADR-005` make each capability withdrawable alone. `RPT-004` lists the Attendance fields.
- `D12` and `D15` set how cancelled Projects appear (`RPT-003`, `RPT-011`) and the two Daily perspectives (`RPT-014`, `UC-16`). The Daily report is mainly for the owning Mentor and the current Leader and is not opened to ordinary Interns, who see their own work logs in their Task pages.
