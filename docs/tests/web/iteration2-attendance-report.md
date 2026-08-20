# Test Evidence: Iteration 2 attendance/compliance HTML report

- **Test type:** Web
- **Requirement IDs:** `I2-UI-03`, `RPT-001`, `RPT-002`, `RPT-003`, `RPT-004`
- **Scenario IDs:** `AC-RPT-001`, `AC-RPT-002`, `AC-RPT-003`
- **Test class/method:** `com.lab.labtimesheet.feature.reporting.controller.AttendanceReportControllerWebTest#rendersAttendanceReportForAnAuthenticatedIntern`
- **Implementation commit:** `5308e913dc10248ec4482fa8c85beb3f59934d89`

## Protected behavior

An authenticated Intern can render the server-side attendance/compliance report route with inclusive date filters. The Reporting controller delegates target authorization and dataset construction to the public Attendance service boundary.

## Test method

The MVC slice supplies an authenticated Intern and a mocked Reporting dataset service, then verifies the route and view contract. The test does not mock Attendance persistence or bypass authorization because those decisions remain owned by the Attendance service.

## Hand-derived expected result

The request preserves `2026-08-01` through `2026-08-31` as inclusive local-date filters and renders `reports/attendance`.

## RED

**Command**

```text
./mvnw '-Dtest=AttendanceReportControllerWebTest' test
```

**Observed result**

```text
Compilation failed because the Iteration 2 Reporting attendance controller/service contract did not yet exist on the baseline.
```

## GREEN

**Command**

```text
./mvnw '-Dtest=AttendanceReportControllerWebTest,AttendanceReportServiceTest' test
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

This slice does not prove PostgreSQL attendance rows, producer authorization, formula calculations, browser keyboard behavior, or Chart.js runtime enhancement. Those require the affected integration and browser/E2E checks after the producer API is frozen.
