# Test Evidence: normalized email/source-IP login throttling

- **Test type:** Unit
- **Requirement IDs:** `SEC-006`, `SEC-007`
- **Scenario IDs:** `AC-SEC-003`
- **Test class/method:** `com.lab.labtimesheet.feature.account.service.LoginThrottleTest`
- **Implementation commit:** `091361eb73c7d7118d8212df630a83aca4ad5f9e`
- **Review-fix commit:** `bad2764c41bf1694dcfcc7c1407fd66c39eace4f`

## Protected behavior

Login failure state is keyed by trimmed, case-folded email plus the server-observed source IP. Five failures in a
rolling fifteen-minute window throttle the sixth attempt for fifteen minutes; a successful login clears only the
applicable pair, and another source IP remains independent. The in-memory state is bounded for the supported
single-instance deployment and never evicts an active block under sequential or concurrent capacity pressure. When
capacity is full of recent nonblocked histories, one bounded nonblocked history may be evicted so a new target is
tracked and can reach its fifth-failure block.

## Test method

The unit test injects a mutable deterministic clock and exercises the concrete throttle with five failures, distinct
source addresses, exact 15-minute expiry, successful-login clearing, rolling-window expiry, interleaved arbitrary
identifiers, 15,120-key capacity pressure, eight-worker concurrent capacity pressure, and a new-target saturation
regression. It avoids Spring or a database because this boundary is intentionally bounded in-memory state.

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

2026-08-22T15:52:07+07:00 — Round-2 interleaving RED: Tests run: 6, Failures: 1, Errors: 0; interleaved arbitrary
usernames erased the target's four live partial failures before its fifth attempt.

2026-08-22T15:23:19+07:00 — Review-fix GREEN: Tests run: 5, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS.

2026-08-22T15:53:02+07:00 — Round-2 fix GREEN: Tests run: 6, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS.
Capacity purge now runs only at actual capacity and removes only expired, empty states.

2026-08-22T16:31:00+07:00 — Saturated-new-target RED: Tests run: 7, Failures: 1, Errors: 0; 10,000 recent
nonblocked filler histories left the new victim untracked, so its sixth attempt was not blocked.

2026-08-22T16:31:30+07:00 — Saturated-new-target GREEN: Tests run: 7, Failures: 0, Errors: 0, Skipped: 0;
BUILD SUCCESS. One nonblocked state is evicted at capacity while the pre-existing active block remains protected.
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=NotificationServiceIntegrationTest,LoginThrottleTest,ProductionReadinessTest,SecurityResponseIntegrationTest,AuthenticationWebIntegrationTest,CalendarDevelopmentProfileWebIntegrationTest test

2026-08-22T15:22:10+07:00 — prior affected combined Platform security/notification suite; total `25/25`.

2026-08-22T19:52:00+07:00 — affected Account/security PostgreSQL suite `53/53` passed, including LoginThrottleTest
`7/7`; no failures, errors, or skips.
```

## External-test boundaries

This unit evidence does not prove a multi-node throttle (explicitly unsupported by `SEC-007`) or a production proxy's
network topology. The filter uses the server request address and profile-controlled forwarded-header handling; manual
account locks remain persisted separately.
