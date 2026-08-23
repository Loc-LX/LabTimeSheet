# Test Evidence: Attendance policy defaults and boundaries

- **Test type:** Unit
- **Requirement IDs:** `ATT-001`, `ATT-002`, `ATT-003`, `ATT-004`
- **Scenario IDs:** `AC-ATT-001`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.model.AttendancePolicyTest`
- **Implementation commit:** `71901d1670f633a1b594bdce3348efebe73fc175`

## Protected behavior

The seeded policy applies from 1970-01-01 with the required timezone, schedule,
workdays, grace values, leave quota, and penalty. Grace outside 0..720 or a
checkout cutoff at midnight is rejected.

## Test method

Plain JUnit constructs the immutable policy and timeline directly, resolves two
dates, and exercises the validation boundary without Spring or persistence.

## Hand-derived expected result

08:30 plus 30 minutes makes the inclusive on-time boundary 09:00. 15:30 plus
30 minutes makes the inclusive checkout boundary 16:00. A 23:30 end plus 30
minutes reaches midnight and is invalid.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=AttendancePolicyTest test
```

**Observed result**

```text
[ERROR] AttendancePolicyTest.java:[51,20] cannot find symbol
  symbol:   class AttendancePolicy
[INFO] BUILD FAILURE
Process exited 1. The test reached compilation and failed because the required policy domain did not exist.
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=AttendancePolicyTest test
```

**Observed result**

```text
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
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

This unit test does not prove the platform-owned Flyway seed, PostgreSQL policy
loading, policy-management authorization, or web rendering.
