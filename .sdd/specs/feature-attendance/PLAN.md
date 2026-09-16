# Attendance Plan

**Version:** 0.1 · **Owner:** Loc-LX · **Status:** DRAFT, awaiting approval · **Date:** 2026-09-16

How the attendance rules of [SPEC.md](SPEC.md) will be built. It is a technical design, not
a tracker: progress belongs in [`plan.md`](../../../plan.md). The rules themselves are in
the spec, and where this plan and the spec disagree, the spec wins. The schema is designed
once for every feature in [the platform plan](../feature-platform/PLAN.md) §3; this plan
does not repeat it and does not change it.

## 1. Scope

**In scope.** The attendance behavior the recorded decisions added and have no code:

- **Attendance periods and finalization**, `ATT-019`–`ATT-023`. The concept does not exist in the code at all.
- **Attendance exceptions**, `EXC-001`–`EXC-007`. An excused late arrival or early departure, requested by the Intern or marked by the responsible Mentor. No code, no table, no route.
- **The shared decision mechanism**, `ATT-024`. Append-only history, latest effective decision as current, audited amendment and reversal.
- **The unified adjustment clock**, `COR-003`, `COR-004`, `EXC-002`, `EXC-003`. Corrections move from 24/24 hours to 48/48 and gain a period boundary.
- **Leave changes**, `LEV-010`–`LEV-013`: `OVERDUE` that is not a rejection, withdrawal by the Intern, amendment of approved leave that withdraws dates without touching the frozen snapshot.
- **Compliance**, `ATT-016`: an excused violation leaves the applicable count and stays visible.

**Out of scope, owned elsewhere.** Naming them here is what keeps this plan from
growing a second copy of somebody else's decision.

| Concern | Rules | Owner |
|---|---|---|
| Every table this plan stores into | §3 of this document | [platform plan](../feature-platform/PLAN.md) §3 |
| Who may call each operation | `AUTH-012`, §5.2 matrix | [platform plan](../feature-platform/PLAN.md) §2 |
| Assigning and replacing the responsible Mentor | `ACC-021`, `ACC-026` | account plan |
| Who is notified, and on what | `NOT-011`, `NOT-003` | notification plan |
| Showing any of it in a report or export | `RPT-004`, `RPT-015` | reporting plan |

**Unchanged.** `ATT-001`–`ATT-015`, `ATT-017`, `ATT-018`, `CAL-001`–`CAL-009`, and
`LEV-001`–`LEV-009` are built and keep their behavior. They appear below only where a new
rule reads from them, never as work.

**Blocked by nothing.** Every decision this plan implements is confirmed for build:
`D14` on 14 September 2026, `D23`, `D24` and `D27` on 16 September. Steps 1 to 4 need
`V3`, which is step 5 of the platform plan and itself waits on nothing.

## 2. Design

### 2.1 The one concept the code does not have

Everything new in this plan hangs off a single idea the current code has no name for: an
**attendance period**. Today an attendance row, a leave day and a correction are each
governed by their own deadline and nothing else. `D14` added a second boundary that sits
above all of them, and most of the work below is making one boundary answer for many
kinds of record.

A period is one Intern and one calendar month in the business timezone. It is `OPEN` or
`FINALIZED`. `ATT-020` closes it at 23:59 on the fifth day of the following month unless
a leave, correction or exception affecting it is pending or overdue, and `ATT-021` refuses
every change to an attendance result inside a finalized period. `ATT-022` reopens a named
range, and `ATT-023` says only the responsible Mentor acts inside it.

**A period row is created lazily**, by the first write that needs one for that Intern and
month: an attendance row, a leave day, a correction, an exception. There is no monthly job
that creates periods for everyone, because a month nobody touched has nothing to finalize
and no result to protect. The migration backfills the months already worked, and `D27`
says what state each of them gets.

### 2.2 Two kinds of adjustment, one clock, one mechanism, different evidence

`D23` made a missed-checkout correction and an attendance exception share a policy without
making them the same workflow, and `ATT-024` says exactly how far the sharing goes. The
distinction is worth stating precisely, because getting it wrong in either direction is
the obvious way to build this badly.

| | Correction (`COR-*`) | Exception (`EXC-*`) |
|---|---|---|
| What it changes | the effective checkout of an attendance row | whether a recorded violation is excused |
| Raised by | the owning Intern only | the Intern, **or** the responsible Mentor marking one directly |
| Evidence it carries | a proposed checkout and a reason | a reason |
| Submit deadline | scheduled end + 48 hours, and the period | the same |
| Decide deadline | 48 hours from submission, and the period | the same |
| Decided by | the responsible Mentor | the responsible Mentor |
| Effect on the raw record | none; the raw checkout stays null | none; the classification stays |

**Shared:** the clock, the period boundary, the decision history shape, the meaning of
`OVERDUE`, and the rule that a decided request does not lock. **Not shared:** the states,
the actions, the evidence, and the direct Mentor mark, which exists for exceptions and has
no correction equivalent.

