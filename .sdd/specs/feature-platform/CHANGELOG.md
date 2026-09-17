# Changelog — Platform spec

Each entry records a change to [SPEC.md](SPEC.md): the version, the date, what changed,
and why. A change that alters a rule raises the version.

## 1.6.0 — 2026-09-17

Decision `D32`: the specification describes the data model the decided rules require. Counts become 304 rules, 285 of them with a scenario, 149 scenarios, and 30 tables.

- §19.1 gains the attendance period, reopen request, attendance exception, exception and leave decisions, Task status transition, and the responsible Mentor relation. §19.2 lists the six added tables. §19.3 points to `DB-014`–`DB-021` in the specs that own them.
- §19.4 says what its diagram is: the tables the Flyway migrations create, column by column, as `DB-010` requires. It does not name columns for tables no migration creates.
- `AC-DB-001` counts 30 tables and verifies the six added tables against `DB-014`–`DB-017` and `DB-020`. The companion-files note points to the migrations without counting their tables.

## 1.5.1 — 2026-09-17

Counts only: `D31` added `AC-EXC-004` to the attendance spec, so §1 and §22.1 count 146 scenarios.

## 1.5.0 — 2026-09-17

Rules changed; decision `D30`. Counts stay 296 rules and 145 scenarios.

- **Withdrawn, identifiers kept:** `OPS-018` (a baseline before parallel work, which is done), `OPS-020` (the integration order of team branches, replaced by the layering of `ARC-005` in `ADR-006`) and `OPS-021` (commit discipline, whose limit on push and merge the constitution and `AGENTS.md` hold). Each row now says it is withdrawn, as `AUTH-010` and `TSK-017` stay as pointers, so no identifier is reused.
- **`OPS-019` rewritten:** an owner may be a person or an agent session, and a branch is named `work/fix/<area>/<what>` by a module of `ARC-005`, `platform`, `architecture` or `docs`, instead of by the five team features. A name is invalid where Git refuses it beside an existing branch, in either direction; where `work/fix/<area>` itself exists as a branch, as `work/fix/project` does, the change uses `work/fix/<area>-<what>`.
- §18 loses the table of five team branches and is named for what it holds. The non-EARS table and the §20 exclusion table say which of the four rules still binds.

## 1.4.3 — 2026-09-17

Wording only; no rule or scenario changed. A specification states what the system must do, so the history it carried was removed. Each piece is already recorded where history belongs:

- §1: where the document was authored and when it was copied here; the EARS rewrite and the gaps it surfaced (`D6`–`D9`). The companion-files note keeps what is still true: the DDL and the images are not tracked here.
- §1.1: the earlier authority order, the clause about dated revisions, and the three superseded blocks on Admin report scope of 27 August, 30 August and 14 September 2026. The current scope is in `RPT-004`, `RPT-005`, `RPT-011`, `UI-019` and the §5.2 matrix; its history is `ADR-002` and `D1`.
- Appendix D and Appendix F: what earlier versions held. §19.2: the 21-table baseline. §1.3: why the deferral note exists.
- §22: the gate the section once described, the quality review of the single-file specification, the date OQ-1 closed with its table and the Playwright repair (`D4`), the earlier third condition of approval (`D11`), and the approval narrative (version 1.0.0 in each changelog).
- §18: the condition that the split waits for approval, which it no longer does.
- Appendix B, the validation record of 14 August 2026 for another repository; "Recovered appendices", which also described an Appendix C that no longer exists here; and "Constitution changes", which is the constitution's own record and `D20`.

The opening paragraph no longer says the section numbers come from a single-file specification; it states the numbering convention they follow.

§1 names the audience the specification has: the maintainer, contributors, and the instructor as reviewer.

## 1.4.2 — 2026-09-17

Wording only; no rule changed. The introduction of §20 gains the principle that decides which spec a scenario lives in, and loses a reference to a practice that no longer exists.

- **Where a scenario lives.** Until now the principle was written only in the changelog entries of 1.4.1 and of the calendar spec, which record how step 4 of `D28` applied it. Those entries stay as history. The principle now lives once, in §20, which section 7 of every spec already points to; the spec template points there too rather than repeating it. It adds that steps which only set up or trigger the behavior under test do not decide, and that a scenario still readable two ways has its reason recorded where it is placed.
- Applied to the three scenarios that raised the question, nothing moves. `AC-NOT-001` and `AC-NOT-004` stay in notification: approving leave, and the invitation and membership-exit workflows, only trigger the behavior under test, which is mail delivery while SMTP is absent. `AC-ACC-011` stays in identity and is the case the last clause is for: its Given and When exercise account creation under `ACC-008` and `ACC-017` and the Intern-only fields of `ACC-019`, so it reads either way. It stays where it was placed, and this entry is its recorded reason: the request under test is an account-creation request, and refusing Intern data on a non-Intern account is a check on that request.
- The scenario count in §1 and §22.1 becomes 145. Applying the principle to `AC-ACC-012` split it: the account directory and email correction stay in identity, and the Student Code and internship-date correction is the new `AC-ACC-015` in internship. No rule changed, and the rules with a scenario stay 277.
- The first sentence of §20 still mapped each scenario to TDD evidence files. `ADR-004` removed those files on 12 September 2026 and moved the trace into the test source, so the sentence now names `TST-005`.

## 1.4.1 — 2026-09-17

Relocation only; no rule text changed. Step 4 of decision `D28` moves the specification into one spec per module. Rules and scenarios move with their identifiers and words unchanged; counts stay 296 rules and 144 scenarios in eight specs.

- The account spec is now the [identity spec](../feature-identity/SPEC.md), and the internship lifecycle has its own [internship spec](../feature-internship/SPEC.md). The policy, the global calendar and HolidayAPI moved from the attendance spec to a new [calendar spec](../feature-calendar/SPEC.md). The task spec merged into the [project spec](../feature-project/SPEC.md). The integration spec was divided by owner and no longer exists.
- Moved here from the integration spec: `INT-001`–`INT-008` and `INT-010` under §12.1, with `AC-INT-001`, `AC-INT-002`, `AC-INT-004` and `AC-INT-005`. `INT-009` went to the calendar spec. `AC-INT-002` covers `INT-006`–`INT-009`, three of its four rules here, so it came here too. The integration spec's history is in git, `.sdd/specs/feature-integration/CHANGELOG.md` at commit `9123150`.
- Every scenario is placed by one principle. A scenario sits in the spec of the module that owns the rules its Given/When actually exercises. A rule named only for the expected result, or a shared platform rule, does not decide where it sits. `AC-AUTH-001` stays here: its Given/When exercises `AUTH-001`–`AUTH-002` across every kind of record, and naming `AUTH-011` does not move it.
- `UC-19` is new prose, reviewed on its own: SMTP configuration, taken out of `UC-04` and tracing `INT-001`–`INT-008`. Section 2 names its actor.
- Moved to the project spec: `AUTH-004`, `AUTH-009` and `AUTH-011`, with `AC-AUTH-005`, `AC-AUTH-006` and `AC-AUTH-010`.
- The pointers in §5.1, §13.1 and §19.3 name the new specs. Section 6 names `INT-007` and section 8 names `INT-010`, as each spec does for its own rules. The note on where use cases sit names `UC-04`, `UC-18` and `UC-19`.

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
