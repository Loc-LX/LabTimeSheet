# Task Plan

**Version:** 0.1 · **Owner:** Loc-LX · **Status:** SUPERSEDED by `D28` · **Date:** 2026-09-16

How the Task rules of [SPEC.md](SPEC.md) will be built. It is a technical design, not a
tracker: progress belongs in [`plan.md`](../../../plan.md). The rules themselves are in the
spec, and where this plan and the spec disagree, the spec wins. The schema is designed once
for every feature in [the platform plan](../feature-platform/PLAN.md) §3; this plan does
not repeat it and does not change it.

## 1. Scope

**In scope.** Four rules that `D13` and `D15` changed and the code has not caught up with.

- **`TSK-023`** — a status change is decided from role, scope, current status and target status. The owning Mentor may block, unblock and reopen, and nothing else.
- **`TSK-025`** — every block, unblock and reopen keeps a transition record, and unblock takes the status to return to from the latest block record.
- **`TSK-024`** — the current Leader may append a Remaining effort forecast whenever the prediction changes, not only at a reassignment.
- **`TSK-021`** — the current Remaining effort is computed for every unfinished Task from the latest effective forecast, not only as variance on a `DONE` Task.

**Out of scope, owned elsewhere.**

| Concern | Rules | Owner |
|---|---|---|
| `task_status_transitions`, the only new table | — | [platform plan](../feature-platform/PLAN.md) §3.1 |
| Removing the Mentor's unrestricted status change | `AUTH-012`, `TSK-023` refusal half | [platform plan](../feature-platform/PLAN.md) §4 step 2 |
| Who is notified when a status changes | `NOT-003` | notification plan |
| Showing Remaining effort and variance in a report | `RPT-012`, `RPT-014` | reporting plan |
| Project lifecycle and membership intervals | `PRJ-*` | project plan |

**Unchanged and built.** `TSK-001`–`TSK-020` and `TSK-022` are implemented and keep their
behavior. `TSK-022` in particular already works: `TaskTransferService` carries exactly one
forecast per worked Task through a confirmed batch, and this plan does not touch it beyond
reusing what it proved.

**Blocked by nothing.** `D13` and `D15` were confirmed for build on 16 September 2026.
Steps 1 and 2 need `task_status_transitions`, which is step 5 of the platform plan, and
step 5 waits on nothing.

## 2. Design

### 2.1 The split that makes `TSK-023` two pieces of work

`TSK-023` has a refusing half and a granting half, and they belong in different plans.
The refusal — an owning Mentor may not set any status — needs no storage and is step 2 of
the platform plan, where the authorization policy is introduced and the capability the
§5.2 matrix does not grant is taken out of `TaskService#changeStatus`. The grant — a
Leader and an owning Mentor may block, unblock and reopen — needs the transition record of
`TSK-025` to exist first, because unblock cannot return a Task to its previous status
without one.

So the order is: the platform plan closes the hole, this plan opens the three doors that
replace it. Between the two steps an owning Mentor can do less than the rule allows, which
is the safe direction to be wrong in.

### 2.2 Where the previous status comes from

`TSK-025` says the status a Task returns to on unblock is taken from the record of its
latest block. That is the whole reason the table exists, and it rules out two cheaper
designs:

- **A `previous_status` column on `tasks`.** One slot, overwritten by each block, and it answers nothing about who blocked the Task or when. `GOV-009` asks for the narrow history, not the current value.
- **Deriving it from Task comments.** `TSK-025` says a reopen reason may be shown as a comment, and that the transition record is the audit trail. A comment is text a person can edit; a transition record is not.

The record holds actor, server time, previous status, new status, and for a reopen the
nonblank reason. Unblock reads the latest record whose kind is `BLOCK` for that Task and
restores its previous status. If there is none, the Task was not blocked and the unblock is
refused, which falls out of the same query rather than needing a separate check.

### 2.3 Forecasts outside a reassignment

`TSK-024` lets the Leader append a forecast at any time; today the only writer is
`TaskTransferService`, at a reassignment. The question the spec left open — recorded in the
notes of [SPEC.md](SPEC.md) — is whether such a forecast can use the existing columns.

It can, and this plan confirms it. `TaskQueryService#latestForecast` already selects
forecasts by `incomingMembershipId == task.assigneeMembershipId` and
`assignmentStartedAt == task.assignedAt`, then takes the newest that nothing supersedes. A
forecast appended outside a reassignment is written with the current assignee membership
and the current assignment start, so it is selected by exactly that filter with no schema
change and no second code path. The append-only and supersession rules are already
enforced there.

What this does need is a second entry point: the transfer path validates a batch, and a
standalone append validates one Task. They share the row shape and the validation of the
value; they do not share the batch confirmation, which exists for `TSK-022` alone.

### 2.4 Remaining effort for an unfinished Task

`TSK-021` defines the current Remaining effort as zero for a `DONE` Task, and otherwise the
latest effective forecast less the Actual effort logged since that forecast was taken.
Every input already exists: the forecast carries `remainingMinutes` and
`actualMinutesSnapshot`, and the lifetime actual is already summed for the Task list.

The computation is therefore one method, not a subsystem:
`remaining = max(0, forecast.remainingMinutes - (actualNow - forecast.actualMinutesSnapshot))`.
It is floored at zero because a Task worked past its forecast has no negative effort left;
the overrun shows as variance against the estimate, which is a different number and stays
where it is.

The code today computes variance for `DONE` Tasks only. Extending it is where the risk
sits, because the same numbers appear in the Daily report, and `GOV-015` keeps the estimate
as the baseline while the forecast is a current prediction. This plan changes the
computation in the task feature; the reporting plan decides what is displayed.

### 2.5 What the code has today

Measured on 16 September 2026, not estimated.

