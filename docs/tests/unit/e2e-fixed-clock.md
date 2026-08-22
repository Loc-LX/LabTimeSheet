# Test Evidence: deterministic local E2E clock handoff

- **Test type:** Integration / configuration contract
- **Requirement IDs:** `ACC-019`, `ACC-020`, `I3-UI-05`
- **Scenario IDs:** `AC-ACC-010` (business-date boundary); local E2E date-sensitive journey handoff
- **Test class/method:** `com.lab.labtimesheet.config.E2eProfileIntegrationTest#e2eStartupUsesDevelopmentDatasourceAndAdvancingClock`, `com.lab.labtimesheet.config.TimeConfigurationTest#e2eProfileStartsNearTheConfiguredInstant`, `#e2eProfileClockAdvancesFromTheConfiguredStartInstant`, `#e2eProfileIsRejectedAlongsideProduction`; `src/test/js/delivery-contract.test.mjs`
- **Implementation commit:** `b8d4a77c29fe0b0349861c1daabd9cf363ee82d1`

## Protected behavior

The local-only `e2e` profile supplies an advancing Vietnam-zone clock anchored at a configured start instant for
deterministic browser journeys and activates the normal `dev` profile group, including datasource/Mailpit/origin/key
configuration. It is absent from the default profile, cannot be changed at runtime, and fails startup if combined with
`prod`.

## Test method

The full Spring context test uses a PostgreSQL 18.4 Testcontainer and the real advancing E2E clock without the
test-only clock configuration, asserting development datasource-related properties and a start instant near the
configured value. Focused Java tests prove the clock advances and load the production `TimeConfiguration` with `prod`
and `e2e` to prove rejection. The delivery contract checks the profile group, profile guards, and documented startup
command.

## Hand-derived expected result

The first instant is near `2026-08-22T02:00:00Z` in `Asia/Ho_Chi_Minh` and subsequent samples advance with elapsed
time; production activation must throw before a clock bean is available.

## RED

**Command**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -q -Dtest=TimeConfigurationTest test
```

**Observed result**

```text
Tests run: 4, Failures: 1, Errors: 0, Skipped: 0
The new advancing-clock regression observed the same instant before and after a delay because the E2E bean was still
`Clock.fixed`; this was the expected missing behavior RED.
BUILD FAILURE

2026-08-22T20:38:56+07:00 — Historical startup RED: context entered only `e2e` and failed with `lab.security.master-key`
required, proving the documented profile did not load the normal dev configuration.
```

## GREEN

**Command**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -q -Dtest=TimeConfigurationTest test
```

**Observed result**

```text
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

2026-08-22T21:03:48+07:00 — Focused advancing-clock GREEN: TimeConfigurationTest `4/4`; the clock started near the
configured instant and advanced after 20ms; prod+e2e rejection remained green.

2026-08-22T21:03:48+07:00 — Full `E2eProfileIntegrationTest`: active profiles `e2e`, `dev`; PostgreSQL 18.4
Testcontainers context started and the advancing E2E clock plus dev datasource/Mailpit/origin properties passed `1/1`.
```

## Affected suite

**Command and result**

```text
npm run test:ui
Tests: 10 passed

env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -q test
Tests run: 483, Failures: 0, Errors: 0, Skipped: 0
PostgreSQL: 18.4; Java: 25.0.4

Affected Account/security/E2E suite: 57/57; Node delivery contracts: 10/10.
```

## External-test boundaries

This proves configuration selection, elapsed-time behavior, and startup rejection. It does not claim a browser run,
calendar override, or production deployment; the Reports/UI owner must use the documented start instant for the blocked
Saturday journey.
