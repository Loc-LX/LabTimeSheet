# Task remaining-effort forecast evidence

## Scope

This Packet 3A1b slice exposes the immutable initial forecast created during worked-Task
manual reassignment on the authorized Task detail page. It exercises the forecast read projection,
deterministic history ordering, incoming/Leader membership provenance, assignment timestamp,
remaining minutes, lifetime-actual snapshot, note, and empty-state rendering. Forecast correction,
Project-exit batch transfer, and direct Mentor removal remain deferred to later slices.

The implementation preceded these additional projection tests, so there is no fabricated historical
RED result. Existing service boundary tests already cover the initial forecast write contract.

## Focused non-Docker verification

Command:

```powershell
$env:JAVA_HOME='C:\Users\dookubt\.jdks\loom-ea-25-loom+1-11'
& 'C:\Users\dookubt\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd' -q '-Dtest=TaskMutationBoundaryTest,TaskControllerTest,TaskDefinitionRulesTest' '-Duser.timezone=Asia/Ho_Chi_Minh' test
```

Result: exit 0. Surefire reported 22 service tests, 26 controller tests, and 3 domain tests;
0 failures, 0 errors, and 0 skipped in each class.

Test compilation also passed with `-DskipTests test-compile` under the same Java 25 toolchain.

## PostgreSQL boundary

The focused PostgreSQL/Testcontainers integration class now includes the forecast scenarios:

```powershell
$env:JAVA_HOME='C:\Users\dookubt\.jdks\loom-ea-25-loom+1-11'
& 'C:\Users\dookubt\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd' -q '-Dtest=TaskCreationIntegrationTest' '-Duser.timezone=Asia/Ho_Chi_Minh' test
```

PostgreSQL 18.4/Testcontainers connected successfully, Flyway applied V1 and V2, and the class
passed: 35 tests, 0 failures, 0 errors, 0 skipped. The added scenarios are
`workedReassignmentPersistsForecastProvenanceAndSnapshot`,
`workedReassignmentAcceptsInclusiveForecastMinuteBounds`,
`unsolicitedForecastOnUnworkedReassignmentDoesNotMutateState`, and
`multiAuthorLifetimeActualAndOriginalEstimateSurviveWorkedReassignment`. The fixture aligns
membership start with the deterministic test clock and activates the Project before retained
work is logged. The provenance scenario uses distinct incoming-member and forecasting-Leader
memberships and asserts the persisted Task assignment timestamp equals the forecast timestamp.
This is green evidence for the tested initial-forecast write/read and lifetime
variance paths; correction, batch transfer, and concurrent correction remain unproven.

## Review boundary

The detail read remains behind `TaskService.details`, which uses the existing Project/Task access
boundary. No forecast-specific endpoint or authorization path was introduced. `git diff --check`
completed without whitespace errors.
