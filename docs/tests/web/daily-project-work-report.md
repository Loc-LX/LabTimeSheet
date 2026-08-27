# Test Evidence: Daily Project Work Report HTML

- **Test type:** Web
- **Requirement IDs:** `RPT-011`, `RPT-012`
- **Scenario IDs:** `AC-RPT-004`
- **Test class/method:** `com.lab.labtimesheet.feature.reporting.controller.DailyProjectWorkReportControllerWebTest`
- **Historical implementation commit:** `b70ce241ab85917aae9395ac4ab17e29703d22dac6f0b9ef1bf75710edad4ba4`

> **Historical Q31-R scope (retained evidence):** This file preserves the I4-UI-01 HTML evidence
> and its historical results; its earlier Mentor/Admin wording is superseded. The historical Daily
> contract permitted an owning Mentor to request all owned Projects when `projectId` is omitted or
> one selected owned
> Project when it is supplied. A current Project Leader may request HTML, XLSX, or PDF only with
> one exact mandatory `projectId` for a PLANNED or ACTIVE Project that the actor currently leads,
> entered from Project detail. For that Leader scope, a missing, guessed, other-Project, or
> completed-Project ID is denied; Admin, ordinary Intern, former Leader, and other-Project Leaders
> are denied regardless of ID. The historical test counts below remain factual for the cases they
> exercised; current authorization evidence is in [`q31-r-daily-report.md`](q31-r-daily-report.md).

> **Latest reporting-role revision — 27 August 2026:** Admin has no dedicated Attendance,
> Project/Task, or Daily report scope, navigation, HTML/XLSX/PDF export, or report dataset; the
> Admin dashboard is account/configuration-only and has no Active Projects metric. Current-Leader
> Daily navigation is now conditionally discoverable for active Interns who currently lead one or
> more open Projects, with one-Project redirect, multi-Project selector, and no-eligible-Project
> `Project unavailable` behavior. The historical HTML tests above do not prove these new
> navigation or Admin Attendance/Project/Task denial requirements.

## Protected behavior

The historical `/reports/daily` HTML route test renders an authorized Daily Project Work Report
with the selected local date context and retained Task-work facts. Its historical Q31-R scope let
an owning Mentor omit `projectId` for all owned Projects or provide one owned Project; a current
Project Leader had to provide the exact currently-led PLANNED/ACTIVE `projectId`. The page groups rows by
historical log author, preserves repeated descriptions, visibly labels the current Task status
with a `Current status:` text label and value, distinguishes selected-date minutes from lifetime
actual minutes, shows optional planning values neutrally (`N/A`/`Pending`), renders a signed DONE
variance, and marks soft-deleted Tasks. Unsupported roles and invalid future dates are rejected
before a report is rendered.

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
