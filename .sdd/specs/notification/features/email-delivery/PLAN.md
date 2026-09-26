# Email delivery Plan

**Owner:** Loc-LX · technical design for the [Email delivery SPEC](SPEC.md). Progress belongs in [`plan.md`](../../../../../plan.md); tasks are in [TASKS.md](TASKS.md). This plan inherits the boundaries and shared constraints of [`notification/MODULE.md`](../../MODULE.md). Where this plan and a SPEC disagree, the SPEC wins.

| Part | Subject | Rules | State |
|---|---|---|---|
| ED | Ordinary notification email delivery, retry, secret-link separation, and the notification schema contract | `NOT-002`, `NOT-004`–`NOT-008`, `NOT-012`; `AC-NOT-001`–`AC-NOT-004`, `AC-NOT-007`; ED-01 | Drafted on 27 September 2026 against current code; approved by the maintainer the same day after independent review |

## Part ED — Email delivery

This plan covers the notification-owned delivery behavior in the SPEC. SMTP configuration remains platform-owned; identity continues to own activation and password-reset token handling. Data-model C-07 is the notification contract step from the [Data model plan](../../../platform/features/data-model/PLAN.md#c4-contract-migrations-and-the-module-that-ships-each). Code work follows the part C integration branch `work/fix/architecture/schema-contracts`.

### What the current code offers

This is a rule-by-rule inventory at the draft's starting revision. “Implemented, no test proves it” means the code appears to provide the behavior but the inspected tests do not prove it; it is not a pass claim.

| Rule | Current implementation and evidence | Planned outcome |
|---|---|---|
| `NOT-002` | `NotificationType.java:11-25` designates leave, correction, membership/leadership, Project invitation, membership exit, and Task assignment/reassignment event families for email; `NotificationService.java:105-147` persists notifications and dispatches designated mail. `NotificationServiceIntegrationTest.java:265-315` exercises invitation, exit, leave, and correction families. The attendance-exception feature is not built yet, and the Attendance exception, Leave, and Missed-checkout correction plans have not added their overdue notification publishers. Existing service tests and domain integration tests cover only their named cases. | Keep existing event types and recipients in notification ownership. Close the NOT-002 event families whose producing feature exists, and prove each under `AC-NOT-004`. Attendance-exception events and overdue notices are published by the Attendance exception, Leave and Missed-checkout correction plans, with the recipients `NOT-011` defines; this plan does not add them. |
| `NOT-004` | `NotificationService.java:120-146` chooses `UNAVAILABLE` with SMTP absent, stores the in-app row, and sends after commit when SMTP is active. `NotificationServiceIntegrationTest.java:79-120` proves absent and transient failure do not suppress the in-app record or domain transaction for its exercised events. | Preserve atomic domain/in-app behavior and add integrated proof for each event family closed under `NOT-002`, including `AC-NOT-001` and `AC-NOT-004`. |
| `NOT-005` | `NotificationServiceIntegrationTest.java:79-99` proves an `UNAVAILABLE` invitation row is not retroactively sent after SMTP becomes available; `NotificationRepository.java:69-79` selects due `PENDING` rows only. | Preserve no-replay behavior and verify it alongside the designated domain workflows in `AC-NOT-001` and `AC-NOT-004`. |
| `NOT-006` | `NotificationDeliveryScheduler.java:16-19` schedules retry scans; `NotificationService.java:326-352` applies retry state and delays. `NotificationServiceIntegrationTest.java:124-168` proves the five configured delays, six total attempts, terminal `FAILED`, and manual retry path. | Preserve the bounded schedule and prove its terminal and resumed states through `AC-NOT-002` and `AC-NOT-007`. |
| `NOT-007` | `NotificationService.java:227-251` provides failed-email views and an Admin-checked manual retry that reuses the row; tests at `NotificationServiceIntegrationTest.java:124-168` exercise retry without a duplicate row. `NotificationController.java:25-62` exposes only the inbox; no Admin failed-email inspection/retry route or view is present. | Add the Admin inspection and manual-retry browser flow using notification-owned service operations; keep authorization rules unchanged and prove no duplicate in-app row under `AC-NOT-002` and `AC-NOT-007`. |
| `NOT-008` | `AccountService.java:86-150,194-217,642-702` sends activation/reset links on the identity-owned path and invalidates tokens on failed delivery. `AccountActivationIntegrationTest.java:75-126` and `PasswordResetIntegrationTest.java:176-189,246-256` cover hashed token storage and failure invalidation. `NotificationActionContractTest.java:12-21` rejects token-bearing action URLs. | Preserve identity ownership and explicit regeneration. Extend `AC-NOT-003` evidence to establish that raw activation/reset links are absent from ordinary notification persistence and logs, and that failed delivery invalidates the token. |
| `NOT-012` | `NotificationEmailStatus.java:4-10` defines the five states. `NotificationService.java:120-146,326-352` implements initial and retry transitions. `V1__baseline.sql:1068-1081` defines status, payload, sent-time, and next-attempt checks. The existing `ck_notifications_email_payload` predicate at `V1__baseline.sql:1072-1075` exempts `NOT_REQUIRED` and `UNAVAILABLE` from requiring payload but does not forbid payload for those states. No test proving this database predicate was found. `D38` settled the five states, invariants, and permitted transitions. | ED-01 carries data-model C-07 and tightens that existing named predicate in V6 after the read-only stored-row check; its PostgreSQL probe proves both permitted payload-free shapes and rejected payload-bearing shapes. Cover the full state matrix and terminal transitions in `AC-NOT-007`. |

The notification module has no notification-specific database acceptance scenario in `notification/MODULE.md`. ED-01 carries data-model C-07 and therefore adds the PostgreSQL/Testcontainers probe required by Data model C.7 for the `NOT-012` predicate; no other schema object is in this plan.

### Order of work

Tasks ED-01 to ED-05 are in [TASKS.md](TASKS.md). Each behavior task starts from its named acceptance scenario, adds the smallest PostgreSQL/Testcontainers test, runs it RED for the intended reason, then runs GitNexus impact before editing each production symbol. `UNKNOWN` requires text-search confirmation; report HIGH or CRITICAL before changing the symbol. Tests name their requirement IDs in Javadoc, and changed public/protected members receive Javadoc in the same step.

1. **ED-01 carries data-model C-07:** identify the target database by Flyway history and read the stored notification rows described in Data model C.2/C.5. Use a read-only query that returns the row id, status, and null/non-null flags for each email payload column where a `NOT_REQUIRED` or `UNAVAILABLE` row has any payload; record per-status counts in root `plan.md`, without copying payload contents. If a database has the conflicting `V2`, stop that database pending a maintainer decision. If the read finds any violating row, report the rows/counts to the maintainer and stop before adding the constraint or migration. Do not edit stored data. Write the PostgreSQL/Testcontainers probe and observe it fail against the current predicate, then add the one V6 migration replacing the existing `ck_notifications_email_payload` predicate. V5 is reserved for Project cancellation. Update the physical data-model diagram in the same change if its documented constraint listing includes this predicate.

   C-07 ships with no application code unless the stored-row check finds a row that breaks the predicate; those rows are reported before the migration.

2. **ED-02:** close the missing `NOT-002` event coverage for event families whose producing feature exists, prioritizing the cases the demo needs. Attendance-exception events and overdue notices are out of this part (see Not in this part).
3. **ED-03:** add the Admin failed-email inspection and manual-retry browser flow for `NOT-007`, reusing the existing service and preserving its row-level retry behavior.
4. **ED-04:** close the integrated evidence gaps for SMTP failure/no replay, secret-link separation, and the delivery-state matrix without broadening the notification contract.
5. **ED-05:** close this plan after all listed notification scenarios and the data-model C-07 probe pass.

### Tests

Use real Spring components and PostgreSQL through Testcontainers for database and delivery behavior; fake only the SMTP boundary. Never use H2 for PostgreSQL constraints. Every task runs its focused test RED before production changes and GREEN afterward, followed by the relevant integration/web suite. Keep the existing test rule traces and add Javadoc naming the rules protected by each new scenario.

- `AC-NOT-001` and `AC-NOT-004`: domain action and in-app notification survive absent SMTP; designated delivery is `UNAVAILABLE`; later SMTP activation sends no retroactive email; invitation and membership-exit transitions retain their existing recipient and deduplication behavior.
- `AC-NOT-002`: exact five retry delays, six total attempts, terminal `FAILED`, and manual retry without duplicate in-app notification.
- `AC-NOT-003`: failed activation/reset delivery invalidates the token; raw link is absent from `notifications` and logs; only explicit regeneration creates a new usable link.
- `AC-NOT-007`: PostgreSQL state/payload matrix, permitted and refused transitions, timestamps, bounded retry, `UNAVAILABLE` non-replay, and Admin retry without changing the in-app record.
- ED-01's data-model C-07 PostgreSQL/Testcontainers probe fails before V6 for payload-bearing `NOT_REQUIRED` and `UNAVAILABLE` rows, then passes after V6; its lawful-row matrix verifies no payload is accepted for both states and payload remains required by the current rule for other email-delivery states.
- Run the full Maven suite, `npm run test:ui`, and the end-to-end suite after the affected behavior changes. Run `git diff --check` and GitNexus change analysis before proposing a commit.

### When done

The plan is complete when `AC-NOT-001`, `AC-NOT-002`, `AC-NOT-003`, `AC-NOT-004`, and `AC-NOT-007` pass; the data-model C-07 probe passes against V6; the full Maven and UI suites pass; and the end-to-end suite covers the Admin retry flow and the SMTP-absent invitation/exit flow. Data-model C-07's stored-row counts and Flyway history are recorded in `plan.md`; no historical notification rows are changed by this plan.

### Risks

| Risk | Handling |
|---|---|
| Historical `NOT_REQUIRED` or `UNAVAILABLE` row contains email payload | Read and count rows before migration. Report any violating rows to the maintainer and stop data-model C-07 before migration; no cleanup or reclassification is specified. |
| Target database carries the other `V2` history | Identify each database using Flyway history first and stop that database pending a maintainer decision, as Data model C.2 requires. |
| Admin retry exposes delivery content or bypasses authorization | Use the existing service authorization and permission matrix; cover the rendered view and refused requests in the web tests. |
| Tightening V1's existing payload predicate is mistaken for adding a duplicate constraint | V6 replaces the existing named predicate; it does not create a second constraint with the same name. |

### Not in this part

- SMTP configuration, transport revisions, and their authorization remain in the platform SMTP-configuration feature.
- Activation and password-reset token creation, invalidation, and regeneration remain in identity; this plan verifies only the boundary required by `NOT-008`.
- Attendance-exception notifications and overdue notices for leave, corrections and exceptions. `NOT-011` defines their recipients; the attendance-exception feature is not built yet (`D14`), so the Attendance exception, Leave and Missed-checkout correction plans publish them through the notification module's existing publish operation.
- No email behavior beyond `NOT-002`, `NOT-004`–`NOT-008`, and `NOT-012` is added. No stored-row cleanup policy is specified. ED-01 does not change application behavior when the stored-row check is clear.
