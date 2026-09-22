# Platform Plan

**Owner:** Loc-LX · each part carries its own state in the table below.

How the platform rules of [MODULE.md](MODULE.md) will be built. It is a technical design, not
a tracker: progress belongs in [`plan.md`](../../../plan.md). The rules themselves are in
the specs, and where this plan and a spec disagree, the spec wins.

The plan is divided into parts, as `D28` divides all work: each part is written, reviewed and
approved on its own, and `plan.md` tracks where each stands. Its tasks are in
[TASKS.md](TASKS.md), grouped the same way.

| Part | Subject | Rules | State |
|---|---|---|---|
| A | Module boundaries, [below](#part-a--module-boundaries) | `ARC-005`, `ARC-006`, `AC-ARC-001` | Approved on 22 September 2026, after three review rounds the same day |
| B | One authorization policy, [below](#part-b--one-authorization-policy) | `AUTH-012`, `AC-AUTH-011`, [ADR-005](../../rfcs/ADR-005-one-authorization-policy.md), and the gaps of `SEC-011`, `SEC-013`, `AUTH-002` and `ARC-006` | Draft of 22 September 2026, for approval on its own; replaces version 1.0 of 16 September |
| C | The schema change the decisions require, [below](#part-c--the-schema-change-the-decisions-require) | `DB-011`, `DB-014`–`DB-022`, the audit of `D39` | Draft of 22 September 2026, for approval on its own; replaces version 1.0 of 16 September |

Part A is written first because it is on the critical path to the first line of code: `D28`
orders step 5 (this part), the merge into `main`, step 6 (building it) and only then step 8,
the business parts that B and C serve. Parts B and C start after part A is done.

## Part A — Module boundaries

**Implements** `ARC-005`, `ARC-006` and `AC-ARC-001`, as decided in `D28` and
[ADR-006](../../rfcs/ADR-006-module-boundaries.md). This part is step 5 of `D28`. Once it is
approved, the documentation branch can merge into `main` with the maintainer's permission, and
step 6 builds the part on a branch `work/fix/architecture/<name>` (`OPS-019`).

### A.1 Goal, and what stays the same

The code meets `ARC-005` and `ARC-006`, each asserted by a test as `AC-ARC-001` requires:
business code in the seven feature modules, shared code in `platform`, wiring in `config`; an
acyclic dependency graph among `platform` and the features, in which nothing depends on
`config`; no reach into another module's repository or JPA entity, `platform` included; and the
four conditions of the `ADR-006` exception checked for every interface that uses it.

What a user can observe does not change. Routes, templates, messages, transactions and the
schema stay as they are, and no dependency or configuration is added. Some changes move where
something is decided or read without changing what is decided or read: `R1` moves the readiness
check of `ACC-022` into the module that owns the rule, `R3` moves Intern creation and correction
into `internship`, `R8` moves where the business date is computed but not the timezone it comes
from, and A.4 replaces joins across modules with reads through service contracts that return
the same rows. No business decision of `D12`–`D39` is built here. In particular the readiness
predicate keeps the meaning the code gives it today; `D35` and `D36` change it in step 8.

The evidence that behavior did not change is the existing suite: it runs green at the start of
step 6 and at its end. A test changes only in the ways A.7 allows, and an assertion only where
A.7 names it.

### A.2 Where each class goes

Measured on 22 September 2026 by applying the class tables of `scripts/module-boundaries.cjs`
(`moduleOfClass`) to every class below `feature`. The table follows `R4` for `SmtpController`,
corrected on that date:

| From package | To module | Classes |
|---|---|---:|
| `account` | `identity` | 34 |
| `account` | `internship` | 9 |
| `attendance` | `attendance` | 49 |
| `attendance` | `calendar` | 18 |
| `integration` | `platform` | 17 |
| `integration` | `calendar` | 12 |
| `integration` | `identity` | 1, `SmtpController` |
| `notification` | `notification` | 12 |
| `project` | `project` | 46 |
| `task` | `project` | 42 |
| `reporting` | `reporting` | 39 |
| `reporting` | `calendar` | 1, `AdminSettingsController` |
| `reporting` | `notification` | 1, `NotificationController` |

These are the 281 classes below `feature` at that commit. The modules end with `project` 88,
`attendance` 49, `reporting` 39, `identity` 35, `calendar` 31, `platform` 17, `notification` 13
and `internship` 9. `SmtpWarningAdvice` stays in `platform`, since it depends only on
`SmtpConfigurationService`. `task` and `project` share no class name, so merging them creates no
clash of Spring bean names; the one simple name the code uses twice, `AttendanceReportDay`,
stays in two different modules.

Some placements are finer than a whole class or depart from the table:

- `AccountService` divides. The methods the analyzer lists as `INTERNSHIP_METHODS`, with the
  private helpers only they call, form an `internship` service; the rest stays in `identity`
  (`R3`).
- `AccountController` divides by what each handler reads. The Admin's account screens join an
  account with its Intern profile or its readiness, so they are composed in `internship`, the
  module above both: the directory and the account page (`GET /admin/accounts` and
  `GET /admin/accounts/{id}`), creation and correction (`/admin/accounts/new`,
  `POST /admin/accounts`, `/admin/accounts/{id}/edit`), and completion and withdrawal. The
  account lifecycle actions and activation stay in `identity`: resend activation, lock, unlock,
  deactivate, and `/activate`. Every route keeps its path and template (`R1`, `R3`).
- `GlobalRole` moves to `platform`, because the authorization policy decides on the actor's role
  (`AUTH-012`, `ADR-006`); `AttendanceRole`, a copy of it, is removed (`R7`).
- `SecurityProperties` moves from `config` to `platform` (`R10`).
- `currentBusinessDate` moves from `AttendanceApplicationService` to `calendar` (`R8`).

Tests move with the class they test. Tests of the whole application live in `architecture` or
`ui`, the two exceptions `ARC-005` allows, so `LayerStructureTest` and
`AttendanceAndTaskWorkSeparationTest` leave the test package `config`, where they sit although
they test no class of `config`.

### A.3 How each resolution is built

The analyzer separates the edges each resolution removes into edges found in the code and
edges drawn from the wording of rules. Only the first kind is work for step 6:

| | Edges it removes | Built in step 6 as |
|---|---|---|
| `R1` | code | `internship` declares the readiness interface of `ADR-006`. It calls it from its own completion and withdrawal, and the account page reads the same interface to show readiness, where it now asks `ProjectQueryService.internshipLifecycleGuard`. `project` implements it with the predicate the code applies today. Completion and withdrawal stop accepting a guard computed by their caller, so `ProjectService` no longer orchestrates them. Done last, with `R3` |
| `R2` | one, from rule wording | Nothing. Requests already store no assigned approver |
| `R3` | code | `internship` composes Intern creation, correction and the account screens over the service contract of `identity`, as A.4 describes. Before any of it, the invariant test of `ACC-019` that `D28` requires is written and seen failing, then passing. Done last, with `R1` |
| `R4` | code | `platform` mail and SMTP services take the verified actor and the recipient from their caller instead of looking them up in `identity`; `SmtpController` moves to `identity`, beside the bootstrap that offers SMTP setup |
| `R5` | none | Nothing. `attendance` and `project` already compute the recipients of `NOT-011`, `NOT-003` and `NOT-010` before calling `notification` |
| `R6` | rule citations only | Nothing in step 6: the policy of `ADR-005` is not built yet. When part B builds it, the owning module resolves the scope and the policy receives it, as `D28` decided; this settled the contradiction between sections 2.3 and 5 of version 1.0, and part B states it in section B.4 |
| `R7` | code | `calendar` resolves its actor through the service contract of `identity`; `AttendanceRole` is removed; `AttendanceCurrentUserService` stays in `attendance` |
| `R8` | code | `calendar` computes the business date from the policy timezone it owns; the source of that timezone does not change |
| `R9` | rule wording | Nothing. The change-impact interface is built with `CAL-007` and `CAL-008` in step 8; `TaskQueryService.dueDateImpacts` has no caller today and is left as it is |
| `R10` | the one reference into `config` | `SecurityProperties` moves to `platform` |

### A.4 What splitting a package breaks

The resolutions of `D28` remove cycles; they do not cover the repository and entity boundary of
`ARC-006`. Today no class reaches a repository or entity of another package, because
`LayerStructureTest` refuses it. When a package splits, references that were inside one package
cross a module boundary. Counting every class that names, in code or in a query string, a
repository or JPA entity of its own package which the tables above place in another module,
fourteen do on 22 September 2026:

| Split | References that cross | How it is resolved |
|---|---|---|
| `attendance` into `attendance` and `calendar` | `AttendanceRecordEntity` and `LeaveRequestDayEntity` hold a JPA association to `AttendancePolicyEntity`; `AttendanceApplicationService`, `LeaveApplicationService` and `AttendanceReportQueryService` use `AttendancePolicyEntity` and `AttendancePolicyRepository`; `AttendanceReportQueryService` also uses `GlobalCalendarEventEntity` and `GlobalCalendarEventRepository`. Ten references | The two entities keep the `policy_version_id` column as an identifier instead of an association; the schema and its foreign key do not change. Today `AttendanceRecordEntity#toDomain` builds the `AttendancePolicy` record it embeds from the associated entity; it receives that record instead. `calendar` gains reads in its service contract that return the existing `AttendancePolicy` record for a set of version identifiers and the calendar events of a date range. Every service of `attendance` that turns one of the two entities into its domain record, or names the policy or calendar entities or repositories, uses those reads: on 22 September 2026, `AttendanceApplicationService`, `AttendanceCorrectionApplicationService`, `LeaveApplicationService` and `AttendanceReportQueryService`. Rows are created with a version identifier where they now take `getReferenceById`. A service collects the version identifiers of every row a request handles and reads them in one call before it loops over the rows; `AttendanceCorrectionApplicationService` turns history rows into records inside two loops today, so it collects first. The number of queries therefore does not grow with the rows (`ARC-010`) |
| `account` into `identity` and `internship` | `AppUserRepository` joins `InternProfile` in the two directory queries; `InternProfileRepository` joins `AppUser` in `findEligibleInternOptions` and `findDueUserIds`; `AccountService` uses `InternProfile` and `InternProfileRepository`. Four references | `internship` composes each result from reads of each module's service contract, joined by user identifier in memory, with the filters and order of the query it replaces. The directory search matches a name, an email *or* a Student Code, so its composed result is the union of two reads: accounts whose name or email matches, from `identity`, and accounts whose Intern profile's Student Code matches, from `internship`. Both are restricted to the requested role; the union has no duplicate; every account in it is then joined with its profile if it has one; and it is ordered by account identifier. The unfiltered directory is every account with its profile, in the same order. `findEligibleInternOptions` and `findDueUserIds` filter both modules with *and*, so their result is the intersection. Eligibility keeps the order the database gives it today, by display name, Student Code and account identifier, and no name is compared in Java, whose code-point order differs from the database collation for accented and lower-case names: `identity` returns the eligible accounts ordered by display name and `internship` returns their profiles ordered by Student Code, each ordered by the database; the composition sorts by each row's position in those two orders, treating identical display names as tied, then by account identifier. Under a deterministic collation two names tie in the database only when they are identical, so this reproduces the database order exactly; A-01 records the collation and its provider. The due-date read keeps its order by user identifier. The directory returns an unpaged list today, so no paging is reimplemented. Each composition reads each module a fixed number of times per request, whatever the number of rows (`ARC-010`). `AccountService`'s references leave with `R3` |

Both are done while the classes still share a package, before they move, so that each task keeps
the suite green and the later move stays mechanical.

### A.5 The structure tests

The cycle test comes first, in plain Java without a new dependency, as `D28` and `ADR-006`
require, in the test package `architecture`. It reads the type references of every production
class with comments removed and string literals kept, so an entity named inside a JPQL string
counts. It fails on a cycle among `platform` and the features, on any reference into `config`,
and on an interface listed in `ADR-006` whose four conditions do not hold.

It knows each class's module from its package, except for classes listed in its **placement
list**, which gives the module a class belongs to while it still sits in its old package. That
list starts as the class tables of A.2 and shrinks as classes reach their packages.

Its **allowance list** holds the violations measured when it is written. Each entry is one
reference: the simple name of the referring class and the simple name of the type it refers to,
so an entry survives a move unchanged. A new reference between the same two modules is not
covered by another entry and fails. The test refuses an entry whose simple names are ambiguous,
and fails on an entry that no longer occurs, so no entry outlives the violation it records.

Both lists live in one file that the test reads. After the commit that creates it, every change
to that file deletes lines and adds none: each task checks its own diff of the file, and the
closing task checks the file's whole history with `git log -p`. Adding an entry to turn a red
build green would weaken the test, which `TST-011` forbids, and those two checks make any such
addition visible. Before the test is trusted it is made to fail once for each reason: an
artificial cycle, a reference into `config`, a new reference between two modules already listed,
and an `ADR-006` interface breaking one of its four conditions.

`LayerStructureTest` and `AttendanceLayerStructureTest` change in the same task as the code they
check. `LayerStructureTest` takes the modules of `ARC-005`, adds `platform` to the approved root
packages, and extends its repository and entity check to `platform`. `AttendanceLayerStructureTest`
stops requiring `AttendancePolicy` inside `attendance`, which moves to `calendar`.
`AttendanceAndTaskWorkSeparationTest` keeps asserting `GOV-004`; `ReportingArchitectureTest` is
kept and checked against the new packages. After step 6 the cycle check of
`scripts/module-boundaries.cjs` is retired, since the test replaces it.

### A.6 Order of work

Each task leaves the build and the full suite green, and each is one reviewable change. The
order follows the layers of `ADR-006` from the bottom, decouples a package before it splits, and
ends with `R3` and `R1`, which `D28` puts last:

1. Baseline: the full Maven suite, the end-to-end suite and `npm run test:ui` pass on the new
   branch, and the GitNexus index is refreshed. That run, not the one of `1ee043e`, is the
   evidence the end of step 6 is compared with. The collation of the test database and its
   provider are recorded, from `select datcollate, datlocprovider from pg_database where datname
   = current_database()`; the eligibility order of A.4 relies on a deterministic collation,
   which a libc provider always is: *"Nondeterministic collations are only supported with the ICU
   provider"* ([PostgreSQL 18, CREATE COLLATION](https://www.postgresql.org/docs/18/sql-createcollation.html),
   read on 22 September 2026).
2. The cycle test, with its two lists.
3. `platform`: the classes of `integration` other than HolidayAPI and `SmtpController`,
   `GlobalRole`, `SecurityProperties` (`R10`), and the `platform` side of `R4`.
4. `identity`: every class of `account` moves unchanged, the nine internship classes included,
   so no repository or entity is reached across a package; `SmtpController` joins it (`R4`).
   The references that `identity` then keeps toward modules above it are allowance entries that
   tasks 10 and 11 remove.
5. `attendance` stops reaching the policy and calendar entities and repositories, still inside
   its package (A.4).
6. `calendar` and `notification`: the policy and calendar classes of `attendance`, the HolidayAPI
   classes of `integration`, `AdminSettingsController`, `NotificationController`, and `R7` and
   `R8`.
7. `project`: `task` merges into it.
8. The test package `config` gives its two whole-application tests to `architecture`.
9. The invariant test of `ACC-019`, seen failing and then passing.
10. `internship` and `R3`: the joins of A.4 are composed, then the nine classes, the internship
    methods of `AccountService` and the Admin's account screens move.
11. `R1`, which empties the allowance list.

Before any symbol changes, GitNexus impact analysis runs on it as `AGENTS.md` requires, and a
high or critical risk is reported before the edit. Moves use `git mv`, so each file keeps its
history.

### A.7 Tests this part changes

The tests this part touches cannot be listed by name ahead of time: dozens of files stub the
policy entity, call the internship methods of `AccountService`, or set up the SMTP and HolidayAPI
services, and a list built by searching for names has been wrong each time it was tried. So the
rule constrains the kind of change instead.

Besides moving with its class, a test may change in three ways only:

- the receiver of a call, where the called method moved to another class;
- the arguments of a call or a constructor, and the fixtures that supply them, where a signature
  changed, such as an attendance entity built with a policy version identifier, or a completion
  that no longer takes a guard;
- a stub or a mock, including the mock a `verify` names, but not the call or the arguments it
  expects.

No assertion changes in any other way. Each task checks this on its own diff of `src/test`: in
every test file, the number of assertion calls (`assert…`, `assertThat…`, `verify`, and the
expectations chained to them) is the same before and after, and every changed line that holds
one keeps the expected values it states and changes only a receiver or an argument. The task's
commit lists those lines.

The only assertion changes this part intends are these, decided here as `TST-011` requires of
any assertion that changes. They are the only departures the check above allows.

| Test | Task | Change | Why |
|---|---|---|---|
| `InternshipLifecycleIntegrationTest` | A-11 | The two assertions that feed a guard to provoke a refusal, a current Leader and an unfinished Task, are removed | The parameter they test is removed. The Leader case is already covered from real state by `ProjectServiceIntegrationTest`, which expects *"Intern is still a current Leader"*; the unfinished-Task case is covered by the new test below |
| New test of the directory union | A-10, before the composition | Added | One search string matches the email of one account and the Student Code of another Intern: both are returned, once each, in account order, and an account matching on both fields appears once. No existing test covers this, and a wrong composition would pass the existing ones. It is written against the current join first, where it passes, passes again after the composition, and is made to fail once by dropping one side of the union |
| New test of the eligibility order | A-10, before the composition | Added | Eligible Interns with names whose order in Java differs from the database collation, accented Vietnamese names such as *Ánh* and *Đạt* beside *An*, *Bình* and *Zed*, a lower-case *an*, and two identical names told apart by Student Code, come back in the order the database gives. The existing test uses ASCII names, whose two orders agree, so it cannot see the difference. The new test is written against the current join first, where it passes, passes again after the composition, and is made to fail once by sorting the names in Java |
| New test of the unfinished-Task refusal | A-11 | Added | An Intern with no leadership term and one unfinished Task is refused completion with *"Intern still owns unfinished Tasks"*, and the profile is unchanged. It is seen failing by breaking the readiness implementation before it passes |
| New invariant test of `ACC-019` | A-09 | Added | Required by `D28`; seen failing on deliberately broken code, then passing |

### A.8 When the part is done

- The cycle test passes with both lists empty, each of its failure paths has been seen, and the
  history of its list file contains no added line after the commit that created it.
- `LayerStructureTest`, `AttendanceLayerStructureTest`, `ReportingArchitectureTest` and
  `AttendanceAndTaskWorkSeparationTest` pass against the new packages.
- The full Maven suite, the end-to-end suite and `npm run test:ui` pass; every test change is of a
  kind A.7 allows, and every assertion change is one A.7 names.
- GitNexus change detection covers every changed symbol, and the affected processes it reports
  are the ones the tasks expected.
- Step 7 of `D28` then checks the code against the documents before the merge, which needs the
  maintainer's permission.

### A.9 Risks

| Risk | Handling |
|---|---|
| A move changes behavior unseen | No task edits logic and moves code in the same change: A.4's decoupling, `R1`, `R3`, `R4`, `R7` and `R8` are their own tasks, and the suite runs after every task |
| A composed read returns different rows or order than the join it replaces | The directory search is composed as a union, and a test of that union, which no existing test covers, is written against the current join before the change (A.7). Eligibility takes its order from the database rather than from Java, keeps the filters and order `EligibleInternOptionIntegrationTest` pins, and gains a test with names whose Java and database orders differ (A.7). For the due-date read an extra row changes nothing, because `activateDueInternships` locks each account and profile and checks the role, the account status, the profile status and the date window again before activating; only a lost row would change behavior, and the activation tests expecting one and two activations would catch it. No test pins each of its filters |
| A composed read multiplies queries | Each composition reads each module once per request, whatever the number of rows (`ARC-010`) |
| The cycle test misses a reference the build does not | It reads string literals as well as imports, and is made to fail for each reason before it is trusted |
| A stale class list | The placement list is taken from the analyzer when task 2 runs, not from the counts above; a class added since is placed by the same tables |
| `R3` loses the `ACC-019` invariant in a failed creation | The invariant test is seen failing on deliberately broken code before `R3` begins |
| Other open branches conflict with the moves | Step 6 starts from `main` after the documentation merge; any branch still open then is rebased or closed first, with the maintainer's decision |

### A.10 Not in this part

Business behavior of `D12`–`D39`; the authorization policy (part B); the schema change (part
C); the change-impact interface of `R9`; and the structure fixes recorded for later that `ARC-005`
does not require, such as renaming the demo seed file.

### A.11 Open questions

None blocks approval. The name of each new class, such as the readiness interface of `R1`, is
chosen in the task that creates it and recorded in the `ADR-006` table in the same change.

## Part B — One authorization policy

**Implements** `AUTH-012` and `AC-AUTH-011`, as [ADR-005](../../rfcs/ADR-005-one-authorization-policy.md)
decides, and closes four platform gaps the constitution lists: `SEC-011`, `SEC-013`,
`AUTH-002`, and the business-SQL half of `ARC-006`. It starts after part A is done, because the
policy belongs to `platform` and every scope it needs is resolved by the module that owns the
record. This text replaces version 1.0 of 16 September 2026; git keeps that version.

### B.1 What the rule requires

`AUTH-012` takes four inputs for every business permission: the actor's role, the actor's
scope in stored context, the record's current state, and, for a transition, the target state.
A higher role never implies a capability the §5.2 matrix does not grant, and no business
permission is decided by a role check outside the policy.

### B.2 The capability catalogue

The §5.2 matrix has 36 capabilities in 4 actor columns, 144 cells, counted on 22 September
2026. The catalogue is the policy's data: one entry per capability and actor column, so one
entry can be withdrawn alone, which `D1` needs.

| Element | Source | Note |
|---|---|---|
| Capability | one §5.2 row | Named after the row, not after a controller method |
| Actor | one §5.2 column | Not a role: derived from the pair of signed-in user and target record |
| Scope predicate | the rule the row cites | Ownership, active membership, current leadership term, current assignee, own record, responsible Mentor |
| State predicate | the rule the row cites | Project status, Task status, request status, attendance period state |
| Target state | `TSK-007`, `TSK-023`, `PRJ-002`, `ATT-024` | Only for transitions |

**Role** keeps the three values `app_users` stores, which `DB-005` protects. **Actor** names a
§5.2 column, and none of the four columns is a role: "Current Leader" is an `INTERN` account
holding a current leadership term on the Project the record belongs to. Two consequences hold:

- A user can satisfy several columns for the same record. The policy takes the **union** of the cells those columns grant and never ranks columns, because ranking is the inference `AUTH-012` and `TSK-023` forbid. A refusal is the absence of any granting cell.
- `LEADER` never becomes a Spring Security authority, `hasRole`, `hasAnyRole` or `sec:authorize` value, because the term it depends on can end between two requests (`AUTH-004`).

The row "Lock/deactivate accounts; manage internship lifecycle" covers every Admin account
transition of `ACC-014`: lock, unlock, deactivation including that of a pending account
(`ACC-028`), and reinstatement (`ACC-029`). `ACC-014` requires all of them to be explicit Admin
actions; the row names lock and deactivation only, so this mapping is recorded here rather than
left to the implementer.

### B.3 Where the catalogue lives

A resource file of `platform`, read once at startup, one entry per capability and actor column,
shaped like the §5.2 table. The application refuses to start when the file names an unknown
capability or a matrix row has no entry. Withdrawing a capability edits one line, which is still a
commit and a deployment. A database table with an Admin screen would need a permission to manage
permissions, which `GOV-007` never granted; Java constants would break the promise of `D1`. This
is a plan-level choice the spec leaves open.

### B.4 Where the decision is taken

| Layer | May do | May not do |
|---|---|---|
| `SecurityConfiguration` | Authenticated or not, and coarse role gates on routes | Decide a business permission |
| A module's service, inside its transaction | Resolve the actor's scope from stored context, ask the policy, enforce the answer | Read a role to decide |
| Template | Ask the same policy what to show | Count as enforcement (`AUTH-002`) |

**The policy decides; it never looks anything up.** The module that owns the record resolves the
actor's scope from stored context inside its transaction and passes it, with the record's state,
to the policy, as `R6` of `D28` decided. The policy belongs to `platform`, which depends on no
feature (`ARC-005`), so it cannot read a membership or a leadership term, and it issues no query
of its own (`ARC-010`). Version 1.0 said the policy resolves scope from stored context, which
contradicted its own risk section and `R6`; this paragraph replaces that sentence.

Each owning module provides the scope its records need: `project` the owning Mentor, the current
Leader, active membership and the current assignee; `internship` the responsible Mentor
(`ACC-026`); `attendance` the owner of a record or request, using the responsible Mentor from
`internship`; `identity` whether the actor is an active account of a given role. A leadership term
that ends between two requests is seen by the next one, because the scope is read in its
transaction.

### B.5 What the current code offers

The code is material, not the design. On 16 September 2026 a text survey counted 50 places in 13
files that compare or gate on a role. Part A changes those file locations, so the survey is not
repeated here: the fixture of B-01 enumerates the work instead, because a text search cannot see a
role decision expressed another way. Two known cases contradict the specification and change in
this part: `TaskService#changeStatus` lets an owning Mentor set any status (`TSK-023`), and the
Attendance report makes three separate Admin checks that `ADR-005` names.

Native SQL in a service breaks `ARC-006` and `D18`: `ProjectService` deletes a draft Project
through `nativeDelete`. B-07 places that SQL behind the data-access layer with its behavior
unchanged; the emptiness check and notification deletion that `PRJ-002` requires belong to the
project plan, since they change behavior.

### B.6 Alternatives rejected

| Alternative | Why not |
|---|---|
| Method security annotations per method | Express role, not scope and state; a withdrawal edits dozens of annotations |
| One service-side `if` per capability, no catalogue | `AC-AUTH-011` cannot iterate the matrix, and a withdrawal touches many files |
| Decisions in `SecurityConfiguration` | Route rules cannot see a record's state or the caller's membership |

### B.7 Order of work

The tasks are B-01 to B-07 in [TASKS.md](TASKS.md). B-01 comes first because its fixture is the work list of B-02 and B-03, and B-04 last among the policy tasks because a withdrawal proves something only once every cell goes through the policy. B-05 to B-07 depend on nothing in B-01 to B-04 and may run in any order after part A.

The grant half of `TSK-023`, block, unblock and reopen by the owning Mentor and the Leader, needs
`task_status_transitions` to restore the status held before a block (`TSK-025`), so it belongs to
the project plan after part C creates that table.

### B.8 Tests this part changes

This part changes behavior on purpose wherever the code grants what the matrix refuses. An
assertion may change only where it asserts such a capability; the commit names, for each changed
assertion, the §5.2 row and the rule that refuse it (`TST-011`). Any other test changes only as
part A's section A.7 allows. A cell that B-01 records as failing is fixed in code, never by
changing the fixture.

### B.9 Risks

| Risk | Handling |
|---|---|
| A capability loses a scope condition when it goes through the policy | B-01 records every cell first, so any change of answer shows in the fixture's result |
| The policy is asked with a stale scope | The owning module resolves scope inside the same transaction as the change it guards |
| Templates keep deciding | B-03 replaces template role checks with policy calls; B-06 tests that a hidden control grants nothing |
| A list asks the policy once per row | A list decides one capability for one scope and applies it to the rows; where a row has its own state the policy receives the loaded rows and issues no query. A test counts policy calls and queries at one row and at fifty (`AC-ARC-002`) |

### B.10 When the part is done

- All 144 cells pass through the policy, and the withdrawal test passes (`AC-AUTH-011`).
- No `LEADER` or `ROLE_LEADER` value appears as an authority or in `hasRole`, `hasAnyRole` or `sec:authorize`.
- The tests of B-05, B-06 and B-07 pass, and the query-count test of `AC-ARC-002` passes.
- The full Maven suite, the end-to-end suite and `npm run test:ui` pass.
- The constitution's gap rows for `SEC-011`, `SEC-013`, `AUTH-002`, `AUTH-012` and the business-SQL row of `ARC-006` are closed in the same change as the test that closes each.

### B.11 Not in this part

Business rules of a single feature, including the grant half of `TSK-023`; an Admin screen for
permissions; the schema change (part C).

## Part C — The schema change the decisions require

**Implements** the schema of `DB-011` and `DB-014`–`DB-022`, the predicate audit `D39` starts,
`DB-002` for overdue leave, `DB-010` for the physical diagram, the stored shape `NOT-012` gives
email delivery, the link `PRJ-002` needs to delete a draft's notifications, and the treatment of
months already worked that `D27` decided. Column names are settled when each migration is written
(`D32`). It starts after part A is done, because the code that obeys each tightened predicate
lives in the modules part A creates. This text replaces sections 3 and 4 of version 1.0.

### C.1 Expand first, then contract, module by module

Version 1.0 planned one migration. Tightening every predicate at once breaks the running code,
which the current classes show:

- `AppUser#deactivate` clears the lock timestamp, which the lock rule of `DB-022` refuses.
- The code cancels a pending leave request as `CANCELLED` with no decision, which `DB-018` refuses.
- `LeaveStatus` has no `WITHDRAWN`, so rows reclassified to it could not be read.

So the change comes in two kinds of migration. **V3 expands**: it adds tables, columns and status
values, and relaxes the predicates that refuse newly lawful rows, all of which the running code can
live with. **Each contract migration tightens** what one module's rules forbid, and ships in the
same change as that module's code that obeys it. Each migration stays one review and one rollback
point. Migrations are numbered in the order they ship, and each file name names its module.

### C.2 Before any migration: which database, and what it holds

Each database a migration will run on is identified first by its Flyway history. A database created
while `V2__account_admin_edit_events.sql` existed, from `4c1fa67` until the merge `787d143` removed
it, holds a different `V2`; this plan does not guess how to reconcile it, and work on that database
stops until the maintainer decides. The same step reads the stored rows that the contract steps
depend on (C.5) and records the counts in `plan.md`.

### C.3 V3, the expansion

New tables, each with its constraints and, where the rule asks, an append-only trigger. No running
code writes them yet:

| Table | Rules |
|---|---|
| `attendance_periods` | `DB-014`, `ATT-019`–`ATT-021` |
| `attendance_period_reopens` | `DB-015`, `ATT-022` |
| `attendance_exceptions` | `DB-016`, `EXC-001`–`EXC-004` |
| `attendance_exception_decisions`, `leave_request_decisions` | `DB-017`, `ATT-024` |
| `task_status_transitions` | `DB-020`, `TSK-023`, `TSK-025` |

Changed predicates, each a `D39` member that refuses a row the rules make lawful, or admits a status
no running code writes yet:

| Predicate | Becomes | Rules |
|---|---|---|
| `ck_projects_status`; `ck_projects_activation` | `CANCELLED` accepted, with or without an activation timestamp | `DB-019` |
| `ck_project_invitations_resolution_code`; the `REVOKED` branch of `ck_project_invitations_resolution_state` | `PROJECT_CANCELLED` accepted, with no resolving actor | `DB-011`, `PRJ-023` |
| `ck_leave_requests_status`; `ck_leave_requests_decision` | `OVERDUE` and `WITHDRAWN` accepted as undecided rows without a decision time | `DB-018` |
| `ck_attendance_corrections_status`; `ck_attendance_corrections_decided_at` | `OVERDUE` accepted as undecided | `DB-018`, `COR-007` |
| `ck_attendance_correction_events_type`, `_from_status`, `_to_status` | The amendment, reversal and overdue entries accepted; `OVERDUE` as a status | `DB-017` |
| `ck_app_users_pending_password`, `ck_app_users_activated_state`, `ck_app_users_lock_timestamp` | A `DEACTIVATED` row with neither hash nor activation timestamp accepted, and a `DEACTIVATED` row keeping a lock beside its activation timestamp accepted; the non-blank hash kept | `DB-022` |
| `ex_leave_requests_no_overlap` | Its predicate covers `PENDING`, `OVERDUE` and `APPROVED`. This tightens, but no `OVERDUE` row exists until the attendance code writes one | `DB-002`, `AC-DB-010` |

New nullable columns: who cancelled a Project, when and why (`DB-019`); when a leave request was
withdrawn (`DB-018`); the withdrawn-approval mark of a leave request day (`DB-018`); the responsible
Mentor of an Intern profile, which may reference only a `MENTOR` account (`DB-021`, `AC-DB-008`);
and the Project a notification was raised for (`PRJ-002`). Existing notifications get that link
from their action route where it names a Project, which is how the Project services build it today
(`/projects/{id}` and routes below it); a notification whose route names no Project keeps no link.

Data: V3 creates a period for every Intern and month that has attendance, then applies `ATT-020`
to each, as `D27` decided.

### C.4 Contract migrations and the module that ships each

| Module plan | Predicates the contract migration tightens | Code in the same change |
|---|---|---|
| `identity` | The exact `DB-022` shapes: a non-blank hash and an activation timestamp together or not at all per status; an activation timestamp set only by `PENDING_ACTIVATION → ACTIVE` and never changed or cleared; a lock timestamp set only by `ACTIVE → LOCKED`, cleared only by `LOCKED → ACTIVE`, and otherwise unchanged. Both timestamp rules by trigger, comparing old and new values with `IS DISTINCT FROM` | Deactivation keeps the lock; `ACC-028`, `ACC-029`, `ACC-030` |
| `attendance` | `DB-018`: an undecided leave request carries no deciding actor (new predicate); `ck_attendance_corrections_pending_decision` counts `OVERDUE` as undecided; a `CANCELLED` leave request carries its approval time and approving Mentor (`ck_leave_requests_decision`, `ck_leave_requests_approval_actor`); a `WITHDRAWN` one carries its withdrawal time. `DB-017`: correction entries carry a reason for an amendment or reversal and refuse update and delete. Before these checks, the reclassification of C.5 | Withdrawal and cancellation under `LEV-011` and `LEV-013`; overdue marking; decision history; corrections stop writing their lock |
| `project` | `DB-019`: a `CANCELLED` Project carries the cancelling Mentor, the time and a non-blank reason | `PRJ-023`; `PRJ-002` deleting a draft's notifications by their Project link; the grant half of `TSK-023` |
| `notification` | `ck_notifications_email_payload`: `NOT_REQUIRED` and `UNAVAILABLE` carry no payload (`NOT-012`) | None, if C.2 finds no stored row that breaks it; otherwise the rows found are reported before this step |

### C.5 Stored rows

| Case | Treatment |
|---|---|
| Months already worked | `D27`: a period per Intern and month with attendance, then `ATT-020` applied as the running system would |
| Interns already `ACTIVE` without a responsible Mentor | The reference stays empty; `ACC-026` shows them to Admins as needing one, and a decision waits for the assignment |
| The lock timestamp on corrections | Kept as a record of what the old rule did and no longer written. `ck_attendance_corrections_locked_state` stays: it is a history-bound member (`D39`) |
| Correction deadlines | Rows keep the deadlines they were given; only new rows use 48 hours (`D23`) |
| `CANCELLED` leave requests without a decision time | Reclassified to `WITHDRAWN`: the cancellation time becomes the withdrawal time and is then cleared, and the owning Intern is the withdrawer (`D39`). Rows with a decision time stay `CANCELLED`. The counts C.2 records must match this reading before the attendance contract runs |
| Accounts deactivated before the identity contract | Their lock timestamp was already cleared and cannot be restored; reinstatement returns them to `ACTIVE` (`D38`) |
| Monthly leave quota (`ATT-003`) | `ck_attendance_policy_versions_quota` and `ck_leave_request_days_quota` are history-bound members. If C.2 finds no stored policy version above 4 on any database, the calendar module narrows both to 0 through 4 in a contract migration of its own; otherwise both stay at 0 through 31 as lawful history, and `AttendancePolicyCommand` keeps enforcing 0 through 4 for new versions |
| The demo seed of `scripts/` | Regenerated after each migration it is affected by, not patched |

### C.6 The predicate audit

**Scope.** The 148 check, exclusion and unique predicates, constraints and unique indexes, on the
24 tables of `V1__baseline.sql` and `V2__add_task_effort_planning.sql`, counted on 22 September
2026. Primary keys, foreign keys and triggers are not counted. **Criterion**, as `D39` states it: a
predicate is a member when, once the rules are applied, it refuses a lawful row or admits an
unlawful one; a history-bound member is one whose tightening would refuse stored rows that were
lawful when written. **No completeness is claimed**: each verdict below is proved by a probe test
(C.7), and no test or reading can show that no member remains.

The eight tables `D39` examined hold 54 predicates, with the verdicts of its table: 19 members,
2 of them history-bound. The other 16 tables hold 94:

| Table | Verdict |
|---|---|
| `notifications` | Member: `ck_notifications_email_payload` (`NOT-012`), tightened by the notification contract. Unaffected: `ck_notifications_type`, which keeps `SYSTEM` for the notices of `NOT-011` unless a module plan adds codes in its own contract; `ck_notifications_email_attempts`, since a manual retry resets the attempt count, so "attempts exhausted" in `NOT-012` is not a stored shape; `ck_notifications_title`, `_body`, `_email_status`, `_email_sent`, `_email_retry`, `_version` |
| `attendance_policy_versions` | History-bound member: `ck_attendance_policy_versions_quota` (C.5). Unaffected: `_timezone`, `_month_boundary`, `_schedule`, `_check_in_grace`, `_checkout_grace`, `_checkout_cutoff`, `_penalty`, `_version`, `uq_attendance_policy_versions_effective_from` |
| `attendance_policy_workdays`, `global_calendar_events`, `attendance_records` | Unaffected: `D14`, `D24`, `CAL-007` and `CAL-008` add tables and interfaces, not rows here. `ck_attendance_policy_workdays_iso_day`; `ck_global_calendar_events_name`, `_source`, `_api_provenance`, `_version`, `uq_global_calendar_events_source_uuid`; `uq_attendance_records_intern_date`, `ck_attendance_records_checkout`, `_version` |
| `project_memberships`, `project_leadership_terms`, `project_membership_exit_requests` | Unaffected: `PRJ-023` closes intervals with the cancelling Mentor as actor and marks pending exits `SUPERSEDED`, which the current predicates admit. `uq_project_memberships_id_project`, `ck_project_memberships_interval`, `_removal_actor`, `_version`, `uq_project_memberships_one_active`; `uq_project_leadership_terms_id_project`, `ck_project_leadership_terms_interval`, `_end_actor`, `ex_project_leadership_terms_no_overlap`, `uq_project_leadership_terms_one_current`; `ck_project_membership_exit_requests_type`, `_reason`, `_resolution_note`, `_participants`, `_status`, `_resolution`, `_version`, `uq_project_membership_exit_requests_one_pending_target` |
| `tasks`, `task_comments`, `task_work_logs`, `task_remaining_effort_forecasts` | Unaffected: `D13`, `D15` and `D35`–`D37` change transitions, which the application enforces, and add `task_status_transitions`. `uq_tasks_id_project`, `ck_tasks_title`, `_status`, `_soft_delete_actor`, `_version`, `_estimated_minutes`; `ck_task_comments_body`; `ck_task_work_logs_minutes`, `_note`, `_version`; `ck_task_forecasts_remaining_minutes`, `_actual_snapshot`, `_initial_or_correction`, `_initial_note`, `uq_task_forecasts_one_successor` |
| `user_action_tokens` | Unaffected: `ACC-028` invalidates tokens through the invalidation time, which the current predicates admit. `uq_user_action_tokens_hash`, `ck_user_action_tokens_purpose`, `_hash_length`, `_expiry`, `_terminal_state`, `uq_user_action_tokens_one_live` |
| `system_state`, `smtp_configurations`, `holiday_api_configurations` | Unaffected: no pending decision changes their rows. `ck_system_state_singleton`, `_initialization`, `_version`; `ck_smtp_configurations_status`, `_port`, `_security`, `_host`, `_from`, `_from_name`, `_secret_pair`, `_test_actor`, `_activation`, `_retirement`, `_version`, `uq_smtp_configurations_one_active`, `_one_draft`; `ck_holiday_api_configurations_status`, `_country`, `_ciphertext`, `_nonce`, `_key_version`, `_test_actor`, `_activation`, `_retirement`, `_version`, `uq_holiday_api_configurations_one_active`, `_one_draft` |

Two non-unique indexes filter on `PENDING` alone, `ix_leave_requests_pending_cutoff` and
`ix_attendance_corrections_pending_deadline`; the attendance plan says whether each must also serve
`OVERDUE` rows.

### C.7 Tests, written first

Every migration step starts with its probe tests, run on PostgreSQL through Testcontainers and seen
failing against the schema before the step: the `AC-DB-*` scenarios the step concerns, and one
probe per member and history-bound member it touches, showing the predicate accepts and refuses what
the rules say. A step is done when its probes pass and the full Maven suite passes; the part is done
when every `AC-DB-*` scenario passes, which is the gate `plan.md` holds for accepting the migration.
Each migration updates, in the same change, the physical diagram of §19.4 for the tables and
columns it adds or changes (`DB-010`).

### C.8 Order of work

The tasks are C-01 to C-08 in [TASKS.md](TASKS.md). C-01 reads before anything writes; C-02 and C-03 are the expansion; C-04 to C-07 are carried out inside the plans of the modules whose code they need, in the order those plans run; C-08 closes the part.

### C.9 Risks

| Risk | Handling |
|---|---|
| A tightened predicate refuses a stored row | C-01 reads the rows first; the contract step's migration runs its data change before its checks |
| A database with the other `V2` receives V3 | C-01 identifies the history first and stops on it |
| An expansion admits a row a rule forbids, until its contract | Only statuses and columns no running code writes are admitted, and each contract ships with the code that writes them |
| The diagram drifts from the SQL | Each migration updates §19.4 in the same change (`DB-010`) |

### C.10 Not in this part

The code of each module that uses the new tables and statuses: each module plan owns it and ships
its contract migration with it. The decision whether to narrow the quota predicates waits on C-01.
