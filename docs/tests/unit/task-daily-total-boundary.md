# Test Evidence: Daily work total serialization and cap

- **Test type:** Unit
- **Requirement IDs:** `TSK-015`, `DB-008`
- **Scenario IDs:** `I2-TSK-02`, `AC-TSK-008`
- **Test class/method:** `com.lab.labtimesheet.feature.task.service.TaskWorkLogBoundaryTest`
- **Implementation commit:** `pending`

## Protected behavior

An Intern's combined Task work across all Projects cannot exceed 1440 minutes on one local date. Both `logWork` and `correctWorkLog` lock the Intern profile before reading the current cross-Project total and before writing; the projected total (existing total minus any replaced minutes plus requested minutes) must stay at or below 1440.

## Test method

Three focused Mockito tests in `TaskWorkLogBoundaryTest` prove the boundary. `newLogIsRejectedWhenDailyTotalAcrossAllProjectsWouldExceedFourteenForty` stubs a 1380-minute total and rejects a 61-minute log; `correctionIsRejectedWhenDailyTotalAcrossAllProjectsWouldExceedFourteenForty` stubs 1420 with a 45-minute existing log and rejects a 66-minute correction (1420 - 45 + 66 = 1441); `dailyTotalIsCheckedAfterLockingInternProfileAndBeforeWriting` verifies the strict order Project lock, Task lock, Intern-profile lock, membership-id lookup, total query, then save. The Intern-profile lock is asserted through `AccountService.lockInternProfileForDailyWork`, the cross-Project membership set through `ProjectQueryService.membershipIdsForIntern`, and the total through `TaskWorkLogRepository.sumMinutesByMembershipIdsAndWorkDate`.

## Hand-derived expected result

`projected = currentTotal - replaced + requested`. Rejection occurs exactly when `projected > 1440`; equality at 1440 is allowed. The Intern-profile lock precedes the total read and the write, so two concurrent transactions serialize instead of both reading a stale total.

## RED

**Command**

```text
.\mvnw.cmd "-Dtest=TaskWorkLogBoundaryTest" test
```

**Observed result**

```text
[ERROR] cannot find symbol
  symbol:   method lockInternProfileForDailyWork(long)
[ERROR] cannot find symbol
  symbol:   method membershipIdsForIntern(long)
[ERROR] cannot find symbol
  symbol:   method sumMinutesByMembershipIdsAndWorkDate
[INFO] BUILD FAILURE
```

The RED was the missing daily-total boundary; no fixture or environment failure.

## GREEN

**Command**

```text
.\mvnw.cmd "-Dtest=TaskWorkLogBoundaryTest" test
```

**Observed result**

```text
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
.\mvnw.cmd "-Dtest=Task*,TaskCreationIntegrationTest,TaskWorkLogIntegrationTest,TaskWorkLogDailyTotalConcurrencyTest" test

[INFO] Tests run: 81, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## External-test boundaries

These unit tests establish the serialization order and the projected-total arithmetic with mocks. They do not simulate two concurrent PostgreSQL transactions; that proof is `TaskWorkLogDailyTotalConcurrencyTest` (`AC-TSK-008`). Leave-quota serialization is the attendance feature's separate DB-008 scope.
