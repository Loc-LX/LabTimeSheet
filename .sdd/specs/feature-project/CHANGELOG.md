# Changelog — Project spec

Each entry records a change to [SPEC.md](SPEC.md): the version, the date, what changed,
and why. A change that alters a rule raises the version.

## 1.2.0 — 2026-09-14

Decision `D12`, recorded in [`.sdd/decisions.md`](../../decisions.md). Marked provisional, pending instructor confirmation.

- `PRJ-002` and `AC-PRJ-014`: deleting an empty draft also deletes the notifications raised for it.

## 1.1.0 — 2026-09-14

Decision `D12`, recorded in [`.sdd/decisions.md`](../../decisions.md).

- `PRJ-002` adds `PLANNED → CANCELLED` and `ACTIVE → CANCELLED`, and permits deletion only of an empty `PLANNED` Project.
- **Added `PRJ-023`** and `AC-PRJ-015`: cancellation by the owning Mentor with a reason, closing intervals, revoking invitations, superseding exit requests, keeping Tasks and history, and notifying closed members.
- `AC-PRJ-014` and `UC-05` follow the new deletion and cancellation rules. Notes rewritten.

## 1.0.0 — 2026-09-14

Approved by Loc-LX. The rules moved here unchanged from the single-file
specification at `.sdd/requirements.md` (commit `625463b`). Sections 1, 2, 4, 5, 6 and 8 and the notes were written for this spec.

Changes made to these rules during the review before approval are recorded in
[`.sdd/decisions.md`](../../decisions.md).
