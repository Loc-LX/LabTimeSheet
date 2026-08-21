# Test Evidence: explicit activation resend after delivery failure

- **Test type:** Web integration
- **Requirement IDs:** `ACC-012`–`ACC-013`, `SEC-001`
- **Scenario IDs:** `AC-ACC-006`
- **Test class/method:** `com.lab.labtimesheet.feature.account.controller.ActivationResendWebIntegrationTest#failedDeliveryShowsExplicitResendActionAndAdminCanRetry`
- **Implementation commit:** pending local milestone

## Protected behavior

When immediate activation delivery fails, the pending account remains and the Admin receives an explicit, CSRF-protected resend action. A successful retry replaces the previous token and reports the result without exposing the raw token.

## Test method

The web test uses the real PostgreSQL-backed SMTP revision path and a controlled delivery probe. It creates a pending Mentor with a simulated failed first send, renders the failure page, then posts the explicit retry action after delivery is restored.

## Hand-derived expected result

The failure page must contain a resend action bound to the failed account. The retry must be an authenticated Admin POST with CSRF and redirect to a success state; no raw activation token is returned in either response.

## RED

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=ActivationResendWebIntegrationTest#failedDeliveryShowsExplicitResendActionAndAdminCanRetry' test
```

**Observed result**

The focused web test failed as expected because the existing failure page did not render `resend-activation` (assertion at `ActivationResendWebIntegrationTest.java:72`); the missing explicit Admin retry route/UI is production behavior, not an environment or fixture failure.

## GREEN

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -Dtest=ActivationResendWebIntegrationTest#failedDeliveryShowsExplicitResendActionAndAdminCanRetry test
```

**Observed result**

`BUILD SUCCESS`; PostgreSQL 18.4 Testcontainers started and `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`.

## Affected suite

**Command and result**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=AccountActivationIntegrationTest,AccountLifecycleIntegrationTest,AccountRecoveryIntegrationTest,AccountRecoveryLockOrderIntegrationTest,EligibleInternOptionIntegrationTest,InternMutationEligibilityIntegrationTest,InternWorkWindowIntegrationTest,InternshipLifecycleIntegrationTest,PasswordRecoveryWebIntegrationTest,ActivationResendWebIntegrationTest,AccountSessionInvalidationWebIntegrationTest,AccountWebIntegrationTest,PasswordResetIntegrationTest' test
```

**Observed result**

`BUILD SUCCESS`; 34 tests passed across the account-focused affected suite on PostgreSQL 18.4 Testcontainers.

## External-test boundaries

The controlled probe does not prove external SMTP delivery; token replacement and invalidation are covered by the account service integration evidence.
