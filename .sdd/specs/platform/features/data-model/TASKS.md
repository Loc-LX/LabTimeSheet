# Data model Tasks

The tasks of [PLAN.md](PLAN.md), part C, moved from the platform tasks under `D40`; C-04 to C-07 now
name the feature plans whose code each contract ships with.
They are approved with that part of the plan; `plan.md` tracks which are done.

## Part C — The schema change the decisions require

**State:** draft of 22 September 2026, approved together with part C of the plan.

Tasks run on a branch `work/fix/<area>/<what>` from `main` (`OPS-019`), after part A is done. Before any symbol is edited, GitNexus impact analysis runs on it, as `AGENTS.md` requires. After each task the full Maven suite and `npm run test:ui` pass. Every migration step starts with its probe tests, seen failing (plan section C.7).

| Task | What | Done when |
|---|---|---|
| C-01 | Identify each target database's Flyway history, and read the stored rows C.5 depends on: `CANCELLED` leave requests with and without a decision time, policy versions with a quota above 4, notifications with a payload in `NOT_REQUIRED` or `UNAVAILABLE`, deactivated accounts. Change nothing | The history and the counts are recorded in `plan.md`; a different `V2` stops the work |
| C-02 | The probe tests of V3, seen failing | Each fails for the reason it names |
| C-03 | V3 (C.3), its backfills, and the §19.4 diagram | The probes of C-02 pass, with `AC-DB-001`, `AC-DB-008` and `AC-DB-010`, and so do the full Maven suite, the end-to-end suite and `npm run test:ui` against the unchanged code |
| C-04 | The identity contract, shipped with the code of the Account lifecycle and Authentication plans | Its probes and `AC-DB-009` pass |
| C-05 | The attendance contract, with the reclassification, shipped with the code of the Leave, Missed-checkout correction and Attendance exception plans | Its probes, `AC-DB-006` and `AC-DB-011` pass |
| C-06 | The project contract, shipped with the code of the Project lifecycle and Task management plans | Its probes, `AC-DB-007` and `AC-DB-012` pass |
| C-07 | The notification contract, shipped with the code of the Email delivery plan | Its probe passes |
| C-08 | Close the part | Every `AC-DB-*` scenario passes |
