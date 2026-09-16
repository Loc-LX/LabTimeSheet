# Platform Plan

**Version:** 0.1 · **Owner:** Loc-LX · **Status:** DRAFT, awaiting approval · **Date:** 2026-09-16

How the platform rules of [SPEC.md](SPEC.md) will be built. It is a technical design, not
a tracker: progress belongs in [`plan.md`](../../../plan.md). The rules themselves are in
the specs, and where this plan and a spec disagree, the spec wins.

This plan carries two subjects that the other features depend on, so it is written first:

1. **One authorization policy** (`AUTH-012`, [ADR-005](../../rfcs/ADR-005-one-authorization-policy.md)).
2. **The schema change the recorded decisions require**, because the platform spec owns the
   domain model of §19 and the shared `DB` rules. Each feature plan refers to this one
   instead of designing its own migration.

## 1. Scope

**In scope.** `AUTH-012` and its scenario `AC-AUTH-011`; the §5.2 permission matrix as the
source of every capability; the shared schema change that `D12`, `D14`, `D21`, `D23`, `D24`
and `ACC-026` require; and the platform gaps the constitution lists, `SEC-011`, `SEC-013`,
`AUTH-002`, `ARC-006` and `GOV-004`, since each is closed by a test that belongs to no
single feature.

**Out of scope.** Business rules of a single feature: they are planned in that feature's
own `PLAN.md`. Also out: `GOV-007`, `GOV-008` and `GOV-015` exclusions, the deployment
pipeline, and any change to the specification itself.

**Blocked until the instructor confirms.** `D12`, `D13`, `D14`, `D15`, `D21`, `D23`, `D24`
and `D25` are provisional. The migration in §3 must not be written before they are
confirmed, because each of them adds or removes a column. The design may be approved now;
the schema is frozen only when the decisions are.

## 2. Design: one authorization policy

### 2.1 What the rule requires

`AUTH-012` takes four inputs for every business permission: the actor's role, the actor's
scope resolved from stored context, the record's current state, and, for a transition, the
target state. A higher role never implies a capability the §5.2 matrix does not grant, and
no business permission is decided by a role check outside the policy.

### 2.2 The capability catalogue

The §5.2 matrix has **36 capabilities across 4 actor columns, so 144 cells**. The catalogue
is the policy's data, not its code: one entry per capability and role, so a single entry
can be withdrawn without touching another. That is what `D1` needs, since the instructor
expects to withdraw part of the Admin read access later.

| Element | Source | Note |
|---|---|---|
| Capability | one §5.2 row | Named after the row, not after a controller method |
| Role | one §5.2 column | `ADMIN`, `MENTOR`, `INTERN`, plus the Leader term, which is scope rather than a role |
| Scope predicate | the rule the row cites | Ownership, active membership, current leadership term, current assignee, own record |
| State predicate | the rule the row cites | Project status, Task status, request status, attendance period state |
| Target state | `TSK-007`, `TSK-023`, `PRJ-002`, `ATT-024` | Only for transitions |

Reading the matrix into a test fixture is what `AC-AUTH-011` asks for: every cell is
exercised for each role, then one Admin capability is withdrawn and only that cell changes.

### 2.3 Where the decision is taken

Three layers keep the jobs `ADR-005` assigns them.

| Layer | May do | May not do |
|---|---|---|
| `SecurityConfiguration` | Authenticated or not, and broad role gates on routes | Decide any business permission |
| Service, inside the transaction | Ask the policy and enforce its answer | Read a role directly |
| Template | Ask the same policy to decide what to show | Be treated as enforcement (`AUTH-002`) |

The policy resolves scope from stored context inside the caller's transaction, never from
the security context, because leadership and membership are intervals that close
(`AUTH-004`, `PRJ-005`).

### 2.4 What the current code offers

The code is material, not the design. Sixty role checks sit in ten production files, three
templates use `sec:authorize`, and `SecurityConfiguration` carries route rules. Each is
read once, mapped to the matrix row it was trying to express, and then replaced by a policy
call. Two known cases contradict the specification and change with this work: the branch in
`TaskService#changeStatus` that lets an owning Mentor set any status, and the three separate
Admin checks on the Attendance report that `ADR-005` names.

### 2.5 Alternatives rejected

| Alternative | Why not |
|---|---|
| Spring method security annotations per method | Expresses role, not scope and state; a withdrawal would edit dozens of annotations |
| One service-side `if` per capability, no catalogue | `AC-AUTH-011` cannot iterate the matrix, and withdrawal touches code in many files |
| Push decisions into `SecurityConfiguration` | Route-level rules cannot see the record's state or the caller's membership |

## 3. Data model: the one migration the decisions require

`ARC-009` forbids editing an applied migration, so this is a new `V3` file. The tables below
are named by what they hold; the exact column names are settled when the migration is
written. Every line names the rules that ask for it.

### 3.1 New tables

| Table | Rules | Why it exists |
|---|---|---|
| `attendance_periods` | `ATT-019`–`ATT-021` | One row per Intern per month with its state and, when closed, who closed it and when |
| `attendance_period_reopens` | `ATT-022`, `GOV-009` | The request (requester, range, reason) and the Admin's decision, approval or refusal with its own reason |
| `attendance_exceptions` | `EXC-001`–`EXC-004`, `D23` | One row per attendance row and violation kind: how it was raised, by whom, its deadlines, and its current outcome |
| `attendance_exception_decisions` | `EXC-007`, `ATT-024`, `GOV-009` | Append-only: each decision, amendment or reversal with its kind, actor, server time and reason |
| `leave_request_decisions` | `LEV-011`, `ATT-024`, `GOV-009` | Append-only, the same shape, so an amendment that withdraws approval from dates is auditable |
| `task_status_transitions` | `TSK-023`, `TSK-025`, `GOV-009` | Block, unblock and reopen with the previous status, actor, time and, for a reopen, the reason |

