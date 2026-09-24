# Missed-checkout correction Spec

**Version:** 1.1.0 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-21

**Module:** `attendance` · **Shared contract:** [MODULE.md](../../MODULE.md)

## 1. Context & Goal

Request and decide a missing checkout time while preserving the original punch history.

This is one cohesive planning scope. Operations below are sections of this feature,
not separate modules or mandatory branches. The split changes document ownership only.

## 2. Actors & Roles

Intern requests; current responsible Mentor decides within the applicable deadline.

Use cases defined here: UC-09.

The [platform permission matrix](../../../platform/features/authorization/SPEC.md#52-permission-matrix)
and the module's shared authorization rules apply to every operation.

## 3. Functional Requirements

The operation summaries below are navigation. The numbered rules remain authoritative;
they do not acquire additional behavior from a heading or summary.

### Submit correction

COR rules define missing-checkout eligibility, proposed time and the submission deadline.

### Decide correction

Approval confirms time only; excuses belong to the exception feature. Decision deadline and history remain mandatory.

### Canonical feature rules

| ID | Requirement |
|---|---|
| COR-001 | THE system SHALL permit only the owning Intern to request a correction, only WHERE the attendance row has no raw checkout, and only after the checkout cutoff of the policy version attached to that row has passed. THE system SHALL permit at most one correction request per attendance row. |
| COR-002 | THE system SHALL require the owning Intern to supply a proposed checkout and a nonblank reason, and SHALL require that proposed checkout to be after check-in, on the original local work date, and not in the future at submission. |
| COR-003 | THE system SHALL accept a submission through the inclusive deadline `scheduled end on the attendance date + 48 hours`, resolved from the attached historical policy version, and only while the attendance period of that date is not finalized. THE system SHALL anchor that deadline to scheduled end rather than to the checkout cutoff; under the seeded defaults it falls at 15:30 two days later. The 48-hour limit is a laboratory policy shared with `EXC-002`. |
| COR-004 | WHEN a submission is accepted, THE system SHALL open a separate 48-hour decision window measured from `submitted_at`, the same window `EXC-003` gives an exception request. |
| COR-005 | WHILE a request is pending or overdue and its period is not finalized, THE system SHALL permit the Intern's responsible Mentor under `ACC-026` to approve or reject it. WHILE a decided request's period is not finalized, THE system SHALL permit that Mentor to amend the decision or reverse it between approved and rejected under `ATT-024`; an amendment SHALL NOT change the proposed checkout. THE system SHALL write an immutable correction event for every transition, amendment, and reversal. |
| COR-006 | WHEN a correction is approved, THE system SHALL leave the raw checkout null and use the proposed checkout only as the effective checkout. THE system SHALL then clear the missing-checkout classification, and WHERE the effective checkout falls before scheduled end, THE system SHALL record an early-departure violation under `ATT-011`. |
| COR-007 | WHEN the decision window expires and a request is still pending, THE system SHALL mark it `OVERDUE` without rejecting it, because the approver rather than the Intern missed the deadline. THE system SHALL NOT lock a decided correction when the window expires. WHERE the current decision changes under `ATT-024`, THE system SHALL derive the effective checkout and the classifications of `COR-006` from the new current decision. |
| COR-008 | THE system SHALL persist the overdue marking and its reminder through a scheduled worker, and SHALL apply the same deadline guard on every correction read and write path before acting. |
| COR-009 | WHILE the attendance period of a correction is finalized, THE system SHALL refuse any Mentor state change except inside a range reopened under `ATT-022`. THE system SHALL NOT permit an Admin, or any Mentor other than the responsible Mentor, to decide, amend, or reverse a correction. |

#### UC-09 — Correct a missed checkout

**Part A3.**

| Field | Specification |
|---|---|
| Primary actor(s) | Intern (submitter); the Intern's responsible Mentor (decision maker) |
| Trigger | An Intern with MISSING_CHECKOUT proposes a checkout, or a Mentor reviews the request. |
| Preconditions | The attendance row has a check-in and no raw checkout; its attached-policy checkout cutoff has passed; the submission deadline remains open. |
| Postconditions | The request/event history explains the effective attendance result while preserving the raw record. |
| Traced requirements | COR-001–COR-009, ATT-024 |

**Main success flow**

1. After the checkout cutoff, submit one proposed checkout through the inclusive scheduled-end-plus-48-hours deadline.
2. Start a separate 48-hour Mentor decision window.
3. The responsible Mentor approves or rejects the request.
4. Until the attendance period is finalized, the responsible Mentor may amend the decision or reverse it with a reason; each change is a new entry.
5. Derive the effective checkout from the current decision without overwriting raw data.

**Alternatives and exceptions**

- A correction before or at the checkout cutoff is rejected because normal checkout remains available.
- The submission deadline stays anchored to scheduled end, not checkout grace; under defaults it is 15:30 two days later.
- The scheduler and the request-time guard both mark a request undecided at the end of its decision window overdue; it is never rejected for the Mentor's delay.
- A decided request does not lock when its decision window ends; after its period is finalized it changes only inside a range reopened under `ATT-022`.
- Concurrent decisions serialize so only a valid current transition wins.

#### State transitions: missed-checkout correction

The transitions the rules above allow. The table adds nothing to them: where it and a rule differ, the rule wins.

| From | Action | To | Who | Rules |
|---|---|---|---|---|
| (none) | request, with a proposed checkout and a reason | `PENDING` | owning Intern | `COR-001`–`COR-003` |
| `PENDING` | the decision window expires | `OVERDUE` | system | `COR-004`, `COR-007`, `COR-008` |
| `PENDING`, `OVERDUE` | approve | `APPROVED` | responsible Mentor | `COR-005`, `COR-006` |
| `PENDING`, `OVERDUE` | reject | `REJECTED` | responsible Mentor | `COR-005` |
| `APPROVED` | reverse | `REJECTED` | responsible Mentor | `COR-005`, `COR-007`, `ATT-024` |
| `REJECTED` | reverse | `APPROVED` | responsible Mentor | `COR-005`, `COR-007`, `ATT-024` |
| `APPROVED` | amend, never the proposed checkout | `APPROVED` | responsible Mentor | `COR-005`, `ATT-024` |
| `REJECTED` | amend, never the proposed checkout | `REJECTED` | responsible Mentor | `COR-005`, `ATT-024` |

No transition returns a decided correction to `PENDING` (`ATT-024`). Every Mentor change is refused while the period is finalized, except inside a reopened range, and no Admin or other Mentor may make one (`COR-009`, `ATT-021`, `ATT-023`).


## 4. Non-functional Requirements

Inherit [module constraints](../../MODULE.md#4-non-functional-requirements) and
[platform constraints](../../../platform/MODULE.md#4-non-functional-requirements),
including authorization, history, concurrency, server time and test evidence.

## 5. Data

`attendance_corrections`, `attendance_correction_events`; the attendance record retains its raw data.

The [module data contract](../../MODULE.md#5-data) retains ownership, constraints and
checks across features. The full physical model stays in platform §19. A feature folder
does not grant repository/entity access across the module boundaries of ARC-006.

### Required contracts

These are required facts or behaviors, not prescribed cross-module imports or an
automatic implementation order. PLAN resolves delivery predecessors against what
already exists and preserves ARC-005/ARC-006.

| Provider | Required fact or behavior | Rule trace |
|---|---|---|
| [attendance/punches-and-results](../punches-and-results/SPEC.md) | Original punch and cutoff under its policy | [ATT-010](../punches-and-results/SPEC.md), [ATT-011](../punches-and-results/SPEC.md), [ATT-012](../punches-and-results/SPEC.md) |
| [internship/responsible-mentor](../../../internship/features/responsible-mentor/SPEC.md) | Current decision authority | [ACC-026](../../../internship/features/responsible-mentor/SPEC.md) |
| [attendance/period-finalization](../period-finalization/SPEC.md) | Permitted date scope | [ATT-021](../period-finalization/SPEC.md), [ATT-022](../period-finalization/SPEC.md), [ATT-023](../period-finalization/SPEC.md) |
| [attendance](../../MODULE.md) | History and recipient contract | [ATT-024](../../MODULE.md), [NOT-011](../../MODULE.md) |

### Related workflows and joint checks

- [Punches and results](../punches-and-results/SPEC.md): Approved correction supplies effective time.
- [Attendance exception](../attendance-exception/SPEC.md): An excuse is a separate decision.
- [Period finalization and reopening](../period-finalization/SPEC.md): Closed periods require the defined reopening workflow.

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
| [Submit correction](#submit-correction) | Intern requests an eligible missing-checkout time within the submission window | [COR-001](SPEC.md), [COR-002](SPEC.md), [COR-003](SPEC.md) | [AC-COR-001](SPEC.md), [AC-COR-002](SPEC.md) | Exact cutoff/deadline and invalid proposed time are separate boundaries. |
| [Decide correction](#decide-correction) | Responsible Mentor decides or amends time confirmation without automatically excusing a violation | [COR-004](SPEC.md), [COR-005](SPEC.md), [COR-006](SPEC.md), [COR-007](SPEC.md), [COR-008](SPEC.md), [COR-009](SPEC.md), [ATT-024](../../MODULE.md) | [AC-COR-003](SPEC.md), [AC-COR-004](SPEC.md), [AC-COR-005](SPEC.md), [AC-COR-006](SPEC.md) | Compose an approved early checkout with a separate exception decision; include finalized-period denial. |

### Canonical acceptance scenarios

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-COR-001 | COR-001–COR-003 | Under defaults, Intern attempts correction before/at the 16:00 checkout cutoff, just after it, at 15:30 two days later, and at the first later instant; an Intern with a normal checkout also tries | Before/at cutoff and normal-checkout attempts are rejected; a missing-checkout request after cutoff through the inclusive 15:30 deadline two days later is accepted once; the first later instant is rejected. |
| AC-COR-002 | COR-002 | Proposed checkout precedes check-in, crosses local date, or is future | Validation rejects each value without creating correction. |
| AC-COR-003 | COR-004–COR-005, COR-007, ATT-024 | The responsible Mentor approves a correction; two days later, before the period is finalized, the Mentor amends its note with a reason, tries to amend the proposed checkout, reverses the approval to rejected with a reason, and tries a further change without a reason | The approval, the amendment, and the reversal each append an ordered immutable event with its kind, actor, and time, and earlier entries stay unchanged; no lock applied when the decision window ended and nothing returned to `PENDING`; changing the proposed checkout and the change without a reason are refused; the effective checkout and the missing-checkout classification follow the current rejection. |
| AC-COR-004 | COR-006 | Mentor approves a proposed checkout before scheduled end | Raw checkout remains null; effective checkout becomes proposal; missing flag clears and early flag appears. |
| AC-COR-005 | COR-007–COR-009 | Pending correction reaches decision deadline while scheduler is late | First access marks it `OVERDUE` atomically without rejecting it; the scheduler later behaves idempotently; the responsible Mentor can still decide it until its period is finalized. |
| AC-COR-006 | AUTH-003, COR-001–COR-009, UI-019 | Intern and Mentor open their correction workflows with pending and terminal requests | Intern sees only owned corrections; the responsible Mentor sees the actionable queue of the Interns they are responsible for before retained correction/event history; unauthorized users and guessed IDs disclose nothing; no Leave form is mixed into either Correction workflow. |

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
