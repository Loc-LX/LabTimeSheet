# Remaining-effort forecast correction and removal-guard evidence

## Scope

I4-TSK-02 completes the forecast correction and reassignment workflow at the approved public
seams. A current Project Leader may append one successor to the latest forecast for an unfinished
current Task before the incoming member creates the first new work log. The predecessor remains
immutable. The successor retains Task/project, incoming membership, assignment timestamp, and
nonnegative lifetime actual snapshot; it records the current Leader provenance, replacement
remaining minutes, normalized mandatory reason, predecessor ID, and its own creation time. The
latest history projection marks the predecessor superseded and derives total forecast minutes
without persisting a duplicate total. Correction does not mutate the Task or publish a
notification.

The Project-exit batch path accepts forecast inputs through the controller and
`ProjectService` public boundary. It validates all selected worked/unworked Tasks and versions
before saving any forecast or assignment. Direct removal of a Mentor with any worked unfinished
Task rejects during the preflight, before leadership, membership, assignment, exit request or
decision, and notification mutation. Once a Leader's worked Tasks are transferred with valid
forecasts, the existing eligible-unworked automatic-transfer behavior remains available.

## Vertical RED/GREEN cycles

The following historical REDs were captured at the public seam before implementation and then
turned GREEN:

| Public test | RED observation | GREEN behavior |
| --- | --- | --- |
| `TaskControllerTest#forecastCorrectionBindsPredecessorAndReasonThroughPublicService` | POST returned 404 because no correction route existed. | The route parses the predecessor, remaining minutes, and reason, then calls the public correction service. |
| `ProjectControllerTest#exitTransferBindsWorkedTaskForecastInputsAtThePublicBoundary` | The controller called the old service overload and the forecast-aware verification failed. | The route parses immutable `taskId:remaining[:note]` inputs and calls the forecast-aware overload. |
| `ProjectControllerTest#exitTransferRendersTaskForecastValidationThroughTheWorkflowFlashBoundary` | Validation escaped as HTTP 400 without redirect/flash. | Safe validation is flashed and the workflow redirects; conflict remains the explicit conflict contract. |
| `TaskCreationIntegrationTest#workedBatchTransferRequiresAForecastBeforeChangingAnyTask` | A worked batch without a forecast completed instead of rejecting. | A worked Task requires one bounded forecast before any assignment/notification write. |
| `ProjectInvitationExitIntegrationTest#directMentorRemovalRejectsWorkedUnfinishedTasksBeforeAnyMutation` | Direct removal completed without the required rule violation. | Worked unfinished count is preflighted and removal rejects with no state mutation. |
| `TaskMutationBoundaryTest#staleVersionRejectsBeforeSameRecipientValidation` | A stale request could report same-recipient validation first. | Expected Task version is checked before recipient-dependent validation and persistence. |
| `TaskWorkLogIntegrationTest#concurrentForecastAwareBatchTransfersAllowOneWinnerWithoutDuplicateHistory` | The first race loser surfaced assignment validation rather than a stable stale conflict. | Locked/version-checked transactions yield one winner and one explicit conflict, with one history row. |

The correction, late-work, wrong-context, unauthorized, superseded-predecessor, and concurrent
correction cases were each added at a public service seam and verified against PostgreSQL. The
tests assert nonmutation through persisted Task, forecast, membership/leadership, exit-state,
and notification observations; they do not rely solely on mock interaction counts.

## Focused public-boundary GREEN

Command:

```powershell
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\jbr'
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:/Users/dookubt/.m2/repository' '-Duser.timezone=Asia/Ho_Chi_Minh' '-Dtest=ProjectControllerTest,Iteration2ProjectWorkflowWebTest,TaskControllerTest,TaskMutationBoundaryTest,TaskTransferServiceTest,ProjectTaskMutationContextTest' test
```

Result: exit 0; 99 tests run, 0 failures, 0 errors, and 0 skipped. Relevant class totals were
`ProjectControllerTest` 32, `Iteration2ProjectWorkflowWebTest` 4, `TaskControllerTest` 27,
`TaskMutationBoundaryTest` 25, `TaskTransferServiceTest` 7, and `ProjectTaskMutationContextTest`
4.

## PostgreSQL 18.4/Testcontainers GREEN

Command:

```powershell
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\jbr'
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:/Users/dookubt/.m2/repository' '-Duser.timezone=Asia/Ho_Chi_Minh' '-Dtest=TaskWorkLogIntegrationTest,TaskCreationIntegrationTest,ProjectInvitationExitIntegrationTest' test
```

Result: exit 0; 96 tests run, 0 failures, 0 errors, and 0 skipped. The logs identify
`postgres:18.4`; Flyway validates and applies V1 baseline plus V2 task-effort-planning for each
context. Surefire totals were `TaskWorkLogIntegrationTest` 20, `TaskCreationIntegrationTest` 40,
and `ProjectInvitationExitIntegrationTest` 36.

The database run covers the required correction and reassignment rejection matrix:

* correction successor history carries current Leader provenance, current actual snapshot,
  predecessor linkage, normalized reason, and derived detail metadata;
* superseded, wrong-project/task context, post-incoming-work, non-Leader, deleted-task,
  former-member, completed-project, and stale correction attempts append nothing;
* two concurrent corrections permit one successor and reject one stale loser;
* manual and mixed batch worked/unworked transfers are atomic across Task assignment, forecast,
  and notifications, including stale versions and invalid/missing/extra forecast inputs;
* two concurrent forecast-aware batches produce one winner without duplicate forecast history;
* direct ordinary-Mentor and current-Leader removal rejects before any leadership, membership,
  assignment, exit request/decision, or notification change when worked unfinished Tasks remain;
  and
* after forecast-aware Leader transfer, only eligible unworked unfinished Tasks use the existing
  automatic transfer behavior.

## Known independent broad-suite boundary

The same Java/Maven command run without `-Dtest` completed 662 tests with 5 failures and 21
errors. These are existing unrelated baselines: `LayerStructureTest` has a hard-coded package
allowlist that predates the current `model\\dto`/`model\\entity` layout; project lifecycle/query
tests use fixtures whose start dates are now in the past; and reporting/web contexts contain the
existing application-context/date failures. No I4-TSK-02 focused or affected test failed.

The integration command explicitly sets `-Duser.timezone=Asia/Ho_Chi_Minh`; without it, the
current test bootstrap passes PostgreSQL the legacy `Asia/Saigon` alias and PostgreSQL 18.4
rejects that alias. This is an environment/test-bootstrap boundary rather than a feature result.
