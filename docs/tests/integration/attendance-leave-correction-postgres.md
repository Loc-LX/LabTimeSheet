# Test Evidence: Attendance leave, quota, correction, and deadline persistence

- **Test type:** PostgreSQL 18.4 integration and concurrency
- **Requirement IDs:** `LEV-003`, `LEV-011`, `COR-008`, `ERR-004` (only the directly exercised deadline/history slices below)
- **Scenario IDs:** `AC-COR-005` (deadline/idempotence slice only), `AC-LEV-005` (approved cancellation boundary slice only)
- **Test class/method:** `AttendancePersistenceIntegrationTest#leaveFreezesEligibleDatesAndRequestTimeExpiryIsDatabaseSafe`; `AttendancePersistenceIntegrationTest#rehydratedPendingLeaveCanBeCancelledBeforeItsFirstCountedStart`; `AttendancePersistenceIntegrationTest#lateLeaveCancellationCommitsExpiryBeforeRejectingMutation`; `AttendancePersistenceIntegrationTest#ambientTransactionLateMutationCommitsExpiryBeforePublicException`; `AttendancePersistenceIntegrationTest#unauthorizedLateLeaveCancellationDoesNotExpireRequest`; `AttendancePersistenceIntegrationTest#approvedLeaveCancellationHonoursLev011BeforeAndAfterFirstCountedStart`; `AttendancePersistenceIntegrationTest#leaveExpiryHonoursDatabaseBatchLimit`; `AttendancePersistenceIntegrationTest#correctionKeepsRawCheckoutNullAndLocksOnceAtSeparateDecisionDeadline`; `AttendancePersistenceIntegrationTest#lateCorrectionDecisionCommitsExpiryBeforeRejectingDecision`; `AttendancePersistenceIntegrationTest#correctionExpiryHonoursDatabaseBatchLimit`; `AttendancePersistenceIntegrationTest#attendanceHistoryUsesApprovedEffectiveCheckoutWithoutChangingRawPunch`; `AttendancePersistenceIntegrationTest#historyAccessExpiresPendingCorrectionBeforeRendering`; `AttendanceConcurrencyIntegrationTest#concurrentLeaveReservationsSerializeOnThePolicyQuotaRow`; `AttendanceConcurrencyIntegrationTest#overlappingHistoryAndExpiryUseAscendingCorrectionLocksWhenAttendanceOrderDiffers`; `AttendanceConcurrencyIntegrationTest#poolSizedMentorDecisionBurstCompletesWithAuthorizationAndExpiryOnInnerConnection`; `AttendanceConcurrencyIntegrationTest#poolSizedInternLeaveMutationBurstCompletesWithoutNestedExpiryConnection`
- **Implementation commit:** pending local green milestone

## Protected behavior

Real PostgreSQL persistence enforces frozen leave-day policy/quota snapshots, overlap/exclusion and foreign keys,
separate correction deadlines, immutable raw attendance punches, and repeatable bounded expiry transitions. A pessimistic
policy-row lock serializes concurrent quota reservations before quota counting.

## Test method

The tests run the application against PostgreSQL 18.4 Testcontainers with Flyway V1 and an injected mutable Clock.
They submit a two-day future leave range, inspect persisted allocation snapshots, move to the inclusive first-counted
start and verify scheduler-safe automatic rejection without a fake actor FK. They also rehydrate a pending leave before
cancelling it, prove that a late cancellation persists the rejection before returning an error, and process a backlog
through a database-limited page. They create a missing-checkout row, submit at 09:01Z after the seeded 09:00Z cutoff,
move to the separate decision deadline, assert event history and raw checkout null, prove late Mentor decision rejection
persists expiry, process a correction backlog through a database-limited page, and read an approved effective checkout
while the raw punch remains null. First history access also locks and auto-rejects an expired pending correction through
the same bulk correction guard used to derive effective values. Approved leave cancellation is accepted before the
first counted start while retaining its frozen allocation row, and is rejected at the boundary while the approved
state/allocation remain; the same test directly observes one reserved day while approved and zero after cancellation,
without deleting the retained allocation. A two-thread test submits two two-day reservations against the three-day
monthly quota; exactly one succeeds. A PostgreSQL lock-order regression stores correction IDs opposite to
attendance-date order, then overlaps history and bounded expiry; pool-sized two-thread Mentor decision and Intern
edit/cancel bursts cover authorization/mutation paths without suspended or nested expiry connections. A proxied
ambient transaction invokes late Intern cancellation and Mentor correction decision, lets each public exception roll
back its caller, and verifies the inner expiry/status/event history remains committed.

