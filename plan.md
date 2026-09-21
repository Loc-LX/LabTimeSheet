# Lab Timesheet — current work

What is being worked on now, what comes next, and what is waiting on someone.
This file tracks progress only. What the system must do is in
[`.sdd/specs/`](.sdd/specs); how a feature will be built goes in that feature's
`PLAN.md`, and its task breakdown in `TASKS.md`. The rules for working here are
in [AGENTS.md](AGENTS.md) and [`.sdd/constitution.md`](.sdd/constitution.md).

**Last updated:** 2026-09-22 · **Branch:** `work/fix/docs/module-feature-specs`.
Documentation branch from checkpoint `c443670`; the one-off `OPS-019`
baseline reason is in `D34`. That checkpoint is backed up on Gitea under branch
`work/fix/docs/backup-before-feature-split-20260920` and tag
`backup/pre-feature-split-20260920`. The D34–D39 work is committed locally on this branch, not pushed.
On 21 September the branch and all 95 changed files were brought into the maintainer's
primary workspace. The prior isolated worktree remains detached at c443670 with a retained
copy; an external handoff patch and file hashes preserve the transfer. Continue work in
the primary workspace, not both copies.

## Where the project is

| Stage | State |
|---|---|
| Delivery, Iterations 1–4 | Built. Every tracked item is done; the Iteration 3 and 4 integration gates were never run. |
| Specification | Eight module contracts and 27 feature SPECs; [entry map](.sdd/specs/README.md). D34 organizes workflows; D35 settles Task creation/unblocking, cancelled-Project internship readiness and current-session logout. D36 additionally excludes soft-deleted Tasks from internship readiness. D37 settles assignee non-deleted Task transitions, aligns DB-011 and DB-012 status constraints with V1__baseline.sql, and defines complete invitation and exit-request state transition tables. D38 settles the account state machine, sign-in by account state, the email delivery states and password-reset eligibility, and removes the parallel `feature-*` document tree. D39 records the status predicates the migration must change beyond the status constraints. Both were corrected on two rounds of independent review. Current catalogue: 311 rules and 167 scenarios. §22.3 is empty, so §22.2's condition on it is met; acceptance by the authority in §1.1 is still required. |
| Constitution | Locked, version `2.0.2`. |
| Technical plans (`PLAN.md`) | The platform technical plan, now at `.sdd/specs/platform/PLAN.md`, is version `1.0` of 16 September and names none of `D32` or `D35`–`D39`. Its line 108 contradicts line 230 and §2.3 on who resolves scope; its §3 is superseded by `D32`; a risk row makes step 5 wait on the instructor, which `D26` rules out; and its line 174 reasons that widening a status set *"is safe for existing rows"*, which `D39` shows is false for every predicate other than the status column. Revise it in the plan phase. Condition on approving the migration's plan: it lists, table by table, every predicate of `V1__baseline.sql` that names a status whose set the rules widen, and what each becomes (`D39`), using the criterion and the seventeen-member table recorded there. Found by the same criterion and not yet decided: `ATT-003` limits the monthly leave quota to 0 through 4 while `ck_attendance_policy_versions_quota` accepts 0 through 31, and whether it can be narrowed depends on the stored policy versions; and `ck_notifications_type` has no code for the exception, overdue and reopen notices of `NOT-011`. New technical plans belong beside their feature SPECs and inherit MODULE.md; no empty plans were created. The cross-module architecture plan location remains open. |
| Task breakdown (`TASKS.md`) | Not started. |
| Implementation of `D1`, `D12`–`D27`, `D31` and `D32` | Not started; the code still follows the earlier rules. |
| Implementation/validation of `D35`, `D36` and `D37` | Specification updated; no Java changes or runtime conformance claim. The approved decisions are ready for technical design and subsequent behavioral tests. |
| Validation against the specification | Not started. |

