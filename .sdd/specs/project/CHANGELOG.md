# Changelog — Project module

## 1.6.0 — 2026-09-21

Navigation cleanup (`D38`). The parallel `feature-project/` compatibility index is
removed: it defined no rule or scenario and had no reader inside the repository.
Its entries are kept below under *Retained history*, so this module keeps one changelog and
one home; that section states the two edits made when they moved. Successor links in this
module's feature changelogs now point at it.
No numbered rule, acceptance row, schema or dependency changed.

## 1.5.3 — 2026-09-21

D37 closes the invitation and exit-request status questions by establishing DB-011 and DB-012 status constraints and their complete state transition tables, and includes AC-TSK-021 in the P5 scenario overview.

The maintainer approved this amendment in `D37`; runtime implementation and
technical-plan approval remain separate.

## 1.5.2 — 2026-09-21

D36 links the soft-deleted Task readiness scenario and adds the missing AC-TSK-019/020 entries to the P5 summary. Shared numbered rules, Task permissions and Project completion/cancellation remain unchanged.

The maintainer approved this amendment in `D36`; runtime implementation and
technical-plan approval remain separate.

## 1.5.1 — 2026-09-21

D35 closes the creation/unblock/readiness questions and links the settled cross-module acceptance contract. Invitation and exit-request state questions remain open; shared numbered rules are unchanged.

Approved business defaults are recorded in `D35`; this revision does not implement
Java behavior or approve a technical plan.

## 1.5.0 — 2026-09-21

