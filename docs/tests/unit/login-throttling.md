# Test Evidence: normalized email/source-IP login throttling

- **Test type:** Unit
- **Requirement IDs:** `SEC-006`, `SEC-007`
- **Scenario IDs:** `AC-SEC-003`
- **Test class/method:** `com.lab.labtimesheet.feature.account.service.LoginThrottleTest`
- **Implementation commit:** `pending`

## Protected behavior

Login failure state is keyed by trimmed, case-folded email plus the server-observed source IP. Five failures in a
rolling fifteen-minute window throttle the sixth attempt for fifteen minutes; a successful login clears only the
applicable pair, and another source IP remains independent. The in-memory state is bounded for the supported
single-instance deployment.

## Test method

The unit test injects a mutable deterministic clock and exercises the concrete throttle with five failures, distinct
source addresses, exact 15-minute expiry, successful-login clearing, and rolling-window expiry. It avoids Spring or a
database because this boundary is intentionally bounded in-memory state.

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
2026-08-22T13:27:11+07:00 — Tests run: 3, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS.
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=AuthenticationWebIntegrationTest test

2026-08-22T13:28:32+07:00 — PostgreSQL 18.4 Testcontainers; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0;
BUILD SUCCESS. Existing normalized-login and success/logout flow remained green with the throttle filter installed.
```

## External-test boundaries

This unit evidence does not prove a multi-node throttle (explicitly unsupported by `SEC-007`) or a production proxy's
network topology. The filter uses the server request address and profile-controlled forwarded-header handling; manual
account locks remain persisted separately.
