# Test Evidence: Terminal Intern date classification

- **Test type:** Integration
- **Requirement IDs:** `ATT-018`, `GOV-005`, `RPT-004`
- **Scenario IDs:** `AC-ACC-010`, `AC-ATT-001`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.service.AttendancePersistenceIntegrationTest#terminalDateKeepsAttendanceRecordedBeforeInternshipCompletion`
- **Implementation commits:** `715e02b0e24125388cf449ca8f81e9d614458c47` (terminal repair); `dc1ab9ab21dcb255033bcfb0ec4c424dded03bd1` (historical-window consumer)

## Protected behavior

Completing an Intern closes future attendance obligations but does not erase an
attendance row already recorded on the terminal local date. A report must retain
that date as `PRESENT` rather than silently dropping it.

## Test method

PostgreSQL 18.4 Testcontainers creates an active Intern ending on 20/08/2026,
persists a raw attendance row on that date, completes the internship through
the Account service, then queries the Attendance-owned report for that one date.

## Hand-derived expected result

The persisted row is evidence that attendance was recorded before terminal
completion, so the report contains one `PRESENT` daily row even though the
Intern is no longer currently eligible for new obligations.

## RED

**Command**

```text
rtk env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin ./mvnw -Dtest="AttendancePersistenceIntegrationTest#terminalDateKeepsAttendanceRecordedBeforeInternshipCompletion" test
```

**Observed result**

```text
FAILURE: Expected size: 1 but was: 0 in AttendancePersistenceIntegrationTest.java:1643
```

The RED reproduced the missing terminal-date retention branch in the report
query; PostgreSQL/Flyway startup was successful.

## GREEN

**Command**

```text
rtk env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin ./mvnw -Dtest="AttendancePersistenceIntegrationTest#terminalDateKeepsAttendanceRecordedBeforeInternshipCompletion" test
```

**Observed result**

```text
BUILD SUCCESS; 1 test, 0 failures, 0 errors on PostgreSQL 18.4.
```

## Affected suite

**Command and result**

```text
rtk env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin ./mvnw -Dtest="AttendanceLombokBoilerplateTest,AttendanceControllerTest,AttendancePolicyControllerWebTest,AttendanceRequestControllerWebTest,CalendarAuthorizationWebIntegrationTest,CalendarControllerWebTest,CalendarDevelopmentProfileWebIntegrationTest,AttendanceApplicationServiceTest,AttendanceConcurrencyIntegrationTest,AttendanceCorrectionApplicationServiceTest,AttendanceDeadlineSchedulerTest,AttendancePersistenceIntegrationTest,AttendancePolicyApplicationServiceTest,AttendanceReadModelServiceTest,AttendanceServiceTest,CalendarImportServiceTest,LeaveApplicationServiceTest,AdminSettingsControllerWebTest,AttendanceReportServiceTest,AttendanceTemplateIntegrationTest,RoleDashboardWebIntegrationTest" test
BUILD SUCCESS; 128 tests, 0 failures, 0 errors on Java 25 and PostgreSQL 18.4 Testcontainers.
```

## External-test boundaries

This test does not exercise browser rendering or XLSX/PDF exports. It covers
the Account lifecycle, persisted attendance row, report classification, and
PostgreSQL/Flyway integration boundary.

## Review-round-2 terminal boundary

The companion PostgreSQL regression
`AttendancePersistenceIntegrationTest#terminalDateWithoutAttendanceDoesNotCreateAbsenceForLeaveOrDayOff`
covers the complementary terminal-date cases on three distinct eligible dates:
no attendance row, an approved leave allocation, and a global day off each
produce no newly synthesized absence after the Intern's terminal date. The
recorded-row case above remains reportable.

```text
rtk env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/opt/homebrew/bin:/usr/local/bin:/usr/sbin:/usr/bin ./mvnw -Dtest="AttendancePersistenceIntegrationTest#terminalDateWithoutAttendanceDoesNotCreateAbsenceForLeaveOrDayOff" test
BUILD SUCCESS; 1 test, 0 failures, 0 errors on PostgreSQL 18.4 Testcontainers.
```

## Post-merge historical-window boundary

The terminal tests set the mutable server clock to each actual lifecycle action
date before completion: August 20 for the empty-row Intern, August 21 for the
approved-Leave Intern, August 24 for the day-off Intern, and August 20 for the
recorded-row Intern. Reports retain pre-terminal empty eligible workdays and
the inclusive terminal date, while excluding dates after the terminal action;
the empty-row case asserts its historical denominator remains two expected
workdays.

The Attendance report consumer uses only the Account-owned
`InternReportingWindow` DTO, with no Account persistence import. Focused
post-merge affected verification passed `133/133`; the invalidated full branch
gate passed `495/495`.
