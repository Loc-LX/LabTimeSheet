# Test Evidence: Iteration 2 attendance report aggregation

- **Test type:** Unit
- **Requirement IDs:** `I2-UI-03`, `RPT-001`, `RPT-002`, `RPT-003`
- **Scenario IDs:** `AC-RPT-001`, `AC-RPT-002`, `AC-RPT-003`
- **Test class/method:** `com.lab.labtimesheet.feature.reporting.service.AttendanceReportServiceTest#computesComplianceAndExplicitNaForMissingCheckoutAndEmptyRows`
- **Implementation commit:** `5308e913dc10248ec4482fa8c85beb3f59934d89`

## Protected behavior

Reporting computes compliance totals from the Attendance-owned violation flags, preserves `N/A` when checkout or the selected period has no value, and never permits an Intern to select another Intern's detail scope.

## Test method

The unit test supplies Attendance DTOs through the public service boundary and verifies independently hand-derived counts, percentage, elapsed-time display, and violation labels. A second test verifies the own-scope authorization guard before the Attendance read is invoked.

## Hand-derived expected result

One compliant row and one late/missing-checkout row produce `1` compliant day, `1` violation day, `50.0%`, and `N/A` elapsed time for the incomplete row.

## RED

**Command**

```text
./mvnw '-Dtest=AttendanceReportControllerWebTest' test
```

**Observed result**

```text
Compilation failed because AttendanceReportController and AttendanceReportService were absent from the baseline.
```

## GREEN

**Command**

```text
./mvnw '-Dtest=AttendanceReportControllerWebTest,AttendanceReportServiceTest' test
```

**Observed result**

```text
`Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS`.
```

## Affected suite

**Command and result**

```text
./mvnw '-Dtest=*Reporting*Test,*Dashboard*Test,*Template*Test,*Shell*Test,*Accessibility*Test,*UiContractWebTest,*AttendanceReport*Test,*ProjectTaskReport*Test' test

`Tests run: 60, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS` on Java 25 with PostgreSQL 18.4 Testcontainers.
```

## External-test boundaries

The unit test does not prove PostgreSQL query authorization, browser rendering, date input parsing, or Chart.js enhancement. Those remain covered by the web slice and later integration/browser evidence.
