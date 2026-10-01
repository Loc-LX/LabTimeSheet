# Email delivery Plan

**Owner:** Loc-LX · technical design for the [Email delivery SPEC](SPEC.md). Progress belongs in [`plan.md`](../../../../../plan.md); tasks are in [TASKS.md](TASKS.md). This plan inherits the boundaries and shared constraints of [`notification/MODULE.md`](../../MODULE.md). Where this plan and a SPEC disagree, the SPEC wins.

| Part | Subject | Rules | State |
|---|---|---|---|
| ED | Ordinary notification email delivery, retry, secret-link separation, and the notification schema contract | `NOT-002`, `NOT-004`–`NOT-008`, `NOT-012`, `NOT-013`; `AC-NOT-001`–`AC-NOT-004`, `AC-NOT-007`, `AC-NOT-008`, `AC-PRJ-017`; ED-01 | Drafted on 27 September 2026 against current code; approved by the maintainer the same day after independent review; ED-01 to ED-05 implemented on 29 September 2026 on the local branch `work/fix/notification/email-payload-contract`, not yet merged; ED-06 and ED-07 drafted on 30 September 2026 against current code, awaiting review. |

## Part ED — Email delivery

This plan covers the notification-owned delivery behavior in the SPEC. SMTP configuration remains platform-owned; identity continues to own activation and password-reset token handling. Data-model C-07 is the notification contract step from the [Data model plan](../../../platform/features/data-model/PLAN.md#c4-contract-migrations-and-the-module-that-ships-each). Code work follows the part C integration branch `work/fix/architecture/schema-contracts`.

### What the current code offers

This is a rule-by-rule inventory at the draft's starting revision. “Implemented, no test proves it” means the code appears to provide the behavior but the inspected tests do not prove it; it is not a pass claim.

| Rule | Current implementation and evidence | Planned outcome |
|---|---|---|
| `NOT-002` | `NotificationType.java:11-25` designates leave, correction, membership/leadership, Project invitation, membership exit, and Task assignment/reassignment event families for email; `NotificationService.java:105-147` persists notifications and dispatches designated mail. `NotificationServiceIntegrationTest.java:265-315` exercises invitation, exit, leave, and correction families. The attendance-exception feature is not built yet, and the Attendance exception, Leave, and Missed-checkout correction plans have not added their overdue notification publishers. Existing service tests and domain integration tests cover only their named cases. | Keep existing event types and recipients in notification ownership. Close the NOT-002 event families whose producing feature exists, and prove each under `AC-NOT-004`. Attendance-exception events and overdue notices are published by the Attendance exception, Leave and Missed-checkout correction plans, with the recipients `NOT-011` defines; this plan does not add them. |
| `NOT-004` | `NotificationService.java:120-146` chooses `UNAVAILABLE` with SMTP absent, stores the in-app row, and sends after commit when SMTP is active. `NotificationServiceIntegrationTest.java:79-120` proves absent and transient failure do not suppress the in-app record or domain transaction for its exercised events. | Preserve atomic domain/in-app behavior and add integrated proof for each event family closed under `NOT-002`, including `AC-NOT-001` and `AC-NOT-004`. |
| `NOT-005` | `NotificationServiceIntegrationTest.java:79-99` proves an `UNAVAILABLE` invitation row is not retroactively sent after SMTP becomes available; `NotificationRepository.java:69-79` selects due `PENDING` rows only. | Preserve no-replay behavior and verify it alongside the designated domain workflows in `AC-NOT-001` and `AC-NOT-004`. |
| `NOT-006` | `NotificationDeliveryScheduler.java:16-19` schedules retry scans; `NotificationService.java:301-306` dispatches delivery synchronously on the request thread in `afterCommit`, holding a database row lock via `findForUpdateById` (`NotificationService.java:313-318`) across SMTP execution; `NotificationService.java:326-352` applies retry state and delays. `NotificationServiceIntegrationTest.java:124-168` proves the five configured delays, six total attempts, terminal `FAILED`, and manual retry path. | Preserve the bounded schedule and prove its terminal and resumed states through `AC-NOT-002` and `AC-NOT-007`. Migrate dispatch to a dedicated background bounded queue and worker pool without holding database locks during SMTP transmission, keeping SMTP out of the request thread and response per `D49` and `AC-NOT-008`. |
| `NOT-007` | `NotificationService.java:227-251` provides failed-email views and an Admin-checked manual retry that reuses the row; tests at `NotificationServiceIntegrationTest.java:124-168` exercise retry without a duplicate row. `NotificationController.java:25-62` exposes only the inbox; no Admin failed-email inspection/retry route or view is present. | Add the Admin inspection and manual-retry browser flow using notification-owned service operations; keep authorization rules unchanged and prove no duplicate in-app row under `AC-NOT-002` and `AC-NOT-007`. |
| `NOT-008` | `AccountService.java:86-150,194-217,642-702` sends activation/reset links on the identity-owned path and invalidates tokens on failed delivery. `AccountActivationIntegrationTest.java:75-126` and `PasswordResetIntegrationTest.java:176-189,246-256` cover hashed token storage and failure invalidation. `NotificationActionContractTest.java:12-21` rejects token-bearing action URLs. | Preserve identity ownership and explicit regeneration. Extend `AC-NOT-003` evidence to establish that raw activation/reset links are absent from ordinary notification persistence and logs, and that failed delivery invalidates the token. |
| `NOT-012` | `NotificationEmailStatus.java:4-10` defines the five states. `NotificationService.java:120-146,326-352` implements initial and retry transitions. `V1__baseline.sql:1068-1081` defines status, payload, sent-time, and next-attempt checks. The existing `ck_notifications_email_payload` predicate at `V1__baseline.sql:1072-1075` exempts `NOT_REQUIRED` and `UNAVAILABLE` from requiring payload but does not forbid payload for those states. No test proving this database predicate was found. `D38` settled the five states, invariants, and permitted transitions. | ED-01 carries data-model C-07 and tightens that existing named predicate in one migration, numbered at implementation, after the read-only stored-row check; its PostgreSQL probe proves both permitted payload-free shapes and rejected payload-bearing shapes. Cover the full state matrix and terminal transitions in `AC-NOT-007`. |
| `NOT-013` | `ProjectService.java:1834-1844` (`publishMembershipChanged`) and `1878-1888` (`publishLeadershipChanged`) deliver notifications to all supplied recipients without excluding the initiating actor; `ProjectService.java:528` erroneously sends `MEMBERSHIP_CHANGED` to the accepting intern (`actorUserId`) upon invitation acceptance. | Exclude the initiating actor from `MEMBERSHIP_CHANGED` and `LEADERSHIP_CHANGED` recipient lists in `ProjectService` per `D50` and `AC-PRJ-017`; suppress publication when the recipient list becomes empty after filtering. |

The notification module has no notification-specific database acceptance scenario in `notification/MODULE.md`. ED-01 carries data-model C-07 and therefore adds the PostgreSQL/Testcontainers probe required by Data model C.7 for the `NOT-012` predicate; no other schema object is in this plan.

### Order of work

Tasks ED-01 to ED-07 are in [TASKS.md](TASKS.md). Each behavior task starts from its named acceptance scenario, adds the smallest PostgreSQL/Testcontainers test, runs it RED for the intended reason, then runs GitNexus impact before editing each production symbol. `UNKNOWN` requires text-search confirmation; report HIGH or CRITICAL before changing the symbol. Tests name their requirement IDs in Javadoc, and changed public/protected members receive Javadoc in the same step.

1. **ED-01 carries data-model C-07:** identify the target database by Flyway history and read the stored notification rows described in Data model C.2/C.5. Use a read-only query that returns the row id, status, and null/non-null flags for each email payload column where a `NOT_REQUIRED` or `UNAVAILABLE` row has any payload; record per-status counts in root `plan.md`, without copying payload contents. If a database has the conflicting `V2`, stop that database pending a maintainer decision. If the read finds any violating row, report the rows/counts to the maintainer and stop before adding the constraint or migration. Do not edit stored data. Write the PostgreSQL/Testcontainers probe and observe it fail against the current predicate, then add one migration, numbered at implementation, replacing the existing `ck_notifications_email_payload` predicate. Update the physical data-model diagram in the same change if its documented constraint listing includes this predicate.

   C-07 ships with no application code unless the stored-row check finds a row that breaks the predicate; those rows are reported before the migration.

2. **ED-02:** close the missing `NOT-002` event coverage for event families whose producing feature exists, prioritizing the cases the demo needs. Attendance-exception events and overdue notices are out of this part (see Not in this part).
3. **ED-03:** add the Admin failed-email inspection and manual-retry browser flow for `NOT-007`, reusing the existing service and preserving its row-level retry behavior.
4. **ED-04:** close the integrated evidence gaps for SMTP failure/no replay, secret-link separation, and the delivery-state matrix without broadening the notification contract.
5. **ED-05:** close this plan after all listed notification scenarios and the data-model C-07 probe pass.
6. **ED-06:** asynchronous email handoff, 5-minute lease against duplicate delivery, crash-recovery retry, and adaptation of existing synchronous tests per `D49` and `AC-NOT-008`. Code implementation follows after the 2 October 2026 demo.
7. **ED-07:** exclude initiating actor from `MEMBERSHIP_CHANGED` and `LEADERSHIP_CHANGED` per `D50` and `AC-PRJ-017`. Code implementation follows after the 2 October 2026 demo.

### Design for ED-06 and ED-07

The design for `D49` (`NOT-006`, `AC-NOT-008`) and `D50` (`NOT-013`, `AC-PRJ-017`) resolves asynchronous delivery guarantees and recipient filtering:

1. **Asynchronous dispatch:**
   Post-commit in `NotificationService`'s `afterCommit` synchronization, the request thread enqueues the persisted notification identifier into a dedicated, bounded `ThreadPoolTaskExecutor` (e.g., 2 worker threads, queue capacity 100). The request thread never invokes SMTP or network I/O, ensuring HTTP response latency is independent of mail delivery. If the queue is saturated, the rejection policy logs a `WARN` and does not throw an exception into the request or rollback domain transactions; the row remains in `PENDING` state in the database for the background scheduler to sweep.

2. **Scheduler and due instant:**
   The existing `NotificationDeliveryScheduler.java:16-19` sweeps due retries once per minute (`fixedDelay = 60_000L`). When a notification is created with SMTP available, `emailNextAttemptAt` is set to `now` (`NotificationService.java:122, 137`), making newly persisted `PENDING` rows immediately due (`NotificationRepository.java:70-79`). The scheduler sweeps due rows as normal without requiring special bootstrap flags.

3. **Double-send prevention (5-minute lease):**
   To prevent duplicate delivery when both the async worker and the 60-second scheduler attempt the same row, delivery attempts acquire a 5-minute lease via a conditional update executed in a short, isolated transaction before SMTP interaction:
   ```sql
   UPDATE notifications
   SET email_next_attempt_at = :leaseExpiresAt
   WHERE id = :id
     AND email_status = 'PENDING'
     AND email_next_attempt_at <= :now
   ```
   Only the execution thread where the update row count is exactly 1 proceeds to send the email via SMTP. No database lock is held across the SMTP network call. After transmission completes, a subsequent transaction updates the row to `SENT` on success (`NotificationEntity.markSent`), or advances the retry count and computes the next delay of the `NOT-006` schedule on failure (`NotificationEntity.retainPendingRetry`).

4. **Network timeouts:**
   SMTP connect, read, and write timeouts must be strictly shorter than the 5-minute lease duration so that a slow or hanging mail server cannot outlast the lease window. `MailDeliveryService` sends through `JavaMailSmtpProbe`, which already sets the connect, read and write timeouts to 5000 ms (`JavaMailSmtpProbe.java:23`, `50-52`), so every SMTP wait stays well within the lease.

5. **Crash recovery:**
   If the application or server crashes while an email is being transmitted, the database row remains in `PENDING` status with `email_next_attempt_at` set to the lease expiry (lease time + 5 minutes). Upon application restart, once the lease duration expires, `NotificationDeliveryScheduler` sweeps the row and retries delivery, satisfying the "and on restart" clause of `NOT-006`.

6. **Impact on existing tests:**
   Existing integration tests that assert on mail delivery or `mail.calls` immediately following domain operations relied on the previous synchronous `deliver()` call on the request thread. These tests must be adapted to wait asynchronously using a test-local polling helper (with a timeout, e.g., 2–5 seconds) without introducing external third-party dependencies (such as Awaitility) or weakening assertions. Affected test files identified:
   - `NotificationServiceIntegrationTest.java` (immediate assertions on `mail.calls`, `statusFor`, `attemptsFor`, and delivered emails)
   - `ProjectInvitationExitIntegrationTest.java` (invitation acceptance and member exit delivery checks)
   - `AttendancePersistenceIntegrationTest.java` (event persistence and notification delivery checks)

   `AccountActivationIntegrationTest`, `AccountIdentityCorrectionIntegrationTest` and `AccountWebIntegrationTest` also assert on mail, but they send secret-link mail directly under `NOT-008` and are unaffected.

7. **Initiator exclusion in ED-07:**
   In `ProjectService.java:1834-1844` (`publishMembershipChanged`) and `1878-1888` (`publishLeadershipChanged`), filter out `actorUserId` from the recipient list. If the filtered recipient list is empty, notification publication is skipped entirely. In particular, this eliminates the self-notification bug in `respondToInvitation` (`ProjectService.java:528`), where an intern accepting an invitation is sent a `MEMBERSHIP_CHANGED` notification about their own action.

8. **Implementation timing:**
   Implementation of production code and tests for both ED-06 and ED-07 will take place after the 2 October 2026 demo.

### Tests

Use real Spring components and PostgreSQL through Testcontainers for database and delivery behavior; fake only the SMTP boundary. Never use H2 for PostgreSQL constraints. Every task runs its focused test RED before production changes and GREEN afterward, followed by the relevant integration/web suite. Keep the existing test rule traces and add Javadoc naming the rules protected by each new scenario.

- `AC-NOT-001` and `AC-NOT-004`: domain action and in-app notification survive absent SMTP; designated delivery is `UNAVAILABLE`; later SMTP activation sends no retroactive email; invitation and membership-exit transitions retain their existing recipient and deduplication behavior.
- `AC-NOT-002`: exact five retry delays, six total attempts, terminal `FAILED`, and manual retry without duplicate in-app notification.
- `AC-NOT-003`: failed activation/reset delivery invalidates the token; raw link is absent from `notifications` and logs; only explicit regeneration creates a new usable link.
- `AC-NOT-007`: PostgreSQL state/payload matrix, permitted and refused transitions, timestamps, bounded retry, `UNAVAILABLE` non-replay, and Admin retry without changing the in-app record.
- `AC-NOT-008`: MockMvc test triggers Complete Project with fake SMTP holding messages via a `CountDownLatch`. Verify the HTTP response returns while emails remain `PENDING`. Release the latch and verify the emails transition to `SENT` with initial attempt timestamp within 1 minute of commit.
- Concurrency contention test: Two concurrent workers attempt to deliver the same due notification row simultaneously. Verify conditional lease update guarantees exactly one worker sends the email and the other aborts without duplicate transmission.
- `AC-PRJ-017`: Integration tests across Project lifecycle transitions (Create Project, member addition, invitation acceptance, leader replacement, member exit/removal, project completion, project cancellation) verify exact recipients; the initiating actor receives neither in-app nor email notification.
- ED-01's data-model C-07 PostgreSQL/Testcontainers probe fails before that migration for payload-bearing `NOT_REQUIRED` and `UNAVAILABLE` rows, then passes after it; its lawful-row matrix verifies no payload is accepted for both states and payload remains required by the current rule for other email-delivery states.
- Run the full Maven suite, `npm run test:ui`, and the end-to-end suite after the affected behavior changes. Run `git diff --check` and GitNexus change analysis before proposing a commit.

### When done

The plan is complete when `AC-NOT-001`, `AC-NOT-002`, `AC-NOT-003`, `AC-NOT-004`, and `AC-NOT-007` pass; the data-model C-07 probe passes against that migration; the full Maven and UI suites pass; and the end-to-end suite covers the Admin retry flow and the SMTP-absent invitation/exit flow. Data-model C-07's stored-row counts and Flyway history are recorded in `plan.md`; no historical notification rows are changed by this plan.

### Risks

| Risk | Handling |
|---|---|
| Historical `NOT_REQUIRED` or `UNAVAILABLE` row contains email payload | Read and count rows before migration. Report any violating rows to the maintainer and stop data-model C-07 before migration; no cleanup or reclassification is specified. |
| Target database carries the other `V2` history | Identify each database using Flyway history first and stop that database pending a maintainer decision, as Data model C.2 requires. |
| Admin retry exposes delivery content or bypasses authorization | Use the existing service authorization and permission matrix; cover the rendered view and refused requests in the web tests. |
| Tightening V1's existing payload predicate is mistaken for adding a duplicate constraint | The migration replaces the existing named predicate; it does not create a second constraint with the same name. |
| Duplicate email sending during concurrent worker dispatch or scheduler overlap | Handled by a 5-minute lease via conditional database update (`UPDATE notifications SET email_next_attempt_at = :leaseExpiresAt WHERE id = :id AND email_status = 'PENDING' AND email_next_attempt_at <= :now`); only count == 1 sends. |
| Existing integration tests fail due to asynchronous dispatch | Handled by introducing a test-local polling wait helper with timeout in test classes, keeping strict assertions without adding new test dependencies. |
| Executor queue overflow or JVM shutdown drops asynchronous tasks | Handled by database persistence: notifications are committed as `PENDING` before worker handoff; the 60-second `NotificationDeliveryScheduler` sweeps unhandled or crashed rows on restart. |
| Modifying `ProjectService` helper methods affects other features or cross-module callers | Run GitNexus impact analysis on `publishMembershipChanged`, `publishLeadershipChanged`, and `notifyMembershipChanged` before editing, reporting HIGH or CRITICAL risk to the maintainer. |

### Not in this part

- SMTP configuration, transport revisions, and their authorization remain in the platform SMTP-configuration feature.
- Activation and password-reset token creation, invalidation, and regeneration remain in identity; this plan verifies only the boundary required by `NOT-008`.
- Attendance-exception notifications and overdue notices for leave, corrections and exceptions. `NOT-011` defines their recipients; the attendance-exception feature is not built yet (`D14`), so the Attendance exception, Leave and Missed-checkout correction plans publish them through the notification module's existing publish operation.
- No email behavior beyond `NOT-002`, `NOT-004`–`NOT-008`, and `NOT-012` is added. No stored-row cleanup policy is specified. ED-01 does not change application behavior when the stored-row check is clear.
- Parallel email sending beyond the 2-thread worker pool.
- Per-user notification preferences or channel opt-out settings.
- Email digests, batching, or delayed summary notifications.
