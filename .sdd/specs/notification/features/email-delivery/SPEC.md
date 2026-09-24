# Notification email delivery Spec

**Version:** 1.1.1 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-21

**Module:** `notification` · **Shared contract:** [MODULE.md](../../MODULE.md)

## 1. Context & Goal

Deliver and retry ordinary notification email without rolling back its domain action or retaining raw secret links.

This is one cohesive planning scope. Operations below are sections of this feature,
not separate modules or mandatory branches. The split changes document ownership only.

## 2. Actors & Roles

Notification worker; Admin inspects failure and invokes manual retry.

The [platform permission matrix](../../../platform/features/authorization/SPEC.md#52-permission-matrix)
and the module's shared authorization rules apply to every operation.

## 3. Functional Requirements

The operation summaries below are navigation. The numbered rules remain authoritative;
they do not acquire additional behavior from a heading or summary.

### Choose email events and attempt delivery

NOT-002 and NOT-004 define designated events and absent/transient SMTP behavior.

### Retry and inspect failure

NOT-005 through NOT-007 define no retroactive send, bounded retry and deduplicated manual retry.

### Exclude secret links

NOT-008 keeps activation/reset links out of ordinary queued delivery.

### Canonical feature rules

| ID | Requirement |
|---|---|
| NOT-002 | WHEN leave, a correction, or an attendance exception is submitted, decided, or marked, membership or leadership changes, a Project invitation is created or resolved, a membership exit is requested or resolved, or a Task is assigned or reassigned, THE system SHALL request both in-app and email delivery. THE system SHALL use the types `PROJECT_INVITATION_CREATED`, `PROJECT_INVITATION_RESOLVED`, `MEMBERSHIP_EXIT_REQUESTED`, and `MEMBERSHIP_EXIT_RESOLVED` for the Project workflow. |
| NOT-004 | WHERE SMTP is absent or transiently failing, THE system SHALL still commit the domain action listed in `NOT-002`. WHERE SMTP is absent, THE system SHALL record delivery as `UNAVAILABLE`; WHERE delivery fails transiently, THE system SHALL retain `PENDING` retry state. |
| NOT-005 | WHEN SMTP is later configured, THE system SHALL NOT fabricate or retroactively send an email for an event already recorded `UNAVAILABLE`. THE system SHALL keep that event's in-app record available. |
| NOT-006 | WHEN non-secret email is raised, THE system SHALL attempt delivery immediately and, after a failure, retry after 1 minute, 5 minutes, 30 minutes, 2 hours, and 12 hours. WHERE the fifth retry also fails, THE system SHALL mark delivery terminally `FAILED`. |
| NOT-007 | THE system SHALL permit an Admin to inspect failed ordinary email and invoke a manual retry. WHEN that retry is invoked, THE system SHALL re-enter bounded retry state without duplicating the in-app notification. |
| NOT-008 | THE system SHALL NOT route activation or password-reset mail through the ordinary notification outbox, because the raw link must not be persisted. WHERE such a send fails, THE system SHALL invalidate the token and require explicit regeneration. |
| NOT-012 | THE system SHALL hold each notification's email delivery in exactly one of `NOT_REQUIRED`, `PENDING`, `SENT`, `FAILED` or `UNAVAILABLE`. `NOT_REQUIRED` SHALL mean the notification designates no email and SHALL carry no delivery payload. `PENDING` SHALL mean an attempt is scheduled and SHALL carry a payload and a next-attempt time. `SENT` SHALL mean an attempt succeeded and SHALL carry its send time. `FAILED` SHALL mean the attempts of `NOT-006` are exhausted. `UNAVAILABLE` SHALL mean no active SMTP revision existed when the notification committed, SHALL carry no payload, and SHALL NOT be sent once SMTP becomes active again. THE system SHALL permit only these transitions: `NOT_REQUIRED`, `PENDING` or `UNAVAILABLE` at creation; `PENDING` to `SENT`, to `PENDING` for a further attempt, or to `FAILED`; and `FAILED` to `PENDING` on an explicit retry under `NOT-007`. `NOT_REQUIRED`, `SENT` and `UNAVAILABLE` SHALL be terminal. |

#### State transitions: notification email delivery

The delivery state of one notification row. The in-app notification itself commits whatever
this state becomes (`NOT-004`); the table adds no permission.

| From | Action | To | Who | Rules |
|---|---|---|---|---|
| (none) | the notification designates no email | `NOT_REQUIRED` | system | `NOT-012` |
| (none) | email is designated while SMTP is active | `PENDING` | system | `NOT-002`, `NOT-006`, `NOT-012` |
| (none) | email is designated while no SMTP revision is active | `UNAVAILABLE` | system | `NOT-004`, `NOT-012` |
| `PENDING` | an attempt succeeds | `SENT` | worker | `NOT-006`, `NOT-012` |
| `PENDING` | an attempt fails and an attempt remains | `PENDING` | worker | `NOT-006`, `NOT-012` |
| `PENDING` | the attempts of `NOT-006` are exhausted | `FAILED` | worker | `NOT-006`, `NOT-012` |
| `FAILED` | an Admin retries explicitly | `PENDING` | Admin | `NOT-007`, `NOT-012` |

No transition leaves `NOT_REQUIRED`, `SENT` or `UNAVAILABLE`. Activating SMTP later does not
move an `UNAVAILABLE` row (`NOT-005`).



## 4. Non-functional Requirements

Inherit [module constraints](../../MODULE.md#4-non-functional-requirements) and
[platform constraints](../../../platform/MODULE.md#4-non-functional-requirements),
including authorization, history, concurrency, server time and test evidence.

## 5. Data

`notifications` delivery metadata; SMTP configuration remains in platform.

The [module data contract](../../MODULE.md#5-data) retains ownership, constraints and
checks across features. The full physical model stays in platform §19. A feature folder
does not grant repository/entity access across the module boundaries of ARC-006.

### Required contracts

These are required facts or behaviors, not prescribed cross-module imports or an
automatic implementation order. PLAN resolves delivery predecessors against what
already exists and preserves ARC-005/ARC-006.

| Provider | Required fact or behavior | Rule trace |
|---|---|---|
| [notification/inbox](../inbox/SPEC.md) | Retained in-app record must survive email failure | [NOT-001](../inbox/SPEC.md) |
| [platform/smtp-configuration](../../../platform/features/smtp-configuration/SPEC.md) | Current active SMTP transport revision | [INT-006](../../../platform/features/smtp-configuration/SPEC.md), [INT-007](../../../platform/features/smtp-configuration/SPEC.md), [INT-008](../../../platform/features/smtp-configuration/SPEC.md) |
| [platform](../../../platform/MODULE.md) | No secret leakage in notification bodies | [INT-005](../../../platform/MODULE.md) |

### Related workflows and joint checks

- [Notification inbox](../inbox/SPEC.md): In-app delivery survives mail failure.

## 6. Error Handling

Apply each refusal, deadline, conflict and delivery-failure clause in the numbered rules
above, together with [module error handling](../../MODULE.md#6-error-handling).
Platform §21 supplies common failure behavior; an operation summary does not override it.

## 7. Acceptance Criteria

### Operation and acceptance map

This map traces existing behavior; its gap column does not define a new rule or
claim full test coverage. Actors and outcomes are summaries of the canonical rules.

| Operation | Actor and observable outcome | Canonical rules | Existing acceptance scenarios | Acceptance boundary or open decision |
|---|---|---|---|---|
| [Choose email events and attempt delivery](#choose-email-events-and-attempt-delivery) | Worker attempts designated email while domain and in-app outcomes survive mail failure | [NOT-002](SPEC.md), [NOT-004](SPEC.md), [NOT-012](SPEC.md) | [AC-NOT-001](SPEC.md), [AC-NOT-004](SPEC.md), [AC-NOT-007](SPEC.md) | Settled by `D38`: `NOT-012` names all five states, their invariants and the permitted transitions. |
| [Retry and inspect failure](#retry-and-inspect-failure) | Worker and Admin retry ordinary failed mail without duplicate in-app records | [NOT-005](SPEC.md), [NOT-006](SPEC.md), [NOT-007](SPEC.md) | [AC-NOT-001](SPEC.md), [AC-NOT-002](SPEC.md) | Use exact retry delays/attempt counts; an UNAVAILABLE event is not sent retroactively. |
| [Exclude secret links](#exclude-secret-links) | Identity secret-link delivery invalidates failed tokens instead of queuing raw links | [NOT-008](SPEC.md) | [AC-NOT-003](SPEC.md) | Verify no raw token/link remains in persistence or logs; regeneration is explicit. |

### Canonical acceptance scenarios

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-NOT-001 | NOT-002–NOT-005 | SMTP absent while Mentor approves leave | Approval and in-app notification commit; email state is UNAVAILABLE; later SMTP activation does not send it retroactively. |
| AC-NOT-002 | NOT-006–NOT-007 | Ordinary mail repeatedly fails | Worker follows five specified retry delays, reaches FAILED after six total attempts, and manual retry does not duplicate in-app record. |
| AC-NOT-003 | NOT-008 | Reset/activation delivery fails | Raw link is never queued; token is invalidated and explicit regeneration is required. |
| AC-NOT-004 | NOT-002–NOT-005, NOT-010 | SMTP is absent during invitation and membership-exit request/decision workflows | Every domain transition and in-app notification commits with deduplicated recipients; email is `UNAVAILABLE` and is not sent retroactively. |
| AC-NOT-007 | NOT-012, NOT-004–NOT-007 | Four notifications are raised: one designating no email, one while SMTP is active, one while no SMTP revision is active, and one whose attempts all fail; SMTP is then activated, the failed one is retried by an Admin, and a transition is attempted out of each terminal state | The four reach `NOT_REQUIRED`, `SENT`, `UNAVAILABLE` and `FAILED` respectively. `NOT_REQUIRED` and `UNAVAILABLE` carry no payload; `SENT` carries a send time; `PENDING` carries a payload and a next-attempt time. Activating SMTP moves no `UNAVAILABLE` row. The Admin retry returns only the `FAILED` row to `PENDING`. Every attempt to leave `NOT_REQUIRED`, `SENT` or `UNAVAILABLE` is refused and the in-app notification is unaffected throughout. |

Shared and cross-feature scenarios in [MODULE.md](../../MODULE.md#7-acceptance-criteria)
also apply. Scenario ownership follows the behavior exercised, not every prerequisite
named by its Requirements cell. Relocation preserves every existing expected result.

## 8. Out of Scope

Inherit [module exclusions](../../MODULE.md#8-out-of-scope). Adjacent feature behavior
is a dependency, not a duplicated contract. This split creates no new product behavior,
schema, dependency, PLAN.md or TASKS.md.

## Notes / Open Questions

`D38` settles the complete delivery status set in `NOT-012`, with the state transition table above. It states what `AC-NOT-001` and `AC-NOT-004` already required and adds no new delivery behavior. The schema needs one change: `ck_notifications_email_payload` only exempts `NOT_REQUIRED` and `UNAVAILABLE` from carrying a payload, and must forbid one (`D38`). Nothing here has been validated against the running application. UC-11 stays in MODULE.md as the combined domain/inbox/email flow.

Read [shared open questions](../../MODULE.md#notes--open-questions) before approving
the technical plan. [plan.md](../../../../../plan.md) is the only progress tracker.
