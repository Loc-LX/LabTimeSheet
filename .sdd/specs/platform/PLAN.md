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
| A | Module boundaries, [below](#part-a--module-boundaries) | `ARC-005`, `ARC-006`, `AC-ARC-001` | Draft of 22 September 2026, revised the same day after review, for approval on its own |
| B | One authorization policy, [§2](#2-design-one-authorization-policy) | `AUTH-012`, [ADR-005](../../rfcs/ADR-005-one-authorization-policy.md) | Approved as version 1.0 on 16 September 2026; revised before it is built, since `D32` and `D35`–`D39` came after it |
| C | The schema change the decisions require, [§3](#3-data-model-the-one-migration-the-decisions-require) | the shared `DB` rules | Approved as version 1.0 on 16 September 2026; superseded by `D32` and `D39`, and rewritten from the audit `D39` starts |

Part A is written first because it is on the critical path to the first line of code: `D28`
orders step 5 (this part), the merge into `main`, step 6 (building it) and only then step 8,
the business parts that B and C serve. Sections 1 to 7 are the version of 16 September and
cover parts B and C.

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
| `R6` | rule citations only | Nothing in step 6: the policy of `ADR-005` is not built yet. When part B builds it, the owning module resolves the scope and the policy receives it, as `D28` decided; this settles, for part B, the contradiction between §2.3 and the second risk of §5 |
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
| `account` into `identity` and `internship` | `AppUserRepository` joins `InternProfile` in the two directory queries; `InternProfileRepository` joins `AppUser` in `findEligibleInternOptions` and `findDueUserIds`; `AccountService` uses `InternProfile` and `InternProfileRepository`. Four references | `internship` composes each result from reads of each module's service contract, joined by user identifier in memory, with the filters and order of the query it replaces. The directory search matches a name, an email *or* a Student Code, so its composed result is the union of two reads: accounts whose name or email matches, from `identity`, and accounts whose Intern profile's Student Code matches, from `internship`. Both are restricted to the requested role; the union has no duplicate; every account in it is then joined with its profile if it has one; and it is ordered by account identifier. The unfiltered directory is every account with its profile, in the same order. `findEligibleInternOptions` and `findDueUserIds` filter both modules with *and*, so their result is the intersection; eligibility keeps its order by display name, Student Code and account identifier, sorted once both sides are read, and the due-date read keeps its order by user identifier. The directory returns an unpaged list today, so no paging is reimplemented. Each composition reads each module a fixed number of times per request, whatever the number of rows (`ARC-010`). `AccountService`'s references leave with `R3` |

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
   evidence the end of step 6 is compared with.
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
| A composed read returns different rows or order than the join it replaces | The directory search is composed as a union, and a test of that union, which no existing test covers, is written against the current join before the change (A.7). Eligibility keeps the filters and the order by display name, Student Code and account identifier that `EligibleInternOptionIntegrationTest` pins; the due-date read keeps its filters, which the activation tests exercise |
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
