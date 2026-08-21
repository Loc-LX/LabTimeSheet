# Test Evidence: leave request validation

- **Test type:** Unit
- **Requirement IDs:** `LEV-001`
- **Scenario IDs:** `N/A — focused blank-reason validation does not cover the multi-month allocation scenario`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.service.LeaveApplicationServiceTest#rejectsBlankReasonBeforeReadingOrWritingLeaveState`
- **Implementation commit:** pending local green milestone

## Protected behavior

Leave requests require a non-blank reason and reject invalid input before any account, calendar, quota, or repository
state is read or written.

## Test method

The test calls the production-shaped Intern submission boundary with a valid one-day range and whitespace-only reason.
The service must fail validation immediately.

## Hand-derived expected result

Whitespace has no business meaning; `IllegalArgumentException` is expected and no persistence operation is valid.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=LeaveApplicationServiceTest test
```

**Observed result**

```text
BUILD FAILURE during test compilation: cannot find symbol LeaveRequestCommand, LeaveRequestRepository, and LeaveRequestDayRepository.
The leave command, persistence boundaries, and application service did not exist yet.
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=LeaveApplicationServiceTest test
```

**Observed result**

```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
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

This unit test does not prove frozen workday allocation, PostgreSQL overlap/quota locking, account internship
intervals, same-day/cross-month boundaries, Mentor decisions, request-time expiry persistence, scheduler idempotence,
notifications, or web authorization. Those are covered only where explicitly listed in the PostgreSQL evidence.
