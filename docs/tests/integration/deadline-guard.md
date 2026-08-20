# Test Evidence: Attendance deadline workers and request-time deadline guards

- **Test type:** Integration
- **Requirement IDs:** `I2-ATT-07`, `LEV-010`, `COR-008`, `ERR-003`, `ERR-004`, `ERR-005`, `NOT-002`
- **Scenario IDs:** `AC-LEV-004`, `AC-COR-005`, `AC-TST-001`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.service.DeadlineGuardIntegrationTest`
- **Implementation commit:** `169830f`

## Protected behavior

Against a real PostgreSQL 18.4 Testcontainer, `AttendanceScheduler` and the
`LeaveService`/`CorrectionService` request-time guards drive the same idempotent,
bounded `AttendanceDeadlineService` transitions against committed rows:

- A pending leave whose first counted start has passed is auto-rejected the
  moment it is accessed (`decide`), the caller still receives `BOUNDARY_PASSED`,
  and the auto-reject survives the rejected mutation in its own `REQUIRES_NEW`
  transaction (`ERR-003`). The persisted row is `REJECTED` with
  `decidedAt` set, `decidedByMentorUserId` null, an "expired" decision note, and
  the reserved quota released. The same access twice more and a late scheduler
  run are no-ops (`ERR-004`).
- The scheduled worker auto-rejects an expired pending leave exactly once across
  repeated invocations; exactly one `leaveAutoRejected` notification is fired.
- The scheduled worker auto-rejects and locks an expired pending correction with
  exactly `SUBMITTED` then `AUTO_REJECTED` events, fires exactly one
  `correctionAutoRejected` notification, and a repeated run appends nothing.
- The Mentor decisions read path closes expired corrections before rendering
  (`expireCorrections` batch), so the listing already shows `REJECTED` and locked
  without waiting for the cron trigger.
- The read path locks an already-decided (approved) correction whose window has
  passed, appending a `LOCKED` event and firing `correctionLocked`, and never
  fires `correctionAutoRejected` for it.

The port contract is exercised through a `@MockitoBean
AttendanceNotificationClient`, proving the notification is fired only on an
actual transition and the fallback (`UnconfiguredAttendanceNotificationClient`,
`@ConditionalOnMissingBean`) supplies the no-op implementation when the
platform wiring (`I2-PLAT-06`) is absent.

## Test method

`DeadlineGuardIntegrationTest` is intentionally non-transactional: every service
call commits independently so the `REQUIRES_NEW` transition is proven against
committed rows, not the caller's uncommitted persistence context. It starts its
own Testcontainers container with a mutable clock and a recording SMTP probe,
seeds admin + SMTP + mentor + intern once (lazily, since rows persist across the
class), and drives each scenario at controlled clock instants. Tests call the
public scheduler worker methods directly; the cron trigger is inert because
`SchedulingConfiguration` is `@Profile("!test")` and this class runs under the
`test` profile.

## Hand-derived expected result

For the seeded policy (Asia/Ho_Chi_Minh, scheduled end 15:30 local) and the
chosen workdays:

- Leave submitted for 2026-09-01; access at `2026-09-01T01:30:00Z` →
  `BOUNDARY_PASSED`, persisted `REJECTED`, `decidedAt =
  2026-09-01T01:30:00Z`, note containing "expired", overview reserved = 0.
- Leave submitted for 2026-09-03; scheduler run at `2026-09-03T01:30:00Z` →
  `REJECTED` with `decidedAt = 2026-09-03T01:30:00Z`; second run no-op.
- Correction submitted 2026-08-14 (deadline `2026-08-15T09:00:01Z`); scheduler
  run at `2026-08-15T09:00:01.001Z` → `REJECTED`, `decidedAt = lockedAt =
  2026-08-15T09:00:01.001Z`, events `SUBMITTED, AUTO_REJECTED`; second run leaves
  the event list at size 2.
- Correction submitted 2026-08-17; `decisions()` at `2026-08-18T09:00:01.001Z`
  → row `REJECTED` and locked before rendering.
- Correction submitted 2026-08-18, approved at `2026-08-19T08:00:00Z`;
  `decisions()` at `2026-08-19T09:00:01.001Z` → row `APPROVED` and locked, events
  `SUBMITTED, APPROVED, LOCKED`, `correctionLocked` once,
  `correctionAutoRejected` never.

## RED

**Command**

```text
.\mvnw.cmd -q test-compile "-Dtest=DeadlineGuardIntegrationTest,AttendanceDeadlineServiceTest,CorrectionWindowGuardTest" -DfailIfNoTests=false -DskipTests
```

**Observed result**

```text
[ERROR] cannot find symbol
  symbol:   class AttendanceNotificationClient
[ERROR] cannot find symbol
  symbol:   class AttendanceDeadlineService
[ERROR] cannot find symbol
  symbol:   class AttendanceScheduler
```

The required behaviour did not exist yet: the notification port, the deadline
service, and the scheduler were absent, so the tests could not even compile.

## GREEN

**Command**

```text
.\mvnw.cmd test "-Dtest=DeadlineGuardIntegrationTest,AttendanceDeadlineServiceTest,CorrectionWindowGuardTest" -DfailIfNoTests=false
```

**Observed result**

```text
DeadlineGuardIntegrationTest: Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
AttendanceDeadlineServiceTest: Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
CorrectionWindowGuardTest:    Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
Tests run: 18, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
.\mvnw.cmd test "-Dtest=LeaveWorkflowIntegrationTest,CorrectionExpiryIntegrationTest,CorrectionPersistenceIntegrationTest,LeaveServiceTest,LeaveLifecycleTest,CorrectionServiceTest,CorrectionDecisionServiceTest,MentorLeaveWebIntegrationTest,InternLeaveWebIntegrationTest,MentorCorrectionWebIntegrationTest,InternCorrectionWebIntegrationTest,LayerStructureTest" -DfailIfNoTests=false
Tests run: 80, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The existing `@Transactional` leave/correction scenarios (including
`LeaveWorkflowIntegrationTest.decisionAndCancellationAfterBoundaryAreRejected`
and `CorrectionPersistenceIntegrationTest`) remain green unchanged: in their
rollback-based tests the `REQUIRES_NEW` guard cannot see the uncommitted rows, so
the guard is a no-op there, while the committed-row behaviour is proven by
`DeadlineGuardIntegrationTest`.

Full attendance suite (service, persistence, concurrency, web, structure,
boilerplate):

```text
.\mvnw.cmd test "-Dtest=com.lab.labtimesheet.feature.attendance.**"
Tests run: 166, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## External-test boundaries

These tests prove the service/scheduler boundary and notification-once semantics
against committed rows. They do not prove the HTTP layer rendering, the actual
cron firing on a non-test profile (the trigger is deliberately disabled under
`test`), or the platform `I2-PLAT-06` notification delivery, which is wired later
through the `AttendanceNotificationClient` port. Those remain covered by the web
and unit evidence and the tracker dependency note.