# Notification inbox Spec

**Version:** 1.1.0 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-21

**Module:** `notification` · **Shared contract:** [MODULE.md](../../MODULE.md)

## 1. Context & Goal

Keep each recipient informed through an isolated, idempotent in-app inbox.

This is one cohesive planning scope. Operations below are sections of this feature,
not separate modules or mandatory branches. The split changes document ownership only.

## 2. Actors & Roles

Authenticated recipient; domain event publisher.

The [platform permission matrix](../../../platform/features/authorization/SPEC.md#52-permission-matrix)
and the module's shared authorization rules apply to every operation.

## 3. Functional Requirements

The operation summaries below are navigation. The numbered rules remain authoritative;
they do not acquire additional behavior from a heading or summary.

### Create in-app notification

NOT-001 creates a recipient record independently of whether the event needs email.

### List, count and mark read

NOT-009 scopes all reads and idempotent mark-read to the authenticated recipient.

### Canonical feature rules

| ID | Requirement |
|---|---|
| NOT-001 | WHEN a notification is raised, THE system SHALL create an in-app record for its recipient. THE system SHALL attach email delivery only to an event designated for email. |
| NOT-009 | THE system SHALL scope the unread count and the notification list to the authenticated recipient, and SHALL make mark-read idempotent. |



## 4. Non-functional Requirements

Inherit [module constraints](../../MODULE.md#4-non-functional-requirements) and
[platform constraints](../../../platform/MODULE.md#4-non-functional-requirements),
including authorization, history, concurrency, server time and test evidence.

## 5. Data

`notifications`, with recipient identity resolved by the module contract.

The [module data contract](../../MODULE.md#5-data) retains ownership, constraints and
checks across features. The full physical model stays in platform §19. A feature folder
does not grant repository/entity access across the module boundaries of ARC-006.

### Required contracts

These are required facts or behaviors, not prescribed cross-module imports or an
automatic implementation order. PLAN resolves delivery predecessors against what
already exists and preserves ARC-005/ARC-006.

| Provider | Required fact or behavior | Rule trace |
|---|---|---|
| [identity](../../../identity/MODULE.md) | Authenticated recipient identity | [ACC-009](../../../identity/MODULE.md) |
| [platform](../../../platform/MODULE.md) | Recipient isolation | [AUTH-002](../../../platform/features/authorization/SPEC.md) |

### Related workflows and joint checks

- [Notification email delivery](../email-delivery/SPEC.md): Email failure does not erase an in-app record.

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
| [Create in-app notification](#create-in-app-notification) | Domain publisher retains an in-app record for each intended recipient | [NOT-001](SPEC.md) | [AC-NOT-006](SPEC.md) | The domain mutation and notification need joint transaction evidence; mail absence cannot erase the record. |
| [List, count and mark read](#list-count-and-mark-read) | Authenticated recipient reads their own inbox/count and marks read idempotently | [NOT-009](SPEC.md) | [AC-NOT-005](SPEC.md) | Use two recipients and a repeated mark-read; preserve isolation in all list/count paths. |

### Canonical acceptance scenarios

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-NOT-005 | NOT-009 | Intern A and Intern B each hold unread notifications; A requests the list and unread count, then marks one notification read twice | A sees only A's notifications and A's count; the second mark-read returns the same state as the first and does not change the count further; B's list and count are unaffected. |
| AC-NOT-006 | NOT-001 | An event designated for email and an event not designated for email each fire while SMTP is unavailable | Both create an in-app record for the intended recipient; only the email-designated event records a delivery attempt; neither loses its in-app record because delivery failed. |

Shared and cross-feature scenarios in [MODULE.md](../../MODULE.md#7-acceptance-criteria)
also apply. Scenario ownership follows the behavior exercised, not every prerequisite
named by its Requirements cell. Relocation preserves every existing expected result.

## 8. Out of Scope

Inherit [module exclusions](../../MODULE.md#8-out-of-scope). Adjacent feature behavior
is a dependency, not a duplicated contract. This split creates no new product behavior,
schema, dependency, PLAN.md or TASKS.md.

## Notes / Open Questions

No additional decision is introduced by this split. No question affecting this feature is open; the cross-feature checks and shared contracts in MODULE.md still apply.

Read [shared open questions](../../MODULE.md#notes--open-questions) before approving
the technical plan. [plan.md](../../../../../plan.md) is the only progress tracker.
