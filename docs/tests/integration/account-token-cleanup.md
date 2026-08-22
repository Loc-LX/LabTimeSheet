# Test Evidence: account-action token cleanup

- **Test type:** Integration
- **Requirement IDs:** `SEC-003`, `SEC-009`
- **Scenario IDs:** No dedicated numbered acceptance scenario; plan trace `I3-PLAT-03` and requirement trace `SEC-003`, `SEC-009`
- **Test class/method:** `com.lab.labtimesheet.feature.account.service.UserActionTokenCleanupIntegrationTest.cleanupDeletesExpiredAndTerminalRowsButRetainsLiveToken`
- **Implementation commit:** `2eaa4b67bdf7ac1a378c3c72341b5664ec18ff8b`
- **Metadata review-fix commit:** `1cd45526cfaadba46c632dd7ea5f77eb26e97b94`

## Protected behavior

Expired, consumed, and invalidated activation/password-reset token rows are removed by a bounded scheduled cleanup,
while a currently usable hashed token remains available. Raw token material is never part of the cleanup boundary.

## Test method

The PostgreSQL 18.4 Testcontainers test creates an expired password-reset row, a consumed activation row, and a live
password-reset row for account-owned users. It invokes the concrete cleanup service with the repository clock and
asserts the deletion count and each row's resulting presence. This is the narrowest production-shaped test for the
bulk cleanup predicate and retention boundary.

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
2026-08-22T13:43:46+07:00 — PostgreSQL 18.4 Testcontainers; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0;
BUILD SUCCESS.
```

## Affected suite

**Command and result**

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -Dtest=AccountActivationIntegrationTest,AccountIdentityCorrectionIntegrationTest,AccountLifecycleIntegrationTest,AccountRecoveryIntegrationTest,AccountRecoveryLockOrderIntegrationTest,AccountScalarLookupIntegrationTest,ActiveMentorIdentityIntegrationTest,BootstrapIntegrationTest,EligibleInternOptionIntegrationTest,InternMutationEligibilityIntegrationTest,InternWorkWindowIntegrationTest,InternshipLifecycleIntegrationTest,LoginThrottleTest,PasswordResetIntegrationTest,UserActionTokenCleanupIntegrationTest test

2026-08-22T13:59:29+07:00 — PostgreSQL 18.4 Testcontainers; Tests run: 46, Failures: 0, Errors: 0, Skipped: 0;
BUILD SUCCESS with Java 25.0.4. The cleanup test was included in this affected Account suite.
```

## External-test boundaries

This evidence does not prove token cleanup scheduling over a long-running process, raw-token activation/reset
delivery, or multi-node coordination. The query is intentionally single-instance and removes only terminal rows;
token issuance and consumption remain in the Account service.
