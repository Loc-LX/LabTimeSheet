# Attendance exception Spec

**Version:** 1.1.0 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-21

**Module:** `attendance` · **Shared contract:** [MODULE.md](../../MODULE.md)

## 1. Context & Goal

Record an auditable excuse for late arrival or early departure.

This is one cohesive planning scope. Operations below are sections of this feature,
not separate modules or mandatory branches. The split changes document ownership only.

## 2. Actors & Roles

Intern requests; current responsible Mentor decides or marks an eligible excuse.

Use cases defined here: UC-15.

The [platform permission matrix](../../../platform/features/authorization/SPEC.md#52-permission-matrix)
and the module's shared authorization rules apply to every operation.

## 3. Functional Requirements

The operation summaries below are navigation. The numbered rules remain authoritative;
they do not acquire additional behavior from a heading or summary.

### Request or mark an exception

EXC rules distinguish request/decision deadlines from the direct-marking window.

### Decide and amend

The decision records actor and reason; amendment obeys D31 and does not rewrite the immutable decision outcome.

### Canonical feature rules

| ID | Requirement |
|---|---|
| EXC-001 | THE system SHALL keep a late arrival or an early departure recorded exactly as `ATT-009` and `ATT-011` classify it, and SHALL hold whether it is excused as a separate decision that never clears the classification and never changes the raw or effective times. |
| EXC-002 | WHEN an Intern requests that their own recorded late arrival or early departure be excused, THE system SHALL require a nonblank reason, SHALL accept the request only through 48 hours after the scheduled end of that work date and only while the attendance period of that work date is not finalized, and SHALL keep at most one request per attendance row and violation kind. The 48-hour limit is a laboratory policy shared with `COR-003`. |
| EXC-003 | WHILE a request is pending or overdue and the attendance period of its work date is not finalized, THE system SHALL permit only the Intern's responsible Mentor under `ACC-026` to decide it as excused or unexcused, and SHALL retain the decider, the server time, and any decision note. WHEN 48 hours pass after submission without a decision, THE system SHALL mark the request overdue and SHALL NOT treat it as excused or unexcused; only a decision by the responsible Mentor makes it either. The 48-hour limit is a laboratory policy shared with `COR-004`. |
| EXC-004 | WHEN the responsible Mentor marks an Intern's recorded late arrival or early departure excused without a request, THE system SHALL require a nonblank reason, SHALL accept the mark only while the attendance period of that work date is not finalized under `ATT-020`, and SHALL retain the Mentor and the server time. THE system SHALL NOT set a separate deadline of its own; the period is the boundary. |
| EXC-005 | WHERE the current decision on a late arrival or early departure is excused, THE system SHALL NOT count it among the applicable violations of `ATT-016`, and SHALL still show it, marked excused, in attendance history, reports, and statistics. Leaving an excused violation out of compliance is a laboratory policy. |
| EXC-006 | THE system SHALL refuse an exception decision or mark by an Intern Leader, an Admin, or any Mentor other than the Intern's responsible Mentor. |
| EXC-007 | WHILE the attendance period of the work date is not finalized, THE system SHALL permit the responsible Mentor to amend an exception decision or mark, or reverse it between excused and unexcused, under `ATT-024`. WHERE the current decision changes, THE system SHALL count the violation under `EXC-005` from the new current decision and SHALL leave the classification of `EXC-001` unchanged. An amendment SHALL change only the decision note or, for a mark made without a request, its reason, which SHALL remain nonblank; it SHALL NOT change the outcome, the work date, the violation kind, or the Intern's request, and a change of outcome is a reversal. |
| DB-016 | THE schema SHALL store at most one attendance exception per attendance record and violation kind, `LATE_ARRIVAL` or `EARLY_DEPARTURE`, recording whether it was raised as an Intern request with its nonblank reason or as a Mentor mark, its deadlines, and a status of `PENDING`, `OVERDUE`, `EXCUSED` or `UNEXCUSED`. |

#### UC-15 — Excuse a late arrival or early departure

**Part A4.**

| Field | Specification |
|---|---|
| Primary actor(s) | Intern (requester); the Intern's responsible Mentor (decision maker) |
| Trigger | An Intern asks for a recorded late arrival or early departure to be excused, or the responsible Mentor marks one excused. |
| Preconditions | The attendance row records the late arrival or early departure, and the Intern has a responsible Mentor. |
| Postconditions | The violation stays recorded; whether it is excused is retained with actor, time, and reason. |
| Traced requirements | EXC-001–EXC-007, ATT-024, ACC-026, ATT-016, NOT-011 |

**Main success flow**

1. The Intern submits a request with a reason within 48 hours after scheduled end, while the attendance period is open.
2. The responsible Mentor sees it in their queue and decides it excused or unexcused; a request still undecided after 48 hours is marked overdue and the Mentor is reminded.
3. The Intern is notified of the decision.
4. An excused violation stops lowering compliance and still appears, marked excused, in history and reports.

**Alternatives and exceptions**

- The responsible Mentor marks a violation excused without a request, giving a reason, while the attendance period of that work date is open; there is no separate limit.
- Until the period is finalized, the Mentor amends or reverses a decision with a reason; each change is a new entry and earlier decisions stay in history.
- A late request, or a decision by anyone other than the responsible Mentor, is refused.

#### State transitions: attendance exception

The transitions the rules above allow. The table adds nothing to them: where it and a rule differ, the rule wins.

| From | Action | To | Who | Rules |
|---|---|---|---|---|
| (none) | request an excuse, with a reason | `PENDING` | Intern whose attendance it is | `EXC-002`, `DB-016` |
| (none) | mark excused without a request, with a reason | `EXCUSED` | responsible Mentor | `EXC-004`, `DB-016` |
| `PENDING` | 48 hours pass after submission without a decision | `OVERDUE` | system | `EXC-003` |
| `PENDING`, `OVERDUE` | decide excused | `EXCUSED` | responsible Mentor | `EXC-003` |
| `PENDING`, `OVERDUE` | decide unexcused | `UNEXCUSED` | responsible Mentor | `EXC-003` |
| `EXCUSED` | reverse | `UNEXCUSED` | responsible Mentor | `EXC-007`, `ATT-024` |
| `UNEXCUSED` | reverse | `EXCUSED` | responsible Mentor | `EXC-007`, `ATT-024` |
| `EXCUSED` | amend the decision note, or the reason of a mark | `EXCUSED` | responsible Mentor | `EXC-007`, `ATT-024` |
| `UNEXCUSED` | amend the decision note | `UNEXCUSED` | responsible Mentor | `EXC-007`, `ATT-024` |

A decision, mark, amendment or reversal by an Intern Leader, an Admin or any other Mentor is refused (`EXC-006`), and so is every change while the period is finalized, except inside a reopened range (`ATT-021`, `ATT-023`).


## 4. Non-functional Requirements

Inherit [module constraints](../../MODULE.md#4-non-functional-requirements) and
[platform constraints](../../../platform/MODULE.md#4-non-functional-requirements),
including authorization, history, concurrency, server time and test evidence.

## 5. Data

`attendance_exceptions`, `attendance_exception_decisions` and retained attendance references.

The [module data contract](../../MODULE.md#5-data) retains ownership, constraints and
checks across features. The full physical model stays in platform §19. A feature folder
does not grant repository/entity access across the module boundaries of ARC-006.

### Required contracts

These are required facts or behaviors, not prescribed cross-module imports or an
automatic implementation order. PLAN resolves delivery predecessors against what
already exists and preserves ARC-005/ARC-006.

| Provider | Required fact or behavior | Rule trace |
|---|---|---|
| [attendance/punches-and-results](../punches-and-results/SPEC.md) | Recorded late/early violation | [ATT-009](../punches-and-results/SPEC.md), [ATT-011](../punches-and-results/SPEC.md) |
| [attendance/missed-checkout-correction](../missed-checkout-correction/SPEC.md) | Effective checkout may determine the early violation | [COR-006](../missed-checkout-correction/SPEC.md) |
| [internship/responsible-mentor](../../../internship/features/responsible-mentor/SPEC.md) | Current responsible Mentor | [ACC-026](../../../internship/features/responsible-mentor/SPEC.md) |
| [attendance/period-finalization](../period-finalization/SPEC.md) | Finalization and authorized reopen scope | [ATT-021](../period-finalization/SPEC.md), [ATT-022](../period-finalization/SPEC.md), [ATT-023](../period-finalization/SPEC.md) |
| [attendance](../../MODULE.md) | Retained decision entries and recipients | [ATT-024](../../MODULE.md), [NOT-011](../../MODULE.md) |

### Related workflows and joint checks

- [Punches and results](../punches-and-results/SPEC.md): Excuse flags affect compliance, not raw punches.
- [Period finalization and reopening](../period-finalization/SPEC.md): Finalization and reopening govern mutation eligibility.

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
| [Request or mark an exception](#request-or-mark-an-exception) | Intern requests or responsible Mentor directly marks an eligible excuse | [EXC-001](SPEC.md), [EXC-002](SPEC.md), [EXC-003](SPEC.md), [EXC-004](SPEC.md), [EXC-006](SPEC.md) | [AC-EXC-001](SPEC.md), [AC-EXC-002](SPEC.md) | Request/decision deadlines and the direct-marking deadline are distinct; include an unrelated Mentor/Admin denial. |
| [Decide and amend](#decide-and-amend) | Responsible Mentor decides or amends permitted note/reason fields with retained history | [EXC-003](SPEC.md), [EXC-005](SPEC.md), [EXC-007](SPEC.md), [ATT-024](../../MODULE.md) | [AC-EXC-003](SPEC.md), [AC-EXC-004](SPEC.md) | D31 forbids changing the immutable decision outcome; preserve it in every amendment case. |

### Canonical acceptance scenarios

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-EXC-001 | EXC-001–EXC-003, EXC-005, NOT-011 | An Intern ten minutes late requests an excuse with a reason 47 hours after scheduled end, and on another day 49 hours after; the responsible Mentor excuses the first request within 48 hours | The first request is accepted and the second refused; the responsible Mentor is notified of the request and the Intern of the decision; the day still shows late, its compliance counts no late violation, and history and reports mark it excused with the Mentor and time. |
| AC-EXC-002 | EXC-004, EXC-006, ATT-021, AUTH-003 | The responsible Mentor marks one early departure excused without a reason, another with a reason while its period is open, and a third after that period has finalized; another Mentor, the Intern's Leader, and an Admin each try to decide a pending request | Only the reasoned mark inside the open period commits; the mark after finalization is refused and changes only through a range reopened under `ATT-022`; every other attempt is refused and changes nothing. |
| AC-EXC-003 | EXC-003, EXC-005, EXC-007, ATT-024, NOT-011 | A request stays undecided for 48 hours; the responsible Mentor then excuses it before the period is finalized, later reverses it to unexcused with a reason, and tries a further change after the period is finalized | At 48 hours the request becomes overdue, the Mentor is notified, and compliance still counts the violation; the later decision commits; the reversal appends a new decision while the earlier one stays unchanged in history; compliance follows the current decision; the change after finalization is refused. |
| AC-EXC-004 | EXC-007, ATT-024 | Before the period is finalized, the responsible Mentor amends the note of an excused decision and the reason of a mark made without a request, each with an amendment reason; then submits amendments that blank the mark's reason, or that change the outcome, the work date, the violation kind, or the Intern's request reason | The note and reason amendments are recorded as new decision entries and both outcomes stay excused; every other amendment is refused and changes nothing; the outcome changes only by a reversal with its own reason. |

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
