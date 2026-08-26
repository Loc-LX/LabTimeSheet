# Test Evidence: Daily Project Work Report retained PostgreSQL dataset

- **Test type:** Integration
- **Requirement IDs:** `RPT-011`, `RPT-012`, `DB-013`
- **Scenario IDs:** `AC-RPT-004`
- **Test class/method:** `com.lab.labtimesheet.feature.task.service.TaskCreationIntegrationTest#dailyReportReadsPostgresRetainedDeletedLogsAndLatestForecastSnapshot`
- **Implementation commit:** pending local commit

## Protected behavior

The Daily report reads persisted retained Task work through the public Task query boundary after a
worked reassignment. Historical work remains attached to its original membership, the incoming
membership's later work is retained, a soft-deleted Task remains reportable, and the latest
applicable Remaining Effort forecast carries its actual-minutes snapshot and derived forecast total.
The selected-date log IDs drive the retained Task, lifetime aggregate, and forecast reads; a date
with no retained logs returns without broad Task, aggregate, or forecast scans. The composed report
then groups those rows by historical author and preserves the 60-minute overall total without
fabricating attendance or forecast notes.

## Test method

The Spring Boot integration test uses PostgreSQL 18.4 through Testcontainers and the existing Task
mutation seams to create a worked Task, append a 45-minute original-assignee log, perform a
forecast-aware reassignment with 90 minutes remaining, append a 15-minute incoming-assignee log,
and soft-delete the Task. It reads `TaskQueryService.dailyReport` and then the report service, so
the assertion covers the producer DTO boundary and the reporting composition without importing a
foreign repository or entity into reporting.

## Hand-derived expected result

The retained lifetime actual is `45 + 15 = 60` minutes. The forecast snapshot is `45` actual
minutes at reassignment with `90` remaining, so the planning total is `45 + 90 = 135` minutes.
The Task remains assigned to the incoming membership, is marked deleted, and its two logs retain
their original memberships. The composed Project/member tree totals exactly 60 selected-date
minutes.

## RED

**Command**

```text
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\jbr'; & 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:/Users/dookubt/.m2/repository' '-Duser.timezone=Asia/Ho_Chi_Minh' '-Dtest=TaskCreationIntegrationTest#dailyReportReadsPostgresRetainedDeletedLogsAndLatestForecastSnapshot' test
```

**Observed result**

```text
The producer/report test method was absent before the vertical slice; its first public-boundary
compile/run was therefore the RED contract for the missing Daily dataset and PostgreSQL proof.
```

## GREEN

**Command**

```text
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\jbr'; & 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:/Users/dookubt/.m2/repository' '-Duser.timezone=Asia/Ho_Chi_Minh' '-Dtest=TaskCreationIntegrationTest#dailyReportReadsPostgresRetainedDeletedLogsAndLatestForecastSnapshot' test
```

**Observed result**

```text
PostgreSQL 18.4 Testcontainers started successfully; Flyway v2 applied; Tests run: 1, Failures: 0,
Errors: 0; BUILD SUCCESS.
```

## Affected suite

**Command**

```text
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\jbr'; & 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:/Users/dookubt/.m2/repository' '-Duser.timezone=Asia/Ho_Chi_Minh' '-Dtest=ProjectServiceIntegrationTest,AttendanceApplicationServiceTest,TaskCreationIntegrationTest#dailyReportReadsPostgresRetainedDeletedLogsAndLatestForecastSnapshot,TaskQueryServiceTest,DailyProjectWorkReportServiceTest,DailyProjectWorkReportControllerWebTest,ProjectTaskReportServiceTest,ProjectTaskReportControllerWebTest,AttendanceReportServiceTest,AttendanceReportControllerWebTest,ReportingArchitectureTest' test
```

**Observed result**

```text
The final affected Project/Task/Attendance/reporting gate passes 49 tests with 0 failures and 0
errors, including the PostgreSQL 18.4 Testcontainers method above, the selected Task/forecast
producer queries, Daily service/MVC tests, Attendance public-surface regression, and existing
Project/Task/Attendance/reporting regressions. The final focused public-seam subset is 20 tests,
also green.

The focused Task query regression additionally verifies that selected-date Task IDs—not all
retained project Tasks—are used for Task, lifetime aggregate, and forecast reads, and that an
empty selected date performs no such reads.
```

## External-test boundaries

This method does not prove every authorization branch, all-project empty-state rendering, future
date rejection, or browser layout. Those are covered by the Daily service/MVC tests and final
affected/architecture/frontend gates. It also does not claim XLSX/PDF output, which is outside
I4-UI-01.
