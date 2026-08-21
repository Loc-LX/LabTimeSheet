# Test Evidence: Attendance notification consumer and Account-first lock ordering

- **Test type:** PostgreSQL 18.4 integration and concurrency
- **Requirement IDs:** `NOT-002`, `NOT-004`, `COR-008`, `LEV-010`
- **Iteration deliverables:** `I2-ATT-04`, `I2-ATT-06`, `I2-ATT-07`
- **Scenario IDs:** `AC-NOT-001`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.service.AttendancePersistenceIntegrationTest#leaveNotificationsUseGlobalMentorsForSubmissionAndInternForDecisionWithoutCancellation`; `#leaveRequestTimeAutoRejectionPublishesOnceThenSchedulerIsIdempotent`; `#correctionNotificationsCoverSubmissionDecisionRevertAndRequestTimeAutoRejectionOnce`; `#recipientAccountLocksPrecedeLeaveAndCorrectionRows`; `#unauthorizedLateLeaveCancellationDoesNotExpireRequest`; `com.lab.labtimesheet.feature.attendance.service.LeaveApplicationServiceTest#expirySamplesClockAfterLockedRowAcquisition`; `com.lab.labtimesheet.feature.attendance.service.AttendanceCorrectionApplicationServiceTest#expirySamplesClockAfterLockedRowAcquisition`
- **Implementation commit:** `7101082b2ea9651e4d6b7ebc25d2d380474a8065`

## Protected behavior

Attendance publishes mandatory leave and correction transition notifications through the concrete
`NotificationService` inside the domain transaction. Submission recipients are every active global Mentor;
decision, manual rejection, revert, and auto-rejection recipients are the requesting Intern; cancellation is silent. SMTP absence
is represented as `UNAVAILABLE` without rolling back the committed domain transition, and repeated request-time
or scheduler expiry does not publish a duplicate transition. Before any leave/correction row is locked, the
service locks the union of actor/recipient Account IDs in ascending order and only then refreshes recipient
identity/email facts. This preserves the Account/profile-to-Attendance lock order and prevents a foreign-key
notification insert from deadlocking against an Account mutation.

## Test method

The PostgreSQL tests create real Intern/Mentor accounts, leave/correction rows, transitions, and notification
records with the SMTP delivery boundary unavailable. They assert global Mentor fan-out, Intern-only decision
delivery for `APPROVED`, manual `REJECTED`, `REVERTED`, and `AUTO_REJECTED`, no cancellation notification, persisted
domain state, `UNAVAILABLE` delivery state, deduplication, and request-time/scheduler idempotence. The
`recipientAccountLocksPrecedeLeaveAndCorrectionRows` test holds a recipient Account row lock in one transaction,
starts leave decision and correction expiry in separate transactions, observes both are waiting while the
Attendance rows remain pending, then releases the Account lock and asserts both transitions complete. Unit
barrier tests independently assert that recipient identity reads occur only after Account lock acquisition.

## Hand-derived expected result

For one submission with two active global Mentors, exactly two submission notifications are committed. Each
approval, manual rejection, revert, or auto-rejection commits exactly one Intern notification; repeated expiry leaves the notification count
unchanged. Cancellation commits no ordinary notification. With a held recipient Account row, the Attendance
transition cannot acquire its row lock until the Account lock is released; after release, the transaction commits
without deadlock and the persisted state is no longer pending.

## RED

**Functional notification RED command**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=AttendancePersistenceIntegrationTest#leaveNotificationsUseGlobalMentorsForSubmissionAndInternForDecisionWithoutCancellation,AttendancePersistenceIntegrationTest#leaveRequestTimeAutoRejectionPublishesOnceThenSchedulerIsIdempotent,AttendancePersistenceIntegrationTest#correctionNotificationsCoverSubmissionDecisionRevertAndRequestTimeAutoRejectionOnce' test
```

**Observed result**

```text
PostgreSQL 18.4; Tests run: 3, Failures: 3, Errors: 0, Skipped: 0.
The expected Mentor/Intern notification rows were absent because Attendance had no notification consumer.
```

**Account-before-Attendance lock-order RED command**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=LeaveApplicationServiceTest#expirySamplesClockAfterLockedRowAcquisition,AttendanceCorrectionApplicationServiceTest#expirySamplesClockAfterLockedRowAcquisition' test
```

**Observed result**

```text
Using the isolated pre-fix inverse that resolved recipient identity before the Account lock:
Tests run: 2, Failures: 2, Errors: 0, Skipped: 0.
Both tests failed the production-shaped assertion “recipient identity must be read after the Account lock”
(LeaveApplicationServiceTest.java:93 and AttendanceCorrectionApplicationServiceTest.java:107).
The inverse was removed; no unsupported PostgreSQL RED is claimed for the non-locking identity SELECT.
```

## GREEN

**Focused notification and lock-order commands**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=AttendancePersistenceIntegrationTest#recipientAccountLocksPrecedeLeaveAndCorrectionRows,AttendancePersistenceIntegrationTest#leaveRequestTimeAutoRejectionPublishesOnceThenSchedulerIsIdempotent,AttendancePersistenceIntegrationTest#correctionNotificationsCoverSubmissionDecisionRevertAndRequestTimeAutoRejectionOnce,AttendancePersistenceIntegrationTest#unauthorizedLateLeaveCancellationDoesNotExpireRequest' test
```

**Observed result**

```text
PostgreSQL 18.4; the four-method batch ran 4/4 with 0 failures, errors, or skips. The separate
`AttendancePersistenceIntegrationTest#leaveNotificationsUseGlobalMentorsForSubmissionAndInternForDecisionWithoutCancellation`
command ran 1/1 with 0 failures, errors, or skips. Together the deterministic Account-before-Attendance
lock-order regression and notification matrix are green, including persisted manual `REJECTED` assertions
for both leave and correction decisions.

The separate focused command was:

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=AttendancePersistenceIntegrationTest#leaveNotificationsUseGlobalMentorsForSubmissionAndInternForDecisionWithoutCancellation' test
```
```

**Affected and source checks**

```text
AttendancePersistenceIntegrationTest: 32/32, no failures/errors/skips.
LeaveApplicationServiceTest + AttendanceCorrectionApplicationServiceTest + AttendanceLombokBoilerplateTest: 9/9.
Affected selection `Attendance*Test,CalendarImportServiceTest,LeaveApplicationServiceTest`: 85/85, no failures/errors/skips.
Architecture/Flyway/time and cross-feature structure selection: 13/13, no failures/errors/skips.
`./mvnw -q -DskipTests compile`: exit 0.
Java 25 direct doclint with `-tag implNote:a`: exit 0; 100 pre-existing warnings, no doclint errors.
```

## Affected suite

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=Attendance*Test,CalendarImportServiceTest,LeaveApplicationServiceTest' test
```

Result: 85/85 passed with 0 failures, 0 errors, and 0 skips on PostgreSQL 18.4 where persistence tests apply.

Full Maven command and result:

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar test
```

PostgreSQL 18.4; 313/313 tests passed with 0 failures, 0 errors, and 0 skips, including the merged Platform
test suites.

## External-test boundaries

These tests do not prove real SMTP delivery, Platform NotificationService internals, browser workflows, UI
rendering, or a remote provider. Production Attendance code does not import Account or Notification
repositories/entities; the integration test reads Platform-owned notification rows solely to assert the public
persistence boundary. The tests do not claim retries, provider availability, or notification history UI beyond
the persisted boundary states asserted above. Recipient authorization is proven through the reviewed Account
lock/eligibility DTO boundary; unrelated Account lifecycle behavior remains Platform-owned.
