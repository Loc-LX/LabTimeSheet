# Test Evidence: Frozen leave dates and concurrent punch outcomes

- **Test type:** Integration
- **Requirement IDs:** `ATT-005`, `ATT-006`, `ATT-007`, `ATT-008`, `ATT-010`, `LEV-003`, `LEV-011`
- **Scenario IDs:** `AC-ATT-003`, `AC-ATT-004`, `AC-LEV-001`, `AC-LEV-005`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.service.AttendancePersistenceIntegrationTest#approvedLeaveBlocksOnlyItsFrozenAllocatedDates`, `com.lab.labtimesheet.feature.attendance.service.AttendanceConcurrencyIntegrationTest#concurrentDuplicatePunchesReturnStableDomainOutcomes`
- **Implementation commit:** `4c39df70e1f901e232669e9090ff5d21393519f0`

## Protected behavior

Approved leave blocks check-in only on exact immutable `leave_request_days`, not
every calendar date inside the request range. Concurrent duplicate punches return
stable attendance rejection codes while preserving a single raw check-in and checkout.

## Test method

Spring Boot migrates PostgreSQL 18.4, creates an active Intern only through public
Account and SMTP services, and persists an Attendance-owned approved leave request
plus one frozen allocation through JPA. A separate non-transactional test releases
two threads simultaneously against each transactional punch endpoint.

## Hand-derived expected result

For an approved 14–17 August range with only 17 August allocated, check-in on
14 August succeeds and 17 August returns `APPROVED_LEAVE`. Two simultaneous
check-ins produce one success and one `ALREADY_CHECKED_IN`; two simultaneous
checkouts produce one success and one `ALREADY_CHECKED_OUT`.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=AttendancePersistenceIntegrationTest#approvedLeaveBlocksOnlyItsFrozenAllocatedDates test
```

**Observed result**

```text
AttendanceException: APPROVED_LEAVE at AttendanceApplicationService.checkIn for
the unallocated 2026-08-14 range date.
Tests run: 1, Failures: 0, Errors: 1, Skipped: 0
BUILD FAILURE
Process exited 1 because the query used the whole leave request range.
```

The repository-exception unit regressions separately failed because raw
`DataIntegrityViolationException` and `ObjectOptimisticLockingFailureException`
escaped the application boundary.

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=AttendanceConcurrencyIntegrationTest test
./mvnw -Dtest=AttendancePersistenceIntegrationTest#approvedLeaveBlocksOnlyItsFrozenAllocatedDates test
```

**Observed result**

```text
AttendanceConcurrencyIntegrationTest: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
PostgreSQL reported SQLSTATE 23505 on uq_attendance_records_intern_date; the caller
received ALREADY_CHECKED_IN. The checkout race returned ALREADY_CHECKED_OUT.
AttendancePersistenceIntegrationTest focused allocation test: Tests run: 1,
Failures: 0, Errors: 0, Skipped: 0.
BUILD SUCCESS
Process exited 0.
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest='*Attendance*Test' test
Tests run: 32, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Process exited 0.
```

## External-test boundaries

The tests do not implement the later leave workflow or account terminal-state
transitions. They prove the current read/query boundary, exact PostgreSQL 18.4
allocation semantics, and duplicate-punch conflict translation.
