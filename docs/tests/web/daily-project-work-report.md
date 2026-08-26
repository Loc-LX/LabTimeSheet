# Test Evidence: Daily Project Work Report HTML

- **Test type:** Web
- **Requirement IDs:** `RPT-011`, `RPT-012`
- **Scenario IDs:** `AC-RPT-004`
- **Test class/method:** `com.lab.labtimesheet.feature.reporting.controller.DailyProjectWorkReportControllerWebTest`
- **Implementation commit:** pending local commit

## Protected behavior

The active `/reports/daily` HTML route renders an authorized Daily Project Work Report with the
selected local date context and retained Task-work facts. The page groups rows by historical log
author, preserves repeated descriptions, visibly labels the current Task status with a
`Current status:` text label and value, distinguishes selected-date minutes from lifetime actual
minutes, shows optional planning values neutrally (`N/A`/`Pending`), renders a signed DONE variance,
and marks soft-deleted Tasks. Unsupported roles and invalid future dates are rejected before a
report is rendered.

## Test method

`@WebMvcTest` exercises the public MVC seam with an immutable report DTO. The empty case verifies
date context and an informative empty state. The populated case supplies two author groups and
asserts retained description, current `IN_PROGRESS` and `DONE` status labels, forecast remaining
facts, neutral `Pending`/`N/A`, signed `+30` DONE variance, and the deleted marker. Separate tests
assert generic non-disclosing authorization and bad-request handling for a future date.

## Hand-derived expected result

The populated fixture has 90 selected-date minutes for `Mai Intern` and 30 for `Nhi Intern`, hence
120 Project/overall minutes. The DONE Task has 150 lifetime actual minutes against a 120-minute
estimate, so the neutral signed variance is `+30`; it has no forecast, so its forecast fields are
`N/A`. The unfinished Task remains `Pending` even though it has a current 120-minute remaining
forecast.

## RED

**Command**

```text
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\jbr'; & 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:/Users/dookubt/.m2/repository' '-Duser.timezone=Asia/Ho_Chi_Minh' '-Dtest=DailyProjectWorkReportControllerWebTest' test
```

**Observed result**

```text
Test-compile RED before the route implementation: DailyProjectWorkReportController was missing.
After the route existed, the focused fixture exposed that current status needed a visible text
label rather than an aria-only value; the template and test were updated to assert the rendered
`Current status:` label and status value.
```

## GREEN

**Command**

```text
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\jbr'; & 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:/Users/dookubt/.m2/repository' '-Duser.timezone=Asia/Ho_Chi_Minh' '-Dtest=DailyProjectWorkReportControllerWebTest' test
```

**Observed result**

```text
Tests run: 4, Failures: 0, Errors: 0
BUILD SUCCESS
```

## Affected suite

**Command**

```text
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\jbr'; & 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:/Users/dookubt/.m2/repository' '-Duser.timezone=Asia/Ho_Chi_Minh' '-Dtest=ProjectServiceIntegrationTest,AttendanceApplicationServiceTest,TaskCreationIntegrationTest#dailyReportReadsPostgresRetainedDeletedLogsAndLatestForecastSnapshot,TaskQueryServiceTest,DailyProjectWorkReportServiceTest,DailyProjectWorkReportControllerWebTest,ProjectTaskReportServiceTest,ProjectTaskReportControllerWebTest,AttendanceReportServiceTest,AttendanceReportControllerWebTest,ReportingArchitectureTest' test
```

**Observed result**

```text
The final affected Project/Task/Attendance/reporting gate passes 49 tests with 0 failures and 0
errors, including the four HTML tests, Attendance public-surface regression, selected Task query
coverage, producer/reporting regressions, and the PostgreSQL 18.4 Testcontainers persistence proof.
The final focused public-seam subset is 20 tests with 0 failures and 0 errors.

The report table's current-status cell contains a visible `Current status:` label and rendered
status value; the contract is not satisfied by an aria-only label.
```

## External-test boundaries

This MVC test does not prove PostgreSQL persistence, effective-dated policy lookup, authorization
repository queries, or browser JavaScript behavior. Those boundaries are covered by the producer
unit tests, PostgreSQL Testcontainers integration proof, architecture checks, and source/build
checks recorded in the coordination report and integration evidence.
