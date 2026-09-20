# Lab Timesheet — current work

What is being worked on now, what comes next, and what is waiting on someone.
This file tracks progress only. What the system must do is in
[`.sdd/specs/`](.sdd/specs); how a feature will be built goes in that feature's
`PLAN.md`, and its task breakdown in `TASKS.md`. The rules for working here are
in [AGENTS.md](AGENTS.md) and [`.sdd/constitution.md`](.sdd/constitution.md).

**Last updated:** 2026-09-20 · **Branch:** `work/fix/docs/spec-parts`.
Isolated documentation worktree from `fd864ec`; the one-off `OPS-019` baseline reason is
in `D33`. This change is uncommitted and has not been pushed.

## Where the project is

| Stage | State |
|---|---|
| Delivery, Iterations 1–4 | Built. Every tracked item is done; the Iteration 3 and 4 integration gates were never run. |
| Specification | Approved rule baseline across eight specs. `D33` organizes attendance into five parts, project into six and platform into six. Existing decisions need no new signature; the clarification gaps below still block approval of the affected plans. |
| Constitution | Locked, version `2.0.2`. |
| Technical plans (`PLAN.md`) | `feature-platform` approved at `1.0`; its line 108 contradicts line 230 on who resolves scope, and its §3 is superseded by `D32`; both are revised in the plan phase. The other modules have no plan yet; each is written per module, one section per part, in step 8. |
| Task breakdown (`TASKS.md`) | Not started. |
| Implementation of `D1`, `D12`–`D27`, `D31` and `D32` | Not started; the code still follows the earlier rules. |
| Validation against the specification | Not started. |

The delivery plan that tracked Iterations 1–4 item by item, with owners, branches
and integration commits, is in git history: `git show b71fc29:plan.md`.

## Now

- `D33` documentation change: three specs organized into parts with rules, use cases, scenarios, data and dependencies. Their changelogs and decision record describe the change; no production/test code changed.
- Acceptance summaries now follow the existing Admin report grants, responsible-Mentor decisions, limited Leader/Mentor Task transitions and cancellation history. The unresolved questions below were not answered by guessing.
- Document verification and independent review are reported below; nothing in this change is committed or merged. The base documentation branch has its earlier Gitea backup; this new worktree is local.

### Readiness for the plan phase

| Scope | What can proceed | What still gates approval |
|---|---|---|
| Attendance A1–A5 | Draft part-level design against the existing rules, including shared history and finalization guards | Design the cross-part cases in §5 and their evidence; do not treat A5 as an optional later guard |
| Project P1–P6 | Draft settled behavior and dependency contracts; P2/P6 still reference the P1/P4/P5 guards | Resolve the named creation/status, invitation/exit and cancellation-readiness questions before approving their dependent sections |
| Platform F1–F6 | Revise the technical plan around the six shared scopes and settled rules | Resolve its scope-context contradiction and superseded data section; include the three observable-rule trace gaps and the business decisions needed by its consumers |
| Architecture steps 5–7 | Prepare the module-boundary plan from `D28` and `ADR-006` | Maintainer chooses where the cross-module plan/tasks live; document organization does not implement the boundary change |

## Next

First close the affected business questions below and review the `D33` diff. Then prepare
plans for settled parts with their dependency checks, followed by tasks, code and validation,
including for the module boundaries
of `D28`. Savepoint before it: `savepoint/pre-module-split-2026-09-17`.

Numbered as the steps of `D28`, so that "step 6" means the same thing here, in `ADR-006`
and in the constitution.

1. **Step 1 — revise the decision documents.** Done, committed in `9123150`.
2. **Step 2 — check all 296 rules for mentions of another module's concepts.** Done, in `9123150`: `node scripts/module-boundaries.cjs` exits 0 with 99 verdicts and reproduces the reviewer's 12 findings.
3. **Step 3 — lock** `D28`, `ADR-006`, `ARC-005`, `ARC-006`, `AC-ARC-001` and the constitution `2.0.0` in one commit. Done: `9123150`.
4. **Step 4 — one spec per module.** Done: `58711c5`. Gate: 296 rules with none lost and no word changed, and the rules of the new `UC-03` and `UC-18`, and of the new `UC-04` and `UC-19`, equal those of the use case each pair replaced.
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
| Project P5: initial Task status; whether the assignee must unblock to the recorded pre-block state (`TSK-007` versus `TSK-025`). Reopen reasons already follow `TSK-025` | The maintainer | [Project Notes](.sdd/specs/feature-project/SPEC.md#notes--open-questions) |
| Project P3/P4: complete invitation and exit-request status sets, not just resolution codes | The maintainer | [Project Notes](.sdd/specs/feature-project/SPEC.md#notes--open-questions) |
| Project P1 with internship: whether an unfinished Task on a cancelled, immutable Project blocks completion/withdrawal (`PRJ-023`, `ACC-022`) | The maintainer | [Project Notes](.sdd/specs/feature-project/SPEC.md#notes--open-questions) |
| Identity and notification: activation/lock/deactivation edges; complete email delivery states including SENT and NOT_REQUIRED | The maintainer | [Platform §22.3](.sdd/specs/feature-platform/SPEC.md) |
| Where the plan and tasks of step 5 live. They are not the platform plan, whose subject is the platform rules, and no feature owns them | The maintainer | This file |

## Evidence for the D33 documentation change

- `npm run test:ui`: 30/30 pass on 20 September 2026, including document structure, counts, versions, references, decision index and relative file links. Node `24.16.0`, npm `11.13.0`; the Playwright contract reads resolved `@playwright/test` `1.62.1` from the lockfile. This run does not execute browser or Java behavior.
- Read-only comparison against `fd864ec`: all 304 rule rows are text-identical in their original canonical specs; all 149 scenario IDs remain, with six acceptance rows corrected. Exactly the eight approved files changed; no `src/` file changed. All 17 part links resolve to unique anchors; `git diff --check` passes.
- No Maven or end-to-end run for this prose change (`TST-009`). GitNexus symbol impact/change analysis does not assess prose; there is no code-symbol edit or proposed commit in this step.
- Independent review is still required before proposing this change for commit/merge; no independent reviewer has examined this diff yet.

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
- `npm run test:ui` passes 30 of 30, including ten spec-structure checks: six on the shape of the documents and four on whether their prose and state transition tables agree with their own rules.
- Playwright 1.62.1 needs Chromium build 1234; run `npx playwright install chromium` after a Playwright update. The end-to-end suite needs `LAB_E2E_START_INSTANT`, `E2E_BUSINESS_DATE` on a Tuesday to Friday, and `E2E_DB_CONTAINER`, as `CONTRIBUTING.md` describes.
