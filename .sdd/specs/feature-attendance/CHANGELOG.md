# Changelog — Attendance spec

Each entry records a change to [SPEC.md](SPEC.md): the version, the date, what changed,
and why. A change that alters a rule raises the version.

## 1.3.6 — 2026-09-17

Relocation only; no rule text changed. Decision `D28`.

- `ATT-001`–`ATT-003`, `CAL-001`–`CAL-009` and `DB-009` moved to the [calendar spec](../feature-calendar/SPEC.md), with `AC-CAL-001`–`AC-CAL-005`, `AC-ATT-001` and `UC-04`. `ATT-004`–`ATT-006` stay: they apply the policy to attendance rows and leave. `AC-ATT-001` moved although it names them, because its Given/When configures the policy and names them only for the result; the principle is in the calendar changelog.
- `NOT-011` moved here from the [notification spec](../feature-notification/SPEC.md), with its note. Every clause concerns leave, corrections, exceptions or the attendance period, and left in notification it would make notification depend on attendance.
- Sections 1, 2, 5, 6, 7 and 8 and the notes were updated for what the spec now holds; the note pointing `UC-04` at the integration spec went with `UC-04`.

## 1.3.5 — 2026-09-16

Two corrections and one status change; no rule text changed in substance.

- **§2 named only four of its six use cases.** `UC-15`, excusing a late arrival or early departure, and `UC-17`, reopening a finalized attendance period, are specified in full in §3 but had no actor line in §2, so the two workflows added by `D14` had no declared actors where a reader looks for them first. Both are listed now, with the actors their own use-case blocks name.
- **The word provisional is gone from six rules.** `COR-003`, `LEV-011`, `EXC-002`, `EXC-003`, `EXC-005` and `ATT-020` each called their limit a *provisional* laboratory policy. The limits are unchanged and still the laboratory's own choice rather than an industry standard; what changed is that they are no longer waiting on a signature. Recorded as provisional on that date and confirmed for build by the maintainer on 16 September 2026; see the note above the decision table in [`.sdd/decisions.md`](../../decisions.md).

## 1.3.4 — 2026-09-16

Wording only; no rule changed. A review found three places that still carried the numbers `D23` and `D24` replaced.

- `UC-15` said 24 hours to submit, 24 hours to decide, and a separate 48-hour limit for the Mentor's own mark. It now matches `EXC-002`, `EXC-003` and `EXC-004`: 48 hours, 48 hours, and the attendance period as the Mentor's only boundary.
- `AC-ATT-009` lost its point when the closing day moved to the fifth: the correction it describes was also decided on 5 October, so both Interns finalized at once. The correction is now decided on 7 October, and the scenario again contrasts a period that closes at the deadline with one held open by an undecided request.

## 1.3.3 — 2026-09-16

Decision `D24`, recorded in [`.sdd/decisions.md`](../../decisions.md). Marked provisional, pending instructor confirmation.

- The attendance period now closes at 23:59 on the **fifth** day of the following month, not the third (`ATT-020`). Since `D24` made that close the only boundary on a Mentor's own mark, three days left almost no time to review a work date at month end. `AC-ATT-009` follows.

## 1.3.2 — 2026-09-16

Decisions `D23` and `D24`, recorded in [`.sdd/decisions.md`](../../decisions.md). Marked provisional, pending instructor confirmation.

- **One adjustment policy (`D23`).** A correction and an exception request now share the same limits: 48 hours to submit and 48 hours to decide. `EXC-002`, `EXC-003`, `COR-003` and `COR-004` carry the same numbers, and each is bounded by the attendance period as well. What stays different is the evidence a correction needs: a proposed checkout with a reason, decided by the responsible Mentor alone.
- **The Mentor's own mark follows the period (`D24`).** `EXC-004` drops its 48-hour limit; a Mentor may mark a late arrival or early departure excused while the period of that work date is open, and afterwards only inside a range reopened under `ATT-022`. The old limit contradicted the monthly close for every work date after the 27th.
- `AC-COR-001`, `AC-EXC-001`, `AC-EXC-002`, `AC-EXC-003` and `UC-09` follow.

## 1.3.1 — 2026-09-14

Decision `D14`, reviewed and closed, recorded in [`.sdd/decisions.md`](../../decisions.md). Still provisional, pending instructor confirmation.

- `ATT-024` now shares only the mechanism: new decision entries with kind, actor, time, and reason; the latest effective entry is current; nothing returns to `PENDING`; a change needing a new approval is a new request; no change after finalization except through reopen. Each kind keeps its own actions and states.
- An amendment changes a decision's content and a reversal its outcome. `COR-005` and `EXC-007` permit both until the period is finalized; a correction amendment never changes the proposed checkout.
- `LEV-011`: a leave decision is never reversed. Approved leave is cancelled by the Intern before it begins, and after that changes only by the responsible Mentor's amendment withdrawing approval from dates, which releases their quota and leaves attendance as it happened; the resulting recalculation is laboratory policy. `LEV-004` and `LEV-012` follow.
- `AC-COR-003`, `AC-LEV-008`, `UC-09`, `UC-10` and `UC-15` follow.

## 1.3.0 — 2026-09-14

Decision `D14`, its last three points, recorded in [`.sdd/decisions.md`](../../decisions.md). Marked provisional, pending instructor confirmation.

- **Added `LEV-013`**: an Intern may withdraw a pending or overdue leave request until its period is finalized. Withdrawal releases quota and overlap blocking, keeps the request and its history, and changes no attendance record. `LEV-007` now covers editing only; `LEV-004`, `LEV-005`, `LEV-012` and `DB-002` follow.
- **Added `ATT-024`**: one decision lifecycle for leave, corrections, and exceptions. Until the period is finalized the responsible Mentor changes a decision only by appending a new decision or a reversal with actor, time, and reason; the latest effective decision is current. `COR-005`, `COR-007` and `COR-009` drop the 24-hour decision lock and the revert to `PENDING`; `EXC-007` and `LEV-011` refer to the shared rule.
- `ATT-022`: an Admin approves or rejects a reopen request, deciding only whether to reopen; a rejection keeps the Admin, time, and reason. `ATT-020` and `ATT-021` follow.
- Added `AC-LEV-007` and `AC-LEV-008`; `AC-COR-003`, `AC-LEV-006`, `AC-EXC-003`, `AC-ATT-010`, `UC-09`, `UC-10`, `UC-15` and `UC-17` follow.

## 1.2.0 — 2026-09-14

Decision `D14`, finalization and overdue handling, recorded in [`.sdd/decisions.md`](../../decisions.md). Marked provisional, pending instructor confirmation.

- **Added `ATT-019`–`ATT-023`**: monthly attendance periods per Intern that finalize at 23:59 on the third day of the next month (laboratory policy) unless a request affecting them is pending or overdue; no change after finalization except in a range an Admin reopens with a reason, where only the responsible Mentor acts and finalizes again.
- Leave and corrections no longer reject automatically when the approver misses the deadline: `LEV-010` and `COR-007` mark them `OVERDUE`, `LEV-004` and `LEV-006` keep an overdue leave reserving quota and blocking overlaps, `LEV-008`, `LEV-012` and `COR-005` let the responsible Mentor still decide, and `COR-008` and `COR-009` follow.
- `EXC-003` and `EXC-007` use the period instead of an undefined window.
- Added `AC-ATT-009`, `AC-ATT-010`, and `UC-17`; `AC-LEV-004`, `AC-COR-005`, `AC-EXC-003`, `UC-09`, `UC-10` and `UC-15` follow.

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
