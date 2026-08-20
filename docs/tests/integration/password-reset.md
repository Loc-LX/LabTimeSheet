# Test Evidence: password-reset lifecycle and security boundaries

- **Test type:** Integration/web integration
- **Requirement IDs:** `ACC-011`, `ACC-018`, `SEC-002`–`SEC-005`, `NOT-008`
- **Scenario IDs:** `AC-SEC-002`, `AC-NOT-003`
- **Test class/method:** `com.lab.labtimesheet.feature.account.service.PasswordResetIntegrationTest`
- **Implementation commit:** pending local milestone

## Protected behavior

Forgot-password POST is enumeration-safe for active, pending, locked, deactivated, unknown, and SMTP-unavailable
accounts. Reset POST is generic for an unknown token, accepts passwords from 12 through 128 characters, persists
only a SHA-256 token hash, expires tokens exclusively at 30 minutes, invalidates replacements, enforces single use,
invalidates a token when delivery fails, and expires existing authenticated sessions after a successful reset.

## Test method

The test boots the application with PostgreSQL 18.4 Testcontainers, a mutable server clock, and a deterministic SMTP
adapter that records only the immediate in-memory body. It exercises the public forgot/reset POST forms and the
Account service through separate fresh contexts, then inspects only Account-owned token, credential, and session
outcomes. Boundary tests use 11/12/128/129-character passwords and verify both old-password rejection and new-
password authentication.

## Hand-derived expected result

Every forgot request redirects to `/forgot-password?requested`, regardless of account state or whether SMTP is
configured; without tested SMTP no token or delivery row is created. Unknown reset tokens render the same generic
message. The valid interval is `now < expiresAt`; at exactly `now + 30 minutes` consumption fails. One replacement
invalidates the old token, one successful consumption marks the token used, delivery failure invalidates it, and reset
expires the logged-in session.

## RED

**Exact-base replay command**

```text
cd /private/tmp/labtimesheet-iteration2/replay-password && JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=PasswordResetIntegrationTest' test
```

**Observed result**

At exact base `58a087b118cc955748d7df1aa47d2bbc3ca0371b`, test compilation failed with 20 missing-API errors for the
corrected reset coverage (`AccountService.lockAccount`, `requestPasswordReset`, and `resetPassword`). This is the
genuine missing production behavior at the replay base; no production source was changed in that detached worktree.

**Current implementation behavior RED**

```text
cd /private/tmp/labtimesheet-iteration2/platform && JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=PasswordResetIntegrationTest' test
```

Before the encoder correction, the focused run reached PostgreSQL 18.4 and failed `11` tests with one error:
`IllegalArgumentException: password cannot be more than 72 bytes` while resetting the required 128-character
password. This was a real password-boundary defect, not a fixture or container failure.

## GREEN

**Command**

```text
cd /private/tmp/labtimesheet-iteration2/platform && JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=PasswordResetIntegrationTest' test
```

**Observed result**

```text
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

The run used PostgreSQL 18.4 Testcontainers. It proves generic active/pending/locked/unknown and no-SMTP forgot
responses, no token/delivery without tested SMTP, 11/129 rejection, 12/128 acceptance, old-password rejection,
new-password authentication, hash-only persistence, exact expiry, replacement invalidation, single-use
consumption, failed-delivery invalidation, and authenticated-session expiry.

## Affected suite

**Command and result**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=AccountActivationIntegrationTest,AccountLifecycleIntegrationTest,AccountRecoveryIntegrationTest,AccountRecoveryLockOrderIntegrationTest,EligibleInternOptionIntegrationTest,InternMutationEligibilityIntegrationTest,InternWorkWindowIntegrationTest,InternshipLifecycleIntegrationTest,PasswordRecoveryWebIntegrationTest,ActivationResendWebIntegrationTest,AccountSessionInvalidationWebIntegrationTest,AccountWebIntegrationTest,PasswordResetIntegrationTest' test
```

```text
[INFO] Tests run: 34, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## External-test boundaries

The SMTP adapter is a deterministic test double; this does not claim a real SMTP server, browser automation, or
multi-node SessionRegistry behavior. It does prove the server-side controller/service contracts, PostgreSQL token
state, credential replacement, and session invalidation without returning raw credentials or tokens from production
code.
