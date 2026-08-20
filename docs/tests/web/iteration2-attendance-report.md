# Test Evidence: Iteration 2 attendance/compliance HTML report

- **Test type:** Web
- **Requirement IDs:** `I2-UI-03`, `RPT-001`, `RPT-002`, `RPT-003`, `RPT-004`
- **Scenario IDs:** `AC-ATT-006`, `AC-ATT-007`, `AC-RPT-001`, `AC-RPT-002`, `AC-UI-004`
- **Test class/method:** `AttendanceReportControllerWebTest#rendersAttendanceReportForAnAuthenticatedIntern`, `#exposesFiltersSummaryNaaAndEquivalentTrendDataInTheRenderedDataset`
- **Implementation commit:** `pending local independent review`

## Protected behavior

An authenticated actor can render the authorized Attendance-owned report with inclusive date filters, exact expected,
present, absent, attendance-rate, and compliance-rate summaries, explicit `N/A`, and an adjacent accessible trend
table carrying the same values used by optional Chart.js enhancement.

## Test method

The MVC slice supplies a producer-shaped `AttendanceReportView`, invokes the real controller and Thymeleaf template,
and verifies the date filters, summary metrics, classification rows, `N/A`, accessible chart label, and equivalent
trend data. The controller delegates scope and dataset construction to `AttendanceReportService`.

## Hand-derived expected result

The selected inclusive range remains `2026-08-01` through `2026-08-31`; the rendered dataset exposes the supplied
expected/present/absent counts and rates unchanged, and the accessible table remains present if canvas or JavaScript
is unavailable.

## RED

**Command**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw '-DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar' '-Dtest=AttendanceReportControllerWebTest,AttendanceReportServiceTest' test
```

**Observed result**

```text
Test compilation failed because the merged producer introduced the richer expected/present/absent and dual-rate
contract while the Reporting view/template still used recorded/compliant/violation totals.
```

## GREEN

**Command**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw '-DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar' '-Dtest=AttendanceReportControllerWebTest,AttendanceReportServiceTest' test
```

**Observed result**

```text
Java 25.0.4; Tests run: 5, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS.
```

## Affected suite

**Command and result**

```text
All 19 `@WebMvcTest` slices were selected explicitly with Java 25.0.4.
Tests run: 100, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS.
```

## External-test boundaries

This slice does not prove the producer's PostgreSQL formulas, target authorization, browser focus/contrast, or export
parity. PostgreSQL producer tests and the local Chart.js contract cover their own boundaries; XLSX/PDF are Iteration 3.
