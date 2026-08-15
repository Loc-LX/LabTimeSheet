# Test Evidence: Attendance punch boundaries

- **Test type:** Unit
- **Requirement IDs:** `GOV-011`, `GOV-012`, `ATT-005`, `ATT-007`, `ATT-008`, `ATT-009`, `ATT-010`, `ATT-011`, `ATT-012`, `ATT-016`
- **Scenario IDs:** `AC-ATT-002`, `AC-ATT-003`, `AC-ATT-004`, `AC-ATT-005`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.service.AttendanceServiceTest`
- **Implementation commit:** `71901d1670f633a1b594bdce3348efebe73fc175`

## Protected behavior

Clock-controlled server time determines the local work date and raw punches.
Check-in rejects inactive, non-workday, day-off, leave, and duplicate attempts.
Exact grace/cutoff instants succeed; later checkout never writes raw checkout;
a missed checkout is not also an early departure.

## Test method

Plain JUnit uses a fixed `Clock`, the production domain service, and a minimal
in-memory repository port. Assertions cover stored state as well as rejection
codes, including non-overwrite behavior.

## Hand-derived expected result

Asia/Ho_Chi_Minh is UTC+07 for the tested date: 09:00 local is 02:00Z,
15:30 local is 08:30Z, and 16:00 local is 09:00Z. Equality is accepted;
adding one millisecond crosses each strict-later boundary.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=AttendanceServiceTest test
```

**Observed result**

```text
[ERROR] AttendanceServiceTest.java:[3,46] cannot find symbol
  symbol:   class AttendanceRejection
[ERROR] AttendanceServiceTest.java:[136,20] cannot find symbol
  symbol:   class AttendanceService
[INFO] 29 errors
[INFO] BUILD FAILURE
Process exited 1. The test reached compilation and failed because the required attendance domain did not exist.
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=AttendanceServiceTest test
```

**Observed result**

```text
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Process exited 0.
```

## Affected suite

**Command and result**

```text
./mvnw -Dtest='*Attendance*Test' test
Tests run: 32, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Process exited 0.
```

## External-test boundaries

This unit test does not prove transaction isolation, PostgreSQL uniqueness,
platform account/intern-state queries, approved-leave persistence, Spring
Security, controller routing, or Thymeleaf rendering.
