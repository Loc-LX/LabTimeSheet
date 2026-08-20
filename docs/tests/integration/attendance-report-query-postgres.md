# Test Evidence: Attendance report query projection

- **Test type:** PostgreSQL 18.4 integration (Testcontainers)
- **Requirement IDs:** `RPT-002`, `RPT-004`, `ATT-013`, `ATT-014`, `ATT-015`, `ATT-016`, `ATT-017`
- **Scenario IDs:** `I2-UI-ATT-01 report precedence`, `AC-ATT-006`, `AC-ATT-007`, `AC-RPT-002`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.service.AttendancePersistenceIntegrationTest#attendanceReportUsesClassificationPrecedenceAndApprovedEffectiveCheckout`; `#attendanceReportMatchesAcAtt006DenominatorAndHistoricalPenalty`; `#attendanceReportUsesExplicitNaForZeroExpectedWorkdays`; `#attendanceReportEnforcesOwnInternScopeAndAllowsActiveMentorAndAdmin`; `#attendanceReportPreservesFourDecimalPenaltyBeforeFinalRounding`; `#attendanceReportNormalizesTargetGuessesAcrossActorScopes`
- **Implementation commit:** `pending local green milestone`

## Protected behavior

`AttendanceReportQueryService.query` is the Attendance-owned immutable, bounded read boundary for the future
Reports/UI report dataset. It accepts an authenticated `AttendanceActor`, one target Intern, and an inclusive local
date range no longer than 366 days. Interns may request only themselves; active Mentors and active Admins may request
an Intern; a Project Leader remains an Intern and receives no cross-user scope. The query reads retained local
Attendance policy, calendar, leave allocation, raw punch, and correction rows only; it does not call HolidayAPI or
import a foreign repository/entity.

Daily rows are oldest-first and use this precedence: imported day-off `HOLIDAY`, other global/configured day-off
`OFF_DAY`, frozen `APPROVED_LEAVE`, `PRESENT`, then `ABSENT` for the remaining eligible workday. The attached policy
on a punch row, or the historical policy timeline for an unpunched date, supplies the applied schedule, timezone,
graces, and penalty. Raw checkout remains separate from an approved effective correction checkout. Violations are
late plus exactly one of early departure or missing checkout; a present score is `max(0, 1 - penalty * count)` and
an absent day scores zero. Present scores retain the applied policy precision for aggregation; only aggregate
attendance and compliance percentages are rounded to two-decimal `HALF_UP`. Off-days and leave have no score and do
not enter the expected denominator; an empty denominator is represented as `N/A`.

## Test method

The first production-shaped PostgreSQL test persists an imported day-off, custom day-off, approved frozen allocation,
raw attendance with null checkout, and an approved correction. It then queries one inclusive five-day range and
asserts the complete precedence sequence, applied policy fields, raw/effective checkout distinction, late/early/missing
flags, and daily score.

The AC-ATT-006 test independently constructs 20 eligible weekdays: one approved leave day, 17 present days, and two
absent days. It spans the seeded policy and a later historical policy version with distinct UTC schedule, timezone,
graces, and penalty, then asserts `17 / 19 = 89.47%`, the compliance average including absent zeroes, and every
attached/timeline-derived applied-policy field. The AC-ATT-007 test uses a weekend-only range and asserts empty
metrics plus both display helpers returning `N/A`; it also proves a 367-day inclusive range is rejected before any
unbounded read. The authorization tests prove an Intern cannot select another Intern or distinguish existing,
non-Intern, and unavailable guessed targets, while active Mentor and Admin actors may select detailed Intern data.
The four-decimal regression proves a `0.3333` violation penalty contributes `0.6667` to compliance before the final
`66.67%` display rounding.

## Hand-derived expected result

For the precedence fixture, the five rows are `HOLIDAY`, `OFF_DAY`, `APPROVED_LEAVE`, `PRESENT`, `ABSENT`; only the
last two are expected workdays, so present is 1, expected is 2, attendance is `50.00%`, and compliance is `25.00%`
because the corrected present day scores `0.50` and the absent day scores zero. The corrected row retains a null raw
checkout while exposing the approved effective checkout and reports late plus early, not missing checkout.

