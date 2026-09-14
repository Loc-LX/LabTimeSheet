# Notification Spec

**Version:** 1.0.0 · **Owner:** Loc-LX · **Status:** APPROVED · **Date:** 2026-09-14

Part of the Lab Timesheet specification. [`.sdd/requirements.md`](../../requirements.md) indexes every
spec, rule prefix, and original section number. Rules every feature shares, including the
glossary, the authorization model, the domain model, and failure handling, are in
[the platform spec](../feature-platform/SPEC.md). Section numbers marked `§` are the
numbers the rules carried in the single-file specification and are kept so existing
references still resolve.

## 1. Context & Goal

In-app notifications and the email delivered alongside them, with retry.
In code this is `feature/notification`.

## 2. Actors & Roles

Primary actors named by this feature's use cases:

- **UC-11 — Receive and manage notifications:** Authenticated user; notification worker

Every capability by role is in the permission matrix, [platform spec](../feature-platform/SPEC.md) §5.2. Authorization is resolved from stored context, never from the global role alone (§5.1).

## 3. Functional Requirements

### §12. Integrations and notifications

#### §12.2 Notification channels and retry

| ID | Requirement |
|---|---|
| NOT-001 | WHEN a notification is raised, THE system SHALL create an in-app record for its recipient. THE system SHALL attach email delivery only to an event designated for email. |
| NOT-002 | WHEN leave or a correction is submitted or decided, membership or leadership changes, a Project invitation is created or resolved, a membership exit is requested or resolved, or a Task is assigned or reassigned, THE system SHALL request both in-app and email delivery. THE system SHALL use the types `PROJECT_INVITATION_CREATED`, `PROJECT_INVITATION_RESOLVED`, `MEMBERSHIP_EXIT_REQUESTED`, and `MEMBERSHIP_EXIT_RESOLVED` for the Project workflow. |
| NOT-003 | WHEN a Task comment is added or a Task status changes, THE system SHALL create an in-app notification only. |
| NOT-004 | WHERE SMTP is absent or transiently failing, THE system SHALL still commit the domain action listed in `NOT-002`. WHERE SMTP is absent, THE system SHALL record delivery as `UNAVAILABLE`; WHERE delivery fails transiently, THE system SHALL retain `PENDING` retry state. |
| NOT-005 | WHEN SMTP is later configured, THE system SHALL NOT fabricate or retroactively send an email for an event already recorded `UNAVAILABLE`. THE system SHALL keep that event's in-app record available. |
| NOT-006 | WHEN non-secret email is raised, THE system SHALL attempt delivery immediately and, after a failure, retry after 1 minute, 5 minutes, 30 minutes, 2 hours, and 12 hours. WHERE the fifth retry also fails, THE system SHALL mark delivery terminally `FAILED`. |
| NOT-007 | THE system SHALL permit an Admin to inspect failed ordinary email and invoke a manual retry. WHEN that retry is invoked, THE system SHALL re-enter bounded retry state without duplicating the in-app notification. |
| NOT-008 | THE system SHALL NOT route activation or password-reset mail through the ordinary notification outbox, because the raw link must not be persisted. WHERE such a send fails, THE system SHALL invalidate the token and require explicit regeneration. |
| NOT-009 | THE system SHALL scope the unread count and the notification list to the authenticated recipient, and SHALL make mark-read idempotent. |
| NOT-010 | WHEN an invitation is created, THE system SHALL notify the invitee. WHEN it is answered, THE system SHALL notify the issuing Leader and the owning Mentor. WHEN it is revoked or superseded, THE system SHALL notify the invitee, the issuing Leader, and the owning Mentor, collapsing duplicate recipients. WHEN a Leader requests a removal, THE system SHALL notify the owning Mentor and the target; WHEN a member requests their own leave, THE system SHALL notify the owning Mentor and the current Leader; WHEN such a request is decided or cancelled, THE system SHALL notify the requester, the target, and the current Leader, collapsing duplicate recipients. WHEN a member creates a self-Task, THE system SHALL send no notification. |

