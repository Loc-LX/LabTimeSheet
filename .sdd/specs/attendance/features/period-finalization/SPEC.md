# Period finalization and reopening Spec

**Version:** 1.1.0 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-21

**Module:** `attendance` · **Shared contract:** [MODULE.md](../../MODULE.md)

## 1. Context & Goal

Finalize an attendance period and control any authorized reopening before further decisions.

This is one cohesive planning scope. Operations below are sections of this feature,
not separate modules or mandatory branches. The split changes document ownership only.

## 2. Actors & Roles

The system finalizes normal periods under ATT-020. The Intern or their responsible
Mentor requests reopening; Admin approves or rejects reopening only. The responsible
Mentor acts within the reopened range and finalizes that range again under ATT-023.

Use cases defined here: UC-17.

The [platform permission matrix](../../../platform/MODULE.md#52-permission-matrix)
and the module's shared authorization rules apply to every operation.

## 3. Functional Requirements

The operation summaries below are navigation. The numbered rules remain authoritative;
they do not acquire additional behavior from a heading or summary.

### Finalize period

ATT-019 through ATT-023 define cutoff, retained unresolved requests and the closed-period guard.

### Request and authorize reopening

Reopening authorization does not let Admin decide leave, corrections or exceptions. Preserve the request and period histories.

### Canonical feature rules

| ID | Requirement |
|---|---|
| ATT-019 | THE system SHALL hold each Intern's attendance in monthly attendance periods, one per calendar month in the business timezone. A period covers the attendance rows, leave days, corrections, and attendance exceptions dated in that month; a leave request spanning months belongs to every period it touches. |
| ATT-020 | WHEN 23:59 on the fifth day of the following month is reached, THE system SHALL finalize an Intern's period unless a leave, correction, or attendance exception request affecting that period is pending or overdue. WHERE such a request remains, THE system SHALL keep the period open and SHALL finalize it as soon as the last such request is decided or withdrawn. The five-day grace is a laboratory policy (`D24`). |
| ATT-021 | WHILE a period or a reopened range within it is finalized, THE system SHALL refuse every change to the attendance results it covers, including every decision or decision change on leave, corrections, and attendance exceptions and every leave withdrawal or cancellation, and SHALL refuse new requests for its dates. |
| ATT-022 | WHEN the Intern's responsible Mentor or the Intern asks to reopen a finalized period, THE system SHALL require a nonblank reason and the attendance records or date range concerned, and SHALL retain the requester and the server time. WHILE the request is undecided, THE system SHALL permit an Admin to approve or reject it, deciding only whether to reopen, from the reason, the records or range requested, and the data-governance and finalization rules. WHEN an Admin approves it, THE system SHALL reopen only those records or that range and SHALL retain the Admin and the server time. WHEN an Admin rejects it, THE system SHALL require a nonblank reason, SHALL retain the Admin, the server time, and that reason, and SHALL leave the period finalized. THE system SHALL NOT let the Admin approve, reject, correct, or decide any leave, correction, or attendance exception. |
| ATT-023 | WHILE a range is reopened, THE system SHALL permit only the Intern's responsible Mentor under `ACC-026` to approve, reject, correct, decide, or reverse within it under the rules that applied before finalization, and SHALL let that Mentor finalize the range again. WHEN the range is finalized again, THE system SHALL retain the Mentor and the server time. |
| DB-014 | THE schema SHALL hold at most one attendance period per Intern and calendar month, with a status of `OPEN` or `FINALIZED` and the server time of its latest finalization, and SHALL enforce that uniqueness with a constraint. |
| DB-015 | THE schema SHALL store each request to reopen a finalized period under `ATT-022` with its period, requester, the records or date range requested, a nonblank reason, the server time, and a status of `PENDING`, `APPROVED` or `REJECTED`. A decided request SHALL carry the deciding Admin and the server time, a rejected one a nonblank rejection reason, and a range finalized again under `ATT-023` the Mentor and the server time. |

#### UC-17 — Reopen a finalized attendance period

**Part A5.**

| Field | Specification |
|---|---|
| Primary actor(s) | The Intern or their responsible Mentor (requester); Admin (approves or rejects the reopen); the responsible Mentor (acts and finalizes again) |
| Trigger | A result in a finalized attendance period needs to change. |
| Preconditions | The period is finalized under `ATT-020`. |
| Postconditions | Only the named records or dates changed, or nothing changed if the request was rejected; the request, its decision, and any renewed finalization are retained with actor, time, and reason. |
| Traced requirements | ATT-019–ATT-024, EXC-007, ACC-026, NOT-011 |

**Main success flow**

1. The Intern or the responsible Mentor asks to reopen, naming the records or date range and a reason.
2. An Admin approves the request, which reopens exactly that range; the approval is recorded.
3. The responsible Mentor makes the correction, approval, or decision the change needs.
4. The responsible Mentor finalizes the range again.

**Alternatives and exceptions**

- An Admin rejects the request with a reason; the rejection is recorded and the period stays finalized.
- An Admin attempting to approve, correct, or decide inside the reopened range is refused.
- Dates outside the reopened range stay finalized and refuse changes.

#### State transitions: attendance period

The transitions the rules above allow. The table adds nothing to them: where it and a rule differ, the rule wins.

| From | Action | To | Who | Rules |
|---|---|---|---|---|
| (none) | a calendar month of the Intern's attendance | `OPEN` | system | `ATT-019`, `DB-014` |
| `OPEN` | 23:59 on the fifth day of the following month with no pending or overdue request affecting it, or later, when the last such request is decided or withdrawn | `FINALIZED` | system | `ATT-020` |

A finalized period stays `FINALIZED`. Its results change only inside a range reopened by a request below (`ATT-021`, `ATT-022`).

#### State transitions: request to reopen a finalized period

The transitions the rules above allow. The table adds nothing to them: where it and a rule differ, the rule wins.

| From | Action | To | Who | Rules |
|---|---|---|---|---|
| (none) | ask to reopen, with a reason and the records or range concerned | `PENDING` | Intern, or their responsible Mentor | `ATT-022`, `DB-015` |
| `PENDING` | approve, reopening only those records or that range | `APPROVED` | Admin | `ATT-022`, `DB-015` |
| `PENDING` | reject, with a reason | `REJECTED` | Admin | `ATT-022`, `DB-015` |
| `APPROVED` | finalize the reopened range again | `APPROVED` | responsible Mentor | `ATT-023`, `DB-015` |

Inside an approved range only the responsible Mentor acts, under the rules that applied before finalization (`ATT-023`); the Admin decides nothing but whether to reopen (`ATT-022`).

## 4. Non-functional Requirements

Inherit [module constraints](../../MODULE.md#4-non-functional-requirements) and
[platform constraints](../../../platform/MODULE.md#4-non-functional-requirements),
including authorization, history, concurrency, server time and test evidence.

## 5. Data

`attendance_periods`, `attendance_period_reopens`.

The [module data contract](../../MODULE.md#5-data) retains ownership, constraints and
checks across features. The full physical model stays in platform §19. A feature folder
does not grant repository/entity access across the module boundaries of ARC-006.

### Required contracts

These are required facts or behaviors, not prescribed cross-module imports or an
automatic implementation order. PLAN resolves delivery predecessors against what
already exists and preserves ARC-005/ARC-006.

| Provider | Required fact or behavior | Rule trace |
|---|---|---|
| [attendance](../../MODULE.md) | Retained request states and recipient selection | [DB-017](../../MODULE.md), [DB-018](../../MODULE.md), [NOT-011](../../MODULE.md) |
| [internship/responsible-mentor](../../../internship/features/responsible-mentor/SPEC.md) | Current Mentor for reopened decisions | [ACC-026](../../../internship/features/responsible-mentor/SPEC.md) |
| [platform](../../../platform/MODULE.md) | Policy-controlled reopening authorization | [AUTH-012](../../../platform/MODULE.md) |

### Related workflows and joint checks

- [Leave](../leave/SPEC.md): Reopened scope controls leave decisions.
- [Missed-checkout correction](../missed-checkout-correction/SPEC.md): Reopened scope controls corrections.
- [Attendance exception](../attendance-exception/SPEC.md): Reopened scope controls excuses.

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
| [Finalize period](#finalize-period) | System finalizes a monthly period only after its cutoff and request guards permit it | [ATT-019](SPEC.md), [ATT-020](SPEC.md), [ATT-021](SPEC.md), [DB-014](SPEC.md) | [AC-ATT-009](SPEC.md), [AC-DB-006](../../MODULE.md) | Exercise a request spanning two periods and the final outstanding request being withdrawn/decided. |
| [Request and authorize reopening](#request-and-authorize-reopening) | Intern or responsible Mentor requests reopening; Admin decides only that request; Mentor later refinalizes | [ATT-022](SPEC.md), [ATT-023](SPEC.md), [DB-015](SPEC.md), [NOT-011](../../MODULE.md) | [AC-ATT-010](SPEC.md), [AC-DB-006](../../MODULE.md) | Verify exact reopened range and denial of Admin leave/correction/exception decisions. |

### Canonical acceptance scenarios

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-ATT-009 | ATT-019–ATT-021, LEV-010, COR-007 | On 5 October at 23:59 one Intern's September period has no open request, while another's has a correction left undecided past its window; that correction is decided on 7 October | The correction became `OVERDUE`, not rejected; the first period finalizes at the deadline and every later change to its September results is refused; the second stays open, finalizes when the correction is decided, and then refuses changes too. |
| AC-ATT-010 | ATT-022–ATT-023, NOT-011 | After September is finalized, the Intern asks to reopen one date with a reason and an Admin rejects it, first without a reason and then with one; the responsible Mentor asks to reopen another date, an Admin approves it and tries to decide an exception in it, and the Mentor reverses the exception decision and finalizes the date again | Admins are notified of each request and the requester of each outcome; the rejection without a reason is refused, and the rejection with one keeps the Admin, time, and reason and leaves the date finalized; the approval records the Admin and time and reopens only the second date; the Admin's exception decision is refused; the Mentor's reversal commits and the date is finalized again with the Mentor and time; every other September date stays finalized throughout. |

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
