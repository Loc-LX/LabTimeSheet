# Test Evidence: Attendance/compliance report formulas

- **Test type:** Unit
- **Requirement IDs:** `ATT-013`, `ATT-014`, `ATT-015`, `ATT-016`, `ATT-017`, `RPT-002`, `RPT-009`
- **Scenario IDs:** `AC-ATT-006`, `AC-ATT-007`
- **Test class/method:** `com.lab.labtimesheet.feature.reporting.service.AttendanceReportFormulasTest`
- **Implementation commit:** pending

## Protected behavior

The shared attendance/compliance report dataset must compute, from per-day classifications, the
hand-checkable totals that HTML/Excel/PDF must not drift from (RPT-001/RPT-009): attendance rate
`present / (eligible − leave)` with `N/A` when the denominator is zero; daily compliance
`max(0, 1 − penalty × applicable violation count)`; period compliance as the average daily score over
expected workdays with `N/A` when there are no expected workdays. Applicable violations count exactly
one of early departure or missing checkout plus late (ATT-016). A failure here would render a
misleading 0% instead of `N/A` (RPT-009, message family "No-data metric") or double-count early/missing.

## Test method

Pure, repository-free `AttendanceReportService` invoked directly with constructed
`AttendanceReportDay` classifications (no Spring context, no database). Cases: violation counting for
all six flag combinations; daily score floor at zero using a 1.00 penalty; a 20-day mixed period
(17 present on time, 2 absent, 1 leave) matching AC-ATT-006; empty and leave/off-day-only periods
matching AC-ATT-007; and per-day historical penalty usage via two present days carrying different
policy penalties. This is the narrowest production-shaped test because the formulas are the entire
protected behavior and have no persistence or IO.

## Hand-derived expected result

- Violation counts: (F,F,F)→0, (T,F,F)→1, (F,T,F)→1, (F,F,T)→1, (T,T,F)→2, (T,F,T)→2.
- Daily score with penalty 0.25: 0 violations→1.0000, 1→0.7500, 2→0.5000; with penalty 1.00 and 2
  violations→0.0000 (floored).
- Mixed period: eligible = 20 workdays, leave = 1, denominator = 20−1 = 19 = present 17 + absent 2;
  rate = 17/19 = 0.894736… → `0.8947` (scale 4, HALF_UP) = 89.47%. Compliance = (17×1.0000 + 2×0.0000)
  /19 = 0.8947.
- No expected workdays: both rate and compliance are empty (`N/A`).
- Historical penalty: day 1 (penalty 0.25, 1 violation) → 0.7500; day 2 (penalty 1.00, 1 violation)
  → 0.0000; average = (0.7500+0.0000)/2 = 0.3750; rate = 2/2 = 1.0000.

## RED

**Command**

```text
.\mvnw.cmd -q test -Dtest=AttendanceReportFormulasTest
```

**Observed result**

```text
[ERROR] .../AttendanceReportFormulasTest.java:[8,56] cannot find symbol
  symbol:   class AttendanceReportDay
  location: package com.lab.labtimesheet.feature.reporting.model.dto
[ERROR] .../AttendanceReportFormulasTest.java:[9,56] cannot find symbol
  symbol:   class AttendanceReportSummary
[ERROR] .../AttendanceReportFormulasTest.java:[10,56] cannot find symbol
  symbol:   class DayKind
[ERROR] .../AttendanceReportFormulasTest.java:[19,26] cannot find symbol
  symbol:   class AttendanceReportService
[ERROR] Failed to execute goal maven-compiler-plugin:3.15.0:testCompile ...
  Compilation failure
```

The test fails to compile because the shared report dataset records and formula service do not exist
yet — the expected missing production behavior.

## GREEN

**Command**

```text
.\mvnw.cmd -q test -Dtest=AttendanceReportFormulasTest
```

**Observed result**

```text
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
-- in com.lab.labtimesheet.feature.reporting.service.AttendanceReportFormulasTest
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
.\mvnw.cmd test "-Dtest=com.lab.labtimesheet.feature.reporting.**,com.lab.labtimesheet.feature.attendance.**,com.lab.labtimesheet.architecture.**"
Tests run: 85, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## External-test boundaries

This test does not prove the page authorization or the date-range classification feed (absent-day
denominator, leave, and off-day classification must be supplied by the Attendance feature). Those are
covered by the web report test and the Attendance feed contract; this file deliberately tests only the
shared formulas.