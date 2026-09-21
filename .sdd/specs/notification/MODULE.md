# Notification Module

<a id="notification-spec"></a>

**Version:** 1.4.0 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-21

Part of the Lab Timesheet specification. Rules every feature shares, including the
glossary, the authorization model, the domain model, and failure handling, are in
[the platform spec](../platform/MODULE.md). Section numbers marked `§` are one
numbering shared by all the specs, so a reference such as §5.2 names the same section
wherever it appears.

## 1. Context & Goal

In-app notifications and the email delivered alongside them, with retry.
In code this is `feature/notification`.


### Feature index

Read this shared contract together with the relevant feature SPEC. Rules are canonical
in exactly one of these documents; references do not create copies. Cross-feature use
cases, shared data constraints and unresolved decisions remain here (`D34`).

| Feature | Operations |
|---|---|
| [Notification inbox](features/inbox/SPEC.md) | Create in-app notification; List, count and mark read |
| [Notification email delivery](features/email-delivery/SPEC.md) | Choose email events and attempt delivery; Retry and inspect failure; Exclude secret links |


## 2. Actors & Roles

Primary actors named by this feature's use cases:

- **UC-11 — Receive and manage notifications:** Authenticated user; notification worker

Every capability by role is in the permission matrix, [platform spec](../platform/MODULE.md) §5.2. Authorization is resolved from stored context, never from the global role alone (§5.1).

## 3. Functional Requirements

### §12. Integrations and notifications

#### §12.2 Notification channels and retry

`NOT-003` and `NOT-010` are in [the project spec](../project/MODULE.md), and `NOT-011` is in [the attendance spec](../attendance/MODULE.md): each chooses its recipients from data those specs own.

Feature contracts: [Notification inbox](features/inbox/SPEC.md), [Notification email delivery](features/email-delivery/SPEC.md).

### Use cases

#### UC-11 — Receive and manage notifications

| Field | Specification |
|---|---|
| Primary actor(s) | Authenticated user; notification worker |
| Trigger | A domain event requires an in-app notification and possibly email. |
| Preconditions | The domain transaction is valid; email eligibility depends on active SMTP. |
| Postconditions | The in-app record remains authoritative for delivery visibility; email cannot roll back the domain action. |
| Traced requirements | NOT-001–NOT-009 |

**Main success flow**

1. Commit the domain action and its in-app notification atomically.
2. Render non-secret email content from the same event context.
3. Attempt email through the active SMTP revision.
4. Retry ordinary email at the configured bounded schedule.
5. Let the recipient read and mark their own in-app notifications.

**Alternatives and exceptions**

- Without SMTP, the domain action still succeeds and email state is UNAVAILABLE.
- Activation/reset emails do not use ordinary queued retry because links are secret and short-lived.
- After the final ordinary retry, email state becomes terminal failure.

## 4. Non-functional Requirements

System-wide non-functional rules apply unchanged: architecture §3 (`ARC`), authentication and security §13 (`SEC`), interface and accessibility §15 (`UI`), delivery §16 (`OPS`), and test evidence §17 (`TST`), all in the [platform spec](../platform/MODULE.md).

## 5. Data

Tables this feature's entities map to: `notifications`.

The conceptual model, the table inventory, the integrity rules (`DB`), and both diagrams are §19 of the [platform spec](../platform/MODULE.md).

## 6. Error Handling

Rules in section 3 whose text names a refusal, rejection, denial, or failure: `NOT-004`, `NOT-006`, `NOT-007`, `NOT-008`.

System-wide failure behavior is §21 (`ERR-001`–`ERR-007`), and the interface message families are Appendix F, both in the [platform spec](../platform/MODULE.md).

## 7. Acceptance Criteria

Scenarios from the §20 acceptance catalogue whose identifiers start with `AC-NOT`. The catalogue's introduction, including the rules deliberately written without a scenario, is in the [platform spec](../platform/MODULE.md).

Feature contracts: [Notification email delivery](features/email-delivery/SPEC.md), [Notification inbox](features/inbox/SPEC.md).

## 8. Out of Scope

System-wide exclusions are §1.3 of the [platform spec](../platform/MODULE.md): `GOV-007` through `GOV-010` and `GOV-015`.

No rule in this spec states an exclusion of its own.

## Notes / Open Questions

The feature split is organizational; read the feature-specific notes as well as the shared
notes below. No question affecting this module is open (`D38`). Should one be recorded here
later, this module and every feature it affects return to the inherited-baseline status.


- Deleting an empty `PLANNED` draft also deletes the notifications raised for it (`PRJ-002`), so no notification is left pointing at a Project that no longer exists. A cancelled Project stays readable, so its notifications keep working links; cancellation notifies closed members under `NOT-002` (`PRJ-023`).
