# Changelog — Platform spec

Each entry records a change to [SPEC.md](SPEC.md): the version, the date, what changed,
and why. A change that alters a rule raises the version.

## 1.4.0 — 2026-09-17

Rules changed: `ARC-005`, `ARC-006`, and scenario `AC-ARC-001`. Decision `D28` and [`ADR-006`](../../rfcs/ADR-006-module-boundaries.md).

- **`ARC-005`** names the modules by dependency rather than by build order: `attendance`, `calendar`, `identity`, `internship`, `notification`, `project` and `reporting` below `feature`, and a root package `platform` for code that belongs to no single feature. It adds three obligations: the dependencies among `platform` and the features form a directed acyclic graph with `platform` depending on no feature; `config` may depend on everything while nothing depends on `config`; and tests mirror production except the `architecture` and `ui` test packages, which the rule previously did not admit although both existed.
- **`ARC-006`** extends the repository and entity boundary to `platform`, and bounds one exception to the ban on one-implementation abstraction layers: an interface a module declares and calls, implemented only in modules that would depend on it even without the implementation. Four conditions a structural test can read, so the exception cannot grow into the `Service`/`ServiceImpl` layering the ban exists to prevent.
- **`AC-ARC-001`** asserts the new obligations. Nothing asserts them yet; the constitution lists the gaps until step 6 of `D28` adds the test.
- No rule was added or removed, and no other rule's text changed. Counts stay 296 rules and 144 scenarios.

## 1.3.3 — 2026-09-16

Wording only; no rule changed. The explanation of `GOV-001` in §1.1 still said the instructor confirms decisions marked provisional. Under `D26` no decision is held provisional and the instructor is a reviewer rather than a gate, so the sentence described a step that no longer exists.

## 1.3.2 — 2026-09-16

Wording only; no rule changed. The exclusion note in §20 said **seventeen** requirements have no acceptance scenario. Its own identifier list holds nineteen, and the counts in §22 give the same answer: 296 rules, 277 with a scenario. The number was right when it was written and stopped being right when `GOV-016`, `OPS-020` and `OPS-021` were added, which is why a count stated in words is worth a test.

## 1.3.1 — 2026-09-16

Wording only; no rule changed. Two places still carried the Admin report scope that `D1` replaced.

- The dated blocks of 27 and 30 August now open by saying they were superseded on 14 September, so a reader meets that before the text itself. The wording stays: a dated record is not rewritten.
- The Appendix C note said `UC-12` gives an Admin no Project/Task and no Daily scope. That was a current statement, not a dated one, and it contradicted `RPT-005` and `RPT-011`.

## 1.3.0 — 2026-09-16

Added during the review of the platform plan. A technical rule inside the agreed scope, so it needs no ADR and no instructor decision.

- **Added `ARC-010`**: the authorization decisions and database queries a request performs stay independent of the number of rows it renders. The plan for `AUTH-012` puts a policy question behind every business permission, and without this rule nothing stops a list of fifty rows from asking it fifty times. It states no time budget, because no deployment environment exists to measure one; that follows `ARC-004`, which chose a compatible range over an exact version, and `RPT-008`, which bounds a request instead of naming milliseconds.
- Added `AC-ARC-002`. Counts are 296 rules and 144 scenarios.

## 1.2.12 — 2026-09-16

Decision `D24`, recorded in [`.sdd/decisions.md`](../../decisions.md).

- The glossary follows `ATT-020`: an attendance period closes at 23:59 on the fifth day of the next month.

## 1.2.11 — 2026-09-16

Decisions `D23` and `D25`, recorded in [`.sdd/decisions.md`](../../decisions.md).

- The §5.2 matrix adds the Intern's own attendance view (`RPT-015`), and the glossary defines the attendance adjustment request that a correction and an exception now share. Counts are 295 rules and 143 scenarios.

## 1.2.10 — 2026-09-15

Counts only, after decision `D21` added `PRJ-024` and `AC-PRJ-016` to the project spec: 294 rules and 142 scenarios.

## 1.2.9 — 2026-09-15

Notes only; no rule changed. The note on constitution changes records that the constitution was locked as version `1.0.0` on 15 September 2026.

## 1.2.8 — 2026-09-14

Decision `D20`, recorded in [`.sdd/decisions.md`](../../decisions.md).

- §1.1: the constitution outranks the specs only for what it states canonically, each indexed rule's layer and exception, the standing deviations, the definition of done, and the AI agent policy. The wording of a rule is in its spec, so a shortened constitution row cannot narrow it.

## 1.2.7 — 2026-09-14

Decision `D20`, recorded in [`.sdd/decisions.md`](../../decisions.md).

- §1.1, the authority order of `GOV-001`, now ranks by document type: the constitution, the feature specs, the agent and contributor guides, then code and tests. The old list named superseded sources and not the constitution, the specs, or the decision record.
- `OPS-019` drops "taskmaster-verified", a role from the retired iteration plan.
- The notes record the constitution's new layer semantics; no rule text of `TST-011`, `ARC-009`, `GOV-006`, `SEC-007`, `SEC-013`, `AUTH-002` or `AUTH-012` changed.

## 1.2.6 — 2026-09-14

Decision `D14`, recorded in [`.sdd/decisions.md`](../../decisions.md).

- The glossary adds decision amendment and decision reversal, and the §5.2 matrix lets the responsible Mentor amend or reverse a decision only where its rule permits.

## 1.2.5 — 2026-09-14

Decision `D14`, recorded in [`.sdd/decisions.md`](../../decisions.md).

- The §5.2 matrix adds withdrawing own leave, asking to reopen a finalized period, and an Admin approving or rejecting that request, and lets the responsible Mentor change a decision. The glossary, `GOV-009`, and the message families follow: `Deadline closed` no longer shows a locked state, and `Period finalized` is added. Counts are 293 rules and 141 scenarios.

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
