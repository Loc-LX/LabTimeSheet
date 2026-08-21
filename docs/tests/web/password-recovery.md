# Test Evidence: generic password recovery entry points

- **Test type:** Web/integration
- **Requirement IDs:** `ACC-011`, `SEC-003`–`SEC-005`, `NOT-008`
- **Scenario IDs:** `AC-SEC-002`, `AC-NOT-003`
- **Test class/method:** `com.lab.labtimesheet.feature.account.controller.PasswordRecoveryWebIntegrationTest#rendersGenericForgotAndResetPasswordPages`
- **Implementation commit:** pending local milestone

## Protected behavior

Forgot-password and reset-password entry points are public, render safe server-side forms, and do not redirect an unauthenticated visitor to login.

## Test method

The test boots the application against PostgreSQL 18.4, initializes the singleton Admin, and requests both public pages. This is the narrowest production-shaped check for the public recovery boundary.

## Hand-derived expected result

Both GET requests return HTTP 200 with their account-feature view names.

## RED

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=PasswordRecoveryWebIntegrationTest' test
```

**Observed result**

Focused Testcontainers run reached PostgreSQL 18.4 and failed at the first request because `GET /forgot-password` returned `302 Location: /login` instead of the expected `200`. The failure is the missing public route/security contract, not a fixture or Docker failure.

## GREEN

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=PasswordRecoveryWebIntegrationTest' test
```

**Observed result**

Focused PostgreSQL 18.4 Testcontainers run passed: `Tests run: 1, Failures: 0, Errors: 0` and `BUILD SUCCESS`.

## Affected suite

**Command and result**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=AccountActivationIntegrationTest,AccountLifecycleIntegrationTest,AccountRecoveryIntegrationTest,AccountRecoveryLockOrderIntegrationTest,EligibleInternOptionIntegrationTest,InternMutationEligibilityIntegrationTest,InternWorkWindowIntegrationTest,InternshipLifecycleIntegrationTest,PasswordRecoveryWebIntegrationTest,ActivationResendWebIntegrationTest,AccountSessionInvalidationWebIntegrationTest,AccountWebIntegrationTest,PasswordResetIntegrationTest' test
```

**Observed result**

`BUILD SUCCESS`; 34 tests passed across the account-focused affected suite on PostgreSQL 18.4 Testcontainers.

## External-test boundaries

This web check proves only public server-side page exposure and Thymeleaf view resolution. Password-reset POST
generic responses, hash-only persistence, expiry, replacement invalidation, single-use consumption, failed-delivery
invalidation, password bounds, and session invalidation are covered by the PostgreSQL integration evidence in
`docs/tests/integration/password-reset.md`; it does not claim a real SMTP server or browser rendering.
