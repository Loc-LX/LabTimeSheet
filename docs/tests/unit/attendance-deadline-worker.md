# Test Evidence: Attendance deadline worker unit surface

- **Test type:** Unit
- **Requirement IDs:** `I2-ATT-07`, `LEV-010`, `COR-008`, `ERR-003`, `ERR-004`, `ERR-005`, `NOT-002`
- **Scenario IDs:** `AC-LEV-004`, `AC-COR-005`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.service.AttendanceDeadlineServiceTest` (new), `com.lab.labtimesheet.feature.attendance.service.CorrectionWindowGuardTest` (updated)
- **Implementation commit:** `169830f`

## Protected behavior

With pure Mockito, `AttendanceDeadlineService`:

- `expireLeave` auto-rejects a pending leave past its first counted start and
  returns `true`, firing `leaveAutoRejected` once with the transition instant.
- `expireLeave` returns `false` and fires nothing for a resolved request, a
  missing request, or a request still before its first counted start.
- `expirePendingLeaves` runs the bounded repository query and auto-rejects only
  the actually-expired subset, returning the count.
- `expirePendingLeavesForIntern` runs the per-Intern bounded query and returns
  the count of transitions applied.
- `expireCorrections` delegates to `CorrectionWindowGuard.expireNow` for each
  bounded candidate and returns the transition count; `expireCorrectionsForIntern`
  delegates the per-Intern bounded batch.
- The correction-batch paths do not double-transition an already-closed row.

`CorrectionWindowGuard` (updated) now returns `boolean` from `expire`, exposes
package-private `expireNow(AttendanceCorrectionEntity)` for the deadline worker,
and fires the `correctionAutoRejected`/`correctionLocked` notification only on an
actual transition (exactly once, `never()` otherwise), matching the shared
scheduler/request-path idempotency contract.

## Test method

`AttendanceDeadlineServiceTest` constructs the service with mocked Clock,
repositories, `CorrectionWindowGuard`, and `AttendanceNotificationClient`; leave
entities are mocked so `status()`/`firstCountedStartAt()` drive the guard, and
`saveAndFlush` is verified on the transition. `CorrectionWindowGuardTest`
mirrors the real mutable-clock fixture and asserts boolean returns plus
notification verification counts.

## Hand-derived expected result

- `expireLeave(id)` at/before boundary: pending + `now >= firstCountedStartAt`
  → `REJECTED`, `leaveAutoRejected` once, `true`.
- `expirePendingLeaves(n)`: query returns 2 candidates, one already past and one
  not → only the expired one transitioned, count 1.
- `expireCorrections(n)`: 2 candidates, guard returns `true` then `false` →
  count 1, second candidate untouched.

## RED

**Command**

```text
.\mvnw.cmd -q test-compile "-Dtest=DeadlineGuardIntegrationTest,AttendanceDeadlineServiceTest,CorrectionWindowGuardTest" -DfailIfNoTests=false -DskipTests
```

**Observed result**

```text
[ERROR] cannot find symbol
  symbol:   class AttendanceDeadlineService
[ERROR] cannot find symbol
  symbol:   class AttendanceNotificationClient
```

The service and port did not exist yet.

## GREEN

**Command**

```text
.\mvnw.cmd test "-Dtest=AttendanceDeadlineServiceTest,CorrectionWindowGuardTest" -DfailIfNoTests=false
```

**Observed result**

```text
AttendanceDeadlineServiceTest: Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
CorrectionWindowGuardTest:    Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## External-test boundaries

Pure Mockito verifies the transition decision and notification-once logic without
a database; committed-row isolation, the bounded JPQL queries, and the scheduler
integration are proven by `docs/tests/integration/deadline-guard.md`.