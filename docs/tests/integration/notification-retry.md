# Test Evidence: ordinary notification email retries

- **Test type:** Integration
- **Requirement IDs:** `NOT-006`, `NOT-007`, `ERR-004`
- **Scenario IDs:** `AC-NOT-002`
- **Test class/method:** `com.lab.labtimesheet.feature.notification.service.NotificationServiceIntegrationTest.ordinaryEmailRetriesUseTheFiveBoundedDelaysThenBecomeTerminalAndManualRetryReusesTheRow`
- **Implementation commit:** `1c762021aa5025ce90a96259a36ea4db1af262b6`
- **Review-fix commit:** `1cd45526cfaadba46c632dd7ea5f77eb26e97b94`

## Protected behavior

Transient ordinary-email failure remains an in-app notification with bounded retry state. The worker retries after
exactly 5 minutes, 30 minutes, 2 hours, and 12 hours following the initial 1-minute delay, marks the sixth total
attempt `FAILED`, and lets an Admin manually requeue the same row without creating a duplicate notification.

## Test method

The PostgreSQL 18.4 Testcontainers test uses the deterministic test clock and a recording SMTP adapter. It publishes
one designated email event that fails immediately, marks the retry row due at each boundary, invokes the concrete
bounded worker, checks attempts/status/next-attempt state, then succeeds through the Admin-authorized manual retry.
The row count is asserted throughout to prove no duplicate in-app record. A blocking SMTP overlap test concurrently
starts immediate after-commit delivery and the retry worker, proving the row lock produces one provider call.

## Hand-derived expected result

Attempt 1 fails and schedules +1 minute. Attempts 2–5 fail and schedule +5 minutes, +30 minutes, +2 hours, and +12
hours. Attempt 6 fails and becomes terminal `FAILED` with no next timestamp. Manual retry resets only the delivery
cycle, sends once, marks `SENT`, and leaves exactly one notification row.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=NotificationServiceIntegrationTest#ordinaryEmailRetriesUseTheFiveBoundedDelaysThenBecomeTerminalAndManualRetryReusesTheRow test
```

**Observed result**

```text
2026-08-22T13:30:33+07:00 — BUILD FAILURE during testCompile: NotificationService had no retryDueEmails or
retryFailedEmail boundary. No container or behavior ran; this was the expected missing-worker RED.
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=NotificationServiceIntegrationTest#ordinaryEmailRetriesUseTheFiveBoundedDelaysThenBecomeTerminalAndManualRetryReusesTheRow test
```

**Observed result**

```text
2026-08-22T13:37:39+07:00 — PostgreSQL 18.4 Testcontainers; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0;
BUILD SUCCESS.

**Review-fix RED/GREEN**

```text
2026-08-22T15:16:49+07:00 — blocking-SMTP overlap regression: Tests run: 1, Failures: 1, Errors: 0; the provider
was called twice while immediate delivery had released its row lock.
2026-08-22T15:19:35+07:00 — review-fix overlap GREEN: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0; one provider
call and one `SENT` row while the retry worker waited for the lock.
```
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=NotificationServiceIntegrationTest test

2026-08-22T15:22:10+07:00 — PostgreSQL 18.4 Testcontainers; Tests run: 10, Failures: 0, Errors: 0, Skipped: 0;
BUILD SUCCESS.
```

## External-test boundaries

This evidence does not exercise a real SMTP provider, Admin browser status page, multi-node scheduling, or external
job orchestration. The worker is intentionally bounded for the supported single application instance and uses row
locks to make repeated due invocations idempotent. Activation/password-reset links remain outside the ordinary
notification outbox.