| Area | Today | What this plan does to it |
|---|---|---|
| `TaskService` | 1,386 lines; `changeStatus` at `:165` lets an owning Mentor take any allowed edge, bypassing the assignee check at `:182` | Replace the bypass with block, unblock and reopen, each writing a transition record |
| `TaskTransferService` | 368 lines; the only forecast writer, batch-validated | Extract the single-forecast validation it already performs, leave the batch alone |
| `TaskQueryService` | 281 lines; `latestForecast` at `:225` already selects the effective forecast | Add the `TSK-021` current Remaining effort for unfinished Tasks |
| `TaskStatus` | the transition graph, `canTransitionTo` | Unchanged. `TSK-023` decides who may take an edge, not which edges exist |
| Transition records | nothing exists | New table, repository, and writes on three paths |

**Two existing tests assert the behavior that `TSK-023` removes.** They are not weakened,
they are rewritten against the new rule, with the reason recorded in the commit. `TST-011`
forbids relaxing an assertion to make a build pass; it does not forbid changing a test when
the rule it encodes has changed, and the distinction is the recorded decision.

### 2.6 Alternatives considered

| Option | Why not |
|---|---|
| Let the Mentor keep every edge and restrict it in the UI only | `AUTH-012` requires the service to enforce the decision and the template to ask the same policy. A UI-only restriction is not a restriction |
| Store the reopen reason only as a Task comment | `TSK-025` makes the transition record the audit trail and the comment optional. A comment can be edited or deleted |
| Recompute Remaining effort by replaying every work log | The forecast already carries the snapshot it was taken against. Replaying is the same answer at more cost, and it would drift the moment a retained log moves |

## 3. Data model

One new table, `task_status_transitions`, designed in
[the platform plan](../feature-platform/PLAN.md) §3.1. No existing table changes.

Invariants this feature enforces above what the schema can express:

- A transition record is append-only: written on block, unblock and reopen, never updated or deleted (`TSK-025`, `GOV-009`).
- A reopen record carries a nonblank reason; a block or unblock record does not require one (`TSK-025`).
- A forecast is never edited or replaced; a correcting successor is accepted only before the incoming assignee's first newly created work log (`TSK-022`, `TSK-024`).
- The estimate is the baseline and a forecast never changes it (`GOV-015`, `TSK-024`).

## 4. Sequence

| # | Step | Satisfies | Depends on |
|---|---|---|---|
| 1 | The transition record: table writes, and block and reopen taking it | `TSK-025` block and reopen halves | platform step 5 |
| 2 | Unblock reading the latest block record to restore the previous status | `TSK-025` unblock half, `TSK-023` grant | 1 |
| 3 | Standalone forecast append by the current Leader, reusing the transfer path's single-forecast validation | `TSK-024` | platform step 5 |
| 4 | Current Remaining effort for unfinished Tasks | `TSK-021` | 3 |

Step 1 before step 2 because unblock has nothing to read until block writes. Step 3 before
step 4 because a Remaining effort computed over forecasts that only reassignment can create
would be tested against a case the rule no longer restricts it to.

This plan assumes step 2 of the platform plan has already removed the Mentor's
unrestricted status change. If it has not, steps 1 and 2 here still work, but the rule is
only half true until it does.

## 5. Risks

- **The status graph and the status policy are different things, and the code currently mixes them.** `canTransitionTo` answers which edges exist; `TSK-023` answers who may take one. Step 2 must not push actor logic into `TaskStatus`, or the graph becomes untestable on its own.
- **Remaining effort appears in the Daily report.** Changing it in the task feature changes what the reporting feature shows. The reporting plan is written after this one for that reason, and `GOV-004` still holds: none of this touches attendance.
- **Two tests change.** Changing a test alongside a rule is legitimate and changing one to make a build pass is not. The difference must be visible in the commit, or the next reader cannot tell them apart.
- **`TaskService` is 1,386 lines before this work.** Every step adds to it. If step 2 pushes it past what a reviewer can hold, the transition record reads and writes move to their own service rather than growing the file further; that is a judgement made in the step, not avoided in advance.

## 6. Verification

| What proves it | Scenario | Step |
|---|---|---|
| The Leader blocks a `TODO` and an `IN_PROGRESS` Task and unblocks both to the status each came from; unblocking to anything else is refused | `AC-TSK-018` | 1, 2 |
| The owning Mentor reopens a `DONE` Task, refused without a reason and accepted with one; each transition keeps actor, time and the two statuses | `AC-TSK-018` | 1, 2 |
| The assignee, the Leader, the owning Mentor, another member and an Admin each attempt every `TSK-007` edge, on an `ACTIVE` and on a `PLANNED` Project | `AC-TSK-016` | 2, with platform step 2 |
| The owning Mentor may block, unblock and reopen on an `ACTIVE` Project and is denied starting or completing a Task | `AC-AUTH-004` | 2, with platform step 2 |
| A Leader records 300 remaining, 120 minutes are logged, then 250 remaining: current Remaining follows the latest forecast less the work since it, and Current Work holds | `AC-TSK-017` | 3, 4 |
| The estimate freezes at the first retained log and a `DONE` Task keeps signed variance against it | `AC-TSK-012` | 4 |

Per `TST-003` each expectation is derived from the rule and written before the
implementation. Where an existing test already covers a rule, it is tagged with the rule
identifier rather than duplicated, which also serves the backlog item in
[`plan.md`](../../../plan.md) about the test classes that carry no rule identifier.

## 7. Open questions

None. `D13` and `D15` are confirmed for build, and the one question the spec notes left —
whether a forecast recorded outside a reassignment can use the existing columns — is
answered in §2.3 from the selection `TaskQueryService` already performs: it can, unchanged.
