# Lab Timesheet — current work

What is being worked on now, what comes next, and what is waiting on someone.
This file tracks progress only. What the system must do is in
[`.sdd/specs/`](.sdd/specs); how a feature will be built goes in that feature's
`PLAN.md`, and its task breakdown in `TASKS.md`. The rules for working here are
in [AGENTS.md](AGENTS.md) and [`.sdd/constitution.md`](.sdd/constitution.md).

**Last updated:** 2026-09-15 · **Branch:** `work/fix/docs/restore-and-restructure`,
ahead of `origin/main`, nothing pushed.

## Where the project is

| Stage | State |
|---|---|
| Delivery, Iterations 1–4 | Built. Every tracked item is done; the Iteration 3 and 4 integration gates were never run. |
| Specification | Approved. Eight specs; each version is in its spec header and `CHANGELOG.md`. `D14` is closed and, like `D12`, `D13` and `D15`, provisional. |
| Technical plans (`PLAN.md`) | Not started. |
| Task breakdown (`TASKS.md`) | Not started. |
| Implementation of the 14 September decisions | Not started; the code still follows the earlier rules. |
| Validation against the specification | Not started. |

The delivery plan that tracked Iterations 1–4 item by item, with owners, branches
and integration commits, is in git history: `git show b71fc29:plan.md`.

## Now

- The 24 decisions of 14 September 2026 are applied to the specs, the constitution, and `.sdd/decisions.md`. The local history was rewritten to drop citations of an outside source, and the branch and its spec tags were pushed to Gitea as a backup; commits after that backup are local. Nothing is merged into `main`. The sixteen single-feature rules have moved out of `feature-platform`, and `cspell.json` holds the project dictionary.

## Next

1. Run the end-to-end suite. The full Maven suite, including the integration tests, passed on Windows on 15 September 2026 without a timezone flag (`D19`); `npm run test:ui` passing alone is not enough to merge.
2. Lock the constitution once step 1 passes (item 15). Its ten ambiguities and the last contradiction and enforcement-gap check are done (`D20`); a current run is what remains.
3. Merge into `main` only after steps 1 and 2 (item 22). Pushing the branch as a backup and merging into `main` are separate actions, each needing its own permission.
4. Verify the application end to end: `scripts/demo-seed.sql` loads, the end-to-end suite passes, the main business flows work; then demonstrate to the instructor (item 23).
5. `PLAN.md` for `feature-platform` (`AUTH-012`, `ADR-005`), then `feature-task`, `feature-project`, `feature-reporting`, `feature-attendance`.

## Waiting on a decision

| Item | Waiting on | Recorded in |
|---|---|---|
| Confirmation of `D12`, `D13`, `D14`, `D15`, all provisional | the instructor | `decisions.md` |
| Split rules that span several features (`UI-019`, `AUTH-003`, `AUTH-004`, `AUTH-009`, `AUTH-011`, `DB-008`) | after the platform plan | this file |

## Validation backlog

Work that checks the code against the specification. It follows the plans, not
the other way round.

- **A test suite derived from the specification.** Agreed method: derive each rule's expectation from the spec first, then look for an existing test. Agreement keeps the test and tags it with the rule; disagreement is a finding; absence means a new test. Needs Docker.
- **Rule identifiers in 88 existing test classes**, so that a rule-to-test report can be generated instead of kept by hand.
- **The Iteration 3 and 4 integration gates.** Most of their checks are acceptance scenarios now: `AC-RPT-001` (format parity), `AC-SEC-004` (production refusal), `AC-OPS-003` (bundled and external PostgreSQL), plus historical stability and concurrency.
- **The end-to-end suite has never run.** No workflow runs `npm run test:e2e`, and it needs the application and PostgreSQL.
- **Known test gaps:** the edit clause of `LEV-012` and the eligible-workday clause of `LEV-002` survived mutation.
- **Known code findings:** `ProjectService#deleteProjectRows` does not delete `task_remaining_effort_forecasts`, and its native SQL moves behind the data-access layer (`D18`).
- **`app.css` trip-wire:** a local Tailwind build emits `.block{display:block}`, which the committed file lacks, and CI compares the file with `git diff --exit-code`.

## Environment

- Maven needs JDK 25; the default `JAVA_HOME` on the maintainer's machine is JDK 8. JDK 25 is at `/c/Users/Admin/AppData/Roaming/Code/User/globalStorage/pleiades.java-extension-pack-jdk/java/25`.
- On Windows run the suite as `./mvnw -B test`, with Docker Desktop running and browsers closed. The last full run, on 15 September 2026, passed 755 tests with no failures and no timezone flag. An earlier attempt the same day stopped after 238 passing tests when the machine ran out of memory.
- `npm run test:ui` passes 26 of 26, including the six spec-structure checks.
