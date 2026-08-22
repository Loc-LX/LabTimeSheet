# Test Evidence: Iteration 3 attendance hardening

- **Test type:** Integration
- **Requirement IDs:** `ATT-001`–`ATT-018`, `CAL-001`–`CAL-009`, `COR-001`–`COR-009`, `LEV-001`–`LEV-012`, `RPT-004`, `GOV-005`
- **Scenario IDs:** `AC-ATT-001`–`AC-ATT-007`, `AC-COR-001`–`AC-COR-006`, `AC-LEV-001`–`AC-LEV-006`, `AC-UI-005`
- **Test class/method:** `AttendancePersistenceIntegrationTest`, `AttendanceConcurrencyIntegrationTest`, `AttendanceDeadlineSchedulerTest`
- **Implementation commit:** pending

## Protected behavior

The attendance producer retains the existing PostgreSQL-backed rules for
historical policy snapshots and attribution, classification precedence and
denominators, exclusion/locking under concurrent leave and correction writes,
request-time and scheduler deadline equivalence, and terminal-date handling.
Rows I3-ATT-01 through I3-ATT-05 had already been implemented in the producer;
this milestone verifies those contracts while adding the terminal-date
regression for the identified gap.

## Test method

The existing persistence tests exercise server-side punches, frozen policy
details, policy replacement, leave allocation and expiry, calendar immutability,
classification precedence, AC-ATT-006 denominator and penalty precision,
explicit N/A for zero expected workdays, and actor scope using PostgreSQL 18.4
Testcontainers. The concurrency tests exercise duplicate punches, quota-row
serialization, ordered correction locks, pool-sized Mentor decisions, Intern
leave mutation bursts, and idempotent calendar imports. The scheduler unit test
asserts the same bounded batch is sent to both deadline services.

## Hand-derived expected result

Historical rows keep their own policy and attribution snapshots. HOLIDAY/OFF_DAY
precedes APPROVED_LEAVE, which precedes PRESENT and ABSENT; expected-workday
denominators exclude non-eligible/off-day/approved-leave dates. Concurrent quota
reservations cannot exceed the policy quota, and a terminal-date row remains
reportable even after future obligations close.

## RED

No new RED was required for I3-ATT-01 through I3-ATT-04: their implementation
was present at the assigned clean base and the branch makes no production change
to those contracts. The I3-ATT-05 terminal-date RED and GREEN are recorded in
`iteration3-attendance-terminal-date.md`.

## GREEN

**Command**

```text
rtk env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin ./mvnw -Dtest="AttendanceLombokBoilerplateTest,AttendanceControllerTest,AttendancePolicyControllerWebTest,AttendanceRequestControllerWebTest,CalendarAuthorizationWebIntegrationTest,CalendarControllerWebTest,CalendarDevelopmentProfileWebIntegrationTest,AttendanceApplicationServiceTest,AttendanceConcurrencyIntegrationTest,AttendanceCorrectionApplicationServiceTest,AttendanceDeadlineSchedulerTest,AttendancePersistenceIntegrationTest,AttendancePolicyApplicationServiceTest,AttendanceReadModelServiceTest,AttendanceServiceTest,CalendarImportServiceTest,LeaveApplicationServiceTest,AdminSettingsControllerWebTest,AttendanceReportServiceTest,AttendanceTemplateIntegrationTest" test
```

**Observed result**

```text
BUILD SUCCESS; 116 tests, 0 failures, 0 errors on Java 25 and PostgreSQL 18.4 Testcontainers.
```

## Affected suite

**Command and result**

```text
rtk env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/opt/homebrew/bin:/usr/local/bin:/usr/sbin:/usr/bin:/bin ./mvnw test
BUILD SUCCESS; 452 tests, 0 failures, 0 errors on Java 25 and PostgreSQL 18.4 Testcontainers.
```

The targeted producer gate above includes all attendance service/controller
tests, Calendar/Leave/Correction integration boundaries, reporting settings and
template checks, and the targeted Lombok surface audit.

## External-test boundaries

These checks do not replace Reports/UI browser acceptance or a production
deployment smoke test. Shared navigation links remain a Reports/UI consumer
responsibility; the producer handoff exposes focused `/admin/attendance-policies`,
`/attendance/leave`, `/attendance/corrections`, and Calendar history data.
