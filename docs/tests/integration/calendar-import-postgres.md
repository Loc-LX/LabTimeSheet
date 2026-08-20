# Test Evidence: PostgreSQL HolidayAPI preview and local import

- **Test type:** Integration
- **Requirement IDs:** `CAL-002`–`CAL-005`, `CAL-007`
- **Scenario IDs:** `AC-CAL-001`, `AC-CAL-002`, `AC-CAL-004`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.service.AttendancePersistenceIntegrationTest#importsPlatformCandidateWithCanonicalProvenanceAndIdempotentHistory`,
  `#rejectsPlatformCandidateOutsideRequestedYearBeforePersistence`; `com.lab.labtimesheet.feature.attendance.service.AttendanceConcurrencyIntegrationTest#concurrentSameHolidayUuidImportsAreIdempotent`,
  `#publicImportRejectsClientIdentityOutsideTrustedPreview`
- **Implementation commit:** pending local green milestone

## Protected behavior

The Attendance calendar consumes the immutable Platform `HolidayApiCandidate` directly. An Admin's selected import
supplies only a source UUID and local day-off decision; the package-private server snapshot path resolves candidate
fields and the successful Platform preview `retrievedAt` before persistence. The resulting local `HOLIDAY_API` row
retains canonical source UUID, name, actual date, observed date, public marker, retrieval provenance, and explicit
day-off choice. Repeating the same source UUID is idempotent, including concurrent first imports. A candidate whose
actual or observed date is outside the requested year is rejected before persistence.

## Test method

The PostgreSQL 18.4 Testcontainers tests stub the reviewed Platform service with a successful preview containing the
same padded candidate twice, invoke the safe public import with only the selected UUID and day-off decision, and read
the retained Admin History projection. They repeat the import and assert no second row. The boundary test submits a
prior-year candidate for the requested year and verifies validation failure and empty local history. The concurrency
test invokes the same safe public import from two PostgreSQL-backed threads and asserts one import plus one idempotent
duplicate result with exactly one retained row. The tamper test submits an identity absent from the trusted preview and
asserts no local row is created. The service uses a fresh provider preview outside a local `REQUIRES_NEW`
`TransactionTemplate` transaction, the existing unique provenance index, a pessimistically locked earliest policy
anchor for absent source rows, and ordered source-row locks; no Attendance query imports Platform entities or
repositories.

## Hand-derived expected result

The canonical UUID is `vn-september-2`; the local event date is the observed date `2026-09-03`; actual and observed
dates, name, public marker, Platform `retrievedAt` provenance instant, and `HOLIDAY_API` source are retained. Public status only defaults
the preview checkbox; the selected false decision remains `dayOff=false`. A second import returns no row, and a
2025 candidate cannot create a 2026 row. Concurrent first imports return `IMPORTED` and `DUPLICATE`, never a unique-key
rollback, and retain one row.

## RED

**Command**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=AttendancePersistenceIntegrationTest#importsPlatformCandidateWithCanonicalProvenanceAndIdempotentHistory,AttendancePersistenceIntegrationTest#rejectsPlatformCandidateOutsideRequestedYearBeforePersistence' test
```

**Observed result**

```text
Tests run: 2, Failures: 0, Errors: 1, Skipped: 0
NoSuchElementException at AttendancePersistenceIntegrationTest.java:332 while looking up the canonical source UUID.
BUILD FAILURE (PostgreSQL 18.4 Testcontainers)
```

The RED was the missing consumer normalization: preview validation returned a canonical projection, but import still
constructed persistence from the original padded selection UUID. The test reached PostgreSQL and failed only when
the required canonical History row could not be found.

**Additional provenance and concurrent-duplicate RED**

**Command**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=AttendancePersistenceIntegrationTest#importsPlatformCandidateWithCanonicalProvenanceAndIdempotentHistory,AttendanceConcurrencyIntegrationTest#concurrentSameHolidayUuidImportsAreIdempotent' test
```

**Observed result**

```text
Tests run: 2, Failures: 2, Errors: 0, Skipped: 0
AttendancePersistenceIntegrationTest: expected Platform retrievedAt 2026-08-13T12:34:56Z but was local clock 2026-08-14T00:00:00Z.
AttendanceConcurrencyIntegrationTest: outcomes [IMPORTED, FAILURE:DataIntegrityViolationException] from uq_global_calendar_events_source_uuid.
BUILD FAILURE (PostgreSQL 18.4 Testcontainers)
```

The REDs prove the missing retrieval propagation and the absent-row race independently of the earlier normalization
fix.

**Trust-boundary RED**

The production-shaped selection regression first failed during test compilation because the old selection still
accepted the full candidate and retrieval instant:

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/usr/bin:/bin ./mvnw -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=CalendarImportServiceTest' test
BUILD FAILURE: CalendarImportSelection required (HolidayApiCandidate, boolean, Instant), but the safe selection
test supplied only (String sourceUuid, boolean) at CalendarImportServiceTest.java:170.
```

## GREEN

**Command**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=AttendancePersistenceIntegrationTest#importsPlatformCandidateWithCanonicalProvenanceAndIdempotentHistory,AttendancePersistenceIntegrationTest#rejectsPlatformCandidateOutsideRequestedYearBeforePersistence' test
```

**Observed result**

```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 8.731 s
BUILD SUCCESS (PostgreSQL 18.4 Testcontainers)
```

**Additional provenance and concurrent-duplicate GREEN**

**Command**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=AttendancePersistenceIntegrationTest#importsPlatformCandidateWithCanonicalProvenanceAndIdempotentHistory,AttendanceConcurrencyIntegrationTest#concurrentSameHolidayUuidImportsAreIdempotent' test
```

**Observed result**

```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS (PostgreSQL 18.4 Testcontainers)
```

**Safe public import and tamper GREEN**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=AttendancePersistenceIntegrationTest#importsPlatformCandidateWithCanonicalProvenanceAndIdempotentHistory,AttendanceConcurrencyIntegrationTest#concurrentSameHolidayUuidImportsAreIdempotent' test
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS (PostgreSQL 18.4 Testcontainers; safe public entry, real REQUIRES_NEW transaction)

env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar '-Dtest=AttendanceConcurrencyIntegrationTest#publicImportRejectsClientIdentityOutsideTrustedPreview' test
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS (PostgreSQL 18.4 Testcontainers; altered client identity rejected and no row persisted)
```

## Affected suite

**Command and result**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/usr/bin:/bin DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -DargLine=-javaagent:/Users/sechmachine/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=HolidayApiIntegrationTest,HolidayApiHttpClientTest,CalendarImportServiceTest,AttendancePersistenceIntegrationTest,AttendanceConcurrencyIntegrationTest test

Tests run: 48, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS (PostgreSQL 18.4 Testcontainers)
```

## External-test boundaries

Platform owns encrypted secret lifecycle, fixed-country VN provider calls, HTTP failure translation, and provider
revision History; those are covered by the reviewed Platform producer and its integration tests. This consumer slice
does not claim browser Calendar UI rendering, live HolidayAPI availability, or notification delivery. Manual custom
events and local day-off reads remain local-only Attendance paths.
