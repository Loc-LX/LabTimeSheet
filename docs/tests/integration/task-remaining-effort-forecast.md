# Task remaining-effort forecast and reassignment evidence

## Scope

This evidence record covers I4-TSK-02's public forecast-aware reassignment behavior. A worked,
unfinished Task must carry one valid Remaining effort forecast when it is manually or batch
reassigned; an unworked Task carries no forecast. A successful forecast stores the immutable
Task, project, incoming membership, forecasting-Leader membership, assignment time, nonnegative
lifetime actual snapshot, remaining minutes, and optional normalized note. Its derived forecast
total is the actual snapshot plus remaining minutes and is not persisted as a separate value.

The same transaction validates every selected Task and forecast before writing. Consequently a
missing worked forecast, an unsolicited unworked forecast, a stale Task version, an invalid
forecast, or a mixed batch validation failure leaves assignments, forecast rows, and
notifications unchanged. The correction-specific append-only and direct-Mentor-removal evidence
is recorded in
`docs/tests/integration/task-remaining-effort-forecast-correction.md`.

## Historical RED/GREEN integrity

The following public-seam REDs were captured before the corresponding production changes:

* `TaskCreationIntegrationTest#workedBatchTransferRequiresAForecastBeforeChangingAnyTask`
  initially failed because the old batch path completed without raising a validation error.
* `ProjectControllerTest#exitTransferBindsWorkedTaskForecastInputsAtThePublicBoundary`
  initially verified the old `ProjectService` overload instead of a forecast-aware overload.
* `Iteration2ProjectWorkflowWebTest#currentLeaderSeesActionOnlyWorkflowsAndSeparateReadOnlyHistory`
  initially failed because the workflow rendered no worked-Task forecast input.
* `TaskMutationBoundaryTest#staleVersionRejectsBeforeSameRecipientValidation` initially exposed
  a same-recipient validation before the stale-version conflict.
* `TaskWorkLogIntegrationTest#concurrentForecastAwareBatchTransfersAllowOneWinnerWithoutDuplicateHistory`
  initially exposed an unstable assignment validation instead of the explicit stale conflict for
  the losing transaction.

Each RED was followed by the smallest production/test change and a current GREEN rerun. The
tests exercise `TaskService`, `TaskTransferService`, `ProjectService`, controllers, rendered
workflow markup, and PostgreSQL persistence rather than only mocking internal collaborators.

## Focused public-boundary verification

Command (Java 25.0.3 and the repository-local IntelliJ Maven 3.9.11 runtime):

```powershell
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\jbr'
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:/Users/dookubt/.m2/repository' '-Duser.timezone=Asia/Ho_Chi_Minh' '-Dtest=ProjectControllerTest,Iteration2ProjectWorkflowWebTest,TaskControllerTest,TaskMutationBoundaryTest,TaskTransferServiceTest,ProjectTaskMutationContextTest' test
```

Result: exit 0; 99 tests run, 0 failures, 0 errors, and 0 skipped. This includes the public
manual forecast binding/correction routes, exit-transfer forecast binding and safe validation
flash, workflow forecast controls, worked/unworked manual atomicity, batch validation and stale
ordering, and service-level authorization/version checks.

## PostgreSQL/Testcontainers verification

Command for the persistence and concurrency boundary:

```powershell
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\jbr'
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:/Users/dookubt/.m2/repository' '-Duser.timezone=Asia/Ho_Chi_Minh' '-Dtest=TaskWorkLogIntegrationTest,TaskCreationIntegrationTest,ProjectInvitationExitIntegrationTest' test
```

Result: exit 0; 96 tests run, 0 failures, 0 errors, and 0 skipped. Testcontainers started
PostgreSQL 18.4 for each Spring integration context and Flyway applied V1 and V2. The run
proves, against the public database boundary:

* worked and unworked manual reassignment rules and persisted forecast provenance/snapshots;
* mixed worked/unworked batch success and all-or-nothing rejection for missing, extra, invalid,
  or stale forecast/version inputs;
* append-only forecast correction successor persistence, latest-history projection, current
  actual snapshot, normalized reason, wrong-context/late-work/unauthorized/superseded rejection;
* one-winner behavior for concurrent forecast corrections and concurrent forecast-aware batch
  transfers without duplicate history;
* direct Mentor-removal rejection before leadership, membership, assignment, request/decision,
  or notification mutation when worked unfinished Tasks remain; and
* preservation of the existing eligible-unworked automatic transfer after a Leader's worked
  Tasks have first been forecast-aware transferred.

## Persistence structure verification

Command:

```powershell
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\jbr'
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:/Users/dookubt/.m2/repository' '-Duser.timezone=Asia/Ho_Chi_Minh' '-Dtest=TaskPersistenceStructureTest,ProjectPersistenceStructureTest' test
```

Result: exit 0; 6 tests run, 0 failures, 0 errors, and 0 skipped. The repository/entity
contracts and PostgreSQL migration-backed structure were loaded successfully.

## Environment and broad-suite boundary

The explicit `-Duser.timezone=Asia/Ho_Chi_Minh` is required for these integration runs because
the current test bootstrap otherwise passes the PostgreSQL server the legacy `Asia/Saigon` alias.
That is an existing test-environment issue, not a forecast rule. The proportionate full-suite
command used the same Maven and timezone settings and completed with 662 tests, 5 failures, and
21 errors. The failures were independent baseline issues: the hard-coded `LayerStructureTest`
does not allow the existing `model\\dto`/`model\\entity` package layout; several project
integration tests use start dates now in the past; and reporting/web contexts have their existing
application-context/date failures. No forecast, reassignment, correction, or direct-removal test
failed in the focused or affected runs above.

`git diff --check` and the staged diff review are the final source/evidence integrity gates for
this packet.
