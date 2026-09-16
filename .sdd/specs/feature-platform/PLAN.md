# Platform Plan

**Version:** 0.5 · **Owner:** Loc-LX · **Status:** DRAFT, awaiting approval · **Date:** 2026-09-16

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

**Nothing in this plan is blocked.** `D12`–`D15`, `D21` and `D23`–`D25` were confirmed
for build by the maintainer on 16 September 2026, and `D27` answered the last open
question the same day: the migration gives every month worked before it a period and then
applies `ATT-020` to it as the running system would. So the schema of §3 may be written
and every step of §4 may start. If the instructor later revises one of these decisions, it
arrives as a new decision and this plan is amended with the spec.

## 2. Design: one authorization policy

### 2.1 What the rule requires

`AUTH-012` takes four inputs for every business permission: the actor's role, the actor's
scope resolved from stored context, the record's current state, and, for a transition, the
target state. A higher role never implies a capability the §5.2 matrix does not grant, and
no business permission is decided by a role check outside the policy.

### 2.2 The capability catalogue

The §5.2 matrix has **36 capabilities across 4 actor columns, so 144 cells**. The catalogue
is the policy's data, not its code: one entry per capability and actor column, so a single
entry can be withdrawn without touching another. That is what `D1` needs, since the instructor
expects to withdraw part of the Admin read access later.

| Element | Source | Note |
|---|---|---|
| Capability | one §5.2 row | Named after the row, not after a controller method |
| **Actor** | one §5.2 column | Not a role. It is derived from the pair (signed-in user, target record) |
| Scope predicate | the rule the row cites | Ownership, active membership, current leadership term, current assignee, own record |
| State predicate | the rule the row cites | Project status, Task status, request status, attendance period state |
| Target state | `TSK-007`, `TSK-023`, `PRJ-002`, `ATT-024` | Only for transitions |

The word **role** keeps the three values `app_users` stores, which `DB-005` protects with a
trigger that rejects any change. The word **actor** names a §5.2 column. The distinction is
not pedantry: none of the four columns is a role. "Owning Mentor" means a Mentor who owns
*that* Project, "Active member / assignee" means the user assigned *that* Task, and
**"Current Leader" means an `INTERN` account holding a current leadership term on the
Project the target record belongs to**. Leadership is the most visible case, not the
exception. `AUTH-004` withdraws Task management the moment a term ends, which no stored role
and no authority in a session can do, and `CLAUDE.md` already records the hour someone lost
looking for a `ROLE_LEADER` that does not exist.

Two consequences the implementation must carry, decided with the maintainer on 16 September
2026:

- **A user can satisfy several columns at once for the same record**, as a Leader who is also the assignee, or an owning Mentor who is also a Mentor. The policy takes the **union** of the cells those columns grant. It never picks the "highest" column, because ranking columns is exactly the inference `AUTH-012` and `TSK-023` forbid: a higher role implying a capability the matrix does not grant. A refusal is simply the absence of any granting cell.
- **`LEADER` never becomes a Spring Security authority.** It appears in no `hasRole`, no `hasAnyRole`, no `sec:authorize`, and no granted authority, because the term it depends on can end between two requests. §6 carries a test for exactly that, since this is how the trap returns.

Reading the matrix into a test fixture is what `AC-AUTH-011` asks for: every cell is
exercised for each role, then one Admin capability is withdrawn and only that cell changes.

### 2.2.1 Where the catalogue lives

Saying the catalogue is data is empty until the plan says where that data sits, because
`D1` and step 4 both promise that withdrawing one Admin capability changes no code.

| Option | Withdrawal means | Cost |
|---|---|---|
| A. A configuration file read at startup, one line per capability and role | Edit a line, restart | No schema, no admin screen; the file is versioned with the code, so a withdrawal is still a commit and a deployment |
| B. A database table with a screen for an Admin | Clear a row | A new table, a new screen, new rules, and a permission to manage permissions, which `GOV-007` never granted |
| C. Constants in Java | Edit code | Fails the promise |

**This plan takes option A.** The catalogue is a resource file beside the application, its
shape mirrors the §5.2 table, and the policy loads it at startup and refuses to start when a
capability named there is unknown or a matrix row has no entry. Option B is the natural next
step if the laboratory ever wants an Admin to change permissions without a deployment; it is
not in this scope, and `GOV-006` says a feature nobody specified needs a new decision.

The spec does not name the mechanism, so this is a plan-level design choice and the
maintainer approves it here.

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

The code is material, not the design. A survey on 16 September 2026 counted **50 places in
13 files that compare a role or gate on one**, counting `equals` or `==` against `"ADMIN"`,
`"MENTOR"` or `"INTERN"`, `hasRole`, `hasAnyRole` and `sec:authorize`, across
`src/main/java` and the templates. Counting every mention of those three words in Java
instead gives 142 in 33 files, which is why the rule used here is stated rather than the
number alone. The survey orients the work; the fixture of step 1 is what enumerates it,
because a survey by text search cannot see a role decision expressed another way.

