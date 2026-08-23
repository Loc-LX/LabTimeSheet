# Test Evidence: Iteration 3 Leave and Correction read models

- **Test type:** Unit
- **Requirement IDs:** `LEV-003`, `LEV-004`, `COR-001`–`COR-009`, `AUTH-003`, `UI-019`
- **Scenario IDs:** `AC-LEV-006`, `AC-COR-006`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.service.AttendanceReadModelServiceTest`
- **Implementation commit:** `5c08db6d3dc4a2962ee83ba9d10eb62b4a09b373`

## Protected behavior

Leave and correction queues expose separate actionable-first read models. Leave
balance reports reserved pending/approved days against the selected month's
policy quota and computes the non-negative remaining amount.

## Test method

The unit test supplies retained approved/pending rows in history order and
asserts pending first. It supplies two reserved days and a frozen seed policy
quota of three, then asserts a selected August balance of `2 / 3 / 1`. A
correction list likewise puts pending before approved history.

## Hand-derived expected result

Pending and approved reserve quota; rejected and cancelled do not. Therefore
`remaining = max(0, 3 - 2) = 1`.

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

The RED is the expected missing producer route/DTO, before production code was
written.

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
Same command; 22/22 tests passed. The three read-model tests prove actionable-first Leave/Correction ordering and selected-month `2 reserved / 3 quota / 1 remaining` balance derivation.
```

## External-test boundaries

The unit tests do not prove PostgreSQL exclusion/locking, frozen allocations
across policy changes, correction event persistence, authorization over HTTP, or
browser presentation. Integration and web evidence will cover those boundaries.
