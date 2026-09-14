# Changelog — Platform spec

Each entry records a change to [SPEC.md](SPEC.md): the version, the date, what changed,
and why. A change that alters a rule raises the version.

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