So the code gets one deadline policy and one decision log, and two aggregates that use
them. It does not get an `AdjustmentRequest` supertype with a `kind` column; that would
put the differences into `if` branches inside shared code, which is the shape that makes
later rule changes touch both workflows at once.

### 2.3 One decision log, three users, three tables

`ATT-024` asks for immutable decision history with the latest effective entry as current,
plus audited amendment and reversal. Three aggregates need it: leave, corrections,
exceptions.

- **Corrections already have theirs.** `attendance_correction_events` exists and carries `event_type`, `from_status`, `to_status`, `actor_user_id`, `note`, `occurred_at`. It is not replaced. It gains the `AMENDED` and `REVERSED` kinds, and for those two kinds the note becomes the required nonblank reason.
- **Exceptions and leave get theirs** from the platform plan §3.1: `attendance_exception_decisions` and `leave_request_decisions`, written to the same shape.
- **The logic exists once.** A small package-private helper in the attendance service layer appends an entry and resolves the current decision as the latest effective one. Three tables, one implementation; per `ARC-006` the SQL stays in repositories.

Three tables rather than one polymorphic table because each references a different parent
key, and a shared table would need either a nullable foreign key per kind or no foreign
key at all. `DB-007` puts state constraints in the database, which a nullable-key design
cannot express.

### 2.4 Two boundaries, checked in one place

Every write path in this feature now has to answer two questions: has the request deadline
passed, and is the period finalized. `COR-008` requires both the scheduled worker and the
request-time guard, and `COR-009`, `ATT-021` and `EXC-004` each restate the period
boundary for their own aggregate.

The design is a single guard resolved from `(intern, business date)` that every write path
calls before acting, returning the period and whether the date sits inside a reopened
range. The alternative, repeating the check in each service method, is how `COR-009`,
`ATT-021` and `EXC-004` end up disagreeing with each other after the third change.

The deadline sweep extends the component that already exists.
`AttendanceDeadlineScheduler` runs a bounded batch of `leave.expirePending` and
`corrections.expire` on a fixed delay. It gains exception expiry and period finalization,
keeping the same shape: bounded batches, request-time guards still authoritative when the
worker is late.

### 2.5 What the code has today

Measured on 16 September 2026, not estimated.

| Area | Today | What this plan does to it |
|---|---|---|
| `LeaveApplicationService` | 772 lines; full lifecycle, quota, snapshot, expiry | Add `WITHDRAWN` and the `LEV-011` amendment; route both through the decision log and the period guard |
| `AttendanceCorrectionApplicationService` | 603 lines; submit, decide, expire, event log | Move 24 to 48 hours in two places, add `OVERDUE`, add amendment and reversal, add the period guard, stop writing `locked_at` |
| `AttendanceDeadlineScheduler` | 26 lines; two sweeps | Add exception expiry and period finalization |
| `AttendanceReportQueryService` | 321 lines; computes the compliance score | Exclude excused violations under `ATT-016` and `EXC-005` |
| `AttendanceRequestController` | 401 lines; correction and leave routes | Add exception and reopen routes |
| Exceptions, periods, reopens | nothing exists | New service, new controller routes, new templates |
| `templates/attendance/` | six templates | `requests.html` and `history.html` change; exceptions and reopen need their own |

The two 48-hour literals are at `AttendanceCorrectionApplicationService.java:199` and
`:221`, written as `24 * 60 * 60L`. They become one named policy value rather than two more
literals, because `COR-003`, `COR-004`, `EXC-002` and `EXC-003` must move together or
`D23` is only half true.

### 2.6 Alternatives considered

| Option | Why not |
|---|---|
| One `attendance_adjustments` table with a `kind` column for corrections and exceptions | The two carry different evidence and different actors. One table means nullable columns that only one kind uses, which `DB-007` cannot constrain, and shared code branching on kind |
| Finalize periods on read, with no worker | `ATT-020` fires on a clock, not on traffic. An Intern nobody reads would keep an open month indefinitely, and `LEV-010` and `COR-007` already established the worker-plus-guard pattern |
| Create every Intern's period at the start of each month | A job that must not be missed, for rows that may never be needed. Lazy creation plus the `D27` backfill covers the same ground with nothing to schedule |
| Keep `locked_at` and stop reading it | `D14` removed the lock; a column still being written is a rule still in force to the next reader. The platform plan keeps the existing values as a record and stops writing new ones |

## 3. Data model

Every table is designed in [the platform plan](../feature-platform/PLAN.md) §3 and created
by the single `V3` migration. This feature owns four of them: `attendance_periods`,
`attendance_period_reopens`, `attendance_exceptions`, `attendance_exception_decisions`,
and shares `leave_request_decisions` with nothing else.

Invariants this feature enforces above what the schema can express:

