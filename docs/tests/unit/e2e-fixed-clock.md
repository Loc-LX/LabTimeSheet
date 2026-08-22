# Test Evidence: deterministic local E2E clock handoff

- **Test type:** Integration / configuration contract
- **Requirement IDs:** `ACC-019`, `ACC-020`, `I3-UI-05`
- **Scenario IDs:** `AC-ACC-010` (business-date boundary); local E2E date-sensitive journey handoff
- **Test class/method:** `com.lab.labtimesheet.config.E2eProfileIntegrationTest#e2eStartupUsesDevelopmentDatasourceAndFixedClock`, `com.lab.labtimesheet.config.TimeConfigurationTest#e2eProfileUsesTheConfiguredFixedInstant`, `#e2eProfileIsRejectedAlongsideProduction`; `src/test/js/delivery-contract.test.mjs`
- **Implementation commit:** `f1052e6ed66db19c1f0419e81eeafdc539aa3393`

## Protected behavior

The local-only `e2e` profile supplies a fixed Vietnam-zone clock for deterministic browser journeys and activates the
normal `dev` profile group, including datasource/Mailpit/origin/key configuration. It is absent from the default
profile, cannot be changed at runtime, and fails startup if combined with `prod`.

## Test method

The full Spring context test uses a PostgreSQL 18.4 Testcontainer and the real E2E clock without the test-only clock
configuration, asserting development datasource-related properties and the fixed instant. The focused Java tests load
the production `TimeConfiguration` with `prod` and `e2e` to prove rejection. The delivery contract checks the profile
group, profile guards, and documented startup command.

## Hand-derived expected result

`2026-08-22T02:00:00Z` remains the supplied instant and is interpreted in `Asia/Ho_Chi_Minh`; production activation
must throw before a clock bean is available.

## RED

**Command**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -q -Dtest=TimeConfigurationTest test
```

**Observed result**

```text
Tests run: 3, Failures: 2, Errors: 0, Skipped: 0
The e2e profile returned the live instant and prod+e2e did not reject because the profile-specific clock and guard were absent.
BUILD FAILURE

2026-08-22T20:38:56+07:00 — Full `E2eProfileIntegrationTest` startup RED: context entered only `e2e` and failed with
`lab.security.master-key is required`, proving the documented profile did not load the normal dev configuration.
```

## GREEN

**Command**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -q -Dtest=TimeConfigurationTest test
```

**Observed result**

```text
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

2026-08-22T20:41:07+07:00 — Full `E2eProfileIntegrationTest`: active profiles `e2e`, `dev`; PostgreSQL 18.4
Testcontainers context started and the fixed E2E clock plus dev datasource/Mailpit/origin properties passed `1/1`.
```

## Affected suite

**Command and result**

```text
npm run test:ui
Tests: 10 passed

env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -q test
Tests run: 482, Failures: 0, Errors: 0, Skipped: 0
PostgreSQL: 18.4; Java: 25.0.4

Affected Account/security/E2E suite: 56/56; Node delivery contracts: 10/10.
```

## External-test boundaries

This proves configuration selection and startup rejection. It does not claim a browser run, calendar override, or
production deployment; the Reports/UI owner must use the documented fixed instant for the blocked Saturday journey.
