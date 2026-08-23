# Test Evidence: Iteration 3 Attendance Policy workflow

- **Test type:** Web
- **Requirement IDs:** `ATT-003`, `UI-014`, `UI-019`
- **Scenario IDs:** `AC-ATT-001`, `AC-UI-005`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.controller.AttendancePolicyControllerWebTest`
- **Implementation commit:** `5c08db6d3dc4a2962ee83ba9d10eb62b4a09b373`

## Protected behavior

The focused Admin policy workflow must accept a native future-month value, derive
the first day server-side, render retained non-secret policy history, and reject
crafted arbitrary-date input before the policy service is called.

## Test method

MockMvc exercises the Admin GET and POST boundary with CSRF enabled. The POST
asserts that the service receives `2026-09-01` for the native `2026-09` value;
the crafted `2026-09-15` value asserts no policy interaction.

## Hand-derived expected result

`YearMonth.of(2026, 9).atDay(1)` is `2026-09-01`. A month control cannot carry a
day component; a day-bearing crafted value is invalid input and must not reach
the transaction boundary.

## RED

**Command**

```text
rtk env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin ./mvnw -Dtest='AttendancePolicyControllerWebTest,AttendanceReadModelServiceTest' test
```

**Observed result**

```text
COMPILATION ERROR: cannot find symbol class AttendancePolicyController
COMPILATION ERROR: cannot find symbol class LeaveBalance
```

The RED is the expected missing producer route/DTO, not a fixture or runtime
failure.

## GREEN

**Command**

```text
rtk env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin ./mvnw -Dtest="AttendancePolicyControllerWebTest,AttendanceReadModelServiceTest,AttendanceRequestControllerWebTest,AdminSettingsControllerWebTest" test
```

**Observed result**

```text
BUILD SUCCESS; affected web/unit run: 22 tests, 0 failures, 0 errors.
```

## Affected suite

**Command and result**

```text
Same command; 22/22 tests passed. The focused policy slice covered native month derivation, crafted date denial, Admin-only access, and legacy settings redirect; the split request slice covered Leave/Correction routes and legacy request redirect.
```

## External-test boundaries

This web slice does not prove a real PostgreSQL policy write, browser native
month-picker behavior, or cross-module Reports/UI rendering. Those require the
affected PostgreSQL/web suite and the consumer's browser gate.
