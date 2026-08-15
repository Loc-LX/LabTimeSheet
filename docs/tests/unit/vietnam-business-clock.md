# Test Evidence: Vietnam business-date clock boundary

- **Test type:** Unit
- **Requirement IDs:** `ACC-019`, `ACC-020`
- **Scenario IDs:** `AC-ACC-010` (business-date boundary only)
- **Test class/method:** `com.lab.labtimesheet.config.TimeConfigurationTest#utcInstantAtVietnamMidnightUsesTheNewLocalBusinessDate`
- **Implementation commit:** `06dba4fb13eed675cc08ff8c00fe3e3650468c3b`

## Protected behavior

The production application clock uses `Asia/Ho_Chi_Minh`, so account lifecycle decisions based on `LocalDate.now`
advance at Vietnam midnight rather than seven hours later at UTC midnight.

## Test method

The test obtains the real production clock configuration, fixes its configured zone at the UTC instant
`2026-08-14T17:00:00Z`, and derives the local business date. No Spring context or database is needed because the
contract under test is the clock bean's zone.

## Hand-derived expected result

Vietnam is UTC+07:00, so `2026-08-14T17:00:00Z` is `2026-08-15T00:00:00+07:00` and the business date is
`2026-08-15`.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=TimeConfigurationTest test
```

**Observed result**

```text
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
Expected 2026-08-15 but was 2026-08-14 because the production clock used UTC.
BUILD FAILURE
```

## GREEN

**Command**

```text
./mvnw -Dtest=TimeConfigurationTest test
```

**Observed result**

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
./mvnw -Dtest=TimeConfigurationTest,BootstrapIntegrationTest,SmtpOnboardingWebIntegrationTest,AccountActivationIntegrationTest,AccountWebIntegrationTest,BootstrapOnboardingWebIntegrationTest,JavaMailSmtpProbeTest,SecurityResponseIntegrationTest test
Tests run: 22, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
PostgreSQL: 18.4
```

## External-test boundaries

This verifies the production clock zone and its midnight boundary. It does not exercise later scheduler behavior or
attendance-policy timezone versioning.
