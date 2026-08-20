# Test Evidence: Calendar preview and idempotent HolidayAPI import

- **Test type:** Unit
- **Requirement IDs:** `CAL-002`–`CAL-005`, `CAL-007`
- **Scenario IDs:** `AC-CAL-001`, `AC-CAL-002`, `AC-CAL-004`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.service.CalendarImportServiceTest#providerPreviewIsAdminOnlyAndLocalReadsDoNotCallProvider`,
  `#providerFailureStaysActionableAndDoesNotBecomeImportRows`, `#previewRejectsCandidateOutsideRequestedYear`,
  `#importsExistingCandidatesInCanonicalSourceUuidLockOrder`, `#publicHolidayOnlySetsTheDefaultAndRepeatedImportDoesNotOverwrite`,
  and `#importResolvesCandidateAndProvenanceFromTrustedPreview`
- **Implementation commit:** pending local green milestone

## Protected behavior

HolidayAPI candidates are suggestions only: public status preselects the local day-off decision, while an Admin may
override it. The external preview path is Admin-only and returns Platform's actionable failure status; local calendar
reads do not call that provider. Candidate dates are constrained to the requested year, duplicate source UUIDs are
deduplicated, and repeated imports lock existing provenance rows in canonical UUID order without overwriting them.
The client import selection exposes only a source UUID and day-off override; candidate fields and `retrievedAt` come
from the trusted Platform preview.

## Test method

The tests submit non-secret Platform `HolidayApiCandidate` values to the Admin preview, verify public defaulting and
the successful preview `retrievedAt` carried to each review row, failure preservation, year validation, and local-read
provider isolation. The import-order test submits reversed source UUIDs against existing rows and verifies the
repository's pessimistic-lock calls are made in canonical order. The idempotence test imports an explicit
`dayOff=false` choice against an existing `HOLIDAY_API` source UUID. No new row is returned and the existing entity is
untouched. The trusted-preview test verifies the selection record has only `sourceUuid` and `dayOff` components, so
client candidate fields and retrieval timestamps cannot be supplied to persistence.

## Hand-derived expected result

`publicHoliday=true` produces `selectedByDefault=true`, but the explicit import choice remains false. A source UUID
already present in the local unique index must not produce a second row or update the local decision. A non-success
provider result retains its status/message for manual fallback, while interpretation yields no import candidates.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=CalendarImportServiceTest test
```

**Observed result**

```text
BUILD FAILURE during test compilation: CalendarApplicationService.preview/importSelected and CalendarImportSelection
still require the Attendance-owned HolidayCandidate type; the Platform HolidayApiCandidate cannot be passed without a
consumer boundary change (3 incompatible-type errors at CalendarImportServiceTest.java:37, :40, and :49).
```

**Additional trust-boundary RED**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/usr/bin:/bin ./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=CalendarImportServiceTest' test

BUILD FAILURE during test compilation: CalendarImportSelection still required
(HolidayApiCandidate, boolean, Instant), while the production-shaped selection boundary requires
(String sourceUuid, boolean); incompatible constructor at CalendarImportServiceTest.java:170.
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=CalendarImportServiceTest,AttendanceLombokBoilerplateTest' test
```

**Observed result**

```text
CalendarImportServiceTest: Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
AttendanceLombokBoilerplateTest: Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent/1.18.10.jar '-Dtest=com.lab.labtimesheet.feature.attendance.**' test

Tests run: 77, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS (PostgreSQL 18.4 Testcontainers)
```

## External-test boundaries

These unit tests do not prove the Platform HolidayAPI client/HTTP failure translation, PostgreSQL persistence and
unique-index behavior, or Calendar History rendering. The integration evidence covers PostgreSQL provenance,
year-validation, sequential idempotence, and the same-UUID concurrent insert race; browser rendering remains outside
this slice.
