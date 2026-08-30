# Test Evidence: Attendance PostgreSQL persistence and calendar rules

- **Test type:** Integration
- **Requirement IDs:** `ATT-002`, `ATT-005`, `ATT-007`, `ATT-008`, `ATT-010`, `CAL-001`, `CAL-006`, `CAL-007`, `CAL-009`, `AUTH-003`, `RPT-004`
- **Scenario IDs:** `AC-ATT-003`, `AC-ATT-004`, `AC-CAL-003`, `AC-CAL-004`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.service.AttendancePersistenceIntegrationTest`
- **Implementation commit:** `8b48e281f7e860af435ae35b16c4edeb139286dc`

> **Reporting-role correction — 29 August 2026:** Active Admin detailed-Intern Attendance scope is
> restored, matching the protected Mentor/Admin/own-history producer boundary below. The 27 August
> Admin Attendance denial was accidental; Admin Project/Task and Daily report scope remains denied.

## Protected behavior

PostgreSQL stores server-time punches with the seeded applied-policy foreign key,
enforces one row per Intern/date, and returns the attached policy in history.
Admin-only manual calendar changes affect check-in, past events are immutable,
stale edits are rejected, and Mentor/Admin/own-history scopes are enforced.

## Test method

A Spring Boot integration test migrates a real PostgreSQL 18.4 Testcontainer,
creates and activates a valid Intern exclusively through public account and SMTP
service/DTO boundaries, invokes the transactional attendance services, and
asserts persisted rows and denied state transitions.

## Hand-derived expected result

The 1970 seed has ID 1 and a 30-minute checkout grace. An event created for
2026-08-14 while server business date is 2026-08-13 blocks check-in on that
date. After business date advances to 2026-08-15, that event cannot change.
An update from version 0 advances the row, so a second version-0 edit is stale.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=AttendancePersistenceIntegrationTest test
```

**Observed result**

```text
[ERROR] cannot find symbol: class AttendanceApplicationService
[ERROR] cannot find symbol: class CalendarApplicationService
[INFO] 8 errors
[INFO] BUILD FAILURE
Process exited 1 before Testcontainers startup because the required persistence/application services did not exist.
```

The optimistic-edit assertion was separately observed RED:

```text
./mvnw -Dtest=AttendancePersistenceIntegrationTest test
[ERROR] method updateManual ... actual and formal argument lists differ in length
[INFO] 4 errors
[INFO] BUILD FAILURE
Process exited 1 because update did not yet accept an expected version.
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=AttendancePersistenceIntegrationTest,AttendanceControllerTest test
```

**Observed result**

```text
PostgreSQL 18.4 container started and Flyway applied V1.
AttendancePersistenceIntegrationTest: Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
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

This test does not prove cross-request check-in races, production authentication
configuration, shared-shell integration, HolidayAPI, leave creation/decision,
corrections, schedulers, or later policy scheduling.
