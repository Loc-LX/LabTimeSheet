# Platform Tasks

The tasks of [PLAN.md](PLAN.md), grouped by the same parts. Each part's tasks are approved with
that part of the plan; `plan.md` tracks which are done.

## Part A — Module boundaries

**State:** draft of 22 September 2026, approved together with part A of the plan.

No task starts before part A is approved and the documentation branch is merged into `main`
with the maintainer's permission; the tasks then run on `work/fix/architecture/<name>`
(`OPS-019`). Every task follows the same three conditions:

- Before any symbol is edited, GitNexus impact analysis runs on it, as `AGENTS.md` requires; a
  high or critical risk, or an unresolved one, is reported before the edit.
- Each task that places code updates, in the same change, the approved package list of
  `LayerStructureTest` and removes the entries it resolves from the cycle test's list. The list
  never grows.
- After the task, the full Maven suite and `npm run test:ui` pass. A test changes only if its
  package moved or it is a structure test of plan section A.4.

| Task | What | Rules | Done when |
|---|---|---|---|
| A-01 | Establish the baseline: run the full Maven suite, the end-to-end suite and `npm run test:ui`; refresh the GitNexus index; re-run `scripts/module-boundaries.cjs` and record the class placement it gives at that commit | `ARC-005` | All pass, and the results and placement are recorded in `plan.md` |
| A-02 | Write the cycle test in the test package `architecture`, starting from the violations measured in A-01; it fails on a listed entry that no longer occurs. Make it fail once for each reason: an artificial cycle, a reference into `config`, and an `ADR-006` interface breaking one of its four conditions | `ARC-005`, `ARC-006`, `AC-ARC-001` | It passes on the measured list, and each failure has been seen and recorded in the commit |
| A-03 | Place `platform`: the non-calendar classes of `integration` except `SmtpController`, `GlobalRole`, and `SecurityProperties` (`R10`). Make the `platform` mail and SMTP services take the verified actor and the recipient from their caller (`R4`) | `ARC-005`, `ARC-006` | Nothing in `platform` refers to a feature or to `config` |
| A-04 | Place `identity`: the classes of `account` other than the nine internship classes, and `SmtpController` (`R4`) | `ARC-005` | `identity` refers to no module above it |
| A-05 | Place `calendar` and `notification`: the policy and calendar classes of `attendance`, the HolidayAPI classes of `integration`, `AdminSettingsController`, and `NotificationController`. `calendar` resolves its actor through `identity` and `AttendanceRole` is removed (`R7`); `calendar` computes the business date (`R8`). Update `AttendanceLayerStructureTest` | `ARC-005`, `ARC-006`, `GOV-011` | Neither module refers to `attendance`, `project` or `reporting`; the business date comes from the same timezone as before |
| A-06 | Merge `task` into `project` | `ARC-005` | No package `task` remains |
| A-07 | Move `LayerStructureTest` and `AttendanceAndTaskWorkSeparationTest` from the test package `config` to `architecture` | `ARC-005`, `AC-ARC-001`, `GOV-004` | The test package `config` holds only tests of `config` |
| A-08 | Write the invariant test of `ACC-019` that `D28` requires: every `INTERN` account has exactly one Intern profile, no other account has one, and a creation that fails midway leaves neither row. Break the code so an `INTERN` account commits without a profile, see the test fail for that reason, restore the code, and see it pass | `ACC-019` | The failing and the passing run are recorded in the commit |
| A-09 | Place `internship` (`R3`): its nine classes, the internship methods of `AccountService` as an `internship` service, and the internship handlers of `AccountController` as an `internship` controller on the same routes, composed over the service contract of `identity` | `ARC-005`, `ACC-019` | A-08 passes unchanged, and `identity` refers to nothing in `internship` |
| A-10 | Build `R1`: `internship` declares the readiness interface and calls it from its own completion and withdrawal; `project` implements it with the predicate the code applies today; completion and withdrawal stop accepting a guard from their caller. Add the interface's name to the `ADR-006` table | `ACC-022`, `ARC-006` | The cycle test's list is empty and it checks the four conditions of the new interface |
| A-11 | Close the part: the cycle test and the structure tests pass; the full Maven suite, the end-to-end suite and `npm run test:ui` pass; GitNexus change detection reports only the expected processes. Retire the cycle check of `scripts/module-boundaries.cjs` and adjust the JS test that runs it | `AC-ARC-001` | `plan.md` records the evidence, and step 7 of `D28` can compare the code with the documents |
