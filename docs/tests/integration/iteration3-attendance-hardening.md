# Test Evidence: Iteration 3 attendance hardening

- **Test type:** Integration
- **Requirement IDs:** `ATT-001`–`ATT-018`, `CAL-001`–`CAL-009`, `COR-001`–`COR-009`, `LEV-001`–`LEV-012`, `RPT-004`, `GOV-005`
- **Scenario IDs:** `AC-ATT-001`–`AC-ATT-007`, `AC-COR-001`–`AC-COR-006`, `AC-LEV-001`–`AC-LEV-006`, `AC-UI-005`
- **Test class/method:** `AttendancePersistenceIntegrationTest`, `AttendanceConcurrencyIntegrationTest`, `AttendanceDeadlineSchedulerTest`
- **Implementation commit:** `5c08db6d3dc4a2962ee83ba9d10eb62b4a09b373`

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
BUILD SUCCESS; 128 tests, 0 failures, 0 errors on Java 25 and PostgreSQL 18.4 Testcontainers.
```

## Branch suite

**Command and result**

```text
rtk env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/opt/homebrew/bin:/usr/local/bin:/usr/sbin:/usr/bin:/bin ./mvnw test
BUILD SUCCESS; 463 tests, 0 failures, 0 errors on Java 25 and PostgreSQL 18.4 Testcontainers.
```

The targeted producer gate above includes all attendance service/controller
tests, Calendar/Leave/Correction integration boundaries, reporting settings and
template checks, and the targeted Lombok surface audit.

## External-test boundaries

These checks do not replace Reports/UI browser acceptance or a production
deployment smoke test. Shared navigation links remain a Reports/UI consumer
responsibility; the producer handoff exposes focused `/admin/attendance-policies`,
`/attendance/leave`, `/attendance/corrections`, and Calendar history data.

## Review-round-2 RED/GREEN evidence

The scoped review found six regressions: queue reads did not apply request-time
expiry, the history CTA used the legacy combined route, `/admin/settings`
redirected before HolidayAPI/import replacements existed, monthly balance had no
production-shaped persisted proof, overlap/race/terminal coverage was incomplete,
and split forms discarded safe input after validation errors.

**RED command (before the repair delta)**

```text
rtk env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/opt/homebrew/bin:/usr/local/bin:/usr/sbin:/usr/bin:/bin ./mvnw -Dtest="AttendancePersistenceIntegrationTest#leaveQueueFirstAccessExpiresPendingRequestBeforeScheduler+AttendancePersistenceIntegrationTest#correctionQueueFirstAccessLocksExpiredRequestBeforeScheduler+AttendancePersistenceIntegrationTest#monthlyBalanceUsesPersistedFrozenAllocationsAcrossStatusesMonthsAndPolicyReplacement+AttendancePersistenceIntegrationTest#postgresExclusionRejectsOverlappingActiveLeaveRanges+AttendancePersistenceIntegrationTest#terminalDateWithoutAttendanceDoesNotCreateAbsenceForLeaveOrDayOff,AttendanceConcurrencyIntegrationTest#concurrentDecisionsOnOneCorrectionCommitOneTransitionAndOneFailure,AttendanceRequestControllerWebTest,AttendanceTemplateIntegrationTest,AdminSettingsControllerWebTest" test
```

Observed `BUILD FAILURE` with the intended missing behavior: expired leave
remained `PENDING`, the settings GET remained a `302`, and the history response
did not contain the split correction href. The persistence race and form tests
also failed before their production/template changes.

**Focused GREEN commands and results**

```text
./mvnw -Dtest="AttendanceRequestControllerWebTest,AttendanceTemplateIntegrationTest,AdminSettingsControllerWebTest" test
BUILD SUCCESS; 23 tests, 0 failures, 0 errors (Java 25; focused MVC/template gate).

./mvnw -Dtest="AttendancePersistenceIntegrationTest#leaveQueueFirstAccessExpiresPendingRequestBeforeScheduler" test
BUILD SUCCESS; 1 test, 0 failures, 0 errors (PostgreSQL 18.4 Testcontainers).

./mvnw -Dtest="AttendancePersistenceIntegrationTest#correctionQueueFirstAccessLocksExpiredRequestBeforeScheduler" test
BUILD SUCCESS; 1 test, 0 failures, 0 errors (PostgreSQL 18.4 Testcontainers).

./mvnw -Dtest="AttendancePersistenceIntegrationTest#monthlyBalanceUsesPersistedFrozenAllocationsAcrossStatusesMonthsAndPolicyReplacement" test
BUILD SUCCESS; 1 test, 0 failures, 0 errors (PostgreSQL 18.4 Testcontainers).

./mvnw -Dtest="AttendancePersistenceIntegrationTest#postgresExclusionRejectsOverlappingActiveLeaveRanges" test
BUILD SUCCESS; 1 test, 0 failures, 0 errors (PostgreSQL 18.4 Testcontainers).

./mvnw -Dtest="AttendancePersistenceIntegrationTest#terminalDateWithoutAttendanceDoesNotCreateAbsenceForLeaveOrDayOff" test
BUILD SUCCESS; 1 test, 0 failures, 0 errors (PostgreSQL 18.4 Testcontainers).

./mvnw -Dtest="AttendanceConcurrencyIntegrationTest#concurrentDecisionsOnOneCorrectionCommitOneTransitionAndOneFailure" test
BUILD SUCCESS; 1 test, 0 failures, 0 errors (PostgreSQL 18.4 Testcontainers).
```

The queue services now lock accounts and target rows in deterministic order,
auto-reject/lock due visible requests, and re-query the queue projection. The
combined settings route remains operational until Reports/UI supplies all
replacement workflows. Split Leave/Correction templates retain posted values
and associate inline errors with their forms.

## Review-round-3 RED/GREEN evidence

**Lifecycle revalidation RED**

```text
rtk env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/local/bin:/usr/sbin:/usr/bin:/bin ./mvnw -Dtest="AttendancePersistenceIntegrationTest#firstAccessExpiryRevalidatesLockedInternAndAdminLifecycle" test
BUILD FAILURE; Expecting code to raise a throwable at AttendancePersistenceIntegrationTest.java:554.
```

The RED was run with the new regression present and before the locked actor
snapshot checks were restored. Stale active Intern/Admin snapshots were
accepted and expired rows were mutated.

**Lifecycle and distinct-terminal GREEN**

```text
./mvnw -Dtest="AttendancePersistenceIntegrationTest#firstAccessExpiryRevalidatesLockedInternAndAdminLifecycle" test
BUILD SUCCESS; 1 test, 0 failures, 0 errors on PostgreSQL 18.4 Testcontainers.

./mvnw -Dtest="AttendancePersistenceIntegrationTest#terminalDateWithoutAttendanceDoesNotCreateAbsenceForLeaveOrDayOff" test
BUILD SUCCESS; 1 test, 0 failures, 0 errors on PostgreSQL 18.4 Testcontainers.
```

The terminal regression uses distinct eligible dates for empty-row,
approved-Leave, and global-day-off Interns, so the global calendar event cannot
mask the other two classification paths. The affected gate is 128/128 and the
exact branch gate is 463/463.
