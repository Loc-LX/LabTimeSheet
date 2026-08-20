# Test Evidence: Notification persistence and ordinary-email delivery boundary

- **Test type:** Integration
- **Requirement IDs:** `NOT-001`–`NOT-005`, `NOT-010`, `AC-NOT-001`, `AC-NOT-004`; producer rows `I2-PLAT-06`, `I2-ATT-PLAT-03`, `I2-PRJ-PLAT-01`
- **Scenario IDs:** deduplicated recipients; absent SMTP; transient SMTP failure; in-app-only events; self-Task silence;
  mandatory caller transaction; rollback/after-commit delivery; safe action-route rejection
- **Test class/method:** `com.lab.labtimesheet.feature.notification.service.NotificationServiceIntegrationTest` and
  `com.lab.labtimesheet.feature.notification.model.dto.NotificationActionContractTest`
- **Implementation commit:** `pending (uncommitted producer patch; base 1f55c3a078494ab88673b27bbf66b09a0c4e1bba)`

## Protected behavior

The Platform-owned notification service requires the consumer's active domain transaction and persists one in-app row
per distinct recipient in that same transaction. It optionally attempts ordinary email only for events designated by
`NotificationType`, and records safe `SENT`, `PENDING`, or `UNAVAILABLE` delivery state. A transient ordinary-email
failure remains `PENDING` with a next-attempt timestamp for the later I3 retry worker; terminal `FAILED`
transition/retry behavior is outside this I2 slice. A caller domain marker and its notification row commit or roll
back together; ordinary-email I/O runs only after commit with transaction resources suspended. An `UNAVAILABLE` event
is retained in-app and is never replayed when SMTP becomes available later. Task comments/status changes remain
in-app-only, and explicit self-Task silence is accepted only for a `TASK_ASSIGNED` event with a validated self-task
transition marker. After commit, each recipient's persistence/send attempt is isolated so one post-commit failure
cannot starve later recipients. Action routes are safe relative application paths only: authority/scheme/control/
backslash forms, activation/reset routes, raw token-shaped links, and encoded sensitive query names are rejected.

## Test method

The PostgreSQL 18.4 Testcontainers integration test bootstraps real accounts, calls the concrete service with
immutable scalar recipient/event/action values, and uses a deterministic test-only `RecordingSmtpProbe`. JDBC
assertions inspect only the Platform-owned `notifications` table: duplicate recipient IDs produce one row, absent
SMTP produces `UNAVAILABLE` without later delivery, transient SMTP failure leaves the row committed as `PENDING`
with a retry timestamp, successful delivery is `SENT`, one failed recipient does not starve a later recipient,
in-app-only events use `NOT_REQUIRED`, and self-Task publication creates no row. A transaction marker proves
outside-transaction rejection and caller rollback/no-mail behavior, while a committed marker proves after-commit
delivery observes no active Spring transaction. The unit contract test covers authority, scheme, controls,
backslashes, recovery routes, raw-token links, and percent-encoded sensitive query names.

## Hand-derived expected result

Each unique recipient receives exactly one in-app row. Publication outside a caller transaction fails before a row
or mail attempt; a domain marker and notification row roll back together when the caller rolls back. A missing active
SMTP revision yields `UNAVAILABLE` and no external call; enabling SMTP afterward cannot change or deliver that row.
A failing probe cannot roll back the saved row and yields `PENDING` with a future retry timestamp. A second
recipient is still attempted after the first recipient's post-commit failure. Designated events send ordinary email
even when the action has no caller-controlled email flag; non-designated events use `NOT_REQUIRED`. Only the exact
Task self-assignment shape is silent, and contradictory self-task markers are rejected.

## RED

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -Dtest=NotificationServiceIntegrationTest test
```

**Observed result**

```text
2026-08-20T23:44:57+07:00 — BUILD FAILURE during testCompile; the production-shaped test could not find the expected
concrete NotificationService boundary. No container or behavioral execution occurred.
```

### RED: corrected transient-failure state

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -Dtest=NotificationServiceIntegrationTest#transientFailureLeavesDomainCommittedAndRetainsPendingRetryState test
```

**Observed result**

