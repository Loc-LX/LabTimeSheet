# Architecture Tasks

The tasks of [PLAN.md](PLAN.md), grouped by the same parts. Each part's tasks are approved with
that part of the plan; `plan.md` tracks which are done. The tasks of part A are in the
[platform tasks](../../TASKS.md) until `A-12` closes it. D-01 was B-07 of the platform tasks.

## Part D — Business SQL behind the data-access layer

**State:** draft of 22 September 2026, approved together with part D of the plan.

Tasks run on a branch `work/fix/<area>/<what>` from `main` (`OPS-019`), after part A is done. Before any symbol is edited, GitNexus impact analysis runs on it, as `AGENTS.md` requires. After each task the full Maven suite and `npm run test:ui` pass. A test changes only as plan section D.3 allows.

| Task | What | Rules |
|---|---|---|
| D-01 | Native SQL of `ProjectService` behind the data-access layer, behavior unchanged; a build check that fails on native SQL (`createNativeQuery`, `JdbcTemplate`, native `@Query`) outside a repository | `ARC-006`, `D18` |
