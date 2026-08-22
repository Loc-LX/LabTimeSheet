# Test Evidence: normalized email/source-IP login throttling

- **Test type:** Unit
- **Requirement IDs:** `SEC-006`, `SEC-007`
- **Scenario IDs:** `AC-SEC-003`
- **Test class/method:** `com.lab.labtimesheet.feature.account.service.LoginThrottleTest`
- **Implementation commit:** `091361eb73c7d7118d8212df630a83aca4ad5f9e`
- **Review-fix commit:** `1cd45526cfaadba46c632dd7ea5f77eb26e97b94`

## Protected behavior

Login failure state is keyed by trimmed, case-folded email plus the server-observed source IP. Five failures in a
rolling fifteen-minute window throttle the sixth attempt for fifteen minutes; a successful login clears only the
applicable pair, and another source IP remains independent. The in-memory state is bounded for the supported
single-instance deployment and never evicts an active block under sequential or concurrent capacity pressure.

## Test method

The unit test injects a mutable deterministic clock and exercises the concrete throttle with five failures, distinct
source addresses, exact 15-minute expiry, successful-login clearing, rolling-window expiry, 15,120-key capacity
pressure, and eight-worker concurrent capacity pressure. It avoids Spring or a database because this boundary is
intentionally bounded in-memory state.

## Hand-derived expected result

The fifth failure starts a block ending fifteen minutes later. At 14:59 the pair remains blocked; at exactly the
expiry instant it is allowed again. Four failures from the prior window do not combine with one new failure after the
window, and clearing removes the pair's state without affecting another IP.

## RED

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=LoginThrottleTest test
```

**Observed result**

```text
2026-08-22T13:26:26+07:00 — BUILD FAILURE during testCompile: LoginThrottle did not exist. The production-shaped
boundary test could not compile, which was the expected RED for the missing throttle.
```

## GREEN

**Command**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Dtest=LoginThrottleTest test
```

**Observed result**

```text
2026-08-22T15:15:24+07:00 — Review regression RED: Tests run: 5, Failures: 1, Errors: 0; the active target block was
evicted after capacity pressure.

2026-08-22T15:23:19+07:00 — Review-fix GREEN: Tests run: 5, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS.
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=NotificationServiceIntegrationTest,LoginThrottleTest,ProductionReadinessTest,SecurityResponseIntegrationTest,AuthenticationWebIntegrationTest,CalendarDevelopmentProfileWebIntegrationTest test

2026-08-22T15:22:10+07:00 — affected combined Platform security/notification suite; LoginThrottleTest 5/5 and
AuthenticationWebIntegrationTest 1/1 passed with PostgreSQL 18.4 Testcontainers; total affected command `25/25`.
```

## External-test boundaries

This unit evidence does not prove a multi-node throttle (explicitly unsupported by `SEC-007`) or a production proxy's
network topology. The filter uses the server request address and profile-controlled forwarded-header handling; manual
account locks remain persisted separately.