### 3.2 Changed tables

| Table | Change | Rules |
|---|---|---|
| `projects` | Add `CANCELLED` to the status constraint; keep who cancelled it, when, and the reason | `PRJ-002`, `PRJ-023`, `D12` |
| `leave_requests` | Add `OVERDUE` and `WITHDRAWN` to the status constraint; keep who withdrew it and when | `LEV-010`, `LEV-013` |
| `leave_request_days` | Mark a date whose approval an amendment withdrew, without touching its frozen policy and quota snapshot | `LEV-011`, `GOV-005` |
| `attendance_corrections` | Add `OVERDUE` to the status constraint; stop writing `locked_at`, which `D14` removed; move both deadlines to 48 hours | `COR-003`, `COR-004`, `COR-007`, `D23` |
| `intern_profiles` | Keep the responsible Mentor, replaceable by an Admin | `ACC-026`, `ACC-021` |
| `attendance_records` | No change | — |

### 3.3 Invariants the migration must carry

- Exactly one open period per Intern per month, and no attendance result inside a finalized period changes except through a reopened range (`ATT-020`, `ATT-021`).
- A decision table is append-only: no update, no delete, and the current value is the latest effective entry (`ATT-024`).
- A frozen snapshot stays frozen. An amendment marks a leave date as no longer approved; it never rewrites the policy version or quota snapshot that date carries (`GOV-005`, `LEV-003`).
- Every status constraint lists its values in the database as well as the service, because `DB-007` puts state graphs inside the transaction and `ARC-003` tests them against PostgreSQL.

## 4. Sequence

Each step is small enough to review on its own and names what it satisfies. A step starts
only when the step it depends on is green.

| # | Step | Satisfies | Depends on |
|---|---|---|---|
| 1 | Read the §5.2 matrix into a test fixture and assert every one of the 144 cells against the current code, marking the cells that fail | `AC-AUTH-011` first half | — |
| 2 | Introduce the policy and the capability catalogue; move the three Admin report checks and the Mentor branch of `TaskService#changeStatus` behind it | `AUTH-012`, `TSK-023` | 1 |
| 3 | Move the remaining role checks in services and templates behind the policy, file by file, keeping `SecurityConfiguration` as coarse route protection | `AUTH-012`, `AUTH-002` | 2 |
| 4 | Withdraw one Admin capability in the catalogue and prove only that cell changes | `AC-AUTH-011` second half, `D1` | 3 |
| 5 | Write the `V3` migration of §3 with its constraints | §3 rules | instructor confirmation |
| 6 | Close the platform gaps the constitution lists: security headers read back, development relaxations refused under production, a not-found response identical for unauthorized and absent records, and a build check for business SQL outside a repository | `SEC-011`, `SEC-013`, `AUTH-002`, `ARC-006` | 3 |

Steps 1 to 4 need no schema change and are not blocked by the instructor. Step 5 is.

## 5. Risks

| Risk | Handling |
|---|---|
| A capability is moved behind the policy and silently loses a scope condition | Step 1 records the current answer of all 144 cells first, so any change of behavior is visible in the diff of that fixture |
| The policy is asked outside a transaction and reads a stale membership or leadership term | The policy takes the scope it needs as resolved context; the service calls it inside the transaction (`AUTH-011`) |
| Templates keep deciding | `AUTH-002` says a hidden control is not authorization; step 3 removes `sec:authorize` from business decisions, and the gap row for `AUTH-002` gets a test |
| The migration is written against a decision the instructor then changes | Step 5 waits for confirmation. This is the cheapest veto point, and it is deliberate |
| The schema change is large and touches attendance, leave, task and project at once | One migration, one review, one rollback point, rather than four migrations that must be applied in order |

## 6. Verification

| Layer | What it proves here |
|---|---|
| Matrix-driven test | `AC-AUTH-011`: every cell of §5.2, and the withdrawal of one capability |
| Service tests | Scope and state predicates: closed membership, ended leadership term, finalized period, wrong Project |
| Web tests | The route gate and the not-found response that reveals nothing (`AUTH-002`) |
| Schema tests | Status constraints, append-only decision tables, one open period per Intern and month, run against PostgreSQL (`ARC-003`) |
| End-to-end | One journey per role that the matrix says may act, and one that may not |

No step is done until the rules it names are covered; `TST-005` puts those rule
identifiers in the test source.

## 7. Open questions

| # | Question | Owner | Blocks |
|---|---|---|---|
| 1 | Confirmation of `D12`, `D13`, `D14`, `D15`, `D21`, `D23`, `D24`, `D25` | the instructor | Step 5, and the attendance, project, task and reporting plans |
| 2 | Does the Leader belong in the catalogue as a fourth role, or stay a scope predicate over the Intern role? The §5.2 matrix gives it a column, while `app_users` has three roles and leadership is an interval | maintainer | Step 2 |
| 3 | Are the six cross-feature rules `UI-019`, `AUTH-003`, `AUTH-004`, `AUTH-009`, `AUTH-011` and `DB-008` split into their features before or after this work? `plan.md` defers the split to after this plan | maintainer | Step 3 |

A plan with an open question is not ready for implementation. Questions 2 and 3 are the
maintainer's and can be closed in review; question 1 is the instructor's.