The delivery plan that tracked Iterations 1–4 item by item, with owners, branches
and integration commits, is in git history: `git show b71fc29:plan.md`.

## Now

- Backup `c443670` includes the D33 organization and six corrected acceptance summaries. Remote branch and annotated tag were pushed atomically and verified to resolve to that commit.
- `D34` split eight module contracts into 27 feature SPECs with operation headings, shared dependencies and retained use cases/state tables. At that step, all 304 rule rows and 149 acceptance rows were text-identical to the checkpoint; D35/D36 subsequently amend the named business contracts.
- `D34` kept the legacy SPEC paths as compatibility indexes; `D38` later removed them. Root maps, template, document contract tests and analyzer input discovery follow the new structure. No Java, migration or dependency changed. The JS test under `src/test/` did change.
- Gitea PR #12's head is already a parent of verified main; the API refused both manual-merge recording and a normal merge (HTTP 405), so its open UI status remains unresolved. PR #13 conflicts with main and contains the retired Task branch of D17; it was not merged. The maintainer's choice about closing or retaining that PR is pending. Neither limitation prevented the verified backup.
- Independent review so far covered `D38` and `D39` in two rounds, each checking the claims the previous correction made; every finding was confirmed and corrected. A full review of `D34`–`D37` has not been done. Nothing from this branch has been pushed or merged into main.
- The maintainer-authorized follow-up corrects operation summaries and distinguishes required contracts from related workflows. All 27 features now map their 71 operations to actors/outcomes, existing rules/scenarios and explicit acceptance gaps. The entry map explains the local adaptation of public Azure Boards and Jira guidance; it does not claim their internal engineering practices or change the business baseline.
- Documentation checks now reject broken section anchors and missing operation-map rows. The boundary analyzer includes the eight previously unassigned DB rules and resolves the outdated concept verdicts. These are specification/tool checks, not evidence that Java implements the required module boundaries.
- `D35` records the maintainer's acceptance of four concrete defaults: create Tasks at TODO, restore the latest pre-block state for every authorized actor, exclude cancelled-Project Tasks from internship readiness while retaining history, and logout from the current session to login. Rules, operation maps, acceptance rows, state tables, related notes and changelogs are aligned. No architecture boundary changed.
- `D36` closes the review's soft-deleted Task dead end through ACC-022 and AC-ACC-019, including COMPLETED Projects. Current leadership and non-deleted unfinished work outside CANCELLED Projects still block internship termination. P5's overview includes AC-TSK-019/020; document tests now verify each labelled rule/scenario link against its canonical owner.
- `D37` aligns `TSK-007` to explicitly require a non-deleted Task for assignee transitions and adds `AC-TSK-021` for rejection of status transitions on soft-deleted Tasks. Constrains `DB-011` and `DB-012` status sets to match `V1__baseline.sql`, aligns invitation resolution codes to revoked status, and adds state transition tables for Project invitations and exit requests with public Azure/Jira comparison points.
- `D38` closes the last entries of §22.3. `ACC-014` names every account transition, `ACC-028` deactivates an account created in error while still pending, `ACC-029` reinstates a deactivated account to the state its record allows, keeping a lock it had, and `ACC-030` refuses sign-in to every account that is not `ACTIVE`. `DB-022` loosens V1 by exactly the two rows those rules need and keeps its non-blank hash; by trigger, the activation timestamp is set only by activation and the lock timestamp only by locking and cleared only by unlocking, so the schema itself forces the three outcomes of `ACC-029`. `NOT-012` states the five email delivery states and their transitions. `SEC-015` states password-reset eligibility, keeps an Admin's lock through a reset, and clears the throttle. Eight scenarios cover them. `ACC-028`, `ACC-029`, `ACC-030`, `DB-022` and the throttle clause need a migration or new code; no Java, schema or dependency changed here.
- Independent review of `b008c1f` and `399dc17` found two false claims (that `DB-022` tightened the schema, and that `D39` was a single omission), three partly true, and three further errors: a rollback that would have duplicated every rule, a wrong rule cited for activation, and two scenario counts. Every finding was confirmed against the repository before `D38` and `D39` were corrected in place, each with a record of what was wrong. The correction also found the spec map's tree, `CLAUDE.md` and this file still describing the removed folders, and a fourth member of the class in `ck_attendance_corrections_decided_at`.
- A second review of `b36842d` found the lock still removable by one update that moves a locked `DEACTIVATED` account to `ACTIVE`, the throttle clearing labelled `ENTERPRISE-BACKED` on an on-premises source, and a seventh unrecorded predicate. It also rejected a proposal to make a Project's activation timestamp write-once, which rested on a claim that reports read it; nothing does. Each finding was confirmed before `D38` and `D39` were corrected again: the lock is set only by locking and cleared only by unlocking, the label is `ADAPTED` on the smart lockout page, and the criterion for protecting a column is that a rule reads it to choose a later transition.
- `D39` states a criterion instead of a number: a predicate is a member when, once the rules apply, it refuses a lawful row or admits an unlawful one. Applied to all 53 check, exclusion and unique predicates on the eight existing tables the pending decisions change, it finds seventeen members: six recorded by `D32`, three by `D38`, and eight no decision had recorded, including `ck_projects_activation`, which refuses every cancellation, and the leave overlap exclusion, which lets overdue leave overlap without failing any existing test. `DB-018` now requires a withdrawal time and names the approval a cancelled request carries; `DB-019` accepts a cancelled Project with or without an activation timestamp; `AC-DB-010`–`AC-DB-012` cover them. Stored `CANCELLED` rows with no decision time become `WITHDRAWN`, their cancellation time becoming the withdrawal time, once the database's migration history is identified.
- `D38` Part 1b defines the `Status` field in the specification map, which nothing had defined and no check had read. An audit found no document still naming an open question, so twenty-five move to `APPROVED BUSINESS BASELINE`, each with its changelog entry, and the sentences pointing at closed gaps were corrected. A new check binds the field to the open questions and was proved in both directions.
- `D38` Part 1 removes the parallel `feature-*` document tree. Its changelogs are kept in the owning module's changelog under *Retained history*: the ninety-one entries committed at `c443670`, all present, eighty-two byte for byte and nine with a link retargeted, plus eight `D34` entries that were never committed on their own. `feature-platform/PLAN.md` moved to `.sdd/specs/platform/PLAN.md`, successor links were retargeted, and the sixteen files were deleted.