## RED

The original production-shaped REDs are recorded in the companion unit evidence:
`docs/tests/unit/leave-request-validation.md` and `docs/tests/unit/attendance-correction-validation.md`.
For this scoped review, the following exact REDs were captured before GREEN. The clock RED used the detached replay
tree `/private/tmp/labtimesheet-attendance-replay.uZjOJe` at base `58a087b118cc955748d7df1aa47d2bbc3ca0371b` with
the minimal inverse hunk moving `clock.instant()` before the locked-row lookup:

```text
./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=LeaveApplicationServiceTest#expirySamplesClockAfterLockedRowAcquisition,AttendanceCorrectionApplicationServiceTest#expirySamplesClockAfterLockedRowAcquisition test
Tests run: 2, Failures: 2, Errors: 0, Skipped: 0
expected: 1 but was: 0 (both expirySamplesClockAfterLockedRowAcquisition tests)
BUILD FAILURE
```

The same replay tree removed owner authorization from the inner leave guard (leaving the outer mutation check),
which is the minimal pre-fix shape:

```text
./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=AttendancePersistenceIntegrationTest#unauthorizedLateLeaveCancellationDoesNotExpireRequest test
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
expected: PENDING but was: REJECTED at AttendancePersistenceIntegrationTest.java:439
BUILD FAILURE
```

Removing the `prepareHistory` expiry loop in that replay produced the first-access history RED:

```text
./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=AttendancePersistenceIntegrationTest#historyAccessExpiresPendingCorrectionBeforeRendering test
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
expected: REJECTED but was: PENDING at AttendancePersistenceIntegrationTest.java:655
BUILD FAILURE
```

Before the canonical query change and pool transaction-shape change, the live staged tests produced these direct
PostgreSQL REDs:

```text
./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=AttendanceConcurrencyIntegrationTest#overlappingHistoryAndExpiryUseAscendingCorrectionLocksWhenAttendanceOrderDiffers,AttendanceConcurrencyIntegrationTest#poolSizedMentorDecisionBurstCompletesWithAuthorizationAndExpiryOnInnerConnection test
canonical: actual [3L, 2L], expected [2L, 3L]
pool burst: [FAILURE:CannotCreateTransactionException, FAILURE:CannotCreateTransactionException]
HikariPool: Connection is not available, request timed out after 2005ms (total=2, active=2, idle=0)
Tests run: 2, Failures: 2, Errors: 0, Skipped: 0
BUILD FAILURE
```

The canonical regression was then replayed with history already ordered by correction ID, while the minimal inverse
left `findExpiredUnlocked` ordered by deadline and removed the in-memory reorder. The two expired corrections used
different deadlines (the correction with the lower ID expired later), while attendance insertion order remained
opposite to correction-ID order. This isolated the scheduler-side canonical-order defect:

```text
./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=AttendanceConcurrencyIntegrationTest#overlappingHistoryAndExpiryUseAscendingCorrectionLocksWhenAttendanceOrderDiffers test
Tests run: 1, Failures: 0, Errors: 1, Skipped: 0
org.springframework.dao.CannotAcquireLockException: PostgreSQL ERROR: deadlock detected
  ... while history locked correction IDs in ascending order and expiry retained deadline order
BUILD FAILURE
```

The matching detached replay of the old Intern edit/cancel wrappers retained their outer transactions and
`REQUIRES_NEW` expiry guards. With Hikari capped at two connections, the new pool-sized mutation burst failed both
operations before the one-transaction wrappers were implemented:

```text
./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=AttendanceConcurrencyIntegrationTest#poolSizedInternLeaveMutationBurstCompletesWithoutNestedExpiryConnection test
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
actual: [FAILURE:CannotCreateTransactionException, FAILURE:CannotCreateTransactionException]
expected: [EDITED, CANCELLED]
HikariPool: Connection is not available, request timed out after 2004ms (total=2, active=2, idle=0)
BUILD FAILURE
```

The LEV-011 test was written against an already-supported approved-cancel API. Its exact replay RED used the
minimal inverse entity hunk restricting `cancel` to `PENDING`, proving the newly added before/after boundary test:

