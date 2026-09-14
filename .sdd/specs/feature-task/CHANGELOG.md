# Changelog — Task spec

Each entry records a change to [SPEC.md](SPEC.md): the version, the date, what changed,
and why. A change that alters a rule raises the version.

## 1.1.0 — 2026-09-14

Decisions `D13` and `D15`, recorded in [`.sdd/decisions.md`](../../decisions.md).

- `TSK-007` keeps the transition graph and gives the assignee all of it; **added `TSK-023`** for block, unblock and reopen by the current Leader and owning Mentor, decided by role, scope, current and target status. `TSK-009` lets a `DONE` Task be reopened by either route before reassignment.
- `TSK-021` defines current Remaining effort, Current Work, and variance as Current Work minus the estimate; **added `TSK-024`** so the Leader can append a forecast whenever the prediction changes.
- Added `AC-TSK-016` and `AC-TSK-017`; `AC-TSK-012` and `UC-06` follow. Notes rewritten.

## 1.0.0 — 2026-09-14

Approved by Loc-LX. The rules moved here unchanged from the single-file
specification at `.sdd/requirements.md` (commit `625463b`). Sections 1, 2, 4, 5, 6 and 8 and the notes were written for this spec.

Changes made to these rules during the review before approval are recorded in
[`.sdd/decisions.md`](../../decisions.md).
