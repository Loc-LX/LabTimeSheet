# Test Evidence: Task estimate, lifetime effort, and variance

- **Test type:** Integration
- **Requirement IDs:** TSK-020, TSK-021
- **Scenario IDs:** AC-TSK-012
- **Test class/methods:** `TaskCreationIntegrationTest.leaderEstimateIsVisibleAndLifetimeVarianceIsDerivedFromRetainedLogs`; `estimateBoundsAndMemberForgeryAreRejectedWhileUnestimatedTaskIsNADisplay`; `leaderCanChangeAndClearEstimateBeforeWork`
- **Implementation commit:** pending

## Protected behavior

Leader-created estimates are optional whole-Task minutes, ordinary members cannot forge them, estimates can be changed or cleared before work, and the first retained log freezes the estimate. Task details derive lifetime actual effort across retained logs and expose only `VALUE` for DONE estimated Tasks, `PENDING` for unfinished estimated Tasks, and `NOT_ESTIMATED` otherwise.

## RED

No historical behavioral RED is claimed: the production skeleton preceded these tests. A safe temporary-withholding RED was not captured during this run.

## GREEN

**Command**

```powershell
$env:JAVA_HOME='C:\Users\dookubt\.jdks\loom-ea-25-loom+1-11'
& 'C:\Users\dookubt\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd' -q '-Dtest=TaskCreationIntegrationTest#leaderEstimateIsVisibleAndLifetimeVarianceIsDerivedFromRetainedLogs+estimateBoundsAndMemberForgeryAreRejectedWhileUnestimatedTaskIsNADisplay+leaderCanChangeAndClearEstimateBeforeWork' '-Duser.timezone=Asia/Ho_Chi_Minh' test
```

**Observed result**

The command reached test startup but Docker/Testcontainers was unavailable in the later run: `Could not find a valid Docker environment`. Therefore no PostgreSQL GREEN result is claimed for these three tests in this evidence record.

## External-test boundaries

**Web command and result:**

```powershell
$env:JAVA_HOME='C:\Users\dookubt\.jdks\loom-ea-25-loom+1-11'
& 'C:\Users\dookubt\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd' -q '-Dtest=TaskControllerTest' '-Duser.timezone=Asia/Ho_Chi_Minh' test
```

The existing and new controller tests pass without Docker. Java 25 test compilation also passes. A supervisor retry after Docker Desktop processes/pipes appeared still produced `Could not find a valid Docker environment`; the new integration class consequently reported 3 tests, 0 failures, 3 setup errors, Maven exit 1. This is an environment/setup failure, not behavioral RED. Forecasts, reassignment, direct removal, and reporting are intentionally outside this packet.

The AC-TSK-012 multi-author/multi-assignment lifetime-total subcase is deferred to I4-TSK-02 because worked reassignment must be tested through the mandatory forecast seam. The current one-author retained-log scenario does not prove that subcase.
