# Security Tasks

The tasks of [PLAN.md](PLAN.md), grouped by the same parts. Each part's tasks are approved with
that part of the plan; `plan.md` tracks which are done. E-01 was B-05 of the platform tasks.

## Part E — Evidence for the production profile

**State:** draft of 22 September 2026, approved together with part E of the plan.

Tasks run on a branch `work/fix/<area>/<what>` from `main` (`OPS-019`), after part A is done. Before any symbol is edited, GitNexus impact analysis runs on it, as `AGENTS.md` requires. After each task the full Maven suite and `npm run test:ui` pass. A test changes only as plan section E.2 allows.

| Task | What | Rules |
|---|---|---|
| E-01 | One web test of the production response headers and cookie attributes against `AC-SEC-008`, and under the production profile that no development relaxation is present | `SEC-011`, `SEC-013` |
