# Authorization Tasks

The tasks of [PLAN.md](PLAN.md), part B, moved from the platform tasks under `D40`. B-05 and B-07
left with the rules they build: they are E-01 of the [Security](../security/TASKS.md) and D-01 of the
[Architecture](../architecture/TASKS.md) tasks.
They are approved with that part of the plan; `plan.md` tracks which are done.

## Part B — One authorization policy

**State:** draft of 22 September 2026, approved together with part B of the plan.

Tasks run on a branch `work/fix/<area>/<what>` from `main` (`OPS-019`), after part A is done. Before any symbol is edited, GitNexus impact analysis runs on it, as `AGENTS.md` requires. After each task the full Maven suite and `npm run test:ui` pass. A test changes only as plan section B.8 allows.

| Task | What | Rules |
|---|---|---|
| B-01 | Read the §5.2 matrix into a fixture and assert all 144 cells against what the matrix grants. Record the failing cells: they are the work list of B-02 and B-03, each a finding, never a baseline to keep | `AC-AUTH-011`, first half |
| B-02 | The policy and the catalogue in `platform`; route the three Admin report checks through it; refuse an owning Mentor any Task status change the matrix does not grant | `AUTH-012`, refusal half of `TSK-023` |
| B-03 | Every remaining role decision in services and templates, one module at a time, each module adding the scope it resolves (B.4) | `AUTH-012`, `AUTH-002` |
| B-04 | Withdraw one Admin capability in the catalogue and show that only that cell changes | `AC-AUTH-011`, second half; `D1` |
| B-06 | One web test per protected record type: an existing record the caller may not see and an absent identifier give the same status, view and body | `AUTH-002` |
