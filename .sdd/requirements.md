# Lab Timesheet specification — index

This file holds no rule. On 14 September 2026 the specification that stood here was
split into one spec per feature under [`.sdd/specs/`](specs), and each spec was approved as version 1.0.0. This index stays at the old path
because many documents and dated records link here; it tells a reader where each rule
and each old section now lives.

Every rule still has exactly one canonical location (`GOV-016`). Rule identifiers did
not change, and the old section numbers are kept inside the specs as `§` headings, so a
reference such as `RPT-004` or `§19.4` resolves through the tables below.

## Specs

| Spec | Rule prefixes | Acceptance scenarios | Old sections |
|---|---|---|---|
| [Platform](specs/feature-platform/SPEC.md) | `GOV`, `ARC`, `AUTH`, `SEC`, `UI`, `OPS`, `TST`, `DB`, `ERR` | `AC-GOV`, `AC-ARC`, `AC-AUTH`, `AC-SEC`, `AC-UI`, `AC-OPS`, `AC-TST`, `AC-DB`, `AC-ERR` | §1, §2, §3, §5, §13, §15–§19, §20 introduction, §21, §22, Appendices A, B, D, F |
| [Account](specs/feature-account/SPEC.md) | `ACC` | `AC-ACC` | §4; UC-01, UC-02, UC-03 |
| [Attendance](specs/feature-attendance/SPEC.md) | `ATT`, `CAL`, `COR`, `LEV` | `AC-ATT`, `AC-CAL`, `AC-COR`, `AC-LEV` | §8–§11; UC-04, UC-08, UC-09, UC-10 |
| [Integration](specs/feature-integration/SPEC.md) | `INT` | `AC-INT` | §12.1 |
| [Notification](specs/feature-notification/SPEC.md) | `NOT` | `AC-NOT` | §12.2; UC-11 |
| [Project](specs/feature-project/SPEC.md) | `PRJ` | `AC-PRJ` | §6; UC-05, UC-13, UC-14 |
| [Task](specs/feature-task/SPEC.md) | `TSK` | `AC-TSK` | §7; UC-06, UC-07 |
| [Reporting](specs/feature-reporting/SPEC.md) | `RPT` | `AC-RPT` | §14; UC-12 |

At the split the eight specs held 269 numbered rules and 126 acceptance scenarios, the
same counts the single file held. The split was checked line by line: every line of the old file
was either carried into exactly one spec or dropped as a heading, separator, or header
the specs replace. Only `GOV-016` and `RPT-004` changed wording then. Every later change,
including new rules, is in the `CHANGELOG.md` of the spec it touches.

## What each spec contains

Every spec has the same eight sections: 1 Context & Goal, 2 Actors & Roles,
3 Functional Requirements, 4 Non-functional Requirements, 5 Data, 6 Error Handling,
7 Acceptance Criteria, 8 Out of Scope, followed by Notes / Open Questions. A feature
spec points to the platform spec for what every feature shares rather than repeating
it. New specs start from [`specs/_template.md`](specs/_template.md).

## Versions

Each spec is tagged in git as `spec/<name>/v<version>`, for example
`spec/feature-reporting/v1.1.0`. A change to a rule raises that spec's version and adds
an entry to its `CHANGELOG.md`; a change to its notes alone raises the patch number.

Each spec folder will also hold a `PLAN.md` and a `TASKS.md`. They belong to the
planning and task-breakdown phases and have not been written. Progress across
all of them is tracked in [`plan.md`](../plan.md).

## Old references that need translating

- **"§20 of the specification"**, used by the change table in [`constitution.md`](constitution.md), now means section 7 of the spec that holds the rule. The constitution is not edited without the maintainer's agreement to specific wording.
- **"Appendix C"** now means the Use cases part of section 3 in the spec named in the table above.
- **"The header"** of the old file is now the document table in section 1 of the platform spec.