Organization only (`D34`). Inherits the 1.4.2 baseline from
[the retained history](#retained-history). Rules and acceptance rows
move verbatim into cohesive feature contracts; shared rules and cross-feature scenarios
remain in [MODULE.md](MODULE.md). Canonical use cases and state tables move only
when one feature owns the workflow; their former headings remain links.

This entry does not approve a new business rule, technical plan or implementation.

---

## Retained history

These are the entries of the former `feature-project/CHANGELOG.md`: the history of that
document from its first version, through the `D34` split that turned it into an index,
until `D38` removed it. Their version numbers are that document's, not this module's, which
is why a number here can repeat one above. Their text is kept as it was, with two
exceptions made when they moved here: headings are one level deeper, and links to
`feature-*/SPEC.md` files that no longer exist now point at the successor `MODULE.md`.

### 1.5.0 — 2026-09-21

Navigation migration (`D34`). Canonical rules now live in [MODULE.md](../project/MODULE.md)
and its feature SPECs. This old SPEC.md is a compatibility index. Existing entries below
record the pre-split baseline and remain historical.

### 1.4.2 — 2026-09-20

Structure and alignment to existing decisions; `D33`. No numbered rule changed and no scenario identifier was added or removed.

- Six parts: Project lifecycle, membership/leadership, invitations, exits/transfers, Task definition/comments/status, and work logs/effort. Rules and §7 scenarios are grouped accordingly, with data ownership, use cases and cross-part dependencies mapped in place.
- `AC-TSK-003` no longer denies every non-assignee action; `TSK-023` and `D13` already permit scoped Leader/Mentor interventions. The assignee-unblock ambiguity remains explicitly open. `AC-TSK-004` acknowledges authorized reopen actors and the reason required by `TSK-025`; `UC-05` acknowledges the owning Mentor's limited status actions.
- `AC-AUTH-007` exercises retained read access after cancellation as well as completion, as `AUTH-006` and `D12` already require.
- Notes identify missing Task creation/state decisions, invitation/exit status sets, and the cancellation versus internship-readiness interaction. These are questions for the maintainer, not decisions made by this reorganization.

### 1.4.1 — 2026-09-17

No rule changed. The Project gains a state transition table after its rules, which yields to them where they differ.

### 1.4.0 — 2026-09-17

Rules added; decision `D32`.

- **Added `DB-019`**, the `CANCELLED` Project status with the cancelling Mentor, time and reason, and **`DB-020`**, append-only Task status transitions for block, unblock and reopen.
- **Added `AC-DB-007`.** §5 names `task_status_transitions`, and §6 lists `DB-020`.

### 1.3.3 — 2026-09-17

Trace only; no rule changed. `UC-05` now traces `PRJ-024`, which `D21` added on 15 September 2026 without extending the use case that creates Projects, and one alternative says what the rule allows.

Section 1 names the module the spec describes, as `ARC-005` names it. The state of the code belongs in `plan.md`, not in a specification.

The notes no longer say when `D12`, `D13` and `D15` were decided and confirmed, which `decisions.md` records. Two notes about the current code, `ProjectService#deleteProjectRows` and the owning Mentor's status change in `TaskService#changeStatus`, moved to `plan.md`, which tracks the code.

The opening paragraph no longer says the section numbers come from a single-file specification; it states the numbering convention they follow.

Section 6 now lists `PRJ-024`, `TSK-023` and `AUTH-008`, which each refuse something and were missing. Section 7 names the `AC-AUTH` and `AC-DB` scenarios it holds.

### 1.3.2 — 2026-09-17

Relocation only; no rule text changed. Decision `D28` makes Projects and Tasks one module, `project`.

- The whole task spec, which no longer exists, moved here: `TSK-001`–`TSK-025`, `AUTH-005`, `AUTH-008` and `DB-013`, `UC-06` and `UC-07`, every `AC-TSK` scenario, `AC-AUTH-004`, `AC-DB-003`, and its three notes. Its history before this date is in git, `.sdd/specs/feature-task/CHANGELOG.md` at commit `9123150`.
- `AUTH-004`, `AUTH-009` and `AUTH-011` moved here from the [platform spec](../platform/MODULE.md), with `AC-AUTH-005`, `AC-AUTH-006` and `AC-AUTH-010`, which cover only rules of this spec. Each is wholly about Project or Task authorization.
- `NOT-003` and `NOT-010` moved here from the [notification spec](../notification/MODULE.md). Each chooses its recipients from Project data. `AC-NOT-004` covers rules of both specs, so it stays in notification.
- Sections 1, 2, 5, 6, 7 and 8 were updated for what the spec now holds.

### 1.3.1 — 2026-09-16

Status only; no rule changed. `D12` is no longer held provisional. Recorded as provisional on that date and confirmed for build by the maintainer on 16 September 2026; see the note above the decision table in [`.sdd/decisions.md`](../../decisions.md).

### 1.3.0 — 2026-09-15

Decision `D21`, recorded in [`.sdd/decisions.md`](../../decisions.md). Marked provisional, pending instructor confirmation.

- **Added `PRJ-024`**: a Project may be created with a start date in the past, today, or in the future; the end date must not precede it. A past start date does not open retroactive Task work, which `TSK-014` still limits to dates within the member's membership. The code had refused past start dates since 25 August 2026 with no rule behind it; the first end-to-end run found it on 15 September 2026.
- Added `AC-PRJ-016`.

### 1.2.1 — 2026-09-14

Relocation only; no rule text changed. `AUTH-006`, `AUTH-007`, `DB-011`, `DB-012` moved here from the platform spec because they concern only this feature, with `AC-AUTH-003`, `AC-AUTH-007`, `AC-DB-002`.

### 1.2.0 — 2026-09-14

Decision `D12`, recorded in [`.sdd/decisions.md`](../../decisions.md). Marked provisional, pending instructor confirmation.

- `PRJ-002` and `AC-PRJ-014`: deleting an empty draft also deletes the notifications raised for it.

### 1.1.0 — 2026-09-14

Decision `D12`, recorded in [`.sdd/decisions.md`](../../decisions.md).

- `PRJ-002` adds `PLANNED → CANCELLED` and `ACTIVE → CANCELLED`, and permits deletion only of an empty `PLANNED` Project.
- **Added `PRJ-023`** and `AC-PRJ-015`: cancellation by the owning Mentor with a reason, closing intervals, revoking invitations, superseding exit requests, keeping Tasks and history, and notifying closed members.
- `AC-PRJ-014` and `UC-05` follow the new deletion and cancellation rules. Notes rewritten.

### 1.0.0 — 2026-09-14

Approved by Loc-LX. The rules moved here unchanged from the single-file
specification at `.sdd/requirements.md` (commit `625463b`). Sections 1, 2, 4, 5, 6 and 8 and the notes were written for this spec.

Changes made to these rules during the review before approval are recorded in
[`.sdd/decisions.md`](../../decisions.md).
