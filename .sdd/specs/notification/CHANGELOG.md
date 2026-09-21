# Changelog — Notification module

## 1.4.0 — 2026-09-21

`D38` closes this module's open clarification: `NOT-012` in
[Email delivery](features/email-delivery/SPEC.md) states the complete delivery status set
and its transitions, covered by `AC-NOT-007`. No rule of this shared contract changed.


## 1.3.0 — 2026-09-21

Navigation cleanup (`D38`). The parallel `feature-notification/` compatibility index is
removed: it defined no rule or scenario and had no reader inside the repository.
Its entries are retained verbatim below, so this module keeps one changelog and one
home. Successor links in this module's feature changelogs now point at that section.
No numbered rule, acceptance row, schema or dependency changed.

## 1.2.0 — 2026-09-21

Organization only (`D34`). Inherits the 1.1.7 baseline from
[the retained history](#history-before-the-d34-feature-split). Rules and acceptance rows
move verbatim into cohesive feature contracts; shared rules and cross-feature scenarios
remain in [MODULE.md](MODULE.md). Canonical use cases and state tables move only
when one feature owns the workflow; their former headings remain links.

This entry does not approve a new business rule, technical plan or implementation.

---

## History before the D34 feature split

These entries recorded `notification` while its rules lived in a single `feature-notification/SPEC.md`.
They are retained verbatim; only their heading depth changed when they moved here.

### 1.2.0 — 2026-09-21

Navigation migration (`D34`). Canonical rules now live in [MODULE.md](../notification/MODULE.md)
and its feature SPECs. This old SPEC.md is a compatibility index. Existing entries below
record the pre-split baseline and remain historical.

### 1.1.7 — 2026-09-17

Trace only; no rule changed. `UC-11` now traces `NOT-009`. Its last step already let the recipient read and mark notifications, which is what `NOT-009` scopes, and now says they are the recipient's own.

The opening paragraph no longer says the section numbers come from a single-file specification; it states the numbering convention they follow.

### 1.1.6 — 2026-09-17

Relocation only; no rule text changed. Decision `D28`.

- `NOT-003` and `NOT-010` moved to the [project spec](../project/MODULE.md), and `NOT-011` with its note to the [attendance spec](../attendance/MODULE.md). Each chooses its recipients from data another module owns. `NOT-002` stays: it names the events that notify, which is shared vocabulary rather than a dependency.
- `UC-11` still traces `NOT-001`–`NOT-008`, `NOT-003` among them; a trace across specs is allowed. `AC-NOT-001` and `AC-NOT-004` cover rules of more than one spec, so they stay here.

### 1.1.5 — 2026-09-16

Status only; no rule changed. `D14` is no longer held provisional, and the note under `NOT-011` now also says what the rule does, so the note carries something besides a status. Recorded as provisional on that date and confirmed for build by the maintainer on 16 September 2026; see the note above the decision table in [`.sdd/decisions.md`](../../decisions.md).

### 1.1.4 — 2026-09-14

Decision `D14`, recorded in [`.sdd/decisions.md`](../../decisions.md). Marked provisional, pending instructor confirmation.

- `NOT-011` sends a reopen request to every active user the authorization policy lets decide it, not to Admins by role, so narrowing Admin rights or adding an attendance-administration role later needs no notification change. It also names amendments and reversals.

### 1.1.3 — 2026-09-14

Decision `D14`, recorded in [`.sdd/decisions.md`](../../decisions.md). Marked provisional, pending instructor confirmation.

- `NOT-011` also notifies the Intern when a decision changes under `ATT-024`, every active Admin when a reopen is requested, and the requester and responsible Mentor when it is approved or rejected.

### 1.1.2 — 2026-09-14

Decision `D14`, recorded in [`.sdd/decisions.md`](../../decisions.md). Marked provisional, pending instructor confirmation.

- `NOT-011` reminds the responsible Mentor when a leave or correction request becomes overdue, as it already did for exceptions.

### 1.1.1 — 2026-09-14

Decision `D14`, recorded in [`.sdd/decisions.md`](../../decisions.md). Marked provisional, pending instructor confirmation.

- `NOT-011` notifies the responsible Mentor again when an exception request becomes overdue.

### 1.1.0 — 2026-09-14

Decisions `D12`, `D13`, and `D14`, recorded in [`.sdd/decisions.md`](../../decisions.md). Marked provisional, pending instructor confirmation.

- `NOT-003` notifies the assignee when someone else blocks, unblocks, or reopens their Task, and the current Leader too when the owning Mentor does.
- `NOT-002` includes attendance exceptions; **added `NOT-011`**, which sends leave, correction, and exception requests to the responsible Mentor and decisions to the Intern.

### 1.0.1 — 2026-09-14

Notes only. What cancellation and deletion mean for existing notifications.

### 1.0.0 — 2026-09-14

Approved by Loc-LX. The rules moved here unchanged from the single-file
specification at `.sdd/requirements.md` (commit `625463b`). Sections 1, 2, 4, 5, 6 and 8 and the notes were written for this spec.

Changes made to these rules during the review before approval are recorded in
[`.sdd/decisions.md`](../../decisions.md).
