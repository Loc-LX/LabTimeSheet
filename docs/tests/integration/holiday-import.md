# Test Evidence: HolidayAPI import persistence, idempotency, and fallback

- **Test type:** Integration
- **Requirement IDs:** `CAL-001`, `CAL-002`, `CAL-003`, `CAL-004`, `CAL-005`, `CAL-006`
- **Scenario IDs:** `AC-ATT-001`
- **Test class/method:** `com.lab.labtimesheet.feature.attendance.service.HolidayImportIntegrationTest`, `com.lab.labtimesheet.feature.attendance.service.HolidayFallbackIntegrationTest`
- **Implementation commit:** `9be57ef`

## Protected behavior

Importing holiday selections persists rows against the real PostgreSQL schema with
full provenance and honors the `uq_global_calendar_events_source_uuid` unique
index: repeated import of the same source UUID is idempotent. An imported
`day_off` row suppresses attendance on its observed date. When no
`HolidayApiClient` bean is supplied by the platform, the conditional fallback
bean reports an actionable `HolidayCalendarException` while manual custom events
keep working.

## Test method

`@SpringBootTest` + `@ActiveProfiles("test")` + the shared
`IntegrationConfiguration` (Testcontainers PostgreSQL, MutableClock,
RecordingSmtpProbe). An Admin is bootstrapped, SMTP is activated, and an intern is
created/activated. `HolidayImportIntegrationTest` overrides the client with a
`@TestConfiguration @Primary HolidayApiClient` returning three 2026 candidates and
asserts provenance of every persisted column plus idempotent re-import and
attendance suppression on the observed date. `HolidayFallbackIntegrationTest`
uses the default context, so the conditional bean is the only client; it asserts
the actionable failure and that `calendar.createManual` still persists a custom
day off.

## Hand-derived expected result

National Day (`2026-09-02`, public) imported with `day_off=true` stores all
provenance columns and makes `isGlobalDayOff(2026-09-02)` true; check-in on that
date throws `AttendanceException`. Re-importing the same UUID yields
`imported = 0`, `skipped = 1`, and preview marks the row `alreadyImported`.
Lunar New Year Eve stores observed `2026-02-17` while actual remains
`2026-02-16`. With no client bean, `preview` throws a `HolidayCalendarException`
whose message contains "not configured".

## RED

**Command**

```text
cmd /c "mvnw.cmd -Dtest=HolidayImportIntegrationTest,HolidayFallbackIntegrationTest test"
```

**Observed result**

```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 (HolidayImportIntegrationTest)
Tests run: 1, Failures: 0, Errors: 1, Skipped: 0 (HolidayFallbackIntegrationTest)
java.lang.IllegalStateException: Failed to load ApplicationContext ...
Caused by: ... No qualifying bean of type HolidayApiClient available ...
BUILD FAILURE
```

The fallback test proved a real defect: `@ConditionalOnMissingBean` on the
`@Service` class was not registered when no other client existed, leaving the
controller without a `HolidayApiClient`. The fallback bean was moved to a
conditional `@Bean` factory in `HolidayApiClientConfiguration`, which Spring
evaluates reliably.

## GREEN

**Command**

```text
cmd /c "mvnw.cmd -Dtest=HolidayImportIntegrationTest,HolidayFallbackIntegrationTest test"
```

**Observed result**

```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 (HolidayImportIntegrationTest)
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 (HolidayFallbackIntegrationTest)
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
cmd /c "mvnw.cmd test"
Tests run: 243, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## External-test boundaries

These tests use the service layer directly with a test-only client bean; they do
not prove the rendered Admin page, CSRF/form binding, or the HTTP 403 role
enforcement. Those are covered by `docs/tests/web/holiday-import.md`.