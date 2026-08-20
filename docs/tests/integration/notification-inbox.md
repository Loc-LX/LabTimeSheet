# Test Evidence: Recipient-scoped notification inbox

- **Test type:** Integration
- **Requirement IDs:** `NOT-009`
- **Iteration deliverable:** `I2-UI-PLAT-01` (recipient-scoped notification inbox producer)
- **Scenario IDs:** N/A — the requirements catalogue has no dedicated acceptance scenario for the NOT-009 inbox boundary; executable evidence follows the general TDD rule in `AC-TST-001`.
- **Test class/methods:** `com.lab.labtimesheet.feature.notification.service.NotificationInboxIntegrationTest#listsOnlyTheAuthenticatedRecipientAndMarksOwnRowsReadIdempotently`; `#concurrentOwnMarkReadRequestsAreIdempotent`
- **Implementation commit:** `47fe16dbdee1980e420fa73b52becc8eb20f8eb1`

## Protected behavior

The Platform notification service exposes an immutable list and unread count for the already-authenticated recipient
ID supplied by the consumer. Repository reads are recipient-scoped and newest-first. Mark-read finds a row only when
the same recipient owns it; missing or foreign IDs are nondisclosing no-ops, and repeating an own mark-read preserves
the first server timestamp. A recipient-qualified pessimistic write lock serializes concurrent first-time mark-read
requests so both duplicate requests succeed while the first `read_at` remains authoritative. The DTO boundary carries
only in-app content and safe action metadata, never recipient email or ordinary-email delivery/retry/internal fields.

## Test method

The PostgreSQL 18.4 Testcontainers test creates an active Admin recipient A and active Mentor recipient B through the
public concrete BootstrapService/AccountService APIs, activating B through a deterministic test SMTP probe and passing
only immutable AccountIdentity values into the notification fixture. It publishes two in-app-only rows to A and one to B through the real mandatory-transaction publication service, and reads both
inboxes through the real service. It asserts exact newest-first IDs/titles and unread counts, immutable record/list
shape, and no delivery metadata. It then marks A's newest row, repeats the operation, and attempts to mark B's row and
a missing identifier as A; direct PostgreSQL assertions prove only A's first row changed. The second method builds
two real Spring transactions around the same service and uses only a test-side repository proxy to release both
requests before the recipient-qualified lookup; direct PostgreSQL locking then proves both requests complete and the
first committed `read_at` is retained.

## Hand-derived expected result

Recipient A sees exactly its two rows in descending creation/ID order with unread count 2. Recipient B sees exactly
its one row and no A row. A's first mark-read changes its unread count to 1; the repeated mark-read leaves the original
`read_at` unchanged. A's foreign and missing IDs do not change either recipient's state and reveal no row details. Two
simultaneous own mark-read requests both commit successfully; one changes `read_at`, and the other observes that
already-read state without an optimistic-lock error.

## RED

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -Dtest=NotificationInboxIntegrationTest test
```

**Observed result**

```text
2026-08-21T03:01:23+07:00 — BUILD FAILURE during testCompile; NotificationInboxIntegrationTest could not find
symbols NotificationInbox and NotificationInboxItem. No container or behavioral execution occurred. This was the
expected missing recipient-scoped DTO boundary.
```

**Concurrency RED replay (pre-fix inverse)**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -Dtest=NotificationInboxIntegrationTest test
```

```text
2026-08-21T03:19:08+07:00 — temporary test replay removed only @Lock(LockModeType.PESSIMISTIC_WRITE)
from NotificationRepository#findByIdAndRecipientUserId. PostgreSQL 18.4 Testcontainers ran both real
markRead transactions through the test-side barrier; Tests run: 2, Failures: 0, Errors: 1, Skipped: 0.
The second commit raised ObjectOptimisticLockingFailureException, proving the concurrent idempotency
behavior was missing rather than exposing a fixture or environment failure. The production annotation was
restored immediately after the replay.
```

**Sequential idempotency RED replay (pre-fix inverse)**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -Dtest=NotificationInboxIntegrationTest test
```

```text
2026-08-21T03:38:20+07:00 — temporary replay removed only the readAt != null guard from
NotificationEntity#markRead. PostgreSQL 18.4 Testcontainers ran 2 tests with 1 failure: the sequential
test expected 2026-08-21T07:00:01.000 but observed 2026-08-21T07:00:02.000 after the test-local clock
advanced. This proves the new regression detects a repeated timestamp overwrite; the production guard was
restored immediately after the replay.
```

## GREEN

**Command**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -Dtest=NotificationInboxIntegrationTest test
```

**Observed result**

```text
PostgreSQL 18.4 Testcontainers; Tests run: 2, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS at
2026-08-21T03:49:00+07:00. After the review repair moved account setup to public BootstrapService/AccountService
boundaries, both service methods completed; the sequential test advanced its test-local clock between calls and
retained the first committed read_at, while the concurrent test observed the locked, already-read row.
```

## Affected suite

**Command and result**

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -Dtest='**/feature/account/**/*Test,**/feature/integration/**/*Test,**/feature/notification/**/*Test' test
```

```text
PostgreSQL 18.4 Testcontainers; aggregate Surefire Tests run: 76, Failures: 0, Errors: 0, Skipped: 0;
BUILD SUCCESS at 2026-08-21T03:52:36+07:00. Notification tests include 12/12. This rerun includes the review
repair that provisions active Admin/Mentor accounts through public BootstrapService/AccountService APIs; no
foreign Account entity/repository is imported by the inbox fixture. The focused 2/2 GREEN completed at
2026-08-21T03:49:00+07:00.
```

## Branch verification

```text
Full branch suite: JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q test — PostgreSQL 18.4, aggregate Surefire Tests run: 269, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS (completed 03:30:45+07:00; this preceded the final test-only clock-strengthening, with production source unchanged).
Architecture/Flyway: JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/opt/homebrew/opt/openjdk@25/bin:$PATH DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock ./mvnw -q -Dtest=LayerStructureTest,PlatformFoundationTest,AttendanceLayerStructureTest,ReportingArchitectureTest,ProjectPersistenceStructureTest,TaskPersistenceStructureTest test — PostgreSQL 18.4, 12/12, BUILD SUCCESS.
Compile: JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -q -DskipTests compile — BUILD SUCCESS at
2026-08-21T03:54:02+07:00 after the review fixture repair.
Forced-current Javadoc: JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/node@24/bin:/opt/homebrew/opt/openjdk@25/bin:$PATH ./mvnw -q -DskipTests -Ddoclint=all javadoc:javadoc — BUILD SUCCESS, no doclint errors at
2026-08-21T03:54:04+07:00 after the review fixture repair.
Diff: git diff --check — clean; the untracked notification paths were also checked for trailing whitespace.
```

## External-test boundaries

This evidence proves only the Platform service/repository/entity inbox boundary and its PostgreSQL persistence state.
It does not prove authenticated principal extraction, controller authorization/advice, notification menu/list browser
rendering, pagination, email delivery/retry, or consumer-domain transaction wiring. The caller must authenticate and
authorize the recipient ID before calling this concrete service; the service does not look up Account persistence. The
test fixture uses only public BootstrapService/AccountService APIs and imports no foreign Account repository/entity.
The test-side proxy/barrier is not production scaffolding. No migration, dependency, configuration, interface, or UI
change is part of this slice.
