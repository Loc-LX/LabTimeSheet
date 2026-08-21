# Test Evidence: activation resend lifecycle

- **Test type:** Integration
- **Requirement IDs:** `ACC-012`–`ACC-013`, `SEC-003`–`SEC-004`, `NOT-008`
- **Scenario IDs:** `AC-ACC-006`, `AC-NOT-003`
- **Test class/method:** `com.lab.labtimesheet.feature.account.service.AccountRecoveryIntegrationTest#resendInvalidatesPriorActivationTokenBeforeSendingFreshLink`
- **Implementation commit:** pending local milestone

## Protected behavior

Activation resend invalidates the prior live token before issuing a fresh single-use token; only the fresh token activates the pending account.

## Test method

The integration test creates a pending account through the SMTP-gated path against PostgreSQL 18.4, captures two opaque links at the mail adapter boundary, and attempts activation with both. It is the narrowest production-shaped proof of prior-token invalidation and fresh-token delivery.

## Hand-derived expected result

The two raw tokens differ; the first returns `false`, the second returns `true`, and exactly two hashed activation-token rows remain retained. Password-reset issue/consume behavior is covered separately by `docs/tests/integration/password-reset.md`.

## RED

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=AccountRecoveryIntegrationTest#resendInvalidatesPriorActivationTokenBeforeSendingFreshLink' test
```

**Observed result**

Test compilation failed as expected because `AccountService.resendActivation(long,long)` did not exist. This is the missing production service boundary, not a fixture or environment failure.

During the first implementation GREEN attempt, PostgreSQL then exposed the required flush ordering: invalidating the prior row and inserting its replacement in one JPA transaction hit `uq_user_action_tokens_one_live`. The production correction flushes the invalidation before issuing the replacement.

## GREEN

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=AccountRecoveryIntegrationTest#resendInvalidatesPriorActivationTokenBeforeSendingFreshLink' test
```

**Observed result**

Focused PostgreSQL 18.4 Testcontainers run passed: `Tests run: 1, Failures: 0, Errors: 0` and `BUILD SUCCESS` after flushing invalidated token state before replacement insert.

## Affected suite

**Command and result**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=AccountActivationIntegrationTest,AccountLifecycleIntegrationTest,AccountRecoveryIntegrationTest,AccountRecoveryLockOrderIntegrationTest,EligibleInternOptionIntegrationTest,InternMutationEligibilityIntegrationTest,InternWorkWindowIntegrationTest,InternshipLifecycleIntegrationTest,PasswordRecoveryWebIntegrationTest,ActivationResendWebIntegrationTest,AccountSessionInvalidationWebIntegrationTest,AccountWebIntegrationTest,PasswordResetIntegrationTest' test
```

**Observed result:** `Tests run: 34, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS` on PostgreSQL 18.4
Testcontainers.

## PostgreSQL token race coverage

`AccountRecoveryIntegrationTest#resetConsumptionAndReplacementIssuanceUseOneUserThenTokenLockOrder` starts reset-token
consumption and replacement issuance together for eight repeated rounds. Each operation has a ten-second completion
bound; PostgreSQL 18.4 either consumes the old token or reports it stale while replacement issuance completes, with no
deadlock. The service rechecks account/token state after acquiring the shared user-then-token lock order.

The adapter is a deterministic test double; this does not prove a real SMTP server or browser resend control. The
password-reset integration evidence covers the reset-specific lifecycle and session behavior.
