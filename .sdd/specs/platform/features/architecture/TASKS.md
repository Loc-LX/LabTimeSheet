# Architecture Tasks

The tasks of [PLAN.md](PLAN.md), grouped by the same parts. Each part's tasks are approved with
that part of the plan; `plan.md` tracks which are done. [Part A](#part-a--module-boundaries)
moved here from the platform tasks under `D40` after `A-12` closed it. D-01 was B-07 of the platform tasks.

## Part A — Module boundaries

**State:** A-01 to A-12 approved on 22 September 2026, together with part A of the plan; A-13 added and approved on 24 September 2026.

No task starts before part A is approved and the documentation branch is merged into `main`
with the maintainer's permission; the tasks then run on `work/fix/architecture/<name>`
(`OPS-019`). Every task follows the same four conditions:

- Before any symbol is edited, GitNexus impact analysis runs on it, as `AGENTS.md` requires; a
  high or critical risk, or an unresolved one, is reported before the edit.
- Each task that places code updates, in the same change, the approved package list of
  `LayerStructureTest`.
- After A-02, the task's diff of the cycle test's list file deletes lines and adds none.
- After the task, the full Maven suite and `npm run test:ui` pass. Every test change is of a kind
  plan section A.7 allows, checked on the task's diff: each test file keeps its number of assertion
  calls, and every changed line that holds one changes only a receiver or an argument. An
  assertion changes only where A.7 names it for that task. The commit lists the lines checked.

| Task | What | Rules | Done when |
|---|---|---|---|
| A-01 | Establish the baseline: run the full Maven suite, the end-to-end suite and `npm run test:ui`; refresh the GitNexus index; re-run `scripts/module-boundaries.cjs` and record the class placement it gives at that commit; record the test database's collation and provider with `select datcollate, datlocprovider from pg_database where datname = current_database()` | `ARC-005` | All pass, and the results, the placement and the collation are recorded in `plan.md`; the provider is one whose collations are deterministic, or the eligibility design of plan section A.4 is revisited before A-10 |
| A-02 | Write the cycle test in the test package `architecture`, with its placement list taken from A-01 and its allowance list of single references measured then, both in one file. Make it fail once for each reason: an artificial cycle, a reference into `config`, a new reference between two modules already listed, and an `ADR-006` interface breaking one of its four conditions | `ARC-005`, `ARC-006`, `AC-ARC-001` | It passes on the measured lists, each failure has been seen, and the commit records them |
| A-03 | Place `platform`: the classes of `integration` other than HolidayAPI and `SmtpController`, `GlobalRole`, and `SecurityProperties` (`R10`). Make the `platform` mail and SMTP services take the verified actor and the recipient from their caller (`R4`) | `ARC-005`, `ARC-006` | Nothing in `platform` refers to a feature or to `config` |
| A-04 | Place `identity`: every class of `account` moves unchanged, the nine internship classes included, and `SmtpController` joins it (`R4`) | `ARC-005` | No package `account` remains, and every reference from `identity` to a module above it is an allowance entry that A-10 or A-11 removes |
| A-05 | Inside `attendance`, stop reaching the policy and calendar entities and repositories (plan section A.4): the two entities keep the policy version identifier, `toDomain` receives the `AttendancePolicy` record, and every service that turns those entities into records or names the policy or calendar entities and repositories uses new reads of the calendar service contract, collecting the version identifiers a request needs and reading them in one call before looping over rows | `ARC-006`, `ARC-010`, `GOV-005` | No class that stays in `attendance` names a policy or calendar entity or repository, no policy read sits inside a loop over rows, and the attendance and leave results are unchanged |
| A-06 | Place `calendar` and `notification`: the policy and calendar classes of `attendance`, the HolidayAPI classes of `integration`, `AdminSettingsController`, and `NotificationController`. `calendar` resolves its actor through `identity` and `AttendanceRole` is removed (`R7`); `calendar` computes the business date (`R8`). Update `AttendanceLayerStructureTest` | `ARC-005`, `ARC-006`, `GOV-011` | Neither module refers to `attendance`, `project` or `reporting`; the business date comes from the same timezone as before |
| A-07 | Merge `task` into `project` | `ARC-005` | No package `task` remains |
| A-08 | Move `LayerStructureTest` and `AttendanceAndTaskWorkSeparationTest` from the test package `config` to `architecture` | `ARC-005`, `AC-ARC-001`, `GOV-004` | The test package `config` holds only tests of `config` |
| A-09 | Write the invariant test of `ACC-019` that `D28` requires: every `INTERN` account has exactly one Intern profile, no other account has one, and a creation that fails midway leaves neither row. Break the code so an `INTERN` account commits without a profile, see the test fail for that reason, restore the code, and see it pass | `ACC-019` | The failing and the passing run are recorded in the commit |
| A-10 | Place `internship` (`R3`): first write the tests of the directory union and of the eligibility order (plan section A.7) and see them pass against the current join; compose the directory as a union, and the eligibility and due-date reads as intersections with their order, over the service contract of `identity` (plan section A.4); make the union test fail once by dropping one side, and the order test once by sorting names in Java; then move the nine internship classes, the internship methods of `AccountService` as an `internship` service, and the Admin's account screens of `AccountController` as an `internship` controller on the same routes | `ARC-005`, `ARC-006`, `ACC-019`, `ACC-017` | A-09, the union test and the order test pass; the directory, eligibility and due-date tests pass unchanged; `identity` refers to nothing in `internship` |
| A-11 | Build `R1`: `internship` declares the readiness interface; its completion, withdrawal and account page use it; `project` implements it with the predicate the code applies today; completion and withdrawal stop accepting a guard from their caller. Write the new unfinished-Task refusal test of plan section A.7 and see it fail by breaking the readiness implementation. Add the interface's name to the `ADR-006` table | `ACC-022`, `ARC-006` | Both lists of the cycle test are empty, and it checks the four conditions of the new interface |
| A-12 | Close the part: the cycle test and the structure tests pass; the history of the list file adds no line after A-02; the full Maven suite, the end-to-end suite and `npm run test:ui` pass; GitNexus change detection reports only the expected processes. Retire the cycle check of `scripts/module-boundaries.cjs` and adjust the JS test that runs it | `AC-ARC-001` | `plan.md` records the evidence, and step 7 of `D28` can compare the code with the documents |
| A-13 | Close the two structure-test gaps found by step 7. In `LayerStructureTest`, require the production feature modules to equal the seven modules of `ARC-005`, with no `integration`, and add a test that requires every test package to equal a production package except `architecture` and `ui`. Move `AttendanceLombokBoilerplateTest` and `ReportingArchitectureTest` to `architecture` with `git mv`, without changing assertions. Before editing each symbol, run GitNexus impact analysis. For RED, temporarily add one test class in a package with no production counterpart and see the package-mirror test fail for it; remove it, then temporarily add one production class in `feature.integration` and see the exact-module assertion fail for it; remove both probes before GREEN | `ARC-005`, `AC-ARC-001`, `TST-011` | Both temporary defects fail for the intended reason and are removed; the focused structure tests pass; `./mvnw -B clean test` passes on JDK 25 with Docker running; `npm run test:ui` and `git diff --check` pass; GitNexus change detection covers the final working tree without a partial or truncated result |

## Part D — Business SQL behind the data-access layer

**State:** draft of 22 September 2026, approved together with part D of the plan.

Tasks run on a branch `work/fix/<area>/<what>` from `main` (`OPS-019`), after part A is done. Before any symbol is edited, GitNexus impact analysis runs on it, as `AGENTS.md` requires. After each task the full Maven suite and `npm run test:ui` pass. A test changes only as plan section D.3 allows.

| Task | What | Rules |
|---|---|---|
| D-01 | Native SQL of `ProjectService` behind the data-access layer, behavior unchanged; a build check that fails on native SQL (`createNativeQuery`, `JdbcTemplate`, native `@Query`) outside a repository | `ARC-006`, `D18` |
