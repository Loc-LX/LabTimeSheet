# Authentication Tasks

Tasks for [PLAN.md](PLAN.md). `plan.md` is the only progress tracker. This task list was approved with its plan on 26 September 2026.

Work starts from the current approved `main` on an isolated `work/fix/identity/<what>` branch. Each
code step follows TDD: write the smallest test, run it and see the expected failure, then implement
the minimum change (`TST-001`–`TST-010`). Test Javadoc names the requirement IDs and observable
failure. Run GitNexus impact before editing every production symbol; resolve UNKNOWN before
proceeding. Do not weaken existing assertions. The identity contract migration is data-model C-04
and ships with the identity code; this list does not redefine it.

| Task | What | Done when |
|---|---|---|
| AN-01 | Sign-in only for active accounts (`ACC-030`) | The `AC-ACC-022` state matrix is written first. If it fails, impact is recorded before production edits and only `ACTIVE` with the correct password authenticates, every other attempt receiving the same generic response and no session; if it passes on the current code, the evidence records that and no production code changes. |
| AN-02 | C-04 coordination | Authentication and account-lifecycle code ship with data-model C-04; `AC-DB-009` and `AC-ACC-022` pass together. No migration is defined here. |
| AN-03 | Verification and evidence | Focused tests, the affected identity suite, full `./mvnw test`, `npm run test:ui`, and `git diff --check` pass; actual commands, results and tool versions are recorded in `plan.md`. |
