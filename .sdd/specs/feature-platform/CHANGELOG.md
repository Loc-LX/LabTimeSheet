# Changelog — Platform spec

Each entry records a change to [SPEC.md](SPEC.md): the version, the date, what changed,
and why. A change that alters a rule raises the version.

## 1.2.4 — 2026-09-14

Decision `D14`, recorded in [`.sdd/decisions.md`](../../decisions.md).

- The glossary replaces the undefined finalization window with the attendance period and adds the overdue request. `GOV-009` also retains attendance period reopen history. Counts are 291 rules and 139 scenarios.

## 1.2.3 — 2026-09-14

Decision `D14`, recorded in [`.sdd/decisions.md`](../../decisions.md).

- The glossary adds the attendance finalization window, whose length is not yet decided. Counts are 286 rules and 137 scenarios.

## 1.2.2 — 2026-09-14

Spelling only: British spellings in the rule text became the American spellings the rest of the documentation uses. No meaning changed.

## 1.2.1 — 2026-09-14

Relocation only; no rule text changed. Sixteen rules that concern a single feature moved to that feature's spec, with the nine scenarios that cover only them: `SEC-002`–`SEC-007` to account; `AUTH-006`, `AUTH-007`, `DB-011`, `DB-012` to project; `AUTH-005`, `AUTH-008`, `DB-013` to task; `DB-002`, `DB-009` to attendance; `ERR-006` to reporting. Each section they left names where they went. Scenarios that also cover a platform rule stay here.

## 1.2.0 — 2026-09-14

Decisions `D13`, `D14`, `D15` and the maintainer's constitution choices, recorded in [`.sdd/decisions.md`](../../decisions.md). Marked provisional, pending instructor confirmation.

- `AUTH-003`, the §5.2 matrix, and `UI-019` give leave, correction, and attendance exception decisions to the Intern's responsible Mentor only, with Mentor queues limited to those Interns and a new exception workflow.
- `GOV-009` adds Task block, unblock, and reopen history to the narrow histories it retains; `DB-003` adds one attendance exception per attendance row and violation kind.
- The glossary defines the current Remaining effort from the latest effective forecast.
- **Added `ARC-009`** (an applied migration is never edited) and **`TST-011`** (an assertion is never weakened to pass), both declared without a system scenario; the EARS exceptions now number thirty.
- §22.2 no longer calls `D13` unresolved, the recovered-appendices note names `UC-15` and `UC-16`, and the counts are 285 rules and 135 scenarios.

## 1.1.0 — 2026-09-14

Decisions `D1`, `D12`, `D13`, `D15` and `D16`, recorded in [`.sdd/decisions.md`](../../decisions.md).

- **Added `AUTH-012`** and `AC-AUTH-011`: one authorization policy decides every business permission from role, scope, current state and target state, with one entry per matrix capability. The architectural decision is `ADR-005`.
- **Admin reads every report.** §1.1 gains a revision block, the §5.2 matrix splits report access into one row per report, `UI-019`, `AC-UI-005` and the page map give an Admin read-only Attendance, Project/Task and Daily navigation.
- **Task status by hierarchy.** `AUTH-005` and `AUTH-008` now point to `TSK-023`: the owning Mentor and current Leader may block, unblock and reopen, and nobody starts or finishes a Task for its assignee. `AC-AUTH-004` and `AC-AUTH-005` changed with them. The matrix row "Change Task status" became three rows.
- **Cancelled Projects.** `AUTH-006` treats `CANCELLED` like `COMPLETED`; `DB-011` gains a ninth resolution code `PROJECT_CANCELLED`; `UI-016` adds cancellation and deletion to the confirmed actions; the matrix gains a cancel row and limits deletion to empty drafts.
- **Effort terms.** `GOV-015` no longer forbids "continuous replanning"; it forbids acceptance and rejection states and re-baselining, and says reopening for correction is neither. The glossary defines the estimate as the baseline and adds Current Remaining effort and Current Work; variance is Current Work minus the estimate. `DB-013` names the assignment start rather than the reassignment timestamp.
- Counts in §1 and §22.1 updated to 274 rules and 131 scenarios.

## 1.0.0 — 2026-09-14

Approved by Loc-LX. The rules moved here unchanged from the single-file
specification at `.sdd/requirements.md` (commit `625463b`), with these exceptions:

- `GOV-016` now places a feature's rules in that feature's spec and its acceptance scenarios in section 7 of the same spec, instead of in §20 of one document. The maintainer chose on 14 September 2026 to split the specification into feature specs.
- §22.2 records the approval, and the questions and the code disagreement `D13` that remained open when it was given.
- §22.1 and §22.3 no longer point to a header that has gone, and say what "no open question" means now that questions for the instructor are listed.
- The recovered-appendices note records that use cases now sit in feature specs and that `UC-06` covers Task estimates and forecasts.
- The single-file header was replaced by the document table in section 1. Its quality-gate statement, that no open question remained, was dropped because it is no longer true.

Changes made to these rules during the review before approval are recorded in
[`.sdd/decisions.md`](../../decisions.md).
