# Test Evidence: Calendar preview and idempotent HolidayAPI import

- **Test type:** Unit
- **Requirement IDs:** `CAL-002`–`CAL-005`, `CAL-007`
- **Scenario IDs:** `AC-CAL-001`, `AC-CAL-002`, `AC-CAL-004`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.service.CalendarImportServiceTest#publicHolidayOnlySetsTheDefaultAndRepeatedImportDoesNotOverwrite`
- **Implementation commit:** pending local green milestone

## Protected behavior

HolidayAPI candidates are suggestions only: public status preselects the local day-off decision, while an Admin may
override it. Reimporting an existing source UUID is idempotent and never overwrites the local event.

## Test method

The test submits one non-secret candidate to the Admin preview, verifies the default selection, then configures the
repository with an existing `HOLIDAY_API` source UUID and imports an explicit `dayOff=false` choice. No new row is
returned and the existing entity is untouched.

## Hand-derived expected result

`publicHoliday=true` produces `selectedByDefault=true`, but the explicit import choice remains false. A source UUID
already present in the local unique index must not produce a second row or update the local decision.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=CalendarImportServiceTest test
```

**Observed result**

```text
BUILD FAILURE during test compilation: cannot find symbol CalendarImportSelection and HolidayCandidate.
The local candidate/selection DTOs and import boundary did not exist yet.
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=CalendarImportServiceTest test
```

**Observed result**

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=AttendancePersistenceIntegrationTest test

Tests run: 19, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS (PostgreSQL 18.4 Testcontainers)
```

## External-test boundaries

This unit test does not prove the Platform HolidayAPI client, HTTP failure translation, PostgreSQL unique-index
behavior under concurrent import, Spring Admin authorization, or Calendar History rendering. Those remain explicit
cross-branch/integration/web boundaries.
