# Test Evidence: Iteration 2 attendance/compliance HTML report

- **Test type:** Web
- **Requirement IDs:** `I2-UI-03`, `RPT-001`, `RPT-002`, `RPT-003`, `RPT-004`
- **Scenario IDs:** `AC-ATT-006`, `AC-ATT-007`, `AC-RPT-001`, `AC-RPT-002`, `AC-UI-004`
- **Test class/method:** `AttendanceReportServiceTest#preservesDistinctRawAndEffectiveCheckoutDisplays`, `AttendanceReportControllerWebTest#rendersAttendanceReportForAnAuthenticatedIntern`, `#exposesFiltersSummaryNaaAndEquivalentTrendDataInTheRenderedDataset`
- **Implementation commit:** `5c6e0c67cd7be7bfb914e81743538e7f7d9ae47d`

## Protected behavior

An authenticated actor can render the authorized Attendance-owned report with inclusive date filters, separate raw
and effective checkout truth, exact expected/present/absent and rate summaries, explicit `N/A`, and an adjacent
accessible trend table carrying the same values used by optional Chart.js enhancement.

## Test method

The service test supplies a producer day whose persisted raw checkout and correction-effective checkout differ and
asserts each independent policy-zone display. The MVC slice then invokes the real controller and Thymeleaf template
and verifies both checkout columns, date filters, summary metrics, `N/A`, chart label, and equivalent trend data.

## Hand-derived expected result

The selected inclusive range remains `2026-08-01` through `2026-08-31`. A raw `08:00Z` and effective `09:00Z` in
Asia/Ho_Chi_Minh render independently as `15:00` and `16:00`; a missing raw value stays `N/A` even when an approved
effective value exists. The accessible table remains present if canvas or JavaScript is unavailable.

## RED

**Command**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw '-DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar' '-Dtest=AttendanceReportControllerWebTest,AttendanceReportServiceTest' test
```

**Observed result**

```text
Test compilation failed because the merged producer introduced the richer expected/present/absent and dual-rate
contract while the Reporting view/template still used recorded/compliant/violation totals.

The final-review regression then failed compilation because `AttendanceReportRow` had no `rawCheckout()` or
`effectiveCheckout()` components; Reporting collapsed both producer Instants into one checkout column.
```

## GREEN

**Command**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw '-DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar' '-Dtest=AttendanceReportControllerWebTest,AttendanceReportServiceTest' test
```

**Observed result**

```text
Java 25.0.4; Tests run: 6, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS. The rendered table contains distinct
`Raw checkout` and `Effective checkout` columns with independent `N/A` handling.
```

## Affected suite

**Command and result**

```text
The complete Java 25/PostgreSQL 18.4 candidate suite passed 441/441 in 04:45. Node UI contracts passed 7/7.
```

## External-test boundaries

This slice does not prove the producer's PostgreSQL formulas, target authorization, browser focus/contrast, or export
parity. PostgreSQL producer tests and the local Chart.js contract cover their own boundaries; XLSX/PDF are Iteration 3.
