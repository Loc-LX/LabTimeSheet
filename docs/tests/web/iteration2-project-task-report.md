# Test Evidence: Iteration 2 Project/Task HTML report

- **Test type:** Web
- **Requirement IDs:** `I2-UI-04`, `RPT-008`, `RPT-009`
- **Scenario IDs:** `AC-RPT-004`, `AC-RPT-005`
- **Test class/method:** `com.lab.labtimesheet.feature.reporting.controller.ProjectTaskReportControllerWebTest#rendersProjectTaskReportForAnAuthenticatedMentor`
- **Implementation commit:** `6c869b65c99903d8b0ac0db19a3c10b06c920e01`

## Protected behavior

An authenticated Project reader can render the server-side Project/Task report route. Project and member options are supplied only from public authorization-aware Project DTOs; Task rows come only from the public Task service.

## Test method

The MVC slice supplies an authenticated Mentor and a mocked Reporting dataset service, then verifies the route and view contract. Producer visibility and current Task filtering remain delegated to their feature services.

## Hand-derived expected result

An unfiltered request renders the report route and leaves project/member/status/date filters available for the selected authorized scope.

## RED

**Command**

```text
./mvnw '-Dtest=ProjectTaskReportControllerWebTest' test
```

**Observed result**

```text
Compilation failed because the Iteration 2 Project/Task Reporting controller/service contract did not yet exist on the baseline.
```

## GREEN

**Command**

```text
./mvnw '-Dtest=ProjectTaskReportControllerWebTest,ProjectTaskReportServiceTest' test
```

**Observed result**

```text
`Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS` on Java 25.
```

## Affected suite

**Command and result**

```text
./mvnw '-Dtest=*Reporting*Test,*Dashboard*Test,*Template*Test,*Shell*Test,*Accessibility*Test,*UiContractWebTest,*AttendanceReport*Test,*ProjectTaskReport*Test' test

`Tests run: 60, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS` on Java 25 with PostgreSQL 18.4 Testcontainers.
```

## External-test boundaries

This slice does not prove producer Task queries, PostgreSQL visibility, browser keyboard/focus behavior, or later historical Task/transfer projections that require producer API additions.
