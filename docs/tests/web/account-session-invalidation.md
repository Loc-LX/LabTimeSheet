# Test Evidence: account session invalidation

- **Test type:** Web integration
- **Requirement IDs:** `ACC-014`–`ACC-018`
- **Scenario IDs:** `AC-ACC-009`
- **Test class/method:** `com.lab.labtimesheet.feature.account.controller.AccountSessionInvalidationWebIntegrationTest#lockingAnAuthenticatedAccountExpiresItsExistingSession`; `#successfulLoginRotatesThePreAuthenticationSessionId`
- **Implementation commit:** pending local milestone

## Protected behavior

Locking an account must expire its already-authenticated Spring Security session; a later request using that session cannot access protected pages.

## Test method

The test logs a real activated Mentor in through MockMvc, keeps the returned HTTP session, performs the Admin lock mutation through the Account service, and reuses that session against an Admin page.

## Hand-derived expected result

The persisted account moves to `LOCKED`, the old session is expired through the SessionRegistry, and the reused request redirects to `/login` without an authenticated principal.

## RED

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=AccountSessionInvalidationWebIntegrationTest#lockingAnAuthenticatedAccountExpiresItsExistingSession' test
```

**Observed result**

`BUILD FAILURE`; the request reused the authenticated session and returned `403 Forbidden` instead of redirecting to `/login` (`Redirected URL expected:</login> but was:<null>`). The first implementation also looked up the email string directly, while Spring Security's SessionRegistry held a `UserDetails` principal; enabling the concurrent-session filter was additionally required for `SessionInformation.expireNow()` to be enforced on the next request.

## GREEN

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=AccountSessionInvalidationWebIntegrationTest#lockingAnAuthenticatedAccountExpiresItsExistingSession' test
```

**Observed result**

`BUILD SUCCESS`; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. PostgreSQL 18.4 Testcontainers started successfully.

## Affected suite

**Command and result**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=AccountActivationIntegrationTest,AccountLifecycleIntegrationTest,AccountRecoveryIntegrationTest,AccountRecoveryLockOrderIntegrationTest,EligibleInternOptionIntegrationTest,InternMutationEligibilityIntegrationTest,InternWorkWindowIntegrationTest,InternshipLifecycleIntegrationTest,PasswordRecoveryWebIntegrationTest,ActivationResendWebIntegrationTest,AccountSessionInvalidationWebIntegrationTest,AccountWebIntegrationTest,PasswordResetIntegrationTest' test
```

**Observed result:** `Tests run: 34, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS` on PostgreSQL 18.4
Testcontainers.

## Login session-fixation regression

The same PostgreSQL-backed MockMvc slice first creates a pre-authentication session with `GET /login`, then submits a
successful Admin login using that session and asserts the authenticated request has a different session ID. This
protects Spring Security's default session-ID rotation while the maximum-sessions registry remains enabled.

### RED

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=AccountSessionInvalidationWebIntegrationTest#successfulLoginRotatesThePreAuthenticationSessionId' test
```

**Observed result**

`BUILD FAILURE`; the authenticated request reused session ID `1`, so the assertion that login must rotate the
pre-authentication ID failed. The explicit `RegisterSessionAuthenticationStrategy` had replaced Spring Security's
default fixation-protection strategy.

### GREEN

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=AccountSessionInvalidationWebIntegrationTest#successfulLoginRotatesThePreAuthenticationSessionId' test
```

**Observed result**

`BUILD SUCCESS`; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0. PostgreSQL 18.4 Testcontainers started
successfully and login returned a different session ID after the explicit strategy was removed.

## External-test boundaries

The test uses MockMvc's server-side session registry and does not claim browser-cookie or multi-node session-store coverage.
