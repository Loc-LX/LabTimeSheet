# Changelog — Reporting spec

Each entry records a change to [SPEC.md](SPEC.md): the version, the date, what changed,
and why. A change that alters a rule raises the version.

## 1.3.3 — 2026-09-17

Trace only; no rule changed. `UC-12` now traces `RPT-015`. Section 2 already named the Intern as an actor under that rule, which `D25` added on 16 September 2026, but the use case did not trace it; one alternative now says what the Intern sees.

The notes no longer say when `D1`, `D12` and `D15` were confirmed, which `decisions.md` records.

The opening paragraph no longer says the section numbers come from a single-file specification; it states the numbering convention they follow.

Section 6 now lists `ERR-006`, the export failure rule this spec holds, and section 7 names its `AC-ERR` scenario.

## 1.3.2 — 2026-09-16

One correction and one status change; no rule changed.

- **§2 named only `UC-12`.** `UC-16`, the Daily Project Work Report, is specified in full in §3 and has scope rules of its own under `RPT-011`, but had no actor line in §2. It is listed now.
- `D12` and `D15` are no longer held provisional. Recorded as provisional on that date and confirmed for build by the maintainer on 16 September 2026; see the note above the decision table in [`.sdd/decisions.md`](../../decisions.md).

## 1.3.1 — 2026-09-16

Wording only; no rule changed. §2 still described the Admin scope that `D1` replaced on 14 September 2026.

- The actors of `UC-12` said an Admin had detailed Intern Attendance reports only, and named `RPT-005` and `RPT-011` as excluding an Admin. Both rules in the same file grant an Admin read-only scope, and the use case itself already named the Admin as a read-only actor for every report. §2 now says so too, and names `RPT-015` for the Intern's own attendance.
- The contradiction mattered beyond tidiness: §2 is what a reader of a feature spec meets first, and the plan for `AUTH-012` builds its permission fixture from these rules.

## 1.3.0 — 2026-09-16

Decision `D25`, recorded in [`.sdd/decisions.md`](../../decisions.md). Marked provisional, pending instructor confirmation.

- **Added `RPT-015`**: an active Intern can read their own attendance for a date, including whether a late arrival or early departure is excused. The view resolves the Intern from the authenticated account, carries no Task planning value and no other Intern's data, and has no export in this version. It is what makes the 48-hour adjustment window of `D23` usable: an Intern who cannot see a missing checkout cannot report it in time.
- Added `AC-RPT-007`.

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