```text
./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=AttendancePersistenceIntegrationTest#approvedLeaveCancellationHonoursLev011BeforeAndAfterFirstCountedStart test
Tests run: 1, Failures: 0, Errors: 1, Skipped: 0
Only pending or approved leave can be cancelled at AttendancePersistenceIntegrationTest.java:390
BUILD FAILURE
```

The ambient-transaction regression was then run against the live default-`REQUIRED` template before the local
`REQUIRES_NEW` template fix. The proxied caller transaction rolled back the leave expiry when the public exception
escaped; the first persisted assertion therefore observed `PENDING` instead of `REJECTED`:

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=AttendancePersistenceIntegrationTest#ambientTransactionLateMutationCommitsExpiryBeforePublicException test
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
expected: REJECTED but was: PENDING at AttendancePersistenceIntegrationTest.java:440
BUILD FAILURE (PostgreSQL 18.4)
```

## GREEN

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=AttendancePersistenceIntegrationTest test
Tests run: 21, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS (PostgreSQL 18.4)

./mvnw -Dtest=AttendancePersistenceIntegrationTest#ambientTransactionLateMutationCommitsExpiryBeforePublicException test
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS (PostgreSQL 18.4; leave expiry/status and correction expiry/lock/event history persisted after ambient rollback)

./mvnw -Dtest=AttendancePersistenceIntegrationTest#approvedLeaveCancellationHonoursLev011BeforeAndAfterFirstCountedStart test
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS (PostgreSQL 18.4; APPROVED reserved count 1, CANCELLED reserved count 0, retained allocation present)

./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=LeaveApplicationServiceTest#expirySamplesClockAfterLockedRowAcquisition,AttendanceCorrectionApplicationServiceTest#expirySamplesClockAfterLockedRowAcquisition,AttendanceCorrectionApplicationServiceTest#historyGuardUsesOneBulkCorrectionQuery,AttendanceApplicationServiceTest#historyUsesOneBulkCorrectionGuardForLoadedRows test
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=AttendancePersistenceIntegrationTest#unauthorizedLateLeaveCancellationDoesNotExpireRequest,AttendancePersistenceIntegrationTest#historyAccessExpiresPendingCorrectionBeforeRendering test
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS (PostgreSQL 18.4)

./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=AttendanceConcurrencyIntegrationTest#overlappingHistoryAndExpiryUseAscendingCorrectionLocksWhenAttendanceOrderDiffers,AttendancePersistenceIntegrationTest#approvedLeaveCancellationHonoursLev011BeforeAndAfterFirstCountedStart test
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS (PostgreSQL 18.4)

./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=AttendanceConcurrencyIntegrationTest#poolSizedMentorDecisionBurstCompletesWithAuthorizationAndExpiryOnInnerConnection test
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS (PostgreSQL 18.4)

./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=AttendanceConcurrencyIntegrationTest#poolSizedInternLeaveMutationBurstCompletesWithoutNestedExpiryConnection test
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS (PostgreSQL 18.4, Hikari max pool 2)

./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest='com.lab.labtimesheet.feature.attendance.**' test
Tests run: 63, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS (PostgreSQL 18.4 where applicable)

./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=AttendanceConcurrencyIntegrationTest test
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS (PostgreSQL 18.4)

./mvnw -q -DskipTests compile
BUILD SUCCESS (Java 25)

./mvnw -q dependency:build-classpath -Dmdep.outputFile=/private/tmp/labtimesheet-attendance-classpath
BUILD SUCCESS

javadoc -quiet -Xdoclint:all -tag implNote:a -classpath "target/classes:$(cat /private/tmp/labtimesheet-attendance-classpath)" -sourcepath src/main/java -d /private/tmp/labtimesheet-attendance-javadoc $(rg --files src/main/java/com/lab/labtimesheet/feature/attendance -g '*.java')
exit 0; 10 Lombok-generated constructor-comment warnings only
```

The architecture/Flyway/time gate also passed with PostgreSQL 18.4:

```text
./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=AttendanceLayerStructureTest,LayerStructureTest,PlatformFoundationTest,TimeConfigurationTest test
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## External-test boundaries

No schema or migration was added. These tests do not prove valid Mentor reject/reopen state-graph paths,
same-day/cross-month leave boundary permutations beyond the listed fixtures, concurrent correction decisions beyond
the history/expiry overlap, browser screens, HolidayAPI HTTP behavior, account-window locking, notification delivery,
or Platform-owned integrations. The listed requirement/scenario IDs intentionally exclude unsupported DB and workflow
mappings. The test context uses isolated disposable PostgreSQL containers and never the developer database.