### Readiness for the plan phase

| Scope | What can proceed | What still gates approval |
|---|---|---|
| Attendance A1–A5 | Draft part-level design against the existing rules, including shared history and finalization guards | Design the cross-part cases in §5 and their evidence; do not treat A5 as an optional later guard |
| Project P1–P6 | Plan Task creation/status and internship readiness against D35/D36; P2/P6 still inherit the P1/P4/P5 guards | Complete invitation/exit state decisions before approving their dependent sections; other settled sections can proceed |
| Platform F1–F6 | Revise the technical plan around the six shared scopes and settled rules | Resolve its scope-context contradiction and superseded data section; include the three observable-rule trace gaps and the business decisions needed by its consumers |
| Identity and notification | Plan current-session logout against ACC-027 and AC-ACC-016/017; draft other settled behavior in its feature scope | Complete the named account/delivery transitions; password operation-level acceptance coverage is incomplete |
| Architecture steps 5–7 | Prepare the module-boundary plan from `D28` and `ADR-006` | Maintainer chooses where the cross-module plan/tasks live; document organization does not implement the boundary change |

## Next

Review the D34 restructuring and D35/D36 amendments; prepare designs for the settled decisions.
Close the remaining business questions only where the next plan depends on them.
Prepare feature plans for settled workflows with module dependency checks, followed by tasks, code and validation,
including for the module boundaries
of `D28`. Savepoint before it: `savepoint/pre-module-split-2026-09-17`.

Numbered as the steps of `D28`, so that "step 6" means the same thing here, in `ADR-006`
and in the constitution.

1. **Step 1 — revise the decision documents.** Done, committed in `9123150`.
2. **Step 2 — check all 296 rules for mentions of another module's concepts.** Done, in `9123150`: `node scripts/module-boundaries.cjs` exits 0 with 99 verdicts and reproduces the reviewer's 12 findings.
3. **Step 3 — lock** `D28`, `ADR-006`, `ARC-005`, `ARC-006`, `AC-ARC-001` and the constitution `2.0.0` in one commit. Done: `9123150`.
4. **Step 4 — one spec per module.** Historical split completed at `58711c5`: 296 rules preserved, and the UC-03/UC-18 and UC-04/UC-19 traces equal those they replaced. D34 refines that document layout into shared MODULE.md plus cohesive feature SPECs, preserving the later baseline of 304 rules and 149 scenarios.
5. **Step 5 — the plan and tasks that implement `ARC-005` and `ARC-006`**, for the maintainer's approval. Then this documentation branch is merged into `main`, with the maintainer's permission, so the implementation branch starts from the documents it follows. Pushing and merging each need their own permission.
6. **Step 6 — implement the module boundaries** on `work/fix/architecture/<name>` from `main` (`OPS-019`), needing Docker and enough memory.
   - The cycle test first, in plain Java, with the violations known at the start; every task shortens the list and the last empties it.
   - GitNexus impact analysis on every symbol before it changes.
   - `LayerStructureTest` and `AttendanceLayerStructureTest` take the new modules in the same change as the code they check; until then both guard the layout `ARC-005` replaced.
   - R8 changes only where `currentBusinessDate` is declared; the timezone source does not change.
   - R3 and R1 last, as their own tasks, after the `ACC-019` invariant test has been seen failing and then passing.
   - Gates: full Maven suite, end-to-end suite, `npm run test:ui`, the cycle test.
7. **Step 7 — check the code against the documents, then merge**, with the maintainer's permission. The Maven and end-to-end evidence of `1ee043e` (757 tests) expires with the first Java change.
8. **Step 8 — the business decisions `D12`–`D27`**, part by part: plan, tasks, code, validation, with a demonstration to the instructor (item 23).

## Waiting on a decision

| Item | Waiting on | Recorded in |
|---|---|---|
| Where the plan and tasks of step 5 live. They are not the platform plan, whose subject is the platform rules, and no feature owns them | The maintainer | This file |

The three business clarifications this table carried are closed by `D38`: the account
transition edges, the email delivery states, and password-management operation coverage.

## Historical evidence for D33 (checkpoint c443670)

- `npm run test:ui`: 30/30 pass on 20 September 2026, including document structure, counts, versions, references, decision index and relative file links. Node `24.16.0`, npm `11.13.0`; the Playwright contract reads resolved `@playwright/test` `1.62.1` from the lockfile. This run does not execute browser or Java behavior.
- Read-only comparison against `fd864ec`: all 304 rule rows are text-identical in their original canonical specs; all 149 scenario IDs remain, with six acceptance rows corrected. Exactly the eight approved files changed; no `src/` file changed. All 17 part links resolve to unique anchors; `git diff --check` passes.
- No Maven or end-to-end run for this prose change (`TST-009`). GitNexus symbol impact/change analysis does not assess prose; there is no code-symbol edit or proposed commit in this step.
- Independent review was not performed before the user-requested backup checkpoint; the commit records that limitation. Backup is not release or merge approval.

## Evidence for D34

- Document hierarchy check was first run RED: the missing identity/MODULE.md caused the intended failure while the ten previous checks passed. `npm run test:ui` then passed 31/31 on 21 September 2026 (Node 24.16.0, npm 11.13.0; resolved Playwright 1.62.1, without launching browsers).
- Exact read-only comparison to checkpoint c443670: 304 canonical rule rows, 149 canonical acceptance rows, 19 complete use-case bodies and eight state-transition tables preserved, each canonical definition retained once. Shared contracts and nested features are both included in the catalogue checks. All original non-header lines remain in the successor module tree except identity's stale "None recorded." note, replaced with the already identified open questions.
- File and fragment checks for all successor contracts and legacy SPEC indexes found no broken links after restoring the old section anchors. `git diff --check` passed. These checks establish preservation and navigability, not business completeness or implementation conformance.
- Before the review fixes, the module-boundary analyzer failed with 304 rules found, 296 assigned, eight DB rules missing assignment, eleven concept verdicts unresolved and two stale verdicts. The follow-up evidence below supersedes that failed run; the historical 296-rule green result is not current evidence.
- GitNexus pre-edit impact returned UNKNOWN for the document-discovery helpers and analyzer file. Text inspection established their actual uses in the documentation tests and standalone architecture script; UNKNOWN was not treated as proof of no callers. No production symbol was edited.
- After refreshing the index, GitNexus 1.6.12 `detect-changes --scope all --limit 1000` saw all 95 changed files and 21 indexed symbols, reported low risk and no partial/truncated change-result warning. Its zero affected flows does not certify no impact: global flow extraction is capped and Windows FTS is unavailable. The independent text, preservation and executable document checks supply the bounded evidence for this change.
- No Maven or browser end-to-end run for this documentation/tool-discovery change. Independent review remains pending before any final merge.
- The backup push and remote-ref read were verified before the split. A later read-only Gitea recheck on 21 September failed authentication; no subsequent remote mutation was attempted. The local restructuring and its checks do not require another push.

### Review follow-up, 21 September 2026

- A local snapshot of the 95 pre-review changed/new files and their hashes was retained before corrections. At that review stage, no business decision was inferred merely from the request to use Azure/Jira guidance. D35 subsequently records explicit approval of Task initial status, assignee unblocking, cancelled-Project readiness and logout; the other gaps in the decision table remain open.
- Markdown helper tests first failed against empty implementations, then passed after implementing heading/fragment discovery. The boundary-analyzer regression test first failed on the eight missing assignments and outdated verdicts, then passed after corrections. No existing assertion was weakened.
- `npm run test:ui`: 35/35 passed with Node 24.16.0 and npm 11.13.0; the resolved Playwright package remains 1.62.1. The suite checks documentation/tooling contracts and does not launch a browser. The analyzer subprocess finds and assigns all 304 rules, reports zero unresolved or stale verdicts, and reproduces all 12 recorded reviewer findings.
- Two read-only in-memory probes intentionally broke Authentication's section link and removed its Login operation row. The updated checks rejected both for the expected reason. Initial probe-harness attempts failed because of Windows command-length limits and an incorrect target label; those were corrected before recording these results.
- A fresh comparison to c443670 preserves all 304 rule rows, 149 acceptance rows, 19 use-case bodies and eight state tables. Successor/legacy link checks find no broken local file or section links. The removed identity note "None recorded." is replaced by the documented open questions.
- After refreshing GitNexus, pre-edit impact resolved the analyzer and document-test files but returned UNKNOWN with zero callers. Package scripts and CI invocations were inspected to bound their use; UNKNOWN was not an all-clear. Windows full-text search is unavailable and flow extraction is capped. No production Java, schema or dependency changed; three JS test files under `src/test/js` are changed or new, alongside two tooling scripts.
- Final `detect-changes --scope all --limit 1000` includes all 98 changed/new files after marking new paths intent-to-add; no file content is staged. It reports 30 indexed symbols, low risk, zero affected processes and no partial/truncated result warning. New helper symbols are not in the earlier index, so this result is bounded evidence, not a complete call-graph review. `git diff --check` passes.
- Maven and browser end-to-end suites were not run for these documentation/tooling changes. Independent review and approval of affected technical plans remain outstanding.

