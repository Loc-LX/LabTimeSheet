# Account lifecycle Tasks

Tasks for [PLAN.md](PLAN.md). `plan.md` is the only progress tracker. This task list was approved with its plan on 26 September 2026.

Work starts from the current approved `main` on an isolated `work/fix/identity/<what>` branch. Each
code step follows TDD: write the smallest test, run it and see the expected failure, then implement
the minimum change (`TST-001`–`TST-010`). Test Javadoc names the requirement IDs and observable
failure. Run GitNexus impact before editing every production symbol; resolve UNKNOWN before
proceeding. Do not weaken an existing assertion. The identity contract migration is data-model C-04
and ships with these changes; this list does not redefine it.

| Task | What | Done when |
|---|---|---|
| AL-01 | Deactivation keeps the lock (`ACC-016`) | A focused test that deactivates a `LOCKED` account and expects its lock timestamp kept is seen failing first; impact on `AppUser#deactivate` is recorded before the edit; only the clearing of the lock timestamp is removed. |
| AL-02 | Pending-account deactivation (`ACC-028`) | A focused `AC-ACC-020` test is seen failing first; impact is recorded before production edits; implementation invalidates unused activation and reset tokens and retains the specified identity, role, attribution, and credential shape. The full scenario passes without weakening assertions. |
| AL-03 | Reinstatement (`ACC-029`) | The three-state `AC-ACC-021` test is seen failing first; impact is recorded before production edits; reinstatement selects `ACTIVE`, `LOCKED`, or `PENDING_ACTIVATION` from the account's retained history and restores no internship or Project state. The full scenario passes. |
| AL-04 | C-04 coordination | Lifecycle code ships in the same reviewed change as data-model C-04; its `AC-DB-009` schema probes and lifecycle acceptance tests pass together. No separate migration or duplicate schema design is added here. |
| AL-05 | Verification and evidence | Focused tests, affected identity suite, full `./mvnw test`, `npm run test:ui`, and `git diff --check` pass; `plan.md` records commands, results, tool versions, and any remaining scope limits. |