Each site is read once, mapped to the matrix row it was trying to express, and then
replaced by a policy call. Two known cases contradict the specification and change with this work: the branch in
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

### 3.3 Existing data

A migration that only creates tables and tightens constraints leaves the rows already in
the database behind. Four cases have to be answered before the migration is written, and
each one is a business question as much as a technical one.

| Case | What must happen | Why it is not obvious |
|---|---|---|
| Months already worked | Create a period per Intern and month that has attendance, then apply `ATT-020` to each: finalize it where 23:59 on the fifth day of its following month has passed and no leave or correction affecting it is pending or overdue, and leave it open otherwise | Closing them retroactively locks data nobody reviewed; leaving them open means a Mentor can still change months from August. Decided in `D27`: neither, because `ATT-020` already answers it. An earlier draft of this row proposed closing every past month outright with the migration as the actor, which would have contradicted `ATT-020` on exactly the months with something still undecided |
| The responsible Mentor | Every Intern already `ACTIVE` needs one, because `ACC-026` and `ACC-021` require the assignment before an internship becomes `ACTIVE` | The rule was written after the data. No mentor can be inferred: owning a Project the Intern belongs to is not the same relation. The plan proposes: leave it empty, let `ACC-026` show those Interns to Admins as needing one, and refuse a decision until an Admin assigns |
| `locked_at` on corrections | `D14` removed the lock, so the column stops being written | Dropping a column with history in it is not reversible. The plan proposes: stop writing it, keep the values as a record of what the old rule did, and let the next schema review drop it |
| New status values | `CANCELLED`, `OVERDUE`, `WITHDRAWN` widen a constraint rather than narrow it | Widening is safe for existing rows. The 48-hour deadlines are not: `attendance_corrections` stores its two deadlines per row, so rows already submitted keep the deadlines they were given, and only new rows use 48 hours |

The demo seed of `scripts/` is data too. It is regenerated after the migration, not patched.

### 3.4 What the migration does to the specification

The specification pins the schema in three places, and a migration that adds six tables
makes all three false at once:

- the §1 note and §19.4, which both say the schema has **24 tables**;
- `AC-DB-001`, which asserts that each database has **exactly 24 tables**;
- the physical Mermaid diagram of §19.4, which draws 24 entities today, and which `DB-010`
  requires to describe the same tables as the SQL.

So the migration is never a code-only step. Writing `V3` means, in the same change: the
diagram gains its entities and relationships, the two counts move, `AC-DB-001` moves with
them, and the feature specs that own the new tables gain their `DB` rules. Anything less
leaves the specification describing a database that no longer exists, which is the drift
`GOV-016` exists to prevent.

### 3.5 Invariants the migration must carry

- Exactly one open period per Intern per month, and no attendance result inside a finalized period changes except through a reopened range (`ATT-020`, `ATT-021`).
- A decision table is append-only: no update, no delete, and the current value is the latest effective entry (`ATT-024`).
- A frozen snapshot stays frozen. An amendment marks a leave date as no longer approved; it never rewrites the policy version or quota snapshot that date carries (`GOV-005`, `LEV-003`).
- Every status constraint lists its values in the database as well as the service, because `DB-007` puts state graphs inside the transaction and `ARC-003` tests them against PostgreSQL.

## 4. Sequence

Each step is small enough to review on its own and names what it satisfies. A step starts
only when the step it depends on is green.

| # | Step | Satisfies | Depends on |
|---|---|---|---|
| 1 | Read the §5.2 matrix into a test fixture and assert every one of the 144 cells against **what the matrix grants**. Cells that fail are the work list of steps 2 and 3, and each one is a finding to record, not a baseline to keep | `AC-AUTH-011` first half | — |
| 2 | Introduce the policy and the capability catalogue; move the three Admin report checks behind it, and remove from `TaskService#changeStatus` the capability the matrix does not grant, namely an owning Mentor setting any status | `AUTH-012`, the refusal half of `TSK-023` | 1 |
| 3 | Move the remaining role decisions in services and templates behind the policy, file by file, keeping `SecurityConfiguration` as coarse route protection | `AUTH-012`, `AUTH-002` | 2 |
| 4 | Withdraw one Admin capability in the catalogue and prove only that cell changes | `AC-AUTH-011` second half, `D1` | 3 |
| 5 | Write the `V3` migration of §3, its constraints, its data migration, and the specification change §3.4 names | §3 rules, `DB-010`, `GOV-016`, `D27` | — |
| 6 | Close the platform gaps the constitution lists: security headers read back, development relaxations refused under production, a not-found response identical for unauthorized and absent records, and a build check for business SQL outside a repository | `SEC-011`, `SEC-013`, `AUTH-002`, `ARC-006` | 3 |

