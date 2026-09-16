# Lab Timesheet — current work

What is being worked on now, what comes next, and what is waiting on someone.
This file tracks progress only. What the system must do is in
[`.sdd/specs/`](.sdd/specs); how a feature will be built goes in that feature's
`PLAN.md`, and its task breakdown in `TASKS.md`. The rules for working here are
in [AGENTS.md](AGENTS.md) and [`.sdd/constitution.md`](.sdd/constitution.md).

**Last updated:** 2026-09-16 · **Branch:** `work/fix/docs/restore-and-restructure`,
ahead of `origin/main`, nothing pushed.

## Where the project is

| Stage | State |
|---|---|
| Delivery, Iterations 1–4 | Built. Every tracked item is done; the Iteration 3 and 4 integration gates were never run. |
| Specification | Approved. Eight specs; each version is in its spec header and `CHANGELOG.md`. `D14` is closed and, like `D12`, `D13`, `D15`, `D21` and `D23`–`D25`, provisional. |
| Constitution | Locked as version `1.0.0` on 15 September 2026 (`D20`). |
| Technical plans (`PLAN.md`) | `feature-platform` drafted on 16 September 2026 and awaiting approval; the other seven not started. |
| Task breakdown (`TASKS.md`) | Not started. |
| Implementation of the 14 and 16 September decisions | Not started; the code still follows the earlier rules. |
| Validation against the specification | Not started. |

The delivery plan that tracked Iterations 1–4 item by item, with owners, branches
and integration commits, is in git history: `git show b71fc29:plan.md`.

## Now

- The 24 decisions of 14 September 2026 are applied to the specs, the constitution, and `.sdd/decisions.md`. The local history was rewritten to drop citations of an outside source, and the branch and its spec tags were pushed to Gitea as a backup; commits after that backup are local. Nothing is merged into `main`. The sixteen single-feature rules have moved out of `feature-platform`, and `cspell.json` holds the project dictionary.
- The end-to-end suite ran for the first time on 15 September 2026, against a disposable PostgreSQL, and all six journeys pass. The runs found a code check with no rule behind it, a Project start date refused when in the past, which `D21` removed (`PRJ-024`), and journey steps written for an older interface. The correction journey now seeds a previous-workday attendance row instead of correcting a row checked in minutes earlier, which `COR-001` never allowed.
- Four more business decisions on 16 September 2026, benchmarked against large work-management and HR systems: no `ARCHIVED` Project state (`D22`), one 48-hour adjustment policy for corrections and exceptions (`D23`), a Mentor mark bounded by the attendance period instead of a separate limit (`D24`), and an Intern view of their own attendance (`D25`, `RPT-015`).

## Next

1. Merge into `main` (item 22). Every check passed at `1ee043e`: the full Maven suite (757 tests, no timezone flag, `D19`), the end-to-end suite, and `npm run test:ui`. Every commit since then changes documentation only, so the suites still stand for the current head. The merge reaches `main` by review, not by an agent's commit. Pushing the branch as a backup and merging into `main` are separate actions, each needing its own permission.
2. Verify the application end to end: `scripts/demo-seed.sql` loads, the end-to-end suite passes, the main business flows work; then demonstrate to the instructor (item 23).
3. `PLAN.md` for each feature. The platform plan is drafted and carries two things the others depend on: the one authorization policy of `AUTH-012`, and the single `V3` migration the recorded decisions need. Then `feature-attendance`, `feature-task`, `feature-project`, `feature-reporting`, and the three that change least.

## Waiting on a decision

| Item | Waiting on | Recorded in |
|---|---|---|
| Confirmation of `D12`–`D15` and `D21`, `D23`–`D25`, all provisional | the instructor | `decisions.md` |
| Split rules that span several features (`UI-019`, `AUTH-003`, `AUTH-004`, `AUTH-009`, `AUTH-011`, `DB-008`) | after the platform plan | this file |

## Validation backlog

Work that checks the code against the specification. It follows the plans, not
the other way round.

- **A test suite derived from the specification.** Agreed method: derive each rule's expectation from the spec first, then look for an existing test. Agreement keeps the test and tags it with the rule; disagreement is a finding; absence means a new test. Needs Docker.
- **Rule identifiers in the 108 of 123 existing test classes that lack them**, so that a rule-to-test report can be generated instead of kept by hand.
- **The Iteration 3 and 4 integration gates.** Most of their checks are acceptance scenarios now: `AC-RPT-001` (format parity), `AC-SEC-004` (production refusal), `AC-OPS-003` (bundled and external PostgreSQL), plus historical stability and concurrency.
- **No workflow runs the end-to-end suite.** It passed locally on 15 September 2026; CI still runs only Maven and the UI contract tests, and the suite needs the application, a disposable PostgreSQL and Mailpit.
- **Known test gaps:** the edit clause of `LEV-012` and the eligible-workday clause of `LEV-002` survived mutation.
- **Known code findings:** `ProjectService#deleteProjectRows` does not delete `task_remaining_effort_forecasts`, and its native SQL moves behind the data-access layer (`D18`).
- **`app.css` trip-wire:** a local Tailwind build emits `.block{display:block}`, which the committed file lacks, and CI compares the file with `git diff --exit-code`.

## Environment

- Maven needs JDK 25; the default `JAVA_HOME` on the maintainer's machine is JDK 8. JDK 25 is at `/c/Users/Admin/AppData/Roaming/Code/User/globalStorage/pleiades.java-extension-pack-jdk/java/25`.
- On Windows run the suite as `./mvnw -B test`, with Docker Desktop running and browsers closed. The last full run, on 15 September 2026 at `1ee043e`, passed 757 tests with no failures and no timezone flag. An earlier attempt the same day stopped after 238 passing tests when the machine ran out of memory.
- `npm run test:ui` passes 26 of 26, including the six spec-structure checks.
- Playwright 1.62.1 needs Chromium build 1234; run `npx playwright install chromium` after a Playwright update. The end-to-end run of 15 September used `LAB_E2E_START_INSTANT=2026-09-15T01:00:00Z`, `E2E_BUSINESS_DATE=2026-09-15` and `E2E_DB_CONTAINER=labtimesheet-e2e-postgres`, as `CONTRIBUTING.md` describes.