### D35 validation, 21 September 2026

- Retained a local pre-D35 snapshot and SHA-256 manifest for the 18 affected files. This step changes four feature SPECs and their changelogs, three shared MODULEs and their changelogs, the specification README, decisions.md, this tracker and the analyzer's ACC-027 assignment. No file under `src/`, Java implementation, migration or dependency changes in D35; earlier D34 JS test edits remain in the working tree.
- Existing analyzer regression first ran RED after ACC-027 was added: `found 305, assigned 304, problems 1`, with `ACC-027: 0 matches`. After assigning current-session logout to identity, `npm run test:ui` passed 35/35. No assertion was removed or weakened. Node 24.16.0, npm 11.13.0; resolved Playwright 1.62.1 without browser execution.
- GitNexus impact on scripts/module-boundaries.cjs returned UNKNOWN with zero resolved callers. Text verification identifies its subprocess invocation in module-boundaries-contract.test.mjs, npm's test:ui command and the Gitea verification/container jobs; graph zero is not proof of no use. The index's existing FTS/flow limitations remain.
- Exact comparison against the pre-D35 snapshot confirms only TSK-003/007/025, ACC-022 and AC-TSK-003/016 changed among existing canonical rows; only ACC-027 and the five stated scenarios were added, with no identifier removed. All 18 snapshot hashes remain intact. The focused documentation suite passes 12/12, including file/fragment links, versions, counts and decision references; no broken documentation link was found. `git diff --check` passes.
- Final GitNexus change analysis sees all 98 working-tree files (including prior D34 work) and 31 indexed symbols, reports low risk and no partial/truncated result warning. Zero affected processes is not a runtime conformance verdict; the documented index limitations remain. No independent reviewer has approved this working tree.
- No Maven or browser end-to-end suite ran: these checks validate the specification/tooling, not implementation of D35. The amendment is ready for technical planning; independent review and any commit, push or merge remain pending.

### D36 validation, 21 September 2026

- Retained a pre-D36 snapshot and SHA-256 manifest for 12 files: internship lifecycle SPEC/CHANGELOG; project, identity and platform MODULE/CHANGELOG pairs; specification README; decisions.md; plan.md; and src/test/js/spec-structure-contract.test.mjs. Undo only this step using that snapshot, preserving D34/D35. No production Java, migration, dependency or architecture boundary changes.
- The trace-owner regression first failed as intended with `Missing expected exception` against an empty validator, then the focused documentation suite passed 14/14 after implementation. Both rule and scenario links must reject an existing wrong file, and unknown IDs are rejected; correct same-file/fragment links remain valid. The existing assertions remain intact.
- Final `npm run test:ui` passes 37/37 with Node 24.16.0, npm 11.13.0 and resolved Playwright 1.62.1 (no browser launched). Read-only mutations redirecting ACC-027 and AC-ACC-016 to platform/MODULE.md now fail specifically for the wrong canonical owner; the real documents have no broken file/section or trace-owner links. `git diff --check` passes.
- Snapshot comparison confirms only ACC-022 and AC-ACC-018 changed among existing canonical rows; AC-ACC-019 is the sole addition and no ID was removed. All 12 backup hashes match. The decision index and acceptance tables have no detached canonical rows.
- GitNexus pre-edit file impact returned UNKNOWN with zero resolved callers; text inspection confirms npm test:ui and the Gitea verification/container workflows execute the document tests. FTS remains unavailable and the index does not certify every new test helper. No production symbol was edited.
- Final GitNexus detect-changes sees 98 working-tree files including earlier work and 31 indexed symbols, reporting low risk and no partial/truncated result warning. Its zero affected processes is bounded by the stated index limitations; it does not replace the document tests or independent review.
- The documentation-edit script's final cosmetic assertion initially failed because the D36 index row was already adjacent to D35. The intended file edits were present; the redundant assertion was corrected, with the index and document checks used to verify the result.
### D37 validation, 21 September 2026

