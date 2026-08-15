# Test Evidence: Cross-feature account boundary

- **Test type:** Integration
- **Requirement IDs:** `ACC-002, ACC-014, ACC-020–ACC-021, PRJ-017, ATT-007`
- **Scenario IDs:** No direct acceptance-scenario mapping (cross-feature API regression)
- **Test class/method:** `com.lab.labtimesheet.feature.account.service.BootstrapIntegrationTest.exposesIdentityAndDateAwareInternEligibilityWithoutPersistenceTypes`
- **Implementation commit:** `1235204bf1298599264a07943ca1167432556bd2`

## Protected behavior

Other features can resolve an account by normalized email or ID through an immutable identity DTO and can ask whether an Intern is active and within an inclusive internship interval for a supplied work date. They do not need access to account repositories or JPA entities.

## Test method

The PostgreSQL 18.4 integration test creates the initial Admin through the production bootstrap transaction, resolves the resulting identity through `AccountService`, and verifies ID/email equivalence, normalized lookup, role, status, and rejection by both current and date-aware Intern eligibility gates. Starting the context also parses the Spring Data derived interval query against the mapped `intern_profiles` entity.

## Hand-derived expected result

` ADMIN@EXAMPLE.COM ` resolves to the persisted `admin@example.com` identity. An active Admin is not an eligible Intern on `2026-08-14`. The date-aware gate requires an active Intern account, an `ACTIVE` internship, and `start_date <= workDate <= end_date`.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
./mvnw -Dtest=BootstrapIntegrationTest test
```

**Observed result**

```text
BootstrapIntegrationTest.java: method isEligibleIntern in class AccountService
cannot be applied to given types; required: long; found: long, java.time.LocalDate
Tests did not run; test compilation failed
BUILD FAILURE
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=BootstrapIntegrationTest test
```

**Observed result**

```text
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw test

Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## External-test boundaries

The test proves identity lookup and rejection of a non-Intern plus successful repository-query initialization. The positive active-Intern and interval-edge cases remain part of I1-PLAT-06 activation/account lifecycle work; dependent features must still enforce their own authorization and transaction invariants.
