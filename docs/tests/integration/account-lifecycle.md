# Test Evidence: account lock, unlock, and deactivation

- **Test type:** Integration
- **Requirement IDs:** `ACC-014`–`ACC-018`
- **Scenario IDs:** `AC-ACC-009`
- **Test class/method:** `com.lab.labtimesheet.feature.account.service.AccountLifecycleIntegrationTest#lockUnlockAndDeactivatePreserveRoleAndAttribution`
- **Implementation commit:** pending local milestone

## Protected behavior

Admin state actions follow the persisted account state graph, preserve immutable global role and historical account rows, and invalidate sessions through the shared registry boundary.

## Test method

The test creates and activates a Mentor through the SMTP-gated production path, applies lock, unlock, and deactivation, and checks every durable state transition on PostgreSQL 18.4.

## Hand-derived expected result

The account visits `ACTIVE → LOCKED → ACTIVE → DEACTIVATED`; role stays `MENTOR`, and deactivation timestamp is retained.

## RED

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=AccountLifecycleIntegrationTest#lockUnlockAndDeactivatePreserveRoleAndAttribution' test
```

**Observed result**

Test compilation failed as expected because `AccountService.lockAccount`, `unlockAccount`, and `deactivateAccount` were absent. This is the missing production lifecycle boundary, not a fixture or environment failure.

## GREEN

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=AccountLifecycleIntegrationTest#lockUnlockAndDeactivatePreserveRoleAndAttribution' test
```

**Observed result**

`BUILD SUCCESS`; PostgreSQL 18.4 Testcontainers started and `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`.

## Affected suite

**Command and result**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=AccountActivationIntegrationTest,AccountLifecycleIntegrationTest,AccountRecoveryIntegrationTest,AccountRecoveryLockOrderIntegrationTest,EligibleInternOptionIntegrationTest,InternMutationEligibilityIntegrationTest,InternWorkWindowIntegrationTest,InternshipLifecycleIntegrationTest,PasswordRecoveryWebIntegrationTest,ActivationResendWebIntegrationTest,AccountSessionInvalidationWebIntegrationTest,AccountWebIntegrationTest,PasswordResetIntegrationTest' test
```

**Observed result**

`BUILD SUCCESS`; 34 tests passed across the account activation/lifecycle/recovery, lock-order, eligibility, mutation-eligibility,
work-window, internship lifecycle, recovery web, resend web, session invalidation web, account web, and password-reset
classes. PostgreSQL 18.4 Testcontainers was used for each Spring context.

## External-test boundaries

The test inspects durable state but does not yet prove a real browser session is denied after each action; that is covered by the session-invalidation regression milestone.
