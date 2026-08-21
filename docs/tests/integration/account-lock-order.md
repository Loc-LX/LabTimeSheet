# Test Evidence: account/token recovery lock order

- **Test type:** Integration
- **Requirement IDs:** `ACC-018`, `SEC-004`, `SEC-005`
- **Scenario IDs:** `AC-SEC-002`
- **Test class/method:** `com.lab.labtimesheet.feature.account.service.AccountRecoveryLockOrderIntegrationTest#concurrentAccountFirstConsumptionAndIssuanceComplete`
- **Implementation commit:** pending local milestone

## Protected behavior

Password-reset consumption and replacement issuance use one PostgreSQL lock order: account row first, then token
row. The account lock must be acquired before any token lock, and both locks remain transaction-scoped.

## Test method

The detached replay reconstructs the minimal pre-fix token-first consumption plus user-first issuance sequence. Two
transactions each hold one row after a latch and then request the other row, forcing PostgreSQL 18.4 to detect the
cycle. The current-tree test invokes the real `AccountService.resetPassword` and `requestPasswordReset` methods with
test-only dynamic repository proxies. The barrier observes whichever row the consumer locks first: account-first
lets the issuer enter its account lock attempt before the consumer takes the token lock; token-first instead holds the
consumer until the issuer owns the account, forcing the old token-to-account versus account-to-token cycle. No
production test hook or dependency is used.

## Hand-derived expected result

The reconstructed inversion must fail with PostgreSQL SQLSTATE `40P01` rather than complete. The current account-first
sequence must complete both production recovery transactions within the bounded future timeout; reverting only the
consumer's production lock order would make the same barrier form the PostgreSQL cycle.

## RED

**Exact-base replay command**

```text
cd /private/tmp/labtimesheet-iteration2/replay-lock-order && JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=AccountRecoveryLockOrderIntegrationTest' test
```

**Observed result**

At exact base `58a087b118cc955748d7df1aa47d2bbc3ca0371b`, the synchronized replay reached PostgreSQL 18.4 and failed
because one future raised `CannotAcquireLockException`; the server log reported `SQLState: 40P01` and `ERROR:
deadlock detected`. This is a deterministic pre-fix RED, not the previous nondeterministic stress result.

**Unchanged-test production-order replay**

```text
cd /private/tmp/labtimesheet-iteration2/replay-lock-current && JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=AccountRecoveryLockOrderIntegrationTest' test
```

The isolated copy was made from the current implementation and changed only the `resetPassword` lock order back to
token-first; the test source and repository barriers were unchanged. The run compiled and reached PostgreSQL 18.4,
then failed one test with `CannotAcquireLockException`, SQLSTATE `40P01`, and `ERROR: deadlock detected` while the
consumer attempted the account row. This directly demonstrates that reverting only production lock order fails the
same production-path regression.

## GREEN

**Command**

```text
cd /private/tmp/labtimesheet-iteration2/platform && JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=AccountRecoveryLockOrderIntegrationTest' test
```

**Observed result**

```text
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

The current account-first sequence completed on PostgreSQL 18.4 under the same deterministic coordination. The final
rerun after the isolated token-first RED completed at 20:55 Asia/Ho_Chi_Minh with the same 1/1 result.

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

The replay is a test-only reconstruction of the old ordering and does not claim that the detached base contains the
new reset/issuance service. The current test invokes production service methods while observing only the Account-owned
repository boundary, not a foreign feature's persistence or a multi-node deployment. The existing
`AccountRecoveryIntegrationTest` service stress remains separate review evidence.
