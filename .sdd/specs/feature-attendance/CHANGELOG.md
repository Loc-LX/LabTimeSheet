# Changelog — Attendance spec

Each entry records a change to [SPEC.md](SPEC.md): the version, the date, what changed,
and why. A change that alters a rule raises the version.

## 1.1.2 — 2026-09-14

The remaining four points of decision `D14`, recorded in [`.sdd/decisions.md`](../../decisions.md). Marked provisional, pending instructor confirmation.

- `EXC-003`: a request undecided after 24 hours becomes overdue, not rejected; only the responsible Mentor's decision makes it excused or unexcused, while the finalization window is open.
- `EXC-004`: a Mentor may mark an excuse up to 48 hours after scheduled end (laboratory policy).
- **Added `EXC-007`**: a decision changes only by appending a new decision or reversal with a reason; history stays immutable. `EXC-005` follows the current decision.
- Added `AC-EXC-003`; `AC-EXC-002` and `UC-15` follow.

## 1.1.1 — 2026-09-14

Relocation only; no rule text changed. `DB-002`, `DB-009` moved here from the platform spec because they concern only this feature, with `AC-DB-005`.

## 1.1.0 — 2026-09-14

Decision `D14`, recorded in [`.sdd/decisions.md`](../../decisions.md). Marked provisional, pending instructor confirmation.

- **Added `EXC-001`–`EXC-006`** and `AC-EXC-001`–`AC-EXC-002`: excused late arrivals and early departures, requested by the Intern or marked by the responsible Mentor, kept apart from the attendance classification. The 24-hour limits and leaving excused violations out of compliance are marked as provisional laboratory policy.
- `ATT-016` leaves excused violations out of compliance.
- `LEV-008`, `COR-005`, `COR-009`, `AC-COR-006`, `UC-09` and `UC-10` give decisions to the responsible Mentor. Added `UC-15`.

## 1.0.1 — 2026-09-14

Notes only. Decision `D14` on excused late arrivals and early departures is recorded, with the three points still open that keep it from being written as rules.

## 1.0.0 — 2026-09-14

Approved by Loc-LX. The rules moved here unchanged from the single-file
specification at `.sdd/requirements.md` (commit `625463b`). Sections 1, 2, 4, 5, 6 and 8 and the notes were written for this spec.

Changes made to these rules during the review before approval are recorded in
[`.sdd/decisions.md`](../../decisions.md).
