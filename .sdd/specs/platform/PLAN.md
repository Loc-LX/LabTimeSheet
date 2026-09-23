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
| B | One authorization policy, now in the [Authorization plan](features/authorization/PLAN.md) | `AUTH-012`, `AC-AUTH-011` | Moved under `D40` |
| C | The schema change the decisions require, now in the [Data model plan](features/data-model/PLAN.md) | `DB-011`, `DB-014`–`DB-022`, the audit of `D39` | Moved under `D40` |

Part A is written first because it is on the critical path to the first line of code: `D28`
orders step 5 (this part), the merge into `main`, step 6 (building it) and only then step 8,
the business parts that B and C serve. Parts B and C start after part A is done.

Under `D40` every plan belongs to one feature. Part A stays here while it is being built, so
that its tasks keep their path, and moves to the [Architecture](features/architecture/SPEC.md)
feature once task `A-12` closes it; this file and [TASKS.md](TASKS.md) are then removed.

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