```text
2026-08-20T23:56:49+07:00 — BUILD FAILURE at the focused PostgreSQL test assertion: expected PENDING but the
pre-correction implementation persisted FAILED. This was a genuine behavior RED for numbered NOT-004; the
environment and test fixture executed successfully.
```

### RED: notification transaction, action, and caller-suppression regressions

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=NotificationActionContractTest,NotificationServiceIntegrationTest' test
```

**Observed result**

```text
2026-08-21T00:31:41+07:00 — BUILD FAILURE after PostgreSQL 18.4 Testcontainers started; 10 tests executed with
3 failures. The action contract accepted a control-character route, publication outside a transaction did not
reject, and a designated event with the old caller-suppression shape persisted NOT_REQUIRED instead of SENT.
This was a genuine behavior RED, not an environment/setup failure.
```

### RED: control validation before canonicalization

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -Dtest=NotificationActionContractTest test
```

**Observed result**

```text
2026-08-21T00:34:40+07:00 — BUILD FAILURE; 2 tests executed and the contract test still accepted
`/projects/7/\\u0007` because validation occurred after trimming. The production-shaped unit fixture executed
normally; moving control/backslash validation before trimming produced the final action-boundary GREEN.
```

## GREEN

**Initial notification-service GREEN**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -Dtest=NotificationServiceIntegrationTest test

2026-08-21T00:35:42+07:00 — PostgreSQL 18.4 Testcontainers; Tests run: 8, Failures: 0, Errors: 0, Skipped: 0;
BUILD SUCCESS.
```

**Repair-focused GREEN**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=NotificationActionContractTest,NotificationServiceIntegrationTest' test

2026-08-21T00:37:05+07:00 — PostgreSQL 18.4 Testcontainers; Tests run: 10, Failures: 0, Errors: 0, Skipped: 0;
BUILD SUCCESS.
```

## Affected suite

**Command and result**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw '-Dtest=com.lab.labtimesheet.feature.account.**,com.lab.labtimesheet.feature.integration.**,com.lab.labtimesheet.feature.notification.**' test

2026-08-21T00:41:51+07:00 — PostgreSQL 18.4 Testcontainers; Tests run: 73, Failures: 0, Errors: 0, Skipped: 0;
BUILD SUCCESS; total time 03:58.
```

## Full branch and structural verification

**Full PostgreSQL suite**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw test

2026-08-21T00:46:10+07:00 — PostgreSQL 18.4 Testcontainers; Tests run: 266, Failures: 0, Errors: 0, Skipped: 0;
BUILD SUCCESS; total time 04:08.
```

**Architecture/Flyway**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -Dtest=LayerStructureTest,PlatformFoundationTest,AttendanceLayerStructureTest,ReportingArchitectureTest,ProjectPersistenceStructureTest,TaskPersistenceStructureTest test

2026-08-21T00:46:32+07:00 — PostgreSQL 18.4/Flyway; Tests run: 12, Failures: 0, Errors: 0, Skipped: 0;
BUILD SUCCESS.
```

**Compile, forced-current Javadoc, and diff**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -DskipTests compile
2026-08-21T00:46:38+07:00 — BUILD SUCCESS.

JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -DskipTests -Ddoclint=all javadoc:javadoc
2026-08-21T00:46:44+07:00 — BUILD SUCCESS; Javadoc emitted repository-wide warnings but no doclint errors.

git diff --check
2026-08-21T00:53:54+07:00 — no output; clean. An explicit trailing-whitespace scan over the untracked notification
production, test, and evidence paths also produced no output.
```

The forced-current Javadoc command reports repository-wide warnings, including pre-existing warnings outside this
producer and generated Lombok/default-constructor warnings; it completed successfully without doclint errors.

## External-test boundaries

This evidence does not implement or prove Project/Attendance domain mutations, retry scheduling/manual retry or terminal
`FAILED` transition, notification browser/UI rendering or History authorization, recipient authorization in consumers,
or Activation/PasswordReset delivery (those must remain outside the ordinary notification outbox). It proves only the
Platform producer's persisted/failure/deduplication, mandatory caller-transaction, safe-action, self-task, and
after-commit adapter boundaries. The test uses no foreign repository/entity, raw token/link, real external SMTP server,
migration, or new dependency.
