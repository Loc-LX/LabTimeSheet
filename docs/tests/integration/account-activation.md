# Test Evidence: SMTP-gated account creation and activation

- **Test type:** Integration
- **Requirement IDs:** `ACC-008`–`ACC-012`, `ACC-014`, `ACC-019`, `ACC-020`, `NOT-008`, `SEC-002`–`SEC-004`
- **Scenario IDs:** `AC-ACC-004` (Mentor path), `AC-ACC-005` (Mentor/Intern paths), `AC-ACC-006` (initial delivery failure only)
- **Test class/method:** `com.lab.labtimesheet.feature.account.service.AccountActivationIntegrationTest#smtpGatedCreationHashesSingleUseActivationAndRetainsFailedDeliveryHistory`
- **Implementation commit:** `98a52a1ac23591fa1cd30b7b175da81ec607e521`; start-date guard added in `6181984cf85f184be39513d6313f9cbe8267add5`

## Protected behavior

An active Admin can create pending Mentor/Intern accounts only while a tested SMTP revision is active. The raw activation secret exists only in the immediate email, PostgreSQL stores only its SHA-256 hash, activation is single-use, and a failed initial delivery keeps history while invalidating that token. Activating an Intern's lifecycle separately makes the account eligible only inside its inclusive internship dates. Reporting reads account counts through the Account service boundary.

## Test method

The PostgreSQL 18.4 integration test bootstraps the first Admin, proves creation is blocked before SMTP activation, activates a recorded SMTP boundary, and exercises production account creation/activation. It independently hashes the captured raw link token, inspects persisted state through platform-owned repositories, simulates delivery failure, activates an Intern lifecycle, checks date boundaries, and checks the service-level summary used by reporting.

## Hand-derived expected result

The first non-bootstrap creation attempt adds zero rows. A delivered Mentor is pending with no password until one successful activation; replay fails. A failed Intern delivery leaves one pending account and one invalidated token. After the successful Intern is activated at both account and internship levels, the final state has three active accounts (Admin, Mentor, Intern), one pending account, and one active internship.

## RED

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -Dtest=AccountActivationIntegrationTest test
```

**Observed result**

```text
BUILD FAILURE. Test compilation reported five missing account-activation API/model symbols, including CreateAccountCommand, TokenPurpose, and UserActionTokenRepository. No test ran.
```

After the first GREEN implementation, the exact-expiry assertion was added and observed RED before exposing the persisted expiry:

```text
BUILD FAILURE. AccountActivationIntegrationTest could not compile because UserActionToken#getExpiresAt() did not exist.
```

## GREEN

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -Dtest=AccountActivationIntegrationTest test
```

**Observed result**

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Affected suite

**Command and result**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw test
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## External-test boundaries

The recording SMTP boundary proves the exact in-memory handoff but not Mailpit/network delivery. MVC creation, activation, login, role denial, logout, and the additional-Admin path are covered separately by `AccountWebIntegrationTest`. This test covers only the Mentor path of SMTP gating and the Mentor/Intern paths of hash-only creation; it does not claim all-role coverage for AC-ACC-004/005. It covers the initial failure/invalidation part of AC-ACC-006, not resend. Resend, password reset, session invalidation after credential/state changes, lock/deactivation, and production origin/readiness hardening remain separate slices.
