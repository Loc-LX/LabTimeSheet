# Changelog — Project spec

Each entry records a change to [SPEC.md](SPEC.md): the version, the date, what changed,
and why. A change that alters a rule raises the version.

## 1.3.1 — 2026-09-16

Status only; no rule changed. `D12` is no longer held provisional. Recorded as provisional on that date and confirmed for build by the maintainer on 16 September 2026; see the note above the decision table in [`.sdd/decisions.md`](../../decisions.md).

## 1.3.0 — 2026-09-15

Decision `D21`, recorded in [`.sdd/decisions.md`](../../decisions.md). Marked provisional, pending instructor confirmation.

- **Added `PRJ-024`**: a Project may be created with a start date in the past, today, or in the future; the end date must not precede it. A past start date does not open retroactive Task work, which `TSK-014` still limits to dates within the member's membership. The code had refused past start dates since 25 August 2026 with no rule behind it; the first end-to-end run found it on 15 September 2026.
- Added `AC-PRJ-016`.

## 1.2.1 — 2026-09-14

Relocation only; no rule text changed. `AUTH-006`, `AUTH-007`, `DB-011`, `DB-012` moved here from the platform spec because they concern only this feature, with `AC-AUTH-003`, `AC-AUTH-007`, `AC-DB-002`.

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
