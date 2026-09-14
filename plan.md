# Lab Timesheet — current work

What is being worked on now, what comes next, and what is waiting on someone.
This file tracks progress only. What the system must do is in
[`.sdd/specs/`](.sdd/specs); how a feature will be built goes in that feature's
`PLAN.md`, and its task breakdown in `TASKS.md`. The rules for working here are
in [AGENTS.md](AGENTS.md) and [`.sdd/constitution.md`](.sdd/constitution.md).

**Last updated:** 2026-09-14 · **Branch:** `work/fix/docs/restore-and-restructure`,
ahead of `origin/main`, nothing pushed.

## Where the project is

| Stage | State |
|---|---|
| Delivery, Iterations 1–4 | Built. Every tracked item is done; the Iteration 3 and 4 integration gates were never run. |
| Specification | Approved. Eight specs; platform, project, task and reporting at 1.1.0, the rest at 1.0.x. |
| Technical plans (`PLAN.md`) | Not started. |
| Task breakdown (`TASKS.md`) | Not started. |
| Implementation of the 14 September decisions | Not started; the code still follows the earlier rules. |
| Validation against the specification | Not started. |

The delivery plan that tracked Iterations 1–4 item by item, with owners, branches
and integration commits, is in git history: `git show 702d1a5:plan.md`.

## Now

- Nothing is in progress. The documentation restructure finished on 14 September 2026: guides under `docs/guides/`, `.sdd/shared_context.md` as the stack reference, this file as the tracker, the dead verification script removed, and `src/test/js/spec-structure-contract.test.mjs` checking the specs in CI.
- Small leftover: assumptions `A1`, `A3` and `A4` in `.sdd/product.md` still describe evidence records and fixture dates that no longer exist.

## Next

1. `PLAN.md` for `feature-platform`, starting with the single authorization policy (`AUTH-012`, `ADR-005`), because Admin report access (`D1`) and Task status permissions (`D13`) both depend on it.
2. `PLAN.md` for `feature-task`, `feature-project`, and `feature-reporting`, covering `D12`, `D13`, and `D15`.
3. `feature-attendance` waits until `D14` is settled.
4. `TASKS.md` for each approved plan, then implementation.

## Waiting on a decision

| Item | Waiting on | Recorded in |
|---|---|---|
| `D14`: who starts an attendance exception, its deadlines, whether leave and corrections move to a responsible Mentor | the laboratory | `open-decisions.md` `D14`, attendance spec notes |
| Move the sixteen rules that belong to one feature out of `feature-platform` | maintainer | not yet recorded |
| Split rules that span several features (`UI-019`, `AUTH-003`, `AUTH-004`, `AUTH-009`, `AUTH-011`, `DB-008`) | after the platform plan | not yet recorded |
| Constitution: the `GOV-014` row in Known enforcement gaps still proposes a test that cannot be written | maintainer, specific wording | platform spec notes |
| Constitution: no stated criterion for which rules it indexes; no `AUTH` row although `AUTH-002` is a Layer 1 kind of invariant; `SEC-007` and `SEC-013` omitted, and `SEC-001` loses "under every profile"; `OPS-019` row keeps half its rule; the definition-of-done bullet on "evidence paths in the tracker" predates `ADR-004`; "shared fragments" undefined; unused `Supervisor` row; version `1.0.0` while unsigned; `SEC-011`'s gap stated twice | maintainer | this file |
| Two absolutes held only by `AGENTS.md`, not the constitution: never weaken or delete an assertion, never edit an applied Flyway migration | maintainer | this file |
| Sign the constitution | maintainer | constitution header |
| Rewrite six local commit messages before any push | maintainer | this file |

## Validation backlog

Work that checks the code against the specification. It follows the plans, not
the other way round.

- **A test suite derived from the specification.** Agreed method: derive each rule's expectation from the spec first, then look for an existing test. Agreement keeps the test and tags it with the rule; disagreement is a finding; absence means a new test. Needs Docker.
- **Rule identifiers in 88 existing test classes**, so that `.sdd/reviews/traceability.md` can be generated. It currently has 34 rows whose status is unreconciled.
- **The Iteration 3 and 4 integration gates.** Most of their checks are acceptance scenarios now: `AC-RPT-001` (format parity), `AC-SEC-004` (production refusal), `AC-OPS-003` (bundled and external PostgreSQL), plus historical stability and concurrency.
- **The end-to-end suite has never run.** No workflow runs `npm run test:e2e`, and it needs the application and PostgreSQL.
- **Known test gaps:** the edit clause of `LEV-012` and the eligible-workday clause of `LEV-002` survived mutation.
- **Known code findings:** `ProjectService#deleteProjectRows` does not delete `task_remaining_effort_forecasts`; `ProjectService` holds native SQL against `ARC-006`.
- **`app.css` trip-wire:** a local Tailwind build emits `.block{display:block}`, which the committed file lacks, and CI compares the file with `git diff --exit-code`.

## Environment

- Maven needs JDK 25; the default `JAVA_HOME` on the maintainer's machine is JDK 8. JDK 25 is at `/c/Users/Admin/AppData/Roaming/Code/User/globalStorage/pleiades.java-extension-pack-jdk/java/25`.
- On Windows run the suite as `./mvnw -B test -DargLine="-Duser.timezone=Asia/Ho_Chi_Minh"`, with Docker Desktop running. The last full run, on 13 September 2026, passed 754 tests with no failures; no production source has changed since.
- `npm run test:ui` passes 26 of 26, including the six spec-structure checks.
