# Changelog — Reporting spec

Each entry records a change to [SPEC.md](SPEC.md): the version, the date, what changed,
and why. A change that alters a rule raises the version.

## 1.2.1 — 2026-09-14

Relocation only; no rule text changed. `ERR-006` moved here from the platform spec because they concern only this feature, with `AC-ERR-006`.

## 1.2.0 — 2026-09-14

Decisions `D12`, `D14`, and `D15`, recorded in [`.sdd/decisions.md`](../../decisions.md). Marked provisional, pending instructor confirmation.

- `RPT-003` keeps completed and cancelled Projects in the Project/Task report, labelled; `RPT-011` and `AC-RPT-004` leave cancelled Projects out of an all-Projects Daily request unless the filter includes them.
- `RPT-002` and `RPT-004` show whether each late arrival or early departure is excused.
- Added `UC-16` for the Daily Project Work Report.

## 1.1.0 — 2026-09-14

Decisions `D1` and `D15`, recorded in [`.sdd/decisions.md`](../../decisions.md).

- `RPT-005` and `RPT-011` give an active Admin read-only Project/Task and Daily reports with export; `AC-RPT-002`, `AC-RPT-004` and `UC-12` follow.
- `RPT-012` uses the new effort terms. **Added `RPT-014`** and `AC-RPT-006`: a Task view with planning values once per Task, and an Intern view with minutes only.
- Notes rewritten.

## 1.0.0 — 2026-09-14

Approved by Loc-LX. The rules moved here unchanged from the single-file
specification at `.sdd/requirements.md` (commit `625463b`), with one exception:

- `RPT-004` now lists the fields of the Intern attendance dataset and states that an Admin sees none outside it. The instructor confirmed `D1` on 14 September 2026 and asked that the fields an Admin sees be written down, because they are expected to be narrowed later.

Sections 1, 2, 4, 5, 6 and 8 and the notes were written for this spec.

Changes made to these rules during the review before approval are recorded in
[`.sdd/decisions.md`](../../decisions.md).