For AC-ATT-006, expected workdays are `20 - 1 = 19`; attendance is `17 / 19 * 100 = 89.47%` after two-decimal
`HALF_UP` rounding. The first present day has one late and one early violation under the seeded `0.25` penalty and
scores `0.50`; the remaining 16 present days score `1.00`, and two absent days score `0`, so compliance is
`(0.50 + 16) / 19 * 100 = 86.84%`.

## Scoped review repair RED/GREEN

The independent review identified three evidence/behavior gaps. The production fix removes the intermediate
two-decimal rounding from each present-day score and rounds only final aggregate percentages; authorization resolves
and validates the actor before any target lookup, then normalizes unavailable and non-Intern targets to one denied
outcome for broad actors; and the AC-ATT-006 fixture now uses a distinct later UTC policy while asserting effective
date, timezone, start/end, both grace values, and penalty for both attached and timeline-derived rows.

**Precision and actor-order RED command**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=AttendancePersistenceIntegrationTest#attendanceReportUsesClassificationPrecedenceAndApprovedEffectiveCheckout,AttendancePersistenceIntegrationTest#attendanceReportMatchesAcAtt006DenominatorAndHistoricalPenalty,AttendancePersistenceIntegrationTest#attendanceReportUsesExplicitNaForZeroExpectedWorkdays,AttendancePersistenceIntegrationTest#attendanceReportEnforcesOwnInternScopeAndAllowsActiveMentorAndAdmin,AttendancePersistenceIntegrationTest#attendanceReportPreservesFourDecimalPenaltyBeforeFinalRounding,AttendancePersistenceIntegrationTest#attendanceReportNormalizesTargetGuessesAcrossActorScopes' test
```

**Observed result**

```text
PostgreSQL 18.4 Testcontainers; Tests run: 6, Failures: 2, Errors: 0, Skipped: 0
attendanceReportPreservesFourDecimalPenaltyBeforeFinalRounding: expected 0.6667 but observed 0.67;
attendanceReportNormalizesTargetGuessesAcrossActorScopes: an existing non-Intern target returned the target-role
denial instead of the Intern-own denial because the old path looked up the target before the actor.
```

The historical schedule assertion was replayed in an isolated copy at
`/private/tmp/attendance-report-schedule-red.wuqzLM` with the single pre-fix hunk changing the unpunched-row
resolution from `timeline.resolve(date)` to `timeline.resolve(from)`. This is not part of the worktree or Git scope.

**Historical schedule replay RED command**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=AttendancePersistenceIntegrationTest#attendanceReportMatchesAcAtt006DenominatorAndHistoricalPenalty' test
```

**Observed result**

```text
PostgreSQL 18.4 Testcontainers; Tests run: 1, Failures: 1, Errors: 0, Skipped: 0;
expected later policy id 2L for the timeline-derived date but observed seed policy id 1L (line 1501).
```

**Repair GREEN command**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=AttendancePersistenceIntegrationTest#attendanceReportUsesClassificationPrecedenceAndApprovedEffectiveCheckout,AttendancePersistenceIntegrationTest#attendanceReportMatchesAcAtt006DenominatorAndHistoricalPenalty,AttendancePersistenceIntegrationTest#attendanceReportUsesExplicitNaForZeroExpectedWorkdays,AttendancePersistenceIntegrationTest#attendanceReportEnforcesOwnInternScopeAndAllowsActiveMentorAndAdmin,AttendancePersistenceIntegrationTest#attendanceReportPreservesFourDecimalPenaltyBeforeFinalRounding,AttendancePersistenceIntegrationTest#attendanceReportNormalizesTargetGuessesAcrossActorScopes' test
```

**Observed result**

```text
PostgreSQL 18.4 Testcontainers; Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
```

The same AC-ATT-006 method passes with the current per-date timeline resolution and all distinct later-policy
assertions. `AttendancePersistenceIntegrationTest` then passed 38/38, including the six report methods.

## RED

**Command**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/opt/homebrew/opt/node@24/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=AttendancePersistenceIntegrationTest#attendanceReportUsesClassificationPrecedenceAndApprovedEffectiveCheckout,AttendancePersistenceIntegrationTest#attendanceReportMatchesAcAtt006DenominatorAndHistoricalPenalty,AttendancePersistenceIntegrationTest#attendanceReportUsesExplicitNaForZeroExpectedWorkdays,AttendancePersistenceIntegrationTest#attendanceReportEnforcesOwnInternScopeAndAllowsActiveMentorAndAdmin' test
```

