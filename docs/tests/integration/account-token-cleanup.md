# Test Evidence: account-action token cleanup

- **Test type:** Integration
- **Requirement IDs:** `SEC-003`, `SEC-009`
- **Scenario IDs:** `AC-SEC-003`
- **Test class/method:** `com.lab.labtimesheet.feature.account.service.UserActionTokenCleanupIntegrationTest.cleanupDeletesExpiredAndTerminalRowsButRetainsLiveToken`
- **Implementation commit:** `pending`

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
Pending for the branch-wide platform gate. The focused GREEN above is the producer milestone gate; the account
service suite and branch-wide suite will be recorded after the remaining platform rows land.
```

## External-test boundaries

This evidence does not prove token cleanup scheduling over a long-running process, raw-token activation/reset
delivery, or multi-node coordination. The query is intentionally single-instance and removes only terminal rows;
token issuance and consumption remain in the Account service.
