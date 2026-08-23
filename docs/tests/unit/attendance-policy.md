# Test Evidence: Attendance policy defaults and boundaries

- **Test type:** Unit
- **Requirement IDs:** `ATT-001`, `ATT-002`, `ATT-003`, `ATT-004`
- **Scenario IDs:** `AC-ATT-001`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.model.AttendancePolicyTest`
- **Implementation commit:** `71901d1670f633a1b594bdce3348efebe73fc175`

## Protected behavior

The seeded policy applies from 1970-01-01 with the required timezone, schedule,
workdays, grace values, leave quota, and penalty. Grace outside 0..720, a checkout
cutoff at midnight, a monthly leave quota outside 0..31, a violation penalty
outside 0..1, or an empty workday set is rejected at construction.

## Test method

Plain JUnit constructs the immutable policy and timeline directly, resolves two
dates, and exercises the validation boundary without Spring or persistence.

## Hand-derived expected result

08:30 plus 30 minutes makes the inclusive on-time boundary 09:00. 15:30 plus
30 minutes makes the inclusive checkout boundary 16:00. A 23:30 end plus 30
minutes reaches midnight and is invalid. Quota `-1`/`32`, penalty `-0.01`/`1.01`,
and an empty workday set each fail validation.

## RED

**Command**

```text
cmd /c "mvnw.cmd -Dtest=AttendancePolicyTest test"
```

**Observed result**

```text
[ERROR] Tests run: 6, Failures: 3, Errors: 0, Skipped: 0
[INFO] BUILD FAILURE
```

The three new boundary tests (quota, penalty, empty workdays) failed against the
permissive constructor, which accepted the out-of-range values silently.

## GREEN

**Command**

```text
cmd /c "mvnw.cmd -Dtest=AttendancePolicyTest test"
```

**Observed result**

```text
AttendancePolicyTest: Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
cmd /c "mvnw.cmd -Dtest=AttendancePolicyTest,AttendancePolicyServiceTest,AttendancePolicySchedulingIntegrationTest,AttendancePolicyWebIntegrationTest test"
AttendancePolicyTest 6, AttendancePolicyServiceTest 7, AttendancePolicySchedulingIntegrationTest 4, AttendancePolicyWebIntegrationTest 6
Tests run: 23, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## External-test boundaries

This unit test does not prove the platform-owned Flyway seed, PostgreSQL policy
loading, policy-management authorization, or web rendering.