### Use cases

#### UC-11 — Receive and manage notifications

| Field | Specification |
|---|---|
| Primary actor(s) | Authenticated user; notification worker |
| Trigger | A domain event requires an in-app notification and possibly email. |
| Preconditions | The domain transaction is valid; email eligibility depends on active SMTP. |
| Postconditions | The in-app record remains authoritative for delivery visibility; email cannot roll back the domain action. |
| Traced requirements | NOT-001–NOT-008 |

**Main success flow**

1. Commit the domain action and its in-app notification atomically.
2. Render non-secret email content from the same event context.
3. Attempt email through the active SMTP revision.
4. Retry ordinary email at the configured bounded schedule.
5. Let the recipient read and mark the in-app notification.

**Alternatives and exceptions**

- Without SMTP, the domain action still succeeds and email state is UNAVAILABLE.
- Activation/reset emails do not use ordinary queued retry because links are secret and short-lived.
- After the final ordinary retry, email state becomes terminal failure.

## 4. Non-functional Requirements

System-wide non-functional rules apply unchanged: architecture §3 (`ARC`), authentication and security §13 (`SEC`), interface and accessibility §15 (`UI`), delivery §16 (`OPS`), and test evidence §17 (`TST`), all in the [platform spec](../feature-platform/SPEC.md).

## 5. Data

Tables this feature's entities map to: `notifications`.

The conceptual model, the table inventory, the integrity rules (`DB`), and both diagrams are §19 of the [platform spec](../feature-platform/SPEC.md).

## 6. Error Handling

Rules in section 3 whose text names a refusal, rejection, denial, or failure: `NOT-004`, `NOT-006`, `NOT-007`, `NOT-008`.

System-wide failure behavior is §21 (`ERR-001`–`ERR-007`), and the interface message families are Appendix F, both in the [platform spec](../feature-platform/SPEC.md).

## 7. Acceptance Criteria

Scenarios from the §20 acceptance catalogue whose identifiers start with `AC-NOT`. The catalogue's introduction, including the rules deliberately written without a scenario, is in the [platform spec](../feature-platform/SPEC.md).

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-NOT-001 | NOT-002–NOT-005 | SMTP absent while Mentor approves leave | Approval and in-app notification commit; email state is UNAVAILABLE; later SMTP activation does not send it retroactively. |
| AC-NOT-002 | NOT-006–NOT-007 | Ordinary mail repeatedly fails | Worker follows five specified retry delays, reaches FAILED after six total attempts, and manual retry does not duplicate in-app record. |
| AC-NOT-003 | NOT-008 | Reset/activation delivery fails | Raw link is never queued; token is invalidated and explicit regeneration is required. |
| AC-NOT-004 | NOT-002–NOT-005, NOT-010 | SMTP is absent during invitation and membership-exit request/decision workflows | Every domain transition and in-app notification commits with deduplicated recipients; email is `UNAVAILABLE` and is not sent retroactively. |
| AC-NOT-006 | NOT-001 | An event designated for email and an event not designated for email each fire while SMTP is unavailable | Both create an in-app record for the intended recipient; only the email-designated event records a delivery attempt; neither loses its in-app record because delivery failed. |
| AC-NOT-005 | NOT-009 | Intern A and Intern B each hold unread notifications; A requests the list and unread count, then marks one notification read twice | A sees only A's notifications and A's count; the second mark-read returns the same state as the first and does not change the count further; B's list and count are unaffected. |

## 8. Out of Scope

System-wide exclusions are §1.3 of the [platform spec](../feature-platform/SPEC.md): `GOV-007` through `GOV-010` and `GOV-015`.

No rule in this spec states an exclusion of its own.

## Notes / Open Questions

- What happens to a notification whose link points at a deleted `PLANNED` Project is an open question under `D12`, in [the project spec](../feature-project/SPEC.md).