- `npm run test:ui` passed all checks across 37 test files, validating catalogue count consistency (305 rules, 156 scenarios), state transition tables status resolution against normative rules, operation maps, changelogs, versions and cross-references.
- `TSK-007` explicitly requires a non-deleted Task for assignee transitions; `AC-TSK-021` covers rejection of status requests on soft-deleted Tasks.
- `DB-011` and `DB-012` constrain invitation and exit-request status sets to match `V1__baseline.sql`; `PROJECT_COMPLETED` and `PROJECT_CANCELLED` resolution codes align to revoked status.
- State transition tables for Project invitations and membership exit requests were added with public Azure Boards/DevOps and Atlassian/Jira comparison points, checked on 21 September 2026.
- `git diff --check` passes cleanly. No production Java code, schema, or dependency was modified.

## Validation backlog

Work that checks the code against the specification. It follows the plans, not
the other way round.

- **Parts after the module boundaries of `D28` are implemented.** Task and internship compute business dates from the `BUSINESS_ZONE` constant, contradicting `GOV-011`; fixing it changes behavior, so it is a part of its own. `AttendanceRole`, a copy of `GlobalRole` in 15 production and 18 test files, is removed, and `GlobalRole` moves to `platform`.
- **A test suite derived from the specification.** Agreed method: derive each rule's expectation from the spec first, then look for an existing test. Agreement keeps the test and tags it with the rule; disagreement is a finding; absence means a new test. Needs Docker.
- **Rule identifiers in the 108 of 123 existing test classes that lack them**, so that a rule-to-test report can be generated instead of kept by hand.
- **The Iteration 3 and 4 integration gates.** Most of their checks are acceptance scenarios now: `AC-RPT-001` (format parity), `AC-SEC-004` (production refusal), `AC-OPS-003` (bundled and external PostgreSQL), plus historical stability and concurrency.
- **No workflow runs the end-to-end suite.** It passes locally; CI runs only Maven and the UI contract tests, and the suite needs the application, a disposable PostgreSQL and Mailpit.
- **Known test gaps:** the edit clause of `LEV-012` and the eligible-workday clause of `LEV-002` survived mutation.
- **Known code findings:** `ProjectService#deleteProjectRows` deletes with native SQL, which `ARC-006` forbids (`D18`), and does not delete `task_remaining_effort_forecasts`; an empty Project has no Task, so no forecast can exist for it. `TaskService#changeStatus` lets an owning Mentor set any status, keeps no transition record, and computes variance for `DONE` Tasks only, against `TSK-021`, `TSK-023` and `TSK-025`; two tests assert the Mentor behavior.

## Environment

- Maven needs JDK 25, and the default `JAVA_HOME` on the maintainer's machine is JDK 8, so `JAVA_HOME` has to point at a JDK 25 before `./mvnw`. [CONTRIBUTING.md](CONTRIBUTING.md) explains the setup; the path itself is machine-specific and is not recorded here.
- On Windows run the suite as `./mvnw -B test`, with Docker Desktop running and browsers closed. The last full run, at `1ee043e`, passed 757 tests with no failures and no timezone flag. Run nothing else alongside it: the suite can exhaust the machine's memory.
- The pre-split UI contract baseline passed 30 of 30 checks. D34 adds a hierarchy-discovery regression check; its final evidence is recorded above.
- Playwright 1.62.1 needs Chromium build 1234; run `npx playwright install chromium` after a Playwright update. The end-to-end suite needs `LAB_E2E_START_INSTANT`, `E2E_BUSINESS_DATE` on a Tuesday to Friday, and `E2E_DB_CONTAINER`, as `CONTRIBUTING.md` describes.