Steps 1, 2, 3, 4 and 6 need no schema change. Step 5 does, and since `D27` it waits on
nothing either; it stays last because §3.4 makes it the step that also moves the
specification, and that is easier to review once the policy of steps 1 to 4 is in place.

**What step 2 deliberately leaves out.** `TSK-023` has two halves. Refusing what the matrix
does not grant needs no storage, and belongs here. Granting the Leader and the Mentor block,
unblock and reopen does need storage: unblocking returns a Task to the status it held
before the block, and only `task_status_transitions` remembers that status. That half waits
for step 5 and belongs to the task plan, which cites this dependency.

## 5. Risks

| Risk | Handling |
|---|---|
| A capability is moved behind the policy and silently loses a scope condition | Step 1 records the current answer of all 144 cells first, so any change of behavior is visible in the diff of that fixture |
| The policy is asked outside a transaction and reads a stale membership or leadership term | The policy takes the scope it needs as resolved context; the service calls it inside the transaction (`AUTH-011`) |
| Templates keep deciding | `AUTH-002` says a hidden control is not authorization; step 3 removes `sec:authorize` from business decisions, and the gap row for `AUTH-002` gets a test |
| The migration is written against a decision the instructor then changes | Step 5 waits for confirmation. This is the cheapest veto point, and it is deliberate |
| The schema change is large and touches attendance, leave, task and project at once | One migration, one review, one rollback point, rather than four migrations that must be applied in order |
| The policy is asked once per row, so a list of 200 Tasks or a report of 30 dates asks it 200 or 30 times | A list decides one capability for one scope, not one per row: the policy is asked once for the scope, and the answer is applied to the rows. Where a row carries its own state, such as a Task status, the policy takes the rows it has already loaded and answers without another query. No policy call issues a database query of its own; it receives resolved context (§2.3). §6 adds a test that a list page and a report page each ask the policy a number of times that does not grow with the number of rows |

## 6. Verification

| Layer | What it proves here |
|---|---|
| Matrix-driven test | `AC-AUTH-011`: every cell of §5.2, and the withdrawal of one capability |
| Service tests | Scope and state predicates: closed membership, ended leadership term, finalized period, wrong Project |
| Web tests | The route gate and the not-found response that reveals nothing (`AUTH-002`) |
| Schema tests | Status constraints, append-only decision tables, one open period per Intern and month, run against PostgreSQL (`ARC-003`) |
| End-to-end | One journey per role that the matrix says may act, and one that may not |
| Query-count test | `AC-ARC-002`: a list page and a report page ask the policy, and query the database, the same number of times at one row and at fifty |
| Authority test | The strings `LEADER` and `ROLE_LEADER` appear in no granted authority, `hasRole`, `hasAnyRole` or `sec:authorize`, so leadership stays a term read from storage (`AUTH-004`) |

No step is done until the rules it names are covered; `TST-005` puts those rule
identifiers in the test source.

## 7. Open questions

None. A plan with an open question is not ready for implementation, and the two this plan
carried were both answered on 16 September 2026. The confirmation of `D12`–`D15`, `D21`
and `D23`–`D25` was one: the maintainer confirmed them for build rather than hold the
plan. What the migration does with the months that predate it was the other, answered by
`D27`. Every step of §4 is clear.

### 7.1 Closed in review, 16 September 2026

| Question | Answer |
|---|---|
| Is the Leader a fourth role or a scope? | A scope, and the question was the wrong shape: no §5.2 column is a role. The catalogue keys on (capability, actor column), and an actor is derived from the pair of signed-in user and target record. Columns combine as a union, never as a ranking, and `LEADER` never becomes a Spring Security authority. §2.2 carries this, and §6 tests it |
| Do the six cross-feature rules split before or after this work? | After step 3, which is the step that produces the evidence: which rule each of the 144 cells cites. `GOV-016` asks for one canonical location, not for that location to be a feature spec, so a genuinely shared rule may stay in the platform spec and nothing is being violated meanwhile. Expect fewer than six to move: `AUTH-011` may be absorbed by `AUTH-012` rather than relocated, `DB-008` locks one Intern profile for both leave quota and daily work minutes and would break if split, and `AUTH-003` and `UI-019` each carry several clauses, so moving them means rewriting them. `plan.md` now names step 3 as the trigger instead of "after this plan" |
| Does the specification get a performance number? | No number, but an invariant, and in the specification rather than only in this plan: `ARC-010` keeps the authorization decisions and queries of a request independent of the rows it renders, and says plainly that no time budget is stated while there is no environment to measure one. `AC-ARC-002` measures it at one row and at fifty. Same pattern as `ARC-004` choosing a compatible range over an exact version and `RPT-008` bounding a request instead of naming milliseconds. Its blind spot, a policy call that is itself expensive, is covered by loading the catalogue once at startup (§2.2.1) and by the policy receiving resolved context (§2.3) |
