# Leave Spec

**Version:** 1.3.0 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-21

**Module:** `attendance` · **Shared contract:** [MODULE.md](../../MODULE.md)

## 1. Context & Goal

Submit and decide leave while retaining quota and policy snapshots.

This is one cohesive planning scope. Operations below are sections of this feature,
not separate modules or mandatory branches. The split changes document ownership only.

## 2. Actors & Roles

Intern submits; current responsible Mentor decides eligible requests.

Use cases defined here: UC-10.

The [platform permission matrix](../../../platform/features/authorization/SPEC.md#52-permission-matrix)
and the module's shared authorization rules apply to every operation.

## 3. Functional Requirements

The operation summaries below are navigation. The numbered rules remain authoritative;
they do not acquire additional behavior from a heading or summary.

### Submit, edit or cancel

LEV-001 through LEV-013 define eligibility, quota, date windows and editable states.

### Decide and retain allocation

The same leave rules govern responsible-Mentor decisions, concurrent quota checks and retained day allocations.

### Canonical feature rules

| ID | Requirement |
|---|---|
| LEV-001 | THE system SHALL represent leave as a full-day inclusive date range with a nonblank reason. THE system SHALL NOT provide a leave type or a seven-day advance-notice rule in v1. |
| LEV-002 | THE system SHALL require a leave request to fall within the Intern's applicable internship interval and to contain at least one eligible workday after excluding configured non-workdays and global days off. |
| LEV-003 | WHEN a leave request is submitted, THE system SHALL materialize each quota-consuming date with its policy version, calendar month, and monthly quota snapshot. WHERE a request spans months, THE system SHALL allocate each date to its own month, and SHALL show the Intern those frozen allocations grouped by quota month. |
| LEV-004 | THE system SHALL reserve quota for `PENDING`, `OVERDUE`, and `APPROVED` leave days, and SHALL release it WHEN a request becomes `REJECTED`, `WITHDRAWN`, or `CANCELLED`, and for each date an amendment under `LEV-011` leaves without approved leave. For a selected quota month THE system SHALL show the Intern `reserved / applicable quota / remaining`, where remaining is `max(0, quota − reserved)`. THE system SHALL default the dashboard to the current business month and SHALL permit month selection in My Leave. |
| LEV-005 | WHEN quota is validated, THE system SHALL include existing pending, overdue, and approved allocations together with the candidate request, and SHALL serialize on the Intern profile so that concurrent submissions cannot overbook. |
| LEV-006 | WHERE a `PENDING`, `OVERDUE`, or `APPROVED` inclusive date range would overlap another for the same Intern, THE system SHALL reject it in both the application and the database. |
| LEV-007 | WHILE a request is pending and the scheduled start of its first counted workday has not passed, THE system SHALL permit the owning Intern to edit it. WHEN an edit is submitted, THE system SHALL revalidate overlap, frozen day allocations, and quota in one transaction. |
| LEV-008 | WHILE a request is pending before that same boundary, or overdue after it, and no period it touches is finalized, THE system SHALL permit the Intern's responsible Mentor under `ACC-026` to approve or reject it. THE system SHALL NOT permit an Admin to decide leave. |
| LEV-009 | THE system SHALL accept a same-day submission before the scheduled start of the first counted workday. Under the seeded defaults, a request whose first counted date is today is valid before 08:30 and invalid at or after 08:30. |
| LEV-010 | WHEN the first counted start is reached and a request submitted in time is still pending, THE system SHALL mark it `OVERDUE`, SHALL keep its quota reservation, and SHALL NOT reject it, because the approver rather than the Intern missed the deadline. THE system SHALL enforce that boundary from both the scheduler and the access-time guard. |
| LEV-011 | WHILE the first counted start has not passed, THE system SHALL permit the owning Intern to cancel an approved request, which becomes `CANCELLED`. WHEN leave begins, THE system SHALL freeze the request's date range and its materialized day allocation. THE system SHALL NOT reverse a leave decision. WHILE an approved request has begun and no period it touches is finalized, THE system SHALL permit the Intern's responsible Mentor under `ACC-026` to amend it under `ATT-024` only by withdrawing approval from one or more of its dates, never by adding a date or changing the frozen allocation. WHERE an amendment leaves a date without approved leave, THE system SHALL release that date's quota, SHALL NOT create or change an attendance record, and SHALL classify the date as a date without leave. Recalculating attendance and compliance for such a date from the current decision is a laboratory policy. |
| LEV-012 | THE system SHALL NOT create leave retroactively. WHILE the first counted start of a request has passed, THE system SHALL refuse every change to it except a decision on an `OVERDUE` request under `LEV-008`, a withdrawal under `LEV-013`, and an amendment under `LEV-011`. |
| LEV-013 | WHILE a request is `PENDING` or `OVERDUE` and no attendance period it touches is finalized, THE system SHALL permit the owning Intern to withdraw it. WHEN a request is withdrawn, THE system SHALL mark it `WITHDRAWN`, SHALL release its quota reservation and its overlap blocking, SHALL keep the request, its day allocations, and its history unchanged, and SHALL NOT delete or change any attendance record; each of its dates SHALL be classified as a date without leave. |
| DB-002 | THE schema SHALL enable `btree_gist` and SHALL use an exclusion constraint so that a pending, overdue, or approved leave range cannot overlap another for the same Intern. |

#### UC-10 — Request and decide leave

**Part A2.**

| Field | Specification |
|---|---|
| Primary actor(s) | Active Intern; the Intern's responsible Mentor |
| Trigger | An Intern requests, withdraws, or cancels leave, or the responsible Mentor decides a request or amends an approved one. |
| Preconditions | The date range is valid, non-overlapping, within internship dates, and before the first counted workday’s scheduled start. |
| Postconditions | The request and frozen day allocations preserve historical quota and attendance meaning. |
| Traced requirements | LEV-001–LEV-013, ATT-024 |

**Main success flow**

1. Enter an inclusive full-day range and reason.
2. Freeze eligible workdays, policies, quota months, and counted-day snapshots.
3. Reserve monthly quota for pending, overdue, and approved days.
4. The responsible Mentor approves or rejects before the boundary.
5. Allow approved cancellation only before the same boundary.

**Alternatives and exceptions**

- Global days off and non-workdays do not consume quota.
- Cross-month requests reserve each month independently.
- A request still pending at the boundary becomes overdue, keeps its quota, and can still be approved or rejected by the responsible Mentor.
- Overlapping pending, overdue, or approved ranges and exhausted quota are rejected transactionally.
- Until its period is finalized, the Intern may withdraw a pending or overdue request; its quota and overlap blocking are released and its dates are classified as without leave.
- A leave decision is never reversed. Before leave begins the Intern cancels an approved request; after it begins, until the period is finalized, the responsible Mentor may only amend it with a reason by withdrawing approval from dates, which releases their quota and leaves attendance as it happened.

#### State transitions: leave request

The transitions the rules above allow. The table adds nothing to them: where it and a rule differ, the rule wins.

| From | Action | To | Who | Rules |
|---|---|---|---|---|
| (none) | submit | `PENDING` | owning Intern | `LEV-001`–`LEV-003`, `LEV-009`, `LEV-010` |
| `PENDING` | edit, before the first counted start | `PENDING` | owning Intern | `LEV-007` |
| `PENDING` | the first counted start is reached | `OVERDUE` | system | `LEV-010` |
| `PENDING`, `OVERDUE` | approve | `APPROVED` | responsible Mentor | `LEV-008` |
| `PENDING`, `OVERDUE` | reject | `REJECTED` | responsible Mentor | `LEV-008` |
| `PENDING`, `OVERDUE` | withdraw | `WITHDRAWN` | owning Intern | `LEV-013` |
| `APPROVED` | cancel, before the first counted start | `CANCELLED` | owning Intern | `LEV-011` |
| `APPROVED` | amend after leave begins, only by withdrawing approval from dates | `APPROVED` | responsible Mentor | `LEV-011`, `ATT-024`, `DB-017` |

No transition leaves `REJECTED`, `WITHDRAWN` or `CANCELLED`, and a leave decision is never reversed (`LEV-011`, `ATT-024`). Every change is refused while a period the request touches is finalized, except inside a reopened range (`ATT-021`, `ATT-023`), and after the first counted start every change but those `LEV-012` names is refused.


## 4. Non-functional Requirements

Inherit [module constraints](../../MODULE.md#4-non-functional-requirements) and
[platform constraints](../../../platform/MODULE.md#4-non-functional-requirements),
including authorization, history, concurrency, server time and test evidence.

## 5. Data

`leave_requests`, `leave_request_days`, `leave_request_decisions`; shared history and request-state integrity stay in MODULE.md.

The [module data contract](../../MODULE.md#5-data) retains ownership, constraints and
checks across features. The full physical model stays in platform §19. A feature folder
does not grant repository/entity access across the module boundaries of ARC-006.

### Required contracts

These are required facts or behaviors, not prescribed cross-module imports or an
automatic implementation order. PLAN resolves delivery predecessors against what
already exists and preserves ARC-005/ARC-006.

| Provider | Required fact or behavior | Rule trace |
|---|---|---|
| [calendar](../../../calendar/MODULE.md) | Eligible days and applied quota versions | [ATT-001](../../../calendar/features/attendance-policy/SPEC.md), [ATT-002](../../../calendar/features/attendance-policy/SPEC.md), [ATT-003](../../../calendar/features/attendance-policy/SPEC.md), [CAL-006](../../../calendar/features/global-calendar/SPEC.md), [CAL-007](../../../calendar/features/global-calendar/SPEC.md), [CAL-008](../../../calendar/features/global-calendar/SPEC.md) |
| [internship](../../../internship/MODULE.md) | Internship interval and current responsible Mentor | [ACC-019](../../../internship/features/lifecycle/SPEC.md), [ACC-026](../../../internship/features/responsible-mentor/SPEC.md) |
| [attendance/period-finalization](../period-finalization/SPEC.md) | Closed/reopened date scope | [ATT-021](../period-finalization/SPEC.md), [ATT-022](../period-finalization/SPEC.md), [ATT-023](../period-finalization/SPEC.md) |
| [attendance](../../MODULE.md) | Shared decision history, state integrity and recipients | [ATT-024](../../MODULE.md), [DB-017](../../MODULE.md), [DB-018](../../MODULE.md), [NOT-011](../../MODULE.md) |

### Related workflows and joint checks

- [Punches and results](../punches-and-results/SPEC.md): Leave affects expected attendance.
- [Period finalization and reopening](../period-finalization/SPEC.md): Finalization constrains decisions and edits.

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
| [Submit, edit or cancel](#submit-edit-or-cancel) | Intern submits, edits, cancels or withdraws only an eligible leave request | [LEV-001](SPEC.md), [LEV-002](SPEC.md), [LEV-003](SPEC.md), [LEV-004](SPEC.md), [LEV-005](SPEC.md), [LEV-006](SPEC.md), [LEV-007](SPEC.md), [LEV-009](SPEC.md), [LEV-011](SPEC.md), [LEV-012](SPEC.md), [LEV-013](SPEC.md) | [AC-LEV-001](SPEC.md), [AC-LEV-002](SPEC.md), [AC-LEV-003](SPEC.md), [AC-LEV-004](SPEC.md), [AC-LEV-005](SPEC.md), [AC-LEV-006](SPEC.md), [AC-LEV-007](SPEC.md), [AC-DB-005](SPEC.md), [AC-DB-010](SPEC.md) | Cancellation of approved leave and withdrawal of pending/overdue leave are distinct operations; retain both cases. |
| [Decide and retain allocation](#decide-and-retain-allocation) | Responsible Mentor decides or amends leave without overbooking or rewriting frozen allocations | [LEV-004](SPEC.md), [LEV-005](SPEC.md), [LEV-006](SPEC.md), [LEV-008](SPEC.md), [LEV-010](SPEC.md), [LEV-011](SPEC.md), [LEV-012](SPEC.md), [ATT-024](../../MODULE.md) | [AC-LEV-002](SPEC.md), [AC-LEV-004](SPEC.md), [AC-LEV-006](SPEC.md), [AC-LEV-008](SPEC.md) | Include cross-month requests when only one touched period is finalized; shared finalization/history guards are mandatory. |

### Canonical acceptance scenarios

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-LEV-001 | LEV-001–LEV-003 | Request spans weekend, global day off, and two months | Only eligible dates materialize; each date uses its correct quota month/policy snapshot. |
| AC-LEV-002 | LEV-004–LEV-006 | Concurrent requests would exceed quota or overlap | Locking and exclusion constraint allow at most one valid outcome; no overbooking/overlap commits. |
| AC-LEV-003 | LEV-007 | Intern edits pending range | Original allocation is replaced only after new overlap/quota validation succeeds atomically. |
| AC-LEV-004 | LEV-008–LEV-010 | Same-day request submitted at 08:29:59 and at 08:30:00 | First may submit; second rejects. Pending at 08:30 becomes `OVERDUE` through the access guard even if the scheduler has not run, keeps its quota reservation, and can still be approved by the responsible Mentor. |
| AC-LEV-005 | LEV-011–LEV-012 | Intern cancels approved leave before and after first counted start | Before succeeds and releases quota; at/after boundary rejects and allocation remains frozen. |
| AC-LEV-006 | LEV-003–LEV-004, UI-019 | Intern opens the dashboard and My Leave across pending, overdue, approved, rejected, withdrawn, cancelled, and cross-month requests | Dashboard shows current-month reserved/quota/remaining; month selection recomputes from frozen allocations; pending, overdue, and approved requests reserve, rejected, withdrawn, and cancelled requests release, and each cross-month allocation remains separately labelled. |
| AC-LEV-007 | LEV-013, LEV-004, LEV-006 | An Intern withdraws one pending request before it starts and one overdue request whose two workdays have passed without a check-in, then submits a new request over the first range and tries to withdraw an approved request | Both withdrawals mark the requests `WITHDRAWN`, release their quota and overlap blocking, and keep each request, its day allocations, and its history; the two past workdays are classified `ABSENT`; the new request over the released range is accepted; withdrawing the approved request is refused. |
| AC-LEV-008 | LEV-004, LEV-011, ATT-024 | On the third day of an approved three-day leave, before the period is finalized, the responsible Mentor tries to reverse the approval, tries to add a fourth day, amends without a reason, and then amends with a reason to withdraw approval from the first day, on which the Intern did not check in | The reversal, the added day, and the amendment without a reason are refused; the amendment appends an entry with the Mentor, time, and reason while the approval and the frozen allocation stay unchanged in history; the first day's quota is released, no attendance record is created, and the day is classified `ABSENT`. |
| AC-DB-005 | DB-002 | `btree_gist` is queried after replay, then one Intern is given a pending leave range and a second overlapping range is inserted directly by SQL | The extension is present; the second insert is refused by the exclusion constraint; a non-overlapping range for the same Intern and an overlapping range for a different Intern both succeed. |
| AC-DB-010 | DB-002, LEV-006, LEV-013 | One Intern holds an `OVERDUE` leave range and, separately, a `WITHDRAWN` one; an overlapping range for the same Intern is then inserted directly by SQL against each | The insert overlapping the `OVERDUE` range is refused by the exclusion constraint exactly as one overlapping a `PENDING` or `APPROVED` range is; the insert overlapping the `WITHDRAWN` range succeeds, because withdrawal released its overlap blocking. |

Shared and cross-feature scenarios in [MODULE.md](../../MODULE.md#7-acceptance-criteria)
also apply. Scenario ownership follows the behavior exercised, not every prerequisite
named by its Requirements cell. Relocation preserves every existing expected result.

## 8. Out of Scope

Inherit [module exclusions](../../MODULE.md#8-out-of-scope). Adjacent feature behavior
is a dependency, not a duplicated contract. This split creates no new product behavior,
schema, dependency, PLAN.md or TASKS.md.

## Notes / Open Questions

No question affecting this feature is open; the cross-feature checks and shared contracts in MODULE.md still apply.

Two facts the migration must carry, recorded in `D39`. First, `ex_leave_requests_no_overlap` in
`V1__baseline.sql` blocks only `PENDING` and `APPROVED` ranges, while `DB-002` and `LEV-006`
require `OVERDUE` too; widening the status constraint alone would leave overdue leave free to
overlap, and `AC-DB-010` is the scenario that fails until the exclusion is widened. Second, the
code has cancelled pending requests as `CANCELLED`, which `LEV-013` now calls `WITHDRAWN`. A
stored `CANCELLED` row with no decision time is such a request and becomes `WITHDRAWN`: its
`cancelled_at` becomes the withdrawal time `DB-018` requires and is then cleared, because
`ck_leave_requests_cancellation` allows it only on `CANCELLED`. A row with a decision time was
approved and stays `CANCELLED`. The rows are reclassified before the tightened decision checks are
added, or those checks would refuse them. That reading is derived from `LeaveRequestEntity#cancel`,
which never clears the decision and has been reachable only by the owning Intern, and must be
confirmed against the stored rows, once the migration history of the database being read is
known (`D39`).

Read [shared open questions](../../MODULE.md#notes--open-questions) before approving
the technical plan. [plan.md](../../../../../plan.md) is the only progress tracker.