**Observed result**

```text
COMPILATION ERROR
cannot find symbol: AttendanceReport, AttendanceReportClassification, AttendanceReportDay,
and AttendanceReportQueryService
```

The RED was observed against the clean pre-slice head before the report DTO/service/repository boundary existed;
the production-shaped tests could not compile because the expected API was genuinely absent.

## GREEN

**Command**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=AttendancePersistenceIntegrationTest#attendanceReportUsesClassificationPrecedenceAndApprovedEffectiveCheckout,AttendancePersistenceIntegrationTest#attendanceReportMatchesAcAtt006DenominatorAndHistoricalPenalty,AttendancePersistenceIntegrationTest#attendanceReportUsesExplicitNaForZeroExpectedWorkdays,AttendancePersistenceIntegrationTest#attendanceReportEnforcesOwnInternScopeAndAllowsActiveMentorAndAdmin' test
```

**Observed result**

```text
PostgreSQL 18.4 Testcontainers; Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
```

## Affected suite

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=Attendance*Test,CalendarImportServiceTest,LeaveApplicationServiceTest' test
PostgreSQL 18.4 where applicable; Tests run: 89, Failures: 0, Errors: 0, Skipped: 0

Repair rerun of the same affected selection after the three review fixes:

env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=Attendance*Test,CalendarImportServiceTest,LeaveApplicationServiceTest' test
PostgreSQL 18.4 where applicable; Tests run: 91, Failures: 0, Errors: 0, Skipped: 0

env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=AttendancePersistenceIntegrationTest' test
PostgreSQL 18.4; Tests run: 36, Failures: 0, Errors: 0, Skipped: 0

Repair rerun of the persistence report class: PostgreSQL 18.4; Tests run: 38, Failures: 0, Errors: 0, Skipped: 0

env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=AttendanceLayerStructureTest,LayerStructureTest,PlatformFoundationTest,ProjectPersistenceStructureTest,TaskPersistenceStructureTest,ReportingArchitectureTest' test
PostgreSQL 18.4/Flyway; Tests run: 12, Failures: 0, Errors: 0, Skipped: 0

env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw -q -DskipTests compile
Java 25 compile: exit 0

env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin ./mvnw -q -DskipTests compile dependency:build-classpath -Dmdep.outputFile=target/attendance-javadoc-classpath.txt
ATTENDANCE_CP="target/classes:$(tr -d '\n' < target/attendance-javadoc-classpath.txt)"; javadoc -quiet -Xdoclint:all -tag implNote:a -d target/attendance-javadocs -classpath "$ATTENDANCE_CP" -sourcepath src/main/java -subpackages com.lab.labtimesheet.feature.attendance
Attendance-scoped Java 25 doclint: exit 0; 27 warnings (existing repository/Lombok-generated constructor and repository-comment warnings; no doclint errors)

env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar test
PostgreSQL 18.4 where applicable; Tests run: 319, Failures: 0, Errors: 0, Skipped: 0

git diff --check
No output; pass.
```

The repository-wide Maven Javadoc diagnostic without the Attendance scope/tag registration remains blocked by seven
pre-existing unregistered `@implNote` tags in the reviewed Leave/Correction services; the scoped command above
registers that existing tag, emits no doclint errors, and includes the new report API.

## External-test boundaries

This evidence proves the Attendance-owned query/DTO boundary and PostgreSQL persistence calculations, not Reports/UI
HTML rendering, XLSX/PDF export, browser journeys, or a generic report framework. It deliberately does not claim
I3 terminal-internship or later historical lifecycle hardening; the query uses the existing AccountService
date-specific active internship eligibility boundary. It does not invoke HolidayAPI, SMTP, notification persistence,
or any foreign Account/Reports/UI repository/entity. The report projection is intentionally uncommitted and has no
new schema, migration, dependency, configuration, or Platform change.