- Exactly one period per Intern per month, and no attendance result inside a finalized period changes except inside a range reopened under `ATT-022` (`ATT-020`, `ATT-021`).
- A decision log is append-only. No update, no delete; the current decision is the latest effective entry (`ATT-024`).
- An amendment to approved leave marks a date as no longer approved and never rewrites the policy version or quota snapshot that date carries (`LEV-011`, `GOV-005`, `LEV-003`).
- A withdrawal releases the quota reservation and the overlap block, and changes no attendance fact: a day the Intern was absent is still absent (`LEV-013`).
- At most one exception request per attendance row and violation kind (`EXC-002`).

## 4. Sequence

Each step is reviewable on its own and names what it satisfies. A step starts only when
the step it depends on is green.

| # | Step | Satisfies | Depends on |
|---|---|---|---|
| 1 | Periods: lazy creation, the guard of §2.4, and the finalization sweep, with every existing write path calling the guard | `ATT-019`–`ATT-021`, `COR-009` | platform step 5 |
| 2 | Reopen: request, Admin approval or refusal with a reason, and the responsible Mentor acting inside the reopened range | `ATT-022`, `ATT-023` | 1 |
| 3 | The decision log helper of §2.3, applied first to corrections, whose event table already exists | `ATT-024`, `COR-005`, `COR-007` | 1 |
| 4 | The adjustment clock: one policy value, 48 hours in both directions, `OVERDUE` on corrections, the sweep extended | `COR-003`, `COR-004`, `COR-007`, `COR-008` | 3 |
| 5 | Exceptions end to end: request, direct Mentor mark, decision, amendment, reversal, expiry | `EXC-001`–`EXC-007` | 3, 4 |
| 6 | Leave: `WITHDRAWN`, and the `LEV-011` amendment through the same log | `LEV-011`–`LEV-013` | 3 |
| 7 | Compliance: excused violations leave the applicable count and stay visible | `ATT-016`, `EXC-005` | 5 |

Steps 3 and 4 come before 5 deliberately. Corrections already have a decision log and a
deadline sweep, so the shared mechanism is proved against working code before an aggregate
that has none is built on it. Building exceptions first would mean designing the shared
shape against the only aggregate that cannot contradict it.

Step 1 touches every existing write path in the feature. It is first because a period
guard added after the other steps would have to be retrofitted into code written without
it, and `ATT-021` is the rule most expensive to get wrong: it is what stops last month's
numbers from moving.

## 5. Risks

- **Step 1 is the widest change in the feature.** Every leave, correction and attendance write path gains a call. The mitigation is that the guard has one implementation and one test, and that the step ships before any new aggregate exists to complicate it.
- **The period boundary and the request deadline can disagree.** `EXC-002` and `COR-003` are bounded by both, and the real deadline is whichever comes first. A request submitted on the 30th has 48 hours on paper and five days in the month. Both boundaries are evaluated in the guard, never one in the service and one in the worker.
- **`LEV-011` recalculation is laboratory policy.** When an amendment leaves a past date without leave, attendance and compliance are recomputed from the current decision. That is a choice, recorded in `D14`, not an industry standard; if the instructor revises it, step 6 and step 7 both move.
- **Excused violations change a published number.** `ATT-016` changes the compliance score for months that already have one. Periods finalized before the change keep the number they were finalized with; the recomputation applies to open periods only. Anything else would move history, which `GOV-005` forbids.
- **Two workers, one clock.** The sweep now carries four deadline kinds. A slow batch must not let a fifth kind starve; the existing bounded-batch shape is kept, one bounded batch of each kind per invocation.

## 6. Verification

| What proves it | Scenario | Step |
|---|---|---|
| A period closes on time, and does not close while a request is open | `AC-ATT-009` | 1, 4 |
| Reopen is requested, refused without a reason, refused with one, approved, and acted in | `AC-ATT-010` | 2 |
| A decided correction is amended, its proposed checkout refused, reversed, then refused after finalization | `AC-COR-003` | 3, 4 |
| A Mentor marks an excuse without a reason, with one, and after the period closed; another Mentor and the Leader are refused | `AC-EXC-002` | 1, 5 |
| A request goes 48 hours undecided, is excused, reversed to unexcused, then refused after finalization | `AC-EXC-003` | 4, 5 |
| An approved leave is amended, reversal refused, a fourth day refused, amendment without a reason refused | `AC-LEV-008` | 6 |
| An excused violation leaves the applicable count and stays visible in history | `AC-EXC-001`, `AC-EXC-003` | 7 |

Each step also carries the architecture and layer tests the repository already runs, and
per `TST-003` the test is written from the rule before the implementation, not from the
implementation after it.

## 7. Open questions

None. Every decision this plan depends on was confirmed for build by 16 September 2026,
and the schema question it would otherwise carry is answered in the platform plan by
`D27`. Two things are deliberately left to the step that meets them rather than decided
here in advance:

- The exact named constant for the 48-hour policy value, and whether it is configuration or a constant, is decided in step 4 against `ARC-004`.
- Whether the reopen routes live in `AttendanceRequestController` or a controller of their own is decided in step 2, when their size is known.
