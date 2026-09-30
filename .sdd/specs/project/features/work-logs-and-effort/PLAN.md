# Work Logs and Effort Plan

**Owner:** Loc-LX · technical design for the [Work logs and effort SPEC](SPEC.md). Progress belongs in [`plan.md`](../../../../../plan.md); tasks are in [TASKS.md](TASKS.md). This draft inherits the module boundaries and constraints of [`project/MODULE.md`](../../MODULE.md). Where this plan and a SPEC disagree, the SPEC wins.

| Part | Subject | Rules | State |
|---|---|---|---|
| WL | Work logs and effort planning traceability | `TSK-017`, `DB-013`, `AC-TSK-008`, `AC-TSK-009`, `AC-TSK-017`, `AC-DB-003` | Drafted on 30 September 2026 against current code |

The rule `AC-DB-003` is defined in [`project/MODULE.md`](../../MODULE.md).

## Part WL — Work logs and effort planning traceability

This part establishes traceability for task work logs, daily time limits across projects, day-off independence, effort estimates, remaining effort forecasts, and database schema constraints. It identifies candidate tests and required evidence without altering production behavior or schema definitions.

### What the current code offers

The status below is based on the implementation at the draft's starting revision. It is a rule-by-rule scope inventory, not a claim that existing code or tests fully satisfy each rule.

| Rule | Current implementation and evidence | Planned outcome |
|---|---|---|
| `TSK-017` | Pointer rule for day-off effects (`CAL-009`) and separation of Task work from attendance (`GOV-004`): `TaskService.java:620-670`, `TaskWorkLog.java:60-115`. Candidate test to confirm in the task: `TaskWorkLogIntegrationTest#workLogRejectsDateOutsideInternshipAndCombinedDailyLimit`. | Confirm candidate test demonstrates work logging independence from calendar day-off status and attendance presence; cite `TSK-017` in test Javadoc. |
| `DB-013` | Task estimate (nullable integer minutes 1..527040, no backfill); append-only remaining effort forecasts with same-Project references, nonnegative lifetime-actual, linear successors, no derived total column: `V2__add_task_effort_planning.sql:10-50`, `Task.java:60-75`, `TaskRemainingEffortForecast.java:30-80`. Candidate tests to confirm in the task: `TaskWorkLogIntegrationTest#forecastCorrectionAppendsSuccessorWithCurrentHistoryAndDetailMetadata`, `TaskControllerTest#leaderCreateFormShowsEstimateButMemberCreateFormDoesNot`. | Confirm estimate storage, range constraints, append-only forecast succession, and absence of derived columns; cite `DB-013` in test Javadoc. |
| `AC-TSK-008` | Concurrent logs would total 1,441 minutes across Projects; serialization limits to 1,440, rejecting one without partial write: `TaskService.java:630-660`, `TaskWorkLog.java:60-115`. Candidate test to confirm in the task: `TaskWorkLogIntegrationTest#concurrentProjectsRejectOneOfExactly1441AttemptedMinutes`. | Confirm transactional isolation and cross-project daily minute cap of 1,440 minutes under concurrent attempts; cite `AC-TSK-008` in test Javadoc. |
| `AC-TSK-009` | Intern logs work on global day off without attendance; succeeds, no attendance record or implied presence: `TaskService.java:620-650`, `TaskWorkLog.java:60-115`. Candidate tests to confirm in the task: `TaskWorkLogIntegrationTest#workLogRejectsFutureProjectAndMembershipBoundaries`, `TaskWorkLogIntegrationTest#workLogRejectsDateOutsideInternshipAndCombinedDailyLimit`. | Confirm logging work on an authorized day off succeeds without creating attendance records or requiring check-in; cite `AC-TSK-009` in test Javadoc. |
| `AC-TSK-017` | Step-by-step verification of Task estimate, logged work, Leader remaining forecasts, Current Work, and variance across progress transitions through DONE; estimate unchanged and forecasts retained: `Task.java:117-147`, `TaskService.java:700-750`. Candidate tests to confirm in the task: `TaskWorkLogIntegrationTest#forecastCorrectionAppendsSuccessorWithCurrentHistoryAndDetailMetadata`, `TaskControllerTest#detailRendersPlanningFactsAndOnlyLeaderControl`. | Confirm lifecycle progression retains estimates and forecast history while accurately presenting variance through `DONE`; cite `AC-TSK-017` in test Javadoc. |
| `AC-DB-003` | SQL probes attempt invalid estimate bounds (0, 527041), cross-Project Task/membership forecast, negative lifetime-actual, and update of existing forecast; all rejected by constraints; nullable bounds 1..527040 accepted, forecasts insert-only, no derived total column: `V2__add_task_effort_planning.sql:15-60`. Candidate tests to confirm in the task: `TaskPersistenceStructureTest#taskPersistenceUsesJpaEntitiesAndSpringDataRepositories`, `TaskWorkLogIntegrationTest#forecastCorrectionAppendsSuccessorWithCurrentHistoryAndDetailMetadata`. | Confirm PostgreSQL schema enforces forecast immutability, estimate bounds, and foreign key integrity; cite `AC-DB-003` in test Javadoc. |

### Order of work

1. **WL-01 — Work logs and effort planning traceability.** Review candidate tests for `TSK-017`, `DB-013`, `AC-TSK-008`, `AC-TSK-009`, `AC-TSK-017`, and `AC-DB-003`. Attach rule IDs to test Javadocs where assertions demonstrate the requirement. Write targeted tests if any scenario lacks definitive proof.

### Tests

Attach rule IDs to the Javadoc of existing candidate tests when their assertions prove the rule per `TST-005`. Write new tests only when existing coverage does not fully assert the acceptance criteria. Never weaken or delete assertions. PostgreSQL-backed via Testcontainers.

### When done

- Every rule in `TSK-017`, `DB-013`, `AC-TSK-008`, `AC-TSK-009`, `AC-TSK-017`, and `AC-DB-003` is named in the Javadoc of at least one passing test that proves it.
- Focused integration tests, full `./mvnw test`, UI test suite, and disposable E2E suite pass cleanly.

### Risks

| Risk | Handling |
|---|---|
| Concurrent minute limit test flaky under high load | Verify lock granularity and retry semantics in candidate test without relaxing timeout bounds. |
| SQL probe coverage relies on schema structure test rather than direct constraint violation | Ensure database probes explicitly verify constraint failure codes before citing `AC-DB-003`. |

### Not in this part

No production code changes, no Flyway schema migrations, and no dependency additions.
