# Test Evidence: deterministic local E2E clock handoff

- **Test type:** Unit / configuration contract
- **Requirement IDs:** `ACC-019`, `ACC-020`, `I3-UI-05`
- **Scenario IDs:** `AC-ACC-010` (business-date boundary); local E2E date-sensitive journey handoff
- **Test class/method:** `com.lab.labtimesheet.config.TimeConfigurationTest#e2eProfileUsesTheConfiguredFixedInstant`, `#e2eProfileIsRejectedAlongsideProduction`; `src/test/js/delivery-contract.test.mjs`
- **Implementation commit:** `pending`

## Protected behavior

The local-only `e2e` profile supplies a fixed Vietnam-zone clock for deterministic browser journeys. It is absent from
the default `dev` profile, cannot be changed at runtime, and fails startup if combined with `prod`.

## Test method

The Java tests load the production `TimeConfiguration` directly with the `e2e` profile and an explicit ISO-8601
property, then load it with both `prod` and `e2e` to prove the rejection. The delivery contract checks the profile
resource, profile guards, and documented startup command.

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
```

## Affected suite

**Command and result**

```text
npm run test:ui
Tests: 10 passed

env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -q test
Tests run: 478, Failures: 0, Errors: 0, Skipped: 0
PostgreSQL: 18.4; Java: 25.0.4
```

## External-test boundaries

This proves configuration selection and startup rejection. It does not claim a browser run, calendar override, or
production deployment; the Reports/UI owner must use the documented fixed instant for the blocked Saturday journey.
