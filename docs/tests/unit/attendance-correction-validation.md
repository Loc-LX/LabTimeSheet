# Test Evidence: missed-checkout correction lifecycle

- **Test type:** Unit and PostgreSQL integration
- **Requirement IDs:** `COR-001`, `COR-002`, `COR-006`, `COR-007`, `COR-008` (only the directly exercised validation, raw/effective, and deadline slices below)
- **Scenario IDs:** `N/A — focused validation and deadline/history slices do not cover the complete correction-cutoff matrix`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.service.AttendanceCorrectionApplicationServiceTest#rejectsBlankCorrectionReasonBeforeRecordLookup`; `com.lab.labtimesheet.feature.attendance.service.AttendanceCorrectionApplicationServiceTest#expirySamplesClockAfterLockedRowAcquisition`; `com.lab.labtimesheet.feature.attendance.service.AttendanceCorrectionApplicationServiceTest#historyGuardUsesOneBulkCorrectionQuery`; `com.lab.labtimesheet.feature.attendance.service.AttendancePersistenceIntegrationTest#correctionKeepsRawCheckoutNullAndLocksOnceAtSeparateDecisionDeadline`; `com.lab.labtimesheet.feature.attendance.service.AttendancePersistenceIntegrationTest#historyAccessExpiresPendingCorrectionBeforeRendering`
- **Implementation commit:** pending local green milestone

## Protected behavior

Corrections are available only after the attached raw checkout cutoff, remain submit-able through the inclusive
scheduled-end-plus-24-hour deadline, and keep the raw checkout immutable. The separately measured submitted-plus-24-hour
decision window is guarded at request time and by the idempotent worker; expiry emits one automatic rejection and one lock
event.

## Test method

The production-shaped unit test rejects whitespace before attendance lookup. The PostgreSQL 18.4 test creates a real raw
missing-checkout row, submits a same-date proposed checkout after the attached cutoff, verifies raw/effective separation,
advances the injected clock exactly to the separate decision deadline, runs the bounded expiry worker, and repeats it.

## Hand-derived expected result

The seeded policy ends at 15:30 Asia/Ho_Chi_Minh with a 30-minute checkout grace, so the raw cutoff is 09:00Z. A
submission at 09:01Z is accepted and its proposed 14:00 local checkout is retained without changing raw `check_out_at`.
At `submitted_at + 24h`, the pending request becomes `REJECTED`, is locked, and has exactly `SUBMITTED`,
`AUTO_REJECTED`, `LOCKED` events. A second sweep changes zero rows.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=AttendanceCorrectionApplicationServiceTest test
```

**Observed result**

```text
BUILD FAILURE during test compilation: cannot find symbol CorrectionRequestCommand,
AttendanceCorrectionRepository, and AttendanceCorrectionEventRepository.
The correction command, persistence boundaries, and application service did not exist yet.
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=AttendanceCorrectionApplicationServiceTest test
```

**Observed result**

```text
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=AttendancePersistenceIntegrationTest,AttendanceConcurrencyIntegrationTest test

Tests run: 21, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS (PostgreSQL 18.4 Testcontainers)
```

## External-test boundaries

The unit tests do not prove Mentor approve/reject/reopen transitions, concurrent decisions, same-day/cross-month
submission permutations, or Platform's HolidayAPI client, account-window lock DTO, or persisted notification delivery.
Those remain explicit cross-branch dependencies. PostgreSQL integration proves expiry and raw/effective persistence only
for the listed fixtures.
