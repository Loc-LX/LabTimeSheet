# Test Evidence: account-action token cleanup

- **Test type:** Integration
- **Requirement IDs:** `SEC-003`, `SEC-009`
- **Scenario IDs:** No dedicated numbered acceptance scenario; plan trace `I3-PLAT-03` and requirement trace `SEC-003`, `SEC-009`
- **Test class/method:** `com.lab.labtimesheet.feature.account.service.UserActionTokenCleanupIntegrationTest.cleanupDeletesExpiredAndTerminalRowsButRetainsLiveToken`
- **Implementation commit:** `fe48b8bd19fb0961e4f853087ec7ee8c41d7dd1f`
- **Metadata review-fix commit:** `1cd45526cfaadba46c632dd7ea5f77eb26e97b94`

## Protected behavior

Expired, consumed, and invalidated activation/password-reset token rows are removed by a bounded scheduled cleanup,
while a currently usable hashed token remains available. The hourly scheduler entrypoint owns its transaction rather
than relying on a self-invoked transactional method. Raw token material is never part of the cleanup boundary.

## Test method

The PostgreSQL 18.4 Testcontainers test creates an expired password-reset row, a consumed activation row, and a live
password-reset row for account-owned users. It invokes both the concrete cleanup service and the scheduled entrypoint
with the repository clock, asserting deletion and retention through the production scheduler path. This is the
narrowest production-shaped test for the bulk cleanup predicate and transaction boundary.

## Hand-derived expected result

The expired and consumed rows are terminal and are deleted, so the result count is 2. The live row has an expiry after
the cleanup instant and remains present.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=UserActionTokenCleanupIntegrationTest test
```

**Observed result**

```text
2026-08-22T13:38:56+07:00 — BUILD FAILURE during testCompile: UserActionTokenCleanupService was missing. No
container or behavioral execution occurred; this was the expected missing cleanup-service RED.
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=UserActionTokenCleanupIntegrationTest test
```

**Observed result**

```text
2026-08-22T13:43:46+07:00 — PostgreSQL 18.4 Testcontainers; prior direct-cleanup test passed `1/1`.

2026-08-22T20:01:32+07:00 — Scheduler-path RED: PostgreSQL 18.4 Testcontainers; the new scheduled entrypoint test
failed with `TransactionRequiredException` because scheduler self-invocation bypassed the transactional proxy.

2026-08-22T20:02:14+07:00 — Scheduler-path GREEN: PostgreSQL 18.4 Testcontainers; Tests run: 2, Failures: 0, Errors: 0,
Skipped: 0; BUILD SUCCESS after moving the transaction boundary to the scheduled entrypoint.
BUILD SUCCESS.
```

## Affected suite

**Command and result**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -Dtest=AccountActivationIntegrationTest,AccountIdentityCorrectionIntegrationTest,AccountLifecycleIntegrationTest,AccountRecoveryIntegrationTest,AccountRecoveryLockOrderIntegrationTest,AccountScalarLookupIntegrationTest,ActiveMentorIdentityIntegrationTest,BootstrapIntegrationTest,EligibleInternOptionIntegrationTest,InternMutationEligibilityIntegrationTest,InternWorkWindowIntegrationTest,InternshipLifecycleIntegrationTest,LoginThrottleTest,PasswordResetIntegrationTest,UserActionTokenCleanupIntegrationTest test

2026-08-22T20:02:14+07:00 — focused scheduler test `2/2` passed. Fresh affected-suite result is recorded after the
post-fix branch gate because the earlier concurrent full run was invalidated by overlapping Testcontainers contexts.
```

## External-test boundaries

This evidence does not prove token cleanup scheduling over a long-running process, raw-token activation/reset
delivery, or multi-node coordination. The query is intentionally single-instance and removes only terminal rows;
token issuance and consumption remain in the Account service.
