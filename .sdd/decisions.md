# Decisions behind the specification

Each entry is a place where [the specification](specs) stated
something that no recorded decision supported. Section 1.1 names who may settle
one. Until an answer is recorded here and applied to the numbered rules, the
specification stays unapproved and no test written against the affected rule is
authoritative.

## How a question gets answered

This project inherited a working codebase whose decisions were largely unwritten,
so most answers came from evidence rather than from a fresh judgement. Four
approaches were used, strongest evidence first.

1. **Find the decision in history.** A commit, a written ruling, a code comment, or a test. A test asserting `403` is evidence that somebody chose `403`. This settled D1 and D2.
2. **Find the rule behind the behavior, then check it is coherent.** Not copying what the code does, but looking for the principle it follows and asking whether that principle holds everywhere. This settled D6 and D8, and in D6 it overturned this page's own alarm.
3. **Judge the found rule against the domain.** A rule can be consistent and still wrong. This is where D7 changed: the code permitted a monthly leave quota of thirty-one days, which is not a business limit but the absence of one.
4. **Ask a person.** Reserved for what the first three cannot settle, which is usually a question of value rather than of fact.

The order matters. Reaching for the fourth without the first three produces a
list of questions nobody can answer, because the answers were already in the
repository.

A decision recorded here outranks the current behavior of the application. Where
they disagree, the application is wrong, and D7 is the case in point.

## How the decisions are labelled

Since 14 September 2026 a decision says where its content comes from, so that a
laboratory choice is never presented as an industry standard.

| Label | Meaning | Examples |
|---|---|---|
| ENTERPRISE-BACKED | Follows a pattern common to enterprise work-management, time-and-labour, and access-control products | Permission from role, scope, and allowed workflow transition; Admin is not unlimited business authority; Project lifecycle and retained history instead of casual deletion; an attendance fact kept apart from its approved exception; an approver chosen by management responsibility; baseline, actual, remaining, current work, and variance |
| ADAPTED | The pattern, shaped to this product | A blocked Task returning to its previous status; a reason required to reopen; notifying the assignee and the Leader; the Mentor, Intern Leader, Intern responsibility chain; two Daily report perspectives |
| LAB-POLICY | A choice of this laboratory, not a standard | The 48-hour limits for attendance adjustments and the day the attendance period closes; an excused violation not lowering compliance |

**Decided for build.** `D12`–`D15`, `D21` and `D23`–`D25` were decided by the
maintainer from analysis and held provisional while they waited on the instructor. On
16 September 2026 the maintainer confirmed them for build, so that planning and
implementation work from a fixed rule set instead of a queue of open questions. The
instructor still reviews them, but as a reader rather than as a gate: a change they ask
for arrives as a new decision that supersedes the one it replaces, and the specification
is amended then, the way every other rule changes. Nothing on this page is waiting for a
signature. The `LAB-POLICY` label stays where it applies, because it says where a choice
came from, not whether the choice is settled.

## Where these stand

| | Question | Answer | Rules it changes |
|---|---|---|---|
| D1 | Admin access to detailed Intern attendance | Permitted, and since 14 September 2026 read access to every report; confirmed by the instructor | `RPT-004`, `RPT-005`, `RPT-011` |
| D2 | External issue tracker in scope | Excluded; report presets deferred | `GOV-015` |
| D3 | A separate document per feature | One canonical location per rule; the split into feature specs was carried out on 14 September 2026 | `GOV-016` |
| D4 | Exact version pinning for test tooling | Range is enough; action completed 12 September 2026 | `ARC-004` |
| D5 | The recovered appendices | Edited, not kept as written | Appendices C–F |
| D6 | Which HTTP status a denial returns | Three tiers, already coherent in code | `AUTH-002` |
| D7 | Valid range of the monthly leave quota | 0 through 4, one fifth of a month | `ATT-003` |
| D8 | Naming the invitation resolution codes | All eight named with their cause | `DB-011` |
| D9 | Stating the SMTP port range | 1 through 65535, a type constraint | `INT-007` |
| D10 | Which stylesheet the desktop overflow contract requires | The assertion was wrong, not the stylesheet; action completed 12 September 2026 | none |
| D11 | Whether approving the specification waits for the Iteration 3 integrated review | No; resolving every code and specification disagreement is the condition instead | §22.2 |
| D12 | Whether a Project may be deleted | Only an empty `PLANNED` Project, with its notifications; any other Project that will not run is cancelled and stays in reports | `PRJ-002`, `PRJ-023`, `RPT-003`, `RPT-011` |
| D13 | Whether an owning Mentor may change a Task's status | Only block, unblock to the previous status, and reopen with a reason, decided by role, scope, and state | `AUTH-005`, `AUTH-008`, `TSK-007`, `TSK-023`, `TSK-025`, `NOT-003` |
| D14 | Whether a late arrival or early departure can be excused | Yes, as a separate, append-only decision by the responsible Mentor, who also decides leave and corrections, all three reusing one decision-history and finalization mechanism; overdue is never rejected; results finalize by monthly period and reopen with Admin approval | `EXC-001`–`EXC-007`, `ATT-019`–`ATT-024`, `ACC-026`, `LEV-008`, `LEV-010`, `LEV-013`, `COR-005`, `COR-007`, `NOT-011` |
| D15 | What Remaining effort and variance mean, and who the Daily report is for | Estimate is the baseline; Remaining is a current forecast; two report perspectives | `GOV-015`, `TSK-021`, `TSK-024`, `RPT-012`, `RPT-014` |
| D16 | When a rule change needs an ADR | Only when it moves an architectural boundary | constitution, Amendment |
| D17 | What to do with `work/tasks-lab-b5` and `work/tasks-sync` | Superseded history; not merged, not a source of truth | none |
| D18 | Whether native SQL may stay in a service | No; special queries go through the data-access layer | `ARC-006` |
| D19 | Whether the suite must pass on Windows without extra flags | Yes; the timezone is normalized in test configuration; done 15 September 2026 | none |
| D20 | How the constitution ranks rules, and what outranks what | Layers by how strictly a rule binds; one authority order by document type | `GOV-001`, `OPS-019`, constitution |
| D21 | Whether a Project may start before the day it is entered | Yes; the start date is Project metadata, and retroactive work stays governed by `TSK-014` | `PRJ-024` |
| D22 | Whether a Project needs an `ARCHIVED` status | No; `COMPLETED` and `CANCELLED` are the final business states, and archiving is a display concern | none |
| D23 | Whether a correction and an exception keep different deadlines | No; both are attendance adjustment requests with 48 hours to submit and 48 hours to decide | `COR-003`, `COR-004`, `EXC-002`, `EXC-003` |
| D24 | How long a Mentor may mark an excuse without a request | Until the attendance period closes, with no separate limit | `EXC-004` |
| D25 | Whether an Intern may see their own attendance report | Yes, their own date only, without export in this version | `RPT-015` |
| D26 | Who confirms a business decision | The maintainer; the instructor reviews and a revision arrives as a new decision, not as a gate | constitution, `shared_context.md` |
| D27 | What the `V3` migration does with months worked before it | Create a period for each, then apply `ATT-020` to it as the running system would: close the ones whose deadline has passed with nothing pending, leave the rest open | none; `ATT-019`–`ATT-024` already say it |
| D28 | How the code is split into modules, and what may depend on what | Seven features and `platform` by dependency, acyclic, with `config` as wiring; leave stays in attendance, the policy table in calendar, internship on its own; six rules relocated unchanged; documents first, code after | `ARC-005`, `ARC-006`, `AC-ARC-001`, `NOT-003`, `NOT-010`, `NOT-011`, `AUTH-004`, `AUTH-009`, `AUTH-011`, constitution |
| D29 | Whether the constitution and ADR-006 keep history and three false statements | No: constitution `2.0.1` states current rules only and corrects three facts; `ADR-006` corrects one; the `INT-009` item of `D28` is closed | none |
| D30 | Which rules about team branches still bind | `OPS-018`, `OPS-020` and `OPS-021` are withdrawn with their identifiers kept; `OPS-019` keeps one owner per shared file, a person or an agent session, and names branches by module | `OPS-018`–`OPS-021`; constitution `2.0.2` |
| D31 | What an amendment of an attendance exception decision may change | Only the decision note, or the nonblank reason of a mark made without a request; never the outcome, work date, violation kind or the Intern's request | `EXC-007`; adds `AC-EXC-004` |

D1 through D5 came from reading the specification against its own history. D6
through D9 came from the audit described at the end of this page, which read the
269 rules against the schema, the permission matrix, and the situations the
existing tests had actually encountered.

Four of the first five were settled by adopting what was already built or already
decided, rather than by a fresh judgement about the business. That was a
deliberate choice, and the reasoning was that the instructor will test the
product, and that a problem found then can be fixed because the documentation is
now structured well enough to carry the change.

For that to hold, every decision below states what reversing it would cost, and
every open one states the assumption that would be used if nobody answers. Read
those as the price of the choice, not as a formality. Without them, "we can fix
it later" is a hope rather than a plan.

**All ten are answered, and all ten now match the code.** D7 was the only one
the application contradicted, and it was implemented on 12 September 2026 in
commit `275e1a4`. Everything else either matched the shipped behavior already or
was settled by writing down a rule the code was following without saying so.

D10 arrived later and by a different route. It is not a gap in the specification
but a contradiction between two tests, found on 12 September 2026 while checking
why the branch was not green. It is recorded here because `AGENTS.md` forbids
deleting a test assertion and the deletion therefore needed a decision rather
than an edit. The page's opening definition does not cover it, and that is stated
in the entry itself rather than by widening the definition.

D11 is outside the opening definition for a different reason. It changes nothing
the system does; it changes what approving the specification depends on. The
first ten are counted as matching the code. D11 is not counted that way, because
it is a statement about process that no code can match or contradict.

D12 matches the code by construction: it adopts behavior the code already had and
writes it into the rules. It was settled by a rule adopted on 14 September 2026 for
rebuilding this specification, that where earlier decisions conflict the most
recent one applies, and it is recorded as pending confirmation with the instructor.

## D1. May an Admin see one Intern's detailed attendance?

**Affects** `RPT-004`, `RPT-011`, and the permission matrix in section 5.2.

This was the first question on this page where the running application was used
to settle a business rule, and it should not have been. `D12` is the second, and it
was done knowingly under the most-recent-decision rule, with confirmation from the
instructor still owed.

| | |
|---|---|
| What the specification says today | An active Admin may view detailed Intern attendance, with the same target scope, page, export, and dataset as an active Mentor. |
| Why it says that | The shipped code implements it, and the change was made to stop the document contradicting the product. |
| What the earlier text said | Admins have no Attendance-report scope or report dataset. |

The rule has moved three times.
[ADR 0002](rfcs/ADR-002-attendance-report-scope.md) reconstructs the
sequence from commits: granted on 17 August 2026, the specification carrying it
deleted on 20 August, withdrawn on 27 August, the specification deleted again on
29 August, restored on 30 August.

The two most recent moves are not equally documented, and the asymmetry runs
against the current wording.

| Move | What supports it |
|---|---|
| 27 August, withdrawn | A dated written ruling in the Partial Jira/Tempo decision record: "Admin is a configuration and account-lifecycle role, not an operational reporting role." It names the HTML page, both exports, the navigation entry, the dataset, and the export service, and specifies a `403` before any read. |
| 30 August, restored | Commit `684e13c`, subject `fix(reporting): restore admin attendance reports`, with no message body and no accompanying record. |

The written ruling is on the side of denial. The code is on the side of access.

**Read the rest of this section together with the widening at its end.** The paragraphs
below describe the position as it stood on the morning of 14 September 2026, when the
question was only about attendance detail. Later that day the instructor granted read
access to every report, so the sentences here that say an Admin holds no Project, Task or
Daily scope are the record of that moment, not the current rule.

Three facts worth weighing before answering.

- An Admin already administers accounts, the internship lifecycle, and attendance policy. Attendance detail is adjacent to work they already do.
- An Admin has no Project or Task report scope at all. Granting attendance detail does not open Project delivery data.
- Attendance detail identifies a named Intern's presence day by day. Whether a system administrator should hold that is a privacy question, not a technical one.

**Options**

| Option | Consequence |
|---|---|
| A. Admin may see it. | The specification stays as written. No code changes. |
| B. Admin may not see it. | `RPT-004` and `RPT-011` revert, the permission matrix changes, and the reporting code and its tests must be changed to deny Admin. |

**Decision:** A. Admin may view detailed Intern attendance, on the same footing
as an active Mentor. The shipped behavior is adopted as the rule.

This knowingly overrides the 27 August 2026 written ruling quoted above. The
stated reasoning is that the behavior is already built, and that reversing it
later carries low risk because Admin holds no Project, Task, or Daily report
scope and the change would be confined to the reporting feature.

The reversal cost, should it be taken later, is: revert `684e13c`, amend
`RPT-004`, `RPT-011`, the section 5.2 permission matrix, the section 15.2 page
map, and `AC-RPT-002`, and change `.sdd/product.md` and `README.md`.

**Decided by:** Loc-LX, primary implementor, highest rank in the `GOV-001`
authority order in the absence of the instructor.

**Date:** 2026-09-11

**Confirmed by the instructor, 14 September 2026.** An Admin may view detailed Intern
attendance. For now an Admin sees everything a Mentor sees; the instructor expects to
remove some fields from Admin later. Two things follow. `RPT-004` now lists the fields
of that dataset, so a later restriction names what it removes. And the plan must let
the Admin field set change in one place: today it is decided in three,
`SecurityConfiguration`, `AttendanceReportQueryService#authorize` where Admin and
Mentor share one branch, and the shared layout, and none of them works per field.
**Widened on 14 September 2026.** The instructor's "full access" covers every report.
An Admin now views the Attendance, Project/Task, and Daily Project Work reports and
their data read-only, and exports them. An Admin edits no Project or Task and decides
no leave, correction, or attendance exception. `RPT-005`, `RPT-011`, `UI-019`, the
§5.2 matrix, and a new revision block in §1.1 of the platform spec carry it. So that
part of it can be withdrawn later without a search through the code, every capability
is its own matrix row and `AUTH-012` requires one authorization policy to decide them;
the architectural side is `ADR-005`. This supersedes the Project/Task and Daily part of
`ADR-002`, which stays as written because it is a dated record.

## D2. Is the external issue tracker permanently out of scope?

**Affects** `GOV-015`.

The specification now states that version 1 will not integrate with Jira or
Tempo, will not mirror issues, sprints, or story points, will not add a
`SUBMITTED` Task state with Leader acceptance and rejection, will not offer
Weekly or Monthly report presets, and will not perform continuous replanning.

Jira is a commercial issue tracker. Teams file work as issues, group them into
sprints, size them in story points, and move them across a board. Tempo is a
time-tracking add-on for Jira: people log hours against Jira issues and it
produces timesheets and reports, often with a submit-and-approve step.

Lab Timesheet already does a narrow version of both natively. Tasks with a single
assignee, an estimate in minutes, dated work logs, and the Daily Project Work
Report cover the same ground without any external service. Iteration 4 was called
"Partial Jira/Tempo" for that reason: a deliberately partial, local reimplementation.

`GOV-015` draws the boundary of that partial slice. Its source is a decision
record removed from the working tree in commit `dad1a9f` and still readable at
`dad1a9f^`. Question 1 of that record was answered "build local Jira/Tempo-inspired
behavior; do not integrate with external Jira or Tempo APIs".

One correction belongs with this question. `GOV-015` says the product "shall not
offer Weekly or Monthly report presets". The record does not say that. Answer
Q22-R defers them: "First delivery includes one Daily Project Work Report; Weekly
and Monthly presets are deferred." Deferred and forbidden are different, and the
rule as written is stronger than the decision behind it.

**Options**

| Option | Consequence |
|---|---|
| A. Confirm the exclusion, and soften the Weekly and Monthly clause to deferred. | `GOV-015` stays and matches its source. Integration needs a new rule; the extra report presets need only a plan. |
| B. Confirm the exclusion as written. | Weekly and Monthly presets become forbidden rather than postponed, which is a new decision, not the recorded one. |
| C. Withdraw it. | `GOV-015` is deleted. External integration becomes unanswered rather than answered no. |

**Decision:** A. The exclusion holds, and the Weekly and Monthly clause is
softened from forbidden to deferred.

`GOV-015` is rewritten as two clauses of different force.

- **Prohibited in v1.** No integration with external Jira or Tempo APIs, no mirroring of issues, sprints, or story points, no Tempo accounts or synchronization, and no `SUBMITTED` state with a Leader acceptance and rejection workflow. Any of these requires a new numbered rule and a recorded decision.
- **Deferred, not promised.** Weekly and Monthly report presets are outside the v1 acceptance scope and are deferred for later consideration. Deferral is not a commitment to build them.

This is not a fresh judgement. It restates the prior team's answer to question 1
of the 26 August 2026 decision record, "build local Jira/Tempo-inspired
behavior; do not integrate with external Jira or Tempo APIs". That record is no
longer in the working tree and is readable at `dad1a9f^`.

The search that confirms it: across the whole codebase the words Jira and Tempo
appear once, in a comment on line 1 of `V2__add_task_effort_planning.sql` naming
the feature. No client, no credential, no dependency. The rule describes what
exists rather than choosing something new.

**Decided by:** the prior team, 26 August 2026, question 1. The split between
prohibited and deferred was settled by Loc-LX.

**Date:** 2026-09-11

## D3. Does a future feature get its own specification document?

**Affects** `GOV-016`.

The specification now states that it is the system-wide layer, that a later
feature must not be appended to it, and that a feature needing its own
specification gets a separate document naming this one as parent.

This is a working method, not a business rule. It was added to stop the pattern
that already damaged this project, where a second document claimed precedence
over the requirements and the two then disagreed. It is stated here because a
numbered rule that describes how people work should be one the team agreed to.

**Options**

| Option | Consequence |
|---|---|
| A. Keep it as a numbered rule. | The layering is enforceable through review, and a feature document that claims precedence is a defect. |
| B. Move it out of the numbered rules. | It becomes guidance in the contributing guide, and nothing in the specification prevents the pattern recurring. |

**Decision:** Neither option as written. `GOV-016` is replaced by a shorter rule
that says what actually matters, and the layering mechanics move to the
contributing guide.

The replacement text:

> Every requirement has exactly one canonical location, and a change in business
> behavior updates that location. A new feature is documented in the same form as
> the features already documented: numbered rules under an applicable prefix, and
> acceptance scenarios in section 20. Splitting the specification across separate
> documents is an organizational choice made when it helps, never a goal, and no
> separate document overrides the canonical location of a rule.

Two things are deliberately different from the rule it replaces. It no longer
describes a parent-child document hierarchy, which was machinery for a problem
the project does not have. And it states the test a reader can apply by eye: open
the new feature's rules and check that they carry a prefix and an acceptance
scenario, exactly as the effort-planning feature did with `TSK-020` through
`TSK-022`, `DB-013`, and `RPT-011` through `RPT-013`.

Per-feature specification files are **deferred, not rejected**. The rules split
cleanly by feature, 150 feature-owned against 119 that cross every feature, and
the seven feature groups match the seven code packages exactly. When that split
is made, each feature file inherits the cross-cutting rules by reference and
never restates them, and a check asserts that every one of the 269 identifiers
appears in exactly one file.

**Decided by:** Loc-LX.

**Date:** 2026-09-11

**Carried out on 14 September 2026.** The maintainer chose to split it, and the
specification now sits in eight specs under `.sdd/specs/`, one per
code package plus a platform spec for the shared rules. The counts this entry
predicted held: the platform spec carries 119 rules and the seven feature specs 150.
A check confirmed that every identifier appears in exactly one spec, and
`.sdd/requirements.md` became an index. `GOV-016` now says a feature's rules and
scenarios live in that feature's spec.

## D4. Must test tooling versions be pinned exactly?

**Affects** `ARC-004`, and section 22.3 where this is recorded as OQ-1.

`ARC-004` pinned Node and Tailwind and said nothing about the test toolchain. A
committed contract test asserted an exact Playwright version that `package.json`
no longer matched. One of the two encoded a decision nobody wrote down, and the
mismatch was why the pipeline on `main` failed.

State when this was raised, on 11 September 2026:

| Where | Value |
|---|---|
| `src/test/js/playwright-contract.test.mjs`, line 8 | `1.55.0`, asserted for equality |
| `package.json` | `^1.62.1` |

**Options**

| Option | Consequence |
|---|---|
| A. Pin test tooling exactly. | A new numbered rule states the pinning policy, and `package.json` is corrected to the asserted version. |
| B. A compatible range is enough. | The assertion has no requirement behind it and is retired. |

**Decision:** B in principle. A compatible range is sufficient for test tooling.
The version actually used is recorded by each test run rather than asserted as an
equality inside a test.

The action that follows was handled separately from the principle, with its own
verification, because correcting one assertion proves nothing about the rest of
the pipeline. Four things had to be established first. All four are answered.

1. **Why `1.55.0` was pinned.** It entered with the harness itself in `6d2c967`, written as an exact version with no caret. No commit message, decision record, or document gives a reason, so it was a habit rather than a decision, and no behavior depended on it.
2. **What the upgrade changed.** Commit `73df96b`, whose subject is `chore: remove redundant docs files`, moved the declaration to `^1.62.1` and updated the lockfile inside the same commit that deleted the requirements specification. The version change has no recorded reason of its own.
3. **What the lockfile resolves to.** `1.62.1`, and the installed tree agrees. The upgrade was complete and internally consistent. Only the assertion lagged behind it.
4. **An actual run.** Playwright `1.62.1` runs on this machine and its Chromium build is installed. The three end-to-end specs need the application and PostgreSQL, so they were not run. `npm run test:e2e` appears in no workflow either, so the pipeline has never run them.

Two things surfaced that were not part of the question.

`npm run test:ui` runs in both `.gitea/workflows/verify.yml` and
`.gitea/workflows/container.yml`. One failing assertion therefore blocked
verification and image publication together, while guarding a tool the pipeline
never invokes.

A second equality assertion sits at `src/test/js/chart-contract.test.mjs` line 12
and pins `chart.js` to `4.5.1`. It is deliberately left alone. Chart.js is a
runtime library vendored into the static assets rather than test tooling, and
that assertion ties `package.json` to the committed `chart.umd.min.js`.
`ARC-004` governs test tooling and does not reach it.

One consequence was visible when the principle was decided. The recording place
used to be the hand-written evidence files under `docs/tests/`, removed by
`ADR-004`. Recording per run therefore has to mean the run itself prints the
resolved version, not that a person types it into a file afterwards. The
repaired assertion does that.

**Action taken, 12 September 2026.** The equality assertion was replaced by three
checks derived from `ARC-004`: the declared value is a caret range on the major
this harness targets, it does not fall below the minor the harness was written
against, and the lockfile resolves inside that range. The run prints the declared
range and the resolved version. Both failure modes were demonstrated before the
change was kept. Restoring an exact pin produces `must be declared as a caret
range`, and declaring a range above what the lockfile holds produces `lockfile
resolves 1.62.1, below the declared range`. `npm run test:ui` went from one
failure to none, twenty of twenty.

**Decided by:** Loc-LX, for the principle on 11 September 2026 and for the action
on 12 September 2026.

**Date:** 2026-09-11, action completed 2026-09-12

## D5. Do the recovered appendices stay in the baseline?

**Affects** Appendices C through F, roughly 1 000 lines.

They were recovered from a detailed specification deleted on 20 August 2026, and
they hold the only written record of the use-case flows, the screen inventory
with access rules, the desktop layout intent, and the system message catalogue.

They also predate the last two iterations and are stale in four known ways.

- No use case covers the Daily Project Work Report.
- No use case or screen covers Task estimates and remaining effort forecasts.
- The screen inventory lists 48 screens against 43 templates that exist today.
- The Admin reporting scope in them predates D1 above.

They are marked explanatory rather than normative, and the numbered rules win
where they disagree. The question is whether a baseline meant for someone
inheriting the project should contain a thousand lines that are knowingly out of
date.

**Options**

| Option | Consequence |
|---|---|
| A. Keep and re-derive them. | The baseline is complete, and someone must bring four areas up to date. |
| B. Keep them as they are. | The reader gets the flows and the screen list, and must respect the staleness warning. |
| C. Remove them. | The document shrinks to the numbered rules and the acceptance catalogue, and the use-case flows and message catalogue exist nowhere. |

**Decision:** A, edited rather than kept. Keeping stale text because a previous
author wanted it is not a reason. A "non-normative" label lowers a section's
authority; it does not make its content true, and it leaves the reader to work
out which paragraphs still apply. Whatever is wrong or duplicated is removed, and
whatever survives is checked against the code.

Each appendix was measured before this was decided.

| Appendix | Lines | Finding | Action |
|---|---:|---|---|
| C, use-case specifications | 350 | Fourteen use cases. Nothing covers the Daily Project Work Report or Task effort planning, and the Admin reporting scope predates D1. | Update in place. Correct Admin scope, and either add the two missing flows or state plainly that they are uncovered. |
| D, screen inventory | 95 | Forty-eight screen identifiers. Forty-five appear nowhere in `src/`, and none resolves to a route: `admin-users` against the real `/admin/accounts`, `intern-leave` against the real `/leave`. | Rewrite anchored to real routes, verified against the controllers. A row a reader cannot follow is removed. |
| E, desktop mockups | 589 | Fifty entries repeating Appendix D's own fields, each ending in a note that the image is held in another repository. | Delete. It duplicates a 95-line table, and the part that was not duplication is not in this repository. |
| F, business rules and messages | 34 | Currently accurate; both checkable claims match `ATT-009` and `ATT-010`. But it restates numbered rules in different words, which is a second canonical location and is what D3 now forbids. | Delete the business-rules half. Keep the message families only where they state something no `ERR` rule states. |

The appendices go from roughly 1 071 lines to roughly 450, and every remaining
line can be checked against the code or against a numbered rule.

**Decided by:** Loc-LX.

**Date:** 2026-09-11

## D6. Which HTTP status does a denial return?

**Affects** `AUTH-002`, `AUTH-011`, `SEC-009`, and every rule that says a request
is denied.

The specification states no HTTP status code anywhere in its 269 rules. It
describes outcomes in words: "the authenticated access-denied response", "a
non-disclosing response", "denied before downstream reads".

The test suite makes 345 assertions about status.

| Asserted | Times |
|---|---:|
| `isOk` | 182 |
| `is3xxRedirection` | 98 |
| `isNotFound` | 26 |
| `isForbidden` | 24 |
| `isBadRequest` | 7 |
| `isConflict` | 5 |
| `isUnauthorized` | 3 |

So 345 assertions encode a contract that no rule states. Changing a denial from
`403` to `404` would break twenty-four tests and violate nothing.

`AUTH-002` is where the ambiguity is written down. It says a guessed identifier
"shall produce an access-denied **or** not-found result". Either is permitted, and
nothing says which applies where, so each endpoint chose on its own.

This matters beyond tidiness. `SEC-009` forbids "existence distinctions useful
for enumeration", and `403 Forbidden` is precisely such a distinction: it tells
the caller the record exists and they may not have it, where `404` does not.
Twenty-four endpoints answer `403` today. Either `SEC-009` does not mean what it
says, or some of those endpoints contradict it.

**Options**

| Option | Consequence |
|---|---|
| A. State the mapping as a rule: which condition returns which status, with `404` for anything a caller is not entitled to know exists. | The strictest reading of `SEC-009`. Some current `403` responses become defects and change. |
| B. State the mapping as a rule, keeping today's split between `403` and `404`. | Nothing in the code changes. `SEC-009` gains an explicit carve-out saying `403` on an authorized-route-unauthorized-record is acceptable. |
| C. Leave it unstated. | The 345 assertions keep encoding an unwritten decision, and the next person to touch a controller cannot tell whether they are fixing a bug or breaking a contract. |

**Decision:** Neither. The alarm in this entry was wrong, and reading the code
found a rule already there and already coherent.

The tests were read for what condition produces which status. There are three
tiers, not the two this entry assumed.

| Situation | Response |
|---|---|
| Not authenticated | redirect to the sign-in page |
| Authenticated, wrong role for the whole route | `403` |
| Authenticated, may reach the route, record absent or not theirs | `404`, identical in both cases |

`403` appears only where the whole route belongs to another role: a non-Admin at
`/admin/*`, an Admin at `/reports/project-tasks`, a missing CSRF token. No record
is implied, so nothing about existence leaks.

`404` is where concealment matters, and it is already tested:
`guessedProjectIdReturnsTheSameNotFoundResponseAsAMissingProject` asserts exactly
the property `SEC-009` demands, that a guessed identifier and an absent record
produce the same response.

So `SEC-009` is not contradicted. `AUTH-002` now states the three tiers instead
of the ambiguous "access-denied **or** not-found".

One correction to the evidence above: the three `401` assertions are an artifact
of `@WebMvcTest` slices, where the full filter chain does not run. The running
application redirects an unauthenticated request to `/login`.

**Decided by:** derived from the code and the tests, confirmed by Loc-LX.

**Date:** 2026-09-12

## D7. What is the valid range of the monthly leave quota?

**Affects** `ATT-001`.

`attendance_policy_versions` enforces `monthly_leave_quota BETWEEN 0 AND 31`.
`ATT-001` lists the monthly leave quota among the fields a policy version stores
and states no bound for it.

The comparison that makes this a gap rather than an oversight: the same rule set
does bound the neighboring field. Grace minutes are pinned to "an integer from 0
through 720 minutes". Quota is not.

An Admin who enters 50 is refused by the database with a constraint error, and no
document explains why, because no document says 31 is the ceiling.

**Options**

| Option | Consequence |
|---|---|
| A. State `0` through `31` in `ATT-001`, matching the schema. | The document explains the refusal. 31 is defensible as the longest month. |
| B. State a smaller business ceiling. | The schema stays valid, and the application gains a stricter check above it. A quota near 31 means an Intern may be absent every workday, which may not be intended. |

**Decision:** Neither option as written. The business limit is **one fifth of a
month**, and `ATT-003` now caps the configurable monthly leave quota at 0 through
4 days.

Four is the largest whole number that stays within a fifth of the shortest
month. Across 2024 to 2030 a Monday-to-Friday month holds 20 to 23 workdays:

| Workdays in the month | Months | A cap of 4 is | A cap of 5 would be |
|---:|---:|---:|---:|
| 20 | 10 | 20% | 25% |
| 21 | 24 | 19% | 24% |
| 22 | 27 | 18% | 23% |
| 23 | 23 | 17% | 22% |

**The stated fifth is not exact in every month, and that was accepted knowingly.**
`CAL-008` removes a global day off from the denominator, so a month carrying five
public holidays leaves fifteen eligible workdays and four days of leave reaches
27%. The alternative, deriving the quota as a fifth of each month's eligible
workdays, would hold the ratio exactly but change `monthly_leave_quota` from an
absolute count an Admin sets into a value the system computes. The difference is
zero to two days, and it was not judged worth changing what a schema field means.

**Implemented on 12 September 2026 in commit `275e1a4`.** This was the one
requirement on this page the code did not satisfy: `AttendancePolicyCommand`
validated `0 <= monthlyLeaveQuota <= 31`, matching the schema. The bound is now
4, behind a test that failed first for the right reason, a quota of five being
accepted where the rule refuses it.

The database column keeps `0 AND 31` as a type constraint. Aligning it would need
a migration and would gain nothing the application does not already enforce, so
it was not done; if a later decision wants the database to carry the business
limit too, that is a separate choice with its own migration.

One part of the verification is outstanding rather than green. Docker was not
available on the machine, so the PostgreSQL integration suites did not run. What
was checked instead: every construction of `AttendancePolicyCommand` across main
and test passes a quota of 3 or 4, so none is invalidated by the tighter bound.

**Decided by:** Loc-LX.

**Date:** 2026-09-12

## D8. Should the invitation resolution codes be named?

**Affects** `PRJ-019`, `DB-011`.

`project_invitations` constrains its resolution code to seven values:
`INVITEE_ACCEPTED`, `INVITEE_DECLINED`, `INVITER_REVOKED`, `MENTOR_REVOKED`,
`LEADER_CHANGED`, `PROJECT_COMPLETED`, `MENTOR_DIRECT_ADD`.

The specification names one of them, `MENTOR_DIRECT_ADD`, in `PRJ-017`.

The behavior is covered. `PRJ-019` says losing leadership, Project completion, or
invitee ineligibility revokes unusable pending invitations without deleting them,
and `PRJ-014` says completion revokes pending invitations. What is missing is the
mapping from each situation to the value that gets stored.

`PRJ-011` and the Project History rules keep resolved invitations visible. A
history view showing a stored code therefore shows a value the specification
never defines.

**Options**

| Option | Consequence |
|---|---|
| A. Enumerate the seven codes in `DB-011` with the situation that produces each. | A reader of Project History can interpret what they see. The list must be updated whenever the schema adds a value. |
| B. Leave the behavior statement as the specification and treat codes as an implementation detail. | Then no history surface may display a raw code, which is a constraint worth stating rather than assuming. |

**Decision:** A. `DB-011` now names every code with the situation that produces
it, read from `InvitationResolutionCode` and the service that assigns each one.

One correction to the evidence above: there are **eight** codes, not seven. The
earlier count missed `INVITEE_INELIGIBLE`, which is present in both the enum and
the database constraint, so the two agree and there was no defect to find.

| Code | Produced when |
|---|---|
| `INVITEE_ACCEPTED` | the intended Intern accepted and became a member |
| `INVITEE_DECLINED` | the intended Intern declined |
| `INVITER_REVOKED` | the issuing Leader withdrew it |
| `MENTOR_REVOKED` | the owning Mentor withdrew it |
| `LEADER_CHANGED` | the issuing leadership term ended before any response |
| `PROJECT_COMPLETED` | Project completion superseded it |
| `INVITEE_INELIGIBLE` | the intended Intern stopped satisfying eligibility |
| `MENTOR_DIRECT_ADD` | a direct Mentor addition superseded it |

**One design choice here departs from common practice and is recorded rather
than hidden.** `PRJ-018` gives an invitation no time expiry, where most systems
expire one after days. What makes it defensible is that the emailed link is not a
bearer token: it opens only the authenticated response page, so a stale link
grants nothing to whoever holds it. In a laboratory where an invitation may wait
through a term break, never expiring is the more useful behavior. A reviewer who
disagrees should raise it as a new decision rather than treat it as an oversight.

**Decided by:** derived from the code, confirmed by Loc-LX.

**Date:** 2026-09-12

## D9. Should the SMTP port range be stated?

**Affects** `INT-001` through `INT-003`.

`smtp_configurations` enforces `port BETWEEN 1 AND 65535`. No rule states it.

This is the smallest item on the page and is listed only because the audit found
it. The range is the whole valid TCP port space, so it constrains nothing a
caller would reasonably attempt.

**Options**

| Option | Consequence |
|---|---|
| A. State it with the other SMTP field rules. | Complete, at the cost of a rule that carries no business judgement. |
| B. Record that generic data-type validation is not specified as business rules, and apply that consistently. | Shorter specification, and one stated reason covering every similar constraint instead of a rule for each. |

**Decision:** A, with the range stated as what it is. `INT-007` now says the port
is an integer from 1 through 65535.

That is the whole valid TCP port space, so it constrains nothing a caller would
reasonably attempt. It is written down because a reader comparing the rules
against the schema should find every constraint accounted for, and because
silence would leave them wondering whether the bound was deliberate.

Conventional SMTP ports are 25, 465, 587, and sometimes 2525. THE system does not
restrict to those, and deliberately so: a laboratory relay may listen anywhere.
What makes the wide range safe is `INT-007` itself, which rejects plaintext
`NONE` under the production profile, so a permissive port cannot become a
plaintext channel in production.

**Decided by:** Loc-LX.

**Date:** 2026-09-12

## D10. Which stylesheet does the desktop overflow contract require?

**Affects** no numbered rule. This entry is different in kind from the nine above:
it settles a contradiction between two tests, not a place where the specification
stated something unsupported. `UI-002` and `UI-015` are unchanged by it.

Two tests in this repository required incompatible versions of
`src/main/frontend/app.css`.

| Test | Required |
|---|---|
| `FrontendSourceContractTest#desktopLayoutContainsPageLevelOverflowContainment` | `min-width: 64rem` on `html` |
| `src/test/js/narrow-screen-contract.test.mjs` | a `@media (max-width: 64rem)` block |

Both cannot hold. A 64rem floor on `html` means the viewport never reports less
than 64rem, so the narrow-screen block is dead code that no width can reach.

The history says which one arrived later and why. Commit `f5eeb0b` added the Java
assertion on 22 August 2026. Commit `6d2c967`, the same day, changed
`min-width: 64rem` to `min-width: 0` and in the same diff added the
`max-width: 64rem` and `max-width: 40rem` blocks and the JavaScript test that
requires them. The floor was not dropped by accident; it was dropped because the
narrow-screen layout it blocked was being added.

The Java assertion has been red ever since, which `ADR-003` recorded on
11 September 2026 as one of two genuine regressions on `main`. Because CI runs
`./mvnw -B test`, `main` has been failing this check for three weeks.

**Options**

| Option | Consequence |
|---|---|
| A. Restore `min-width: 64rem` in the stylesheet. | The Java test passes and `narrow-screen-contract.test.mjs` becomes vacuous, asserting the presence of a block that can never apply. It also contradicts `UI-015`, which asks a narrower viewport to wrap or scroll rather than be blocked. |
| B. Drop the `min-width` assertion and keep the other two. | The stylesheet is unchanged. Containment is still asserted through `overflow-x: hidden` on `body` and `max-width: 100%`, which are the declarations that actually prevent page-level overflow. |

**Decision: option B.** The assertion was removed; the stylesheet and every
template were left untouched.

The reasoning is that `min-width: 64rem` was never the contract. No numbered rule
asks for it, and the phrase "page-level overflow" that the test method is named
after appears nowhere in the specification. `UI-015` requires tables to stay
usable at desktop widths and asks narrower viewports to wrap or scroll. A minimum
page width satisfies neither sentence; it prevents the second one from ever being
exercised. What does satisfy the rule is horizontal containment on `body` and a
per-table scroll region, and both remain asserted.

**`AGENTS.md` forbids weakening an assertion to make a test pass, and this
decision deletes one.** That prohibition is why this is recorded rather than
quietly edited. The distinguishing fact is that the deleted assertion did not
protect a requirement: it contradicted a passing test and a numbered rule. The
two assertions that carry the intent were kept, and the method still fails if
either is removed.

**A second, smaller correction is recorded under the same decision.**
`everyTemplateTableIsWrappedInAScrollRegion` walked every template containing a
`<table>` and therefore included `reports/print.html`, which failed. That
template is a standalone A4 landscape document with its own `<style>` block and
no application shell; it is rendered to paper and PDF, where a horizontal scroll
region does not exist. `UI-015` bounds its obligation to viewport width, so the
print template is outside its scope and is now excluded by name, with the reason
stated in the source.

**Reversing this** costs one line in one test file, plus a decision about what to
do with `narrow-screen-contract.test.mjs` and the two media blocks, which would
have to be deleted with it. The stylesheet was not touched, so nothing else moves.

**Implemented on 12 September 2026.** `FrontendSourceContractTest` failed first
for the right reason: 28 tests, 2 failures, the `min-width` assertion and the
`reports/print.html` parameter. After the change it runs 27 tests green, one fewer
because the print template is no longer a parameter. `npm run test:ui` passes
20 of 20, including the narrow-screen safeguard, so the two suites now agree.
The class also gained the rule trace `TST-005` requires; it was one of the 118
that carried none.

Verification was run with JDK 25 and `-Duser.timezone=Asia/Ho_Chi_Minh`, which
`ADR-003` records as necessary on Windows. Docker was not running, so no
PostgreSQL suite was run; this class reads files and needs none.

**Decided by:** Loc-LX.

**Date:** 2026-09-12

## D11. Does approving the specification wait for the Iteration 3 integrated review?

**Affects** §22.2 of the specification. No numbered rule. Like `D10`, this entry
settles something other than a gap in a rule: it settles what approval of the
specification depends on.

§22.2 listed three conditions for approving the specification. The third was that
"the Iteration 3 integrated review runs and its exit demonstration passes".

That review checks the code against the specification. A check of code against a
specification can only mean something once the specification is fixed, because
otherwise a failure cannot say whether the code or the rule is wrong. So the third
condition made approval wait for a check that could only run after approval, and
as written the specification could never be approved.

Deleting the condition outright has its own cost, and it is specific to how this
project came about. The specification was not written before the code. It was
recovered afterwards, partly by reading what the code does. Approving it without
reading the code again risks approving a code defect as a requirement, or
approving a rule that contradicts behavior already shipped. `GOV-014` is a live
case: it forbids physically deleting a Project through a normal interface
operation, and `POST /projects/{projectId}/delete` physically deletes a `PLANNED`
Project.

**Options**

| Option | Consequence |
|---|---|
| A. Keep the third condition as written. | Approval waits for validation and validation waits for approval. The specification is never approved. |
| B. Delete the third condition. | Approval no longer depends on anyone reading the code. A rule that contradicts shipped behavior can be approved without anyone noticing. |
| C. Split it. Resolving every disagreement between code and specification becomes the approval condition. The integrated review moves after approval. | Approval can happen, and it happens only after the code has been read against the rules. |

**Decision: option C.**

The two halves of the old condition were different kinds of work. Finding where the
code and the specification say different things is part of reviewing the
specification, because each disagreement is a place where the rule may be wrong
and a person has to say which side is intended. Demonstrating that the whole
system meets the specification is validation, and it needs an approved
specification to measure against.

What each half covers, using what the 13 September review had already found:

| Finding | Kind | Blocks approval |
|---|---|---|
| `GOV-014` against the Project delete route | code and specification disagree | yes |
| Daily report showing a `DONE` Task's variance under each author | code does something the specification neither permits nor forbids | yes |
| `UI-019` and `AC-UI-005` against the shared layout and `RPT-004` | specification disagrees with the code and with `ADR-002` | yes |
| No test fails when the leave edit-after-start guard is removed (`LEV-012`) | missing test; rule and code agree | no |
| No test fails when the eligible-workday guard is removed (`LEV-002`) | missing test; rule and code agree | no |

The last two rows are the distinction worth keeping. A rule the code satisfies and
no test protects is a gap in the evidence, and it belongs to validation. A rule the
code does not satisfy, or satisfies only by an interpretation nobody recorded, is a
question about the requirement, and it belongs to approval.

**Reversing this** restores one bullet in §22.2 and removes the paragraph that
replaced it. Nothing in code or tests depends on either wording.

**Decided by:** Loc-LX, on 14 September 2026, after the split was proposed and the
plan that begins with recording it was approved.

**Date:** 2026-09-14

## D12. May a Project be deleted?

**Affects** `GOV-014`, `PRJ-002`, `AC-PRJ-014`.

`GOV-014` forbade physically deleting a Project, membership, Task, comment or work
log through a normal interface operation. `POST /projects/{projectId}/delete` does
exactly that for a `PLANNED` Project: it removes the Project and the rows it owns
from eight tables, and only its owning Mentor may call it. No numbered rule
permitted it, and the Project use case specification committed alongside it on
25 August 2026 listed eighteen use cases, none of them a deletion.

The two positions carry dates. The prohibition was already in the 17 August
specification and did not change afterwards. The deletion arrived in code on
25 August 2026 with a written reason on the guard that authorizes it: a `PLANNED`
Project is a "disposable draft", and "once execution has started, the Project and
its retained history are immutable".

**Options**

| Option | Consequence |
|---|---|
| A. Keep the prohibition and remove the delete route. | Working, tested behavior is removed, and a Mentor who creates a Project by mistake has no way to discard it. |
| B. Permit deleting a `PLANNED` Project and state the exception. | The specification describes what ships. History is still protected from the moment a Project is activated. |

**Decision: option B.**

It follows the rule adopted for rebuilding this specification: where earlier
decisions conflict, the most recent applies. The deletion dates from 25 August and
the prohibition from 17 August.

`PRJ-002` now carries the behavior, because it already governs the Project
lifecycle, so the rule set keeps 269 identifiers. `GOV-014` states the exception by
reference to `PRJ-002` instead of repeating it. `AC-PRJ-014` adds the scenario.
The wording follows the guard and five existing tests: only the owning Mentor may
delete, only while the Project is `PLANNED`, and an `ACTIVE` Project refuses the
request and offers no delete action.

**One point is weaker than the rest and is recorded as such.** The 25 August
position was never written into any specification; it exists only as code, a
Javadoc comment, and tests. Treating a committed feature as an earlier team's
decision is the interpretation applied here. It is on the list of assumptions to
confirm with the instructor.

**The rows removed are not always empty.** `PRJ-013` refuses work logging while a
Project is `PLANNED`, so work logs should be absent, but a `PLANNED` Project may
already hold Tasks, comments, memberships, leadership terms, invitations and exit
requests, and deletion removes them. The exception is justified by the Project not
having begun, not by it holding nothing.

**Reversing this** means removing the added sentences from `PRJ-002`, `GOV-014`
and `AC-PRJ-014`, and deciding separately whether the delete route stays.

**Decided by:** Loc-LX, on 14 September 2026, by adopting the most-recent-decision
rule, pending confirmation with the instructor.

**Date:** 2026-09-14

**Open questions raised on 14 September 2026 by a domain analysis:** whether accepted
invitations, comments and exit requests from Interns must survive the deletion;
whether an `ACTIVE` Project can be abandoned, since today the only way out is to
soft-delete its unfinished Tasks and complete it; whether former members are told,
and what happens to notifications that link to the deleted Project; and whether a
deletion needs a record or a way back. The same analysis found that an abandoned
`PLANNED` Project keeps its Leader from completing or withdrawing, because the
internship guard counts every current membership, so some way out of `PLANNED` is
needed whatever the answer.

**Decided on 14 September 2026.** A `PLANNED` Project can be deleted only while it is
empty: the one membership and one leadership term created with it, and no Task,
invitation, or exit request (`PRJ-002`). Any other Project that will not run, whether
`PLANNED` with history or `ACTIVE`, is cancelled (`PRJ-023`): the owning Mentor gives a
reason, the Mentor and time are recorded, intervals close but stay, pending invitations
are revoked with a ninth resolution code `PROJECT_CANCELLED`, pending exit requests are
superseded, Tasks and history stay unchanged, and the Project becomes read-only. Only
`CANCELLED` was added; `ARCHIVED` has no meaning yet that `COMPLETED` and `CANCELLED`
lack. Deleting an empty draft leaves no audit record, which `GOV-009` would forbid.

**Refined the same day.** Deleting an empty draft also deletes the notifications raised
for it, so no link is left pointing at nothing. A cancelled Project stays in the
Project/Task report, labelled, and its Daily history is kept; an all-Projects Daily
request leaves it out unless a filter includes it. The constitution's `GOV-014` rows
now say the same. **Status:** decided; confirmed for build on 16 September 2026.

## D13. May an owning Mentor change a Task's status?

**Affects** `AUTH-008`, `TSK-007`, the section 5.2 permission matrix, and `AC-AUTH-004`.

Found on 14 September 2026 while reading the Task service for `D12`.

| | |
|---|---|
| What the rules say | Only the current assignee changes a Task's status. A Mentor may view and comment and is refused every status change. |
| What the code does | `TaskService#changeStatus` lets the owning Mentor change the status of any Task on an `ACTIVE` Project. |
| Evidence for the code | Commit `d897da0`, 24 August 2026, "Mentor task access"; `TaskCreationIntegrationTest#owningMentorCanChangeStatusForAnyTaskOnAnActiveProject`; `TaskMutationBoundaryTest#owningMentorCanChangeStatusForAnyProjectTask`; and `docs/tests/web/task-pages.md` of the same day: "The owning Mentor and current assignee may change status on an ACTIVE Project". |
| Evidence for the rules | The prohibition dates from 17 August. Edits to the specification after 24 August did not touch these rules, so they neither confirmed nor revisited it. |

The most-recent-decision rule does not settle this one. The code change is later, but
nothing shows that anyone weighed it against the rule it breaks.

**Decided on 14 September 2026 by Loc-LX.** Neither version stands. Permission follows
the hierarchy Mentor, Intern Leader, Intern, and is decided by role, scope, current
status, and target status; a higher role never implies any status (`TSK-023`).

| Actor | Scope | May |
|---|---|---|
| Current assignee | Their own Task | Every `TSK-007` transition |
| Current Leader | Tasks of the Project they lead | Block, unblock, reopen `DONE → IN_PROGRESS` |
| Owning Mentor | Tasks of their Project | The same as the Leader |
| Admin | none | Nothing |

Nobody starts a Task or marks it `DONE` for its assignee. No `SUBMITTED`, `ACCEPTED`,
or `REJECTED` state is added, and `GOV-015` now says that reopening for correction is
not an acceptance workflow. The code that lets an owning Mentor set any status, and the
two tests asserting it, change with the implementation.

**Refined the same day.** Unblocking returns a Task to the status it held before the
block, so a Leader or Mentor cannot start a `TODO` Task by blocking and unblocking it.
A reopen needs a reason. Every block, unblock, and reopen leaves a transition record
(`TSK-025`), because a comment alone is not an audit trail; `GOV-009` now lists that
narrow history beside correction and leadership history. The assignee is notified
when someone else acts, and the current Leader too when the owning Mentor does
(`NOT-003`). **Status:** decided; confirmed for build on 16 September 2026.

## D14. May a late arrival or early departure be excused?

**Three of the limits below were replaced later.** `D23` made the correction and
exception deadlines 48 hours on 16 September 2026, and `D24` bounded the Mentor's mark by
the attendance period on the same day. Each superseded line is marked where it stands; the
wording is kept, because this is the dated record of what was decided on 14 September.

**Affects** `ATT-011`, `ATT-015`, `ATT-016`, `COR-006`, `ACC-019`. Raised by the
question under `COR-006` of whether an approved correction excuses an early departure.

**Decided on 14 September 2026 by Loc-LX**, following the practice of time-and-labour
systems that keep a time correction apart from an attendance exception.

- The fact stays recorded as it happened: late, or early departure. An approval never clears it.
- A separate approval marks the violation excused or unexcused.
- An excused violation does not lower the compliance score, and stays in attendance history and statistics.
- The approver is the Mentor responsible for that Intern, not any active Mentor. No such relation exists today, so the Intern profile needs a responsible Mentor assigned by an Admin, unless the laboratory confirms a central approver.
- A Leader never approves, and an Admin never decides.

**Completed the same day and written as rules.**

- Either the Intern requests an excuse with a reason, or the responsible Mentor marks one excused with a reason; both record actor and time (`EXC-002`–`EXC-004`).
- The Intern may request through 24 hours after scheduled end, and the Mentor decides within 24 hours of the request. LAB-POLICY. **Superseded by `D23` on 16 September 2026: both limits are 48 hours (`COR-003`, `COR-004`, `EXC-002`, `EXC-003`).**
- An excused violation does not lower compliance and stays in history and statistics (`EXC-005`). **LAB-POLICY.**
- The responsible Mentor is set before the internship becomes `ACTIVE`, not necessarily at account creation; an Admin may replace them, and earlier decisions keep the Mentor who made them (`ACC-021`, `ACC-026`).
- Leave, corrections, and exceptions are all decided by the responsible Mentor (`LEV-008`, `COR-005`, `AUTH-003`), unless the laboratory later names a central attendance approver. An Intern Leader never approves.

**The last four points, decided the same day.**

- A request undecided after 24 hours becomes **overdue**, and the responsible Mentor is notified again. It is neither excused nor unexcused until the Mentor decides; overdue is not rejected (`EXC-003`, `NOT-011`). ENTERPRISE-BACKED. **Superseded by `D23` on 16 September 2026 in its number only: the window is 48 hours, and what overdue means is unchanged.**
- A Mentor may mark an excuse without a request up to **48 hours** after scheduled end (`EXC-004`). LAB-POLICY. **Superseded by `D24` on 16 September 2026: the mark is bounded by the attendance period of the work date instead, with no separate limit of its own.**
- A decision changes only inside the attendance finalization window, by appending a new decision or a reversal with actor, time, and reason; the latest effective decision is current and history is immutable. After the window, only an explicit reopen flow may change it (`EXC-007`). ENTERPRISE-BACKED.
- When the responsible Mentor is locked or deactivated, an Admin assigns another active Mentor and every pending leave, correction, and exception request moves to them. The Admin does not become an approver, and past decisions keep their original approver (`ACC-026`). ENTERPRISE-BACKED.

**Finalization and overdue handling, decided the same day.**

- Attendance is finalized by period. Each month is a period that finalizes at 23:59 on the fifth day of the next month (`ATT-019`, `ATT-020`); it was the third day until `D24` moved it. Period-based finalization is ENTERPRISE-BACKED; the grace is LAB-POLICY.
- A period does not finalize while a request affecting it is pending or overdue; those are resolved or reassigned first.
- After finalization nothing changes directly. The Intern or the responsible Mentor asks to reopen with a reason; an Admin reopens only the records or dates needed and is recorded doing so, but never approves or decides; the responsible Mentor then acts and finalizes again (`ATT-021`–`ATT-023`).
- The same rule now holds for leave, corrections, and exceptions: a requester who misses a submission deadline is refused, while an approver who misses a decision deadline leaves the request `OVERDUE`, reminded and reassignable, never rejected (`LEV-010`, `COR-007`, `EXC-003`, `NOT-011`). ENTERPRISE-BACKED.

**Interpreted from the decision, not stated in it.** A period belongs to one Intern. A reopened range is finalized
again by the responsible Mentor. An overdue leave request keeps its quota reservation and
blocks overlaps, and `LEV-012` allows approving it after its start.

**The last three points, decided the same day and reviewed before closing.**

- An Intern may withdraw a `PENDING` or `OVERDUE` leave request until its period is finalized. Withdrawal releases the quota reservation and the overlap block and keeps the request and its history. It never deletes or changes an attendance fact: a day the Intern was absent is still absent (`LEV-013`). ENTERPRISE-BACKED.
- An Admin approves or rejects a reopen request, deciding only whether to reopen, from the reason, the records or range, and the data-governance and finalization rules, never the attendance, leave, correction, or exception itself. A rejection keeps actor, time, and reason (`ATT-022`). The request notifies every active user the authorization policy lets decide it, not every Admin by role, so narrowing Admin rights or adding an attendance-administration role later needs no notification change (`NOT-011`). ENTERPRISE-BACKED.
- A correction no longer locks when its 24-hour decision window ends. **The window became 48 hours under `D23` on 16 September 2026; that a decided correction does not lock is unchanged.**
- Leave, corrections, and exceptions reuse one mechanism, not one workflow: immutable decision history, the latest effective decision as current, amendments and reversals audited with actor, time, and reason, and finalization with reopen (`ATT-024`). Each keeps its own actions and states. An **amendment** changes the content of the current decision; a **reversal** reverses its outcome. Neither returns a decision to pending, and a change that needs a new approval is a new request. ENTERPRISE-BACKED.
- Corrections and exceptions may be amended or reversed until the period is finalized (`COR-005`, `EXC-007`).
- Approved leave is never reversed. Before it begins, the Intern cancels it under the cancellation rule; after it begins, only an audited amendment by the responsible Mentor changes it, and the attendance fact stays (`LEV-011`). ENTERPRISE-BACKED. Recalculating attendance and compliance when an amendment leaves a past date without leave is LAB-POLICY.

**Derived from those points, not stated in them.**

- A Mentor sets only an outcome and a note on a correction or an exception today, so an amendment there changes the note; any other amendable value would need a rule of its own.
- An amendment to approved leave can only withdraw approval from dates, because adding a date needs a new approval.
- A leave decision is never reversed, so a rejected request stays rejected and those dates need a new request. Since `LEV-012` forbids retroactive leave, a rejected request whose dates have passed can no longer become leave.
- Under `COR-001` a row keeps one correction request, so after a decision only the responsible Mentor's amendment or reversal changes it.

**Status:** closed on 14 September 2026; decided, and confirmed for build on 16 September 2026.

## D15. What do Remaining effort and variance mean, and who is the Daily report for?

**Affects** `GOV-015`, `TSK-021`, `TSK-022`, `TSK-024`, `RPT-011`, `RPT-012`, `RPT-014`,
and the glossary.

**Decided on 14 September 2026 by Loc-LX**, taking the work model of a project-scheduling
tool as the reference without copying the product.

| Term | Meaning here |
|---|---|
| Baseline | The Task estimate. It never changes once work is retained (`TSK-020`). |
| Actual | Actual Task effort, the lifetime sum of work logs. |
| Remaining | The latest Remaining effort forecast less the work logged since it, never below zero; zero once `DONE`. |
| Current Work | Actual plus Remaining. |
| Variance | Current Work minus Baseline. |

The current Leader may record a new forecast whenever the prediction changes
(`TSK-024`). That is not re-baselining and not the continuous replanning `GOV-015` used
to forbid; `GOV-015` now forbids re-baselining instead. Forecasts are appended, never
overwritten. `ADR-001` described forecasts only at reassignment; its text is kept
unchanged at the end of this page.

Remaining decreases as work is logged, which was chosen over keeping it fixed: a fixed
Remaining would add every logged minute to Current Work without anyone re-estimating.

Variance belongs to the Task. The Daily report is mainly for the owning Mentor and the
current Leader, and shows two perspectives (`RPT-014`): a Task view with each Task's
planning values once and its contributors, and an Intern view with minutes only, so a
Task's variance is never shown as if it belonged to one Intern. Ordinary Interns are not
given the Daily report; they see their own work logs in their Task pages.

**Refined the same day.** The current Remaining effort uses the latest effective forecast,
the most recent one no correction has superseded; earlier forecasts stay as history and
never feed the current value (`TSK-021`). The Daily report gained its own use case,
`UC-16`. **Status:** decided; confirmed for build on 16 September 2026.

## D16. When does a rule change need an ADR?

**Decided on 14 September 2026 by Loc-LX.** The constitution's Amendment table required
an ADR for every change to an existing rule, while the session handoff forbade new
decision files, and in practice no rule change since 11 September had an ADR. The table
now reads: a rule change goes to the spec that holds it and its `CHANGELOG.md`, with the
decision recorded on this page, and an ADR is written only when an architectural
boundary moves. Under that rule `D12` to `D15` are recorded here, and the one
architectural decision of the day, a single authorization policy, is `ADR-005`.

## D17. What happens to `work/tasks-lab-b5` and `work/tasks-sync`?

**Decided on 14 September 2026 by Loc-LX.** Both carry the same two commits of 24 August
2026: Task complexity, effort totals, Leader estimation defaults, per-member metrics on
the Task list, and early design records of the membership-exit flow. Iteration 4 chose a
different effort model two days later. The branches are superseded history. They are not
merged and are not a source of truth, though they may be read to trace history. They stay
on the remote untouched.

## D18. May a service keep native SQL?

**Decided on 14 September 2026 by Loc-LX.** No. `ARC-006` stays as written. The native
SQL in `ProjectService#deleteProjectRows` moves behind the data-access layer when the
deletion is reimplemented for `PRJ-002`. No named exception is created to legitimize the
current code.

## D19. Must the suite pass on Windows without extra flags?

**Decided on 14 September 2026 by Loc-LX.** Yes. `./mvnw test` has to run deterministically
on Windows without anyone remembering `-Duser.timezone`, so the timezone is normalized in
test configuration that every Spring test context reaches. This answers the question
`ADR-003` left for a future decision.

**Done on 15 September 2026.** `LegacyTimeZoneTestListener`, registered in
`src/test/resources/META-INF/spring.factories`, applies the application's own
canonicalization of `Asia/Saigon` when a Spring Boot test context starts, and
`ApplicationTimeZoneIntegrationTest` fails if it stops doing so. A pom-wide
`-Duser.timezone` was rejected because forcing the JVM onto Vietnam time would hide the
failures `GOV-011` exists to catch. `./mvnw -B test` then passed 755 tests on Windows
with no flag.

## D20. How does the constitution rank rules, and what outranks what?

**Decided on 14 September 2026 by Loc-LX**, after reviewing the ten constitution
ambiguities listed in `plan.md`.

- The three layers rank rules by how strictly they bind. Layer 1 admits no exception, Layer 2 only through an approved ADR, Layer 3 a documented deviation. Subject is a grouping inside a layer. Before, the layers named who may authorize an exception while eleven of fourteen Layer 3 rows admitted none, so Layer 3 could not be told apart from Layer 1.
- `TST-011`, `ARC-009` and `GOV-006` move to Layer 1, because no reason excuses weakening an assertion, editing an applied migration, or silently widening scope. `AUTH-002` and `SEC-013` join Layer 1; `AUTH-012` and `SEC-007` join Layer 2, `SEC-007` because it rests on production running one instance.
- A standing deviation names its owner, its reason, and the condition that ends it. The first covers `TST-005` and `TST-007` for test classes written before `ADR-004`.
- The authority order of `GOV-001` named superseded sources and not the constitution, the specs, or this page. It now ranks by document type: the constitution, the feature specs, the agent and contributor guides, then code and tests. This page and the ADRs explain the specs rather than outrank them.
- `OPS-019` drops "taskmaster-verified", a role from the retired iteration plan.
- The constitution header names the instructor as business reviewer, not signer, and carries version `1.0.0-draft` until it is locked.

**Refined the same day, after a last contradiction and enforcement-gap check.**

- The constitution is canonical only for each indexed rule's layer and exception, the standing deviations, the definition of done, and the AI agent policy. For the wording of a rule the spec wins, so a shortened row cannot narrow a rule that `GOV-001` now ranks below it.
- `ARC-006` claimed a build failure that `LayerStructureTest` does not provide, while `ProjectService#nativeDelete` runs native SQL (`D18`); it is now a listed gap. `ARC-001` overstated what the compiler setting refuses. Six rows that had dropped a prohibition were completed.
- Changing the constitution itself needs the maintainer's agreement, a decision here, and a new version: major when an obligation is removed or weakened, minor when one is added, patch for wording.
- The constitution is locked only after a current full test run backs its enforcement column; the last one was on 13 September 2026.

**Status:** decided. **Locked on 15 September 2026** by Loc-LX as version `1.0.0`, after
`./mvnw -B test` passed 755 tests on Windows with no timezone flag (`D19`) and the reverse
enforcement check corrected `GOV-004` and `GOV-011`.

## D21. May a Project start before the day it is entered?

**Decided on 15 September 2026 by Loc-LX**, after the first end-to-end run failed because
the Project form and `ProjectService` refused any start date before today. That check
had been in the code since 25 August 2026, and no rule in the specification asked for it.

- Yes. A Project may be created with a start date in the past, today, or in the future, and its end date must not precede its start (`PRJ-024`). A Project that began a week ago can be entered today without falsifying its history. ENTERPRISE-BACKED: project-scheduling tools default a new project to today but let its start date be set earlier, and record actual starts that already happened.
- A past start date does not open retroactive Task work. Which dates a work log may carry stays with `TSK-014`, which refuses a date before the member joined; the initial Leader joins when the Project is entered. ADAPTED: the laboratory keeps the Project's dates and the right to log work as separate rules.
- The earlier suggestion to keep the check by analogy with `LEV-012` was rejected. Refusing retroactive leave says nothing about when a Project may have started.
- The code changes to match the rule; the end-to-end journey that enters a Project starting seven days earlier stays as it is and now proves the case.

**Status:** decided; confirmed for build on 16 September 2026.

## D22. Does a Project need an `ARCHIVED` status?

**Decided on 16 September 2026 by Loc-LX**, reviewing the Project lifecycle against how
large work-management systems separate state from storage. Recorded by the maintainer as
The maintainer numbered these four D36 to D39 while reviewing them on 16 September
2026. That numbering exists only in that conversation, not in any document here, and this
page numbers decisions in the order the project took them, so they are `D22` to `D25`.

- No. A Project keeps `PLANNED`, `ACTIVE`, `COMPLETED` and `CANCELLED`. ENTERPRISE-BACKED: those systems keep one axis for the business state, which says how work ended, and a separate archive mechanism, which says whether it still appears in a working list. Archiving there is an administrative, reversible operation, not a step in the workflow.
- A long list of finished Projects is a display problem. The list shows `PLANNED` and `ACTIVE` by default and offers a filter for the rest.
- Should archiving ever be needed, it arrives as `archived_at` and `archived_by` with the actor retained, never as a new value in the status column. That keeps the state machine, its transition rules, the schema constraint and every report untouched.

**No rule changed.** This decision exists so the question is not reopened.

**Status:** decided.

## D23. Do a correction and an exception keep different deadlines?

**Decided on 16 September 2026 by Loc-LX.** Until now a
missed-checkout correction had 24 hours to submit and 24 hours to decide, while an
attendance exception had 48 and 48.

- Both are **attendance adjustment requests**: raised after the attendance event, decided by the responsible Mentor, affecting the attendance result. They now share one policy, 48 hours to submit and 48 hours to decide (`COR-003`, `COR-004`, `EXC-002`, `EXC-003`). ENTERPRISE-BACKED: time-and-attendance systems put every post-event change through one adjustment framework and let the period close be the hard boundary. The 48-hour numbers are LAB-POLICY.
- Each request is also bounded by its attendance period, so the real deadline is whichever comes first.
- What stays different is the evidence, not the clock. A correction carries a proposed checkout with a reason, leaves the raw record untouched, and is decided by the responsible Mentor alone.
- 48 hours covers a single weekend, which 24 did not, and still keeps the event fresh enough for the Mentor to judge.

**Status:** decided; confirmed for build on 16 September 2026.

## D24. How long may a Mentor mark an excuse without a request?

**Decided on 16 September 2026 by Loc-LX**, after noticing that
the 7-day proposal, and the 48 hours in `EXC-004` before it, contradicted the monthly
close of `ATT-020`: a work date on the 30th would have lost its window on the 3rd.

- The Mentor may mark a late arrival or early departure excused **while the attendance period of that work date is open**, with no separate limit of its own (`EXC-004`). Afterwards the change goes through the reopen workflow of `ATT-022`. ENTERPRISE-BACKED: a manager adjusts attendance until the period closes, and after that through an explicit retroactive route with a higher approval.
- One boundary instead of two removes the contradiction and matches what the system already enforces everywhere else.
- The cost is that the window is uneven: a work date early in the month has weeks, one at month end has days. The single lever for that is the closing day, and it moves with this decision from the third to the fifth day of the next month, 23:59, in `ATT-020`. Three days left a Mentor almost no time to review a work date at month end; five keeps two more working days without holding the month open long enough to disturb reporting. Eight was considered and refused as too late. LAB-POLICY.

**Status:** decided; confirmed for build on 16 September 2026.

## D25. May an Intern see their own attendance report?

**Decided on 16 September 2026 by Loc-LX**.

- Yes. An active Intern reads their own attendance for a selected date: check-in, raw and effective checkout, counted minutes, the late and early-departure flags, and whether each is excused (`RPT-015`). ENTERPRISE-BACKED: self-service attendance is standard, because someone who cannot see what the system recorded cannot correct it in time.
- The view resolves the Intern from the authenticated account, and carries no other Intern's data, no Task effort, estimate, Current Work or variance, and no Project total. It is not the Daily Project Work Report, which stays with the owning Mentor and the current Leader (`RPT-011`).
- Without it the 48-hour window of `D23` is not usable in practice: an Intern who cannot see a missing checkout learns of it only when someone else notices.
- No export in this version. That limits the first release rather than protecting the data, which is the Intern's own, so adding an export later breaks no principle.

**Status:** decided; confirmed for build on 16 September 2026.

## D26. Who confirms a business decision, now that none is held provisional?

**Decided on 16 September 2026 by Loc-LX**, on the same day the decisions listed
under *Decided for build* above stopped waiting on the instructor.

- The maintainer decides, and the decision is in force from the moment it is recorded. Waiting for a countersignature was holding eight decisions, and through them the migration, the technical plans, and every rule that depends on them.
- The instructor reviews as a reader. A change they ask for arrives as a **new decision that supersedes the one it replaces**, under the first row of the constitution's Amendment table, and the earlier decision keeps its wording with a pointer to what replaced it. Nothing is rewritten backwards.
- This removes nothing from the instructor. It removes a queue: a decision that has not been reviewed yet is now *decided and reviewable*, not *blocked*.
- Two documents said otherwise and were corrected with this decision: the `Business reviewer` row of the constitution, which named the instructor as the person who confirms decisions marked provisional, and assumption `A7` of `shared_context.md`, which assumed the instructor is available for that confirmation. The constitution went to `1.0.1`; the change is wording, so it is a patch.

**Status:** decided.

## D27. What does the `V3` migration do with the months that predate it?

**Decided on 16 September 2026 by Loc-LX.** `D14` gave attendance monthly periods that
finalize, and `ATT-019`–`ATT-024` describe them. The database holds months worked before
any of that existed, so the migration has to give them a state.

- The migration creates one period per Intern and month that has attendance, and then **applies `ATT-020` to each of them exactly as the running system would**: finalize it if 23:59 on the fifth day of its following month has passed, and leave it open otherwise.
- **Nothing is force-closed.** `ATT-020` already refuses to finalize a period while a leave or correction request affecting it is pending or overdue. Those months stay open and finalize on their own when the last request is decided or withdrawn, which is what the rule says everywhere else.
- An earlier draft of the platform plan proposed closing every month before the migration outright, with the migration recorded as the actor. That would have contradicted `ATT-020` on exactly the months that most need a human: the ones with something still undecided. It is not what is being built.
- Because finalization under `ATT-020` is automatic, it records no person as the actor, so the migration introduces no new kind of actor and no new column. Which migration closed them is answerable from the Flyway history and the server time on the period.
- A month closed this way is not frozen forever. `ATT-022` reopens a finalized period on a request with a reason, decided by an Admin, and that route is unchanged for these months.

**Why not leave every past month open.** Open means a Mentor may still change August from
any later date, which is the retroactive editing `GOV-005` and the whole period design
exist to stop. Leaving them open would also make the first real closing day, the fifth of
the month after go-live, close a year of history at once with no warning.

**Consequence for the plan.** Step 5 of the platform plan is no longer blocked. The other
three cases of §3.3, the responsible Mentor, `locked_at` on corrections, and the widened
status constraints, were already decided there and are unchanged.

**Status:** decided.

## D28. How is the code split into modules, and what may depend on what?

**Decided on 17 September 2026 by Loc-LX**, after two independent reviews of a measured
proposal. The feature packages followed the order in which the product was built, and
several depended on each other in a circle. This decision redraws them by dependency
before any of the recorded decisions reaches the code. The architectural record is
[`ADR-006`](rfcs/ADR-006-module-boundaries.md).

**Evidence.** `scripts/module-boundaries.cjs` assigns all 296 rules to a target module and
collects 686 dependency edges from rule citations, from rule prose checked phrase by phrase
against the rule text, and from type references in the code. Before the resolutions below,
all eight modules form one strongly connected group; after them, none. Business dates are
derived from every place the code computes one, so the reading of `GOV-011` applies to all
modules alike.

A concept scan then reads every one of the 296 rules for mentions of a concept another
module owns. Its dictionary is built from the specification itself: the physical table
inventory, the terminology table, and the actors and states the rules use. It finds 157 such
mentions, 58 of them toward a lower layer, which is the allowed direction, and 99 toward the
same or a higher layer. Each of the 99 has a recorded verdict and reason: 10 dependencies,
each naming the resolution below that removes it; 49 references, cross-cutting obligations
that each owning module implements; 22 readers or prohibitions; 12 homonyms; 6 shared
vocabulary. The run fails on a mention without a verdict, on a dependency that names no
resolution, and on a verdict that matches no mention, and each of those failures was made to
happen before the scan was trusted. It reproduces all 12 findings of an independent keyword
scan by the reviewer, and a finding it could not reproduce would mean a gap in the dictionary,
to be fixed there. The scan is complete relative to its dictionary; whether the dictionary is
complete remains a judgment. Widening it to plural forms exposed two mentions the first run
had missed, `INT-004` and `OPS-004`.

The script uses Node built-ins only. It describes the structure before the split and its
class tables are judgments tied to the current packages, so after step 6 its cycle check
becomes a test or the script is retired. The industry references were read,
not recalled: the Odoo 17.0 module manifests and `resource.calendar`, Spring Modulith's
module verification, and Shopify Packwerk.

**The modules.**

| Module | Holds |
|---|---|
| `identity` | accounts, installation, sign-in, the SMTP administration screen |
| `internship` | the internship lifecycle, the responsible Mentor, and the Admin's Intern administration composed over identity |
| `calendar` | the whole attendance policy table, the global calendar, HolidayAPI |
| `attendance` | punches, attendance periods, corrections, exceptions, leave |
| `project` | Projects and Tasks |
| `reporting` | read-only reports and exports |
| `notification` | inbox, outbox, redelivery |
| `platform` | code that belongs to no single feature: raw mail and the SMTP configuration, secret encryption, the authorization policy |
| `config` | wiring only |

Computed layers, bottom first: `platform`; `identity`; `calendar` and `notification`;
`internship`; `attendance` and `project`; `reporting`. Rules per module, as the script counts
them: `identity` 24, `internship` 8, `calendar` 14, `attendance` 52, `project` 61, `reporting`
16, `notification` 8, `platform` 113.

**Choices, each made against an alternative.**

- **Leave stays in attendance.** `ATT-020`, `ATT-021`, `ATT-024` and `LEV-013` bind leave, corrections and exceptions to one period and one decision history, under guards that run in the same transaction (`ERR-003`). Odoo separates attendance from time off because its attendance has no period close; its one crossing sits in the bridge module `hr_holidays_attendance`. Here the bridge would hold the core. An earlier proposal split leave out and had to add an interface at once to join the halves again, which showed the boundary was in the wrong place.
- **The policy table is not split, and it lives in `calendar`.** `attendance_records` and `leave_request_days` reference it for `GOV-005`, and no business need asks for three tables. Calendar needs its timezone to decide that a past event is immutable (`CAL-007`, `GOV-011`), while attendance needs the calendar (`CAL-009`, `LEV-002`); placing the table in attendance would close a cycle. So it sits in the lowest module that uses it. The module keeps the name `calendar` although it holds the policy: that is deliberate, not a misplacement to be undone.
- **Raw mail and the SMTP configuration are in `platform`.** `NOT-008` keeps activation and reset mail out of the outbox, so identity sends it directly. In `notification`, identity and notification would depend on each other.
- **No interface asks whether a date is an Intern's personal leave day.** The calendar is laboratory-wide (`CAL-001`–`CAL-009`). A personal calendar is a feature nobody specified, which `GOV-006` sends to a decision of its own.
- **`internship` is a module of its own, built on `identity`.** Merged into identity, activation needs the business date (`ACC-021`) while calendar asks identity who the actor is: the cycle identity and calendar. `--merge-internship` exits with that cycle.
- **`UI-019`, `AUTH-003` and `DB-008` stay in `platform`.** Each spans several modules. Their citations of module rules are references to definitions; the authorization policy receives scope resolved by the owning module.
- **Global roles are platform vocabulary.** The terminology table of the platform spec defines them, and `AUTH-012` decides on the actor's role, so the role type belongs to `platform`; the concept scan counts roles as platform's. In step 6 `GlobalRole` leaves the account package, and `AttendanceRole`, a copy of it, is removed.
- **`NOT-002` is shared vocabulary, not a dependency.** It names attendance and Project notification types, but `NotificationType` is owned by notification and chosen by the module that publishes the event, and notification imports nothing of attendance or project. `NOT-003`, `NOT-010` and `NOT-011` are different: each chooses recipients from another module's data, which is why they move.
- **The spec directory keeps the name `feature-platform`.** Lines 11 and 12 of the platform spec already record the `feature-{name}` convention. Renaming would touch 65 places in 16 files and the structure tests, and would leave the 17 `spec/feature-platform/…` tags named unlike every later tag, for a gain in appearance only.
- **`UC-04` is split by where its rules live, not by where its screen is.** `UC-04` keeps its number and moves to the calendar spec for policy, calendar and HolidayAPI. SMTP becomes `UC-19` in the platform spec, tracing `INT-001`–`INT-008`. `UC-18` takes the internship lifecycle out of `UC-03`. Gate: the rules traced by the new `UC-04` and `UC-19` together equal exactly those the old `UC-04` traced, `ATT-001`–`ATT-006`, `CAL-001`–`CAL-009` and `INT-001`–`INT-008`, with nothing added and nothing lost. The new `UC-04` still traces `ATT-004`–`ATT-006`, which live in attendance; a trace across specs is allowed.
- **`INT-009` has no use case today**, only `AC-INT-002`. The defect predates this decision and is not fixed while the specification moves; it is tracked as its own item in `plan.md`.
- **No rule text changes in this restructure** except `ARC-005`, `ARC-006` and `AC-ARC-001`, which describe the structure itself.

**Six rules move with their identifiers and words unchanged.**

- `NOT-003` to project: it notifies the assignee and, when the actor is the owning Mentor, the current Leader. Choosing those recipients needs Project data. The code analysis missed it because project already computes the recipients before calling notification, and no chosen phrase covered the rule.
- `NOT-010` to project: it notifies the invitee, the issuing Leader, the owning Mentor, the target of a removal and the current Leader. Every recipient is Project data.
- `NOT-011` to attendance: every clause concerns leave, corrections, exceptions or the attendance period. Left in notification, it makes notification depend on attendance and internship.
- `AUTH-004` to project: Leader permissions and the order of a Leader's exit.
- `AUTH-009` to project: what an active member of an open Project may view and comment on.
- `AUTH-011` to project: the authorization of invitation, membership-exit, exit-transfer, self-Task, Task-definition and history operations.

**Resolutions, none of which changes a rule's text.**

| | Dependency in a cycle | Resolution |
|---|---|---|
| R1 | internship and project | option A below |
| R2 | internship and attendance | Requests store no assigned approver; the responsible Mentor is resolved when a decision is made. V1 already forces `decided_by_mentor_user_id` to NULL while a request is `PENDING`, and every new request table keeps that shape |
| R3 | identity and internship | internship composes Intern creation, correction and the Student Code directory over identity |
| R4 | platform and identity | Platform services receive the verified actor and the recipient; the SMTP screen sits in identity beside the bootstrap that offers SMTP setup |
| R5 | notification with attendance, internship and project | `NOT-011` moves to attendance; `NOT-003` and `NOT-010` move to project |
| R6 | platform rules citing module rules | The authorization policy receives scope resolved by the owning module |
| R7 | calendar asking attendance who the actor is | Calendar asks identity; `AttendanceRole` copies `GlobalRole` |
| R8 | calendar asking attendance for the business date | Computed in calendar, which owns the policy timezone |
| R9 | calendar needing leave reservations and Task due dates for a change preview | Calendar declares an interface each affected module implements with the data it owns. Not built today: `TaskQueryService.dueDateImpacts` has no caller |
| R10 | platform reading `SecurityProperties` from `config` | `SecurityProperties` moves to platform |

**R1, three options.** `ACC-022` refuses to complete or withdraw an Intern who leads a Project
or owns an unfinished Task. Today `AccountService.completeInternship` and
`withdrawInternship` accept a guard computed by their caller, and `ProjectService` is the only
caller.

- **A, chosen.** internship declares an interface asking whether the Intern still holds a leadership term or an unfinished Task, calls it itself, and project implements it. The owner of `ACC-022` checks the rule, so no caller can pass a guard that reads "ready". Behavior seen from outside does not change: the same refusals, the same Admin screen. It needs the exception added to `ARC-006`.
- **B, rejected.** No interface: move the readiness panel and both actions to a route owned by project. The Admin screen changes, and step 6 changes no behavior.
- **C, rejected.** Keep the screen and place its controller in a module above both, such as project. An account-administration controller inside project repeats the misplacement just corrected for `AdminSettingsController`, and the public method would still trust its caller: safe by convention only.

**Conditions on R3.** It is a task of its own in step 6, done last together with R1. Before
the code moves, a test asserts the invariant of `ACC-019` on committed data after a forced
failure: every `INTERN` account has exactly one Intern profile, no other account has one, and a
creation that fails midway leaves neither row behind. `ACC-019` does not say "in one
transaction"; the test checks the invariant, not the mechanism, so it stays valid after the
move. The test must be seen failing first: break the code so that an `INTERN` account commits
without a profile, confirm the test fails for that reason, restore the code, and record this in
the commit. No such test exists today: the duplicate Student Code test checks the message, not
that no account row remains.

**On `GOV-004`.** Attendance and project have no edge between them, in the rules or in the
code. That supports `GOV-004` without proving it, because reporting reads both;
`AttendanceAndTaskWorkSeparationTest` stays.

**Sequence.** Documents come first, then plan, tasks, code and validation, for this
restructure as for any other work. The first order recorded here moved the code before the
specification; the maintainer reversed it on the day of the decision.

1. Revise the decision documents.
2. Check all 296 rules systematically for mentions of another module's concepts.
3. Lock the decision documents (`D28`, `ADR-006`, `ARC-005`, `ARC-006`, `AC-ARC-001`, the constitution) in one commit.
4. Move the specification into directories by the new modules. Gate: a dump of rules with none lost and no word changed. `UC-03` and `UC-04` are reviewed separately, because they are rewritten prose rather than moved text.
5. Write `PLAN.md` and `TASKS.md` for moving the code, and submit them for the maintainer's approval.
6. Move the code by those tasks on a branch of its own, `work/fix/structure/<name>` from `main` as `OPS-019` requires (named `work/fix/architecture/<name>` since `D30`). Before this step the documentation branch is merged into `main`, with the maintainer's permission, so that the branch carries the documents it follows. Gates: the full Maven suite, the end-to-end suite, `npm run test:ui`, and the cycle test.
7. Check that the code matches the documents, then merge with the maintainer's permission.
8. Plan each part of the business decisions of `D12`–`D27`, then implement it.

Steps 1 to 5 happen on the current documentation branch. `feature-attendance/PLAN.md` and
`feature-task/PLAN.md` are superseded.

**How work is divided from here.**

- One `SPEC.md` per module, with no sub-directories of specs; a long spec is divided into sections inside the file.
- Work is divided into parts, each a cluster of rules that goes through plan, tasks, code and validation.
- One `PLAN.md` per module with a section per part, and one `TASKS.md` per module with tasks grouped by part. `plan.md` tracks the state of each part.
- Depth follows risk. A part that changes the schema, a state machine, or anything historical gets a full plan with a state diagram and a review before code; a read-only or simple part gets a short plan; a part that only moves documents needs no plan.
- Three things never shrink, however small the part: the rules of the part are named; every test names the rule it protects and is seen failing before it passes; and the validation gate holds, with every `SHALL` backed by code and a test and the whole suite still green.

**Conditions on step 6.**

- The cycle test is written first, in plain Java like `LayerStructureTest`, with the list of violations known at that moment. Every task shortens the list; the last leaves it empty. ArchUnit and Spring Modulith are not used without an ADR, because either would be a new dependency.
- R8 only moves `currentBusinessDate` to calendar. It does not change where the timezone comes from.
- R3 and R1 are separate tasks, done last.

**Tracked for after step 6.**

- Task and internship compute business dates from the `BUSINESS_ZONE` constant, which contradicts `GOV-011`. Fixing it changes behavior, so it is a part of its own after step 6, not part of the move.
- `AttendanceRole` is removed: it appears in 15 production files and 18 test files.
- `INT-009` gets a use case. Closed by `D29`.

**Status:** decided.

## D29. Does the constitution keep its history, and what was wrong in it?

**Decided on 17 September 2026 by Loc-LX**, who approved the wording of every change below.
A document states what its kind of document is for; history belongs in this file, the ADRs,
the changelogs and git.

- **The constitution becomes `2.0.1`.** It loses the account of when it was locked and why late, the story behind the agent policy, the three amendments told by date, the dated checks at the head of the gaps table, and the sentences explaining why a list moved or a row once read otherwise. Each is recorded here (`D20`, `D26`, `D28`) or in `ADR-002` and `ADR-004`. It is a patch: no obligation is added, removed or weakened.
- **Three statements in it were false and are corrected.** The rollback guard in `.gitea/workflows/container.yml` was described as unable to match, but already accepts forty to sixty-four hex characters. The Amendment section spoke of three kinds of change while its table has four rows. The `ARC-005` gap row said `D28` moves `AttendanceCurrentUserService` out of `attendance`, but `R7` keeps it there and has it delegate to identity; only `AttendancePolicy` moves, to `calendar`.
- **Two replacements were reworded on review.** The definition of done says no other document restates it, which a search can check, rather than that `plan.md` points to it, which `plan.md` does not. The gaps table gives a rule for whoever writes a row, that a test is named only for what it asserts, rather than a sentence implying a check nobody runs.
- **`ADR-006` corrects the same false statement** in its consequences, and its status line reads *Implemented: not yet* without narrating what a later step will do.
- **The `INT-009` item of `D28` is closed.** `UC-04` traces `INT-009` since version 1.0.1 of the calendar spec, so the rule has a use case and no backlog item remains.

**Status:** decided.

## D30. Which rules about team branches still bind?

**Decided on 17 September 2026 by Loc-LX.** §18 of the platform spec described how a
five-person team split the first build into persistent feature branches. That team structure
no longer holds, while one person and several agent sessions may work at once.

| Rule | Decision | Reason |
|---|---|---|
| `OPS-018` | Withdrawn | A reviewed baseline before parallel work was a one-time step, and it is done |
| `OPS-019` | Kept, rewritten | One owner per shared file still matters when a person and agent sessions work in parallel |
| `OPS-020` | Withdrawn | It ordered the integration of team branches; the order that binds is the layering of `ARC-005` in `ADR-006` |
| `OPS-021` | Withdrawn | Its limit on push and merge is already held by the constitution's AI agent policy and `AGENTS.md` |

- **Withdrawn rules keep their identifiers.** Each row says it is withdrawn and by this decision, the way `AUTH-010` and `TSK-017` stay as pointers, so the count stays 296 and no identifier is reused.
- **Branches are named by what they change.** `work/fix/<area>/<what>`, where `<area>` is a module of `ARC-005`, `platform`, `architecture` for a change across modules, or `docs`. The step-6 branch of `D28` was `work/fix/structure/<name>`, which named no feature and so broke the rule it cited; it becomes `work/fix/architecture/<name>`.
- **A name Git refuses is invalid, in both directions.** Git cannot hold `a/b` beside `a/b/c`, whichever exists first; `a/b-c` is fine. `origin/work/fix/project`, merged into `main` on 4 September 2026, is someone else's branch on the shared remote and stays, as `D17` kept older branches, so a change to `project` uses `work/fix/project-<what>`. A first wording said only that an existing name must not be a prefix, which also forbade the `-` form it prescribed; the wording kept was checked against Git in both directions.
- **Changed with it:** the platform spec to `1.5.0`; the `OPS-019` row and the agent policy of the constitution, to `2.0.2`, wording only; `AGENTS.md` §4, the branch section of `CONTRIBUTING.md`, two sentences of `shared_context.md`, and `plan.md`.

**Status:** decided.

## D31. What may an amendment of an attendance exception decision change?

**Decided on 17 September 2026 by Loc-LX.** `EXC-007` let the responsible Mentor amend a
decision or a mark but did not say what an amendment may change, while `COR-005` and
`LEV-011` each say it for their own kind. The gap sat in a note of the attendance spec
("Derived from `D14`") instead of in the rule.

- An amendment changes only the decision note or, for a mark made without a request, its reason, which stays nonblank as `EXC-004` requires. The decision note stays optional, as `EXC-003` has it.
- It never changes the outcome, the work date, the violation kind, or the Intern's request, including the reason the Intern wrote under `EXC-002`. Changing the outcome is a reversal under `ATT-024`, with its own reason.
- `AC-EXC-004` exercises both permitted amendments and each refused one. The two notes that restated `D14` are removed from the attendance spec: every other sentence in them was already a rule.
- A first wording attached "which stays nonblank" ambiguously, which could have made the optional decision note mandatory, and did not protect the Intern's request; the wording kept fixes both.

**Status:** decided.

## What the audit checked and found sound

Listing what passed matters as much as what failed, because a reader otherwise
cannot tell how much of the rule set was examined.

- **Internal contradiction.** A term-overlap sweep across all 269 rules produced 96 candidate pairs where one rule grants and another withholds. The highest-scoring pairs were read; each turned out to be complementary rather than contradictory. `PRJ-007` and `TSK-019` both describe a former Leader's retained assignee rights from different angles.
- **Numeric bounds.** Every other bound in the schema is stated with its subject in the rules: passwords 12 through 128, grace 0 through 720, daily work 1 through 1440, a Task estimate 1 through 527040, a token hash of exactly 32 bytes, an encryption nonce of 12.
- **Actors.** The section 5.2 permission matrix was compared against the rules that name a role. No rule grants a capability the matrix withholds.
- **State and timestamp coherence.** Thirty-seven schema constraints require a status and its timestamp to agree, such as a `LOCKED` account having a lock time. Each follows from a lifecycle rule the specification already states.
- **Meaning kept through the EARS rewrite.** On 14 September 2026 every rule was compared before and after commit `beb0497` for changed numbers, negations and named roles. Ninety-seven rules were flagged and all were read. One had changed meaning, `TSK-021`, corrected in the table below; the rest reword the same rule. `COR-006` also changed meaning in that rewrite and was found earlier, by checking the rewrite's twelve uses of MAY, which this comparison would not have caught. A change that alters none of numbers, negations, roles or MAY would pass both checks.
- **Old wording left behind by later decisions.** For each of the 52 rules changed since 17 August, the phrases the change removed were searched for everywhere else in the specification, and every acceptance scenario and use case still worded as on 17 August was read against the rules it traces. The acceptance scenarios were sound. The use cases, the permission matrix and Appendix F were not, and are corrected below.

The audit could not check one kind of failure.
A domain error is a rule that is internally consistent, testable, and simply
wrong about what the laboratory wants. Nothing in the repository can detect that,
which is why section 22.2 still requires a person to accept the rule set.


## Decided without escalation

These were settled by editing rather than by a business judgement. They are
listed so that a reviewer can object rather than discover them later.

| Rule | What changed | Basis |
|---|---|---|
| `SEC-011` | The word "appropriate" was removed. The rule now states the properties the security headers must have, and the exact directive values sit in the acceptance scenario. | A requirement states a property; a scenario states a value. |
| `OPS-006` | Readiness now names the datasource and the completed Flyway migration set instead of "dependencies appropriate to the active profile". | The earlier wording could not be checked by any reviewer. |
| `DB-010` | The fixed count of 23 tables was removed. The rule now says the diagram covers the 23 baseline tables and that the table added by migration `V2` is not yet drawn. | The schema has 24 tables. The rule asserted a number that was false. |
| `AUTH-010` | Replaced by a pointer to `RPT-005`, which already carried the same rule. | Two rules stating one thing drift apart. |
| `TSK-017` | Replaced by a pointer to `CAL-009` and `GOV-004`, which already carried the same rule. | Same reason. |
| `TSK-021` | Variance for an estimated Task that is unfinished or reopened renders `Pending`; for an unestimated Task it renders `N/A`. The rule had said "undefined" for both. | The EARS rewrite of 12 September 2026 merged two states the earlier wording kept apart ("undefined variance" and "no variance"). `TaskVarianceState` has held both since 26 August 2026. A change of notation should not change meaning. |
| `ACC-024` | "Refuse normal authentication" now names what withdrawal does: the account becomes `DEACTIVATED`, its sessions end, login is refused, and no password reset is issued. | "Normal" implied another path stayed open. None does: `AccountService#withdrawInternship` has deactivated the account and expired its sessions since 20 August 2026. That decision exists only in code, so it is listed below for the instructor. |
| `NOT-010` | "The relevant Leader and Mentor" on revocation or supersession is now the issuing Leader and the owning Mentor. | The code notifies exactly those two, the same pair the rule already named for an answered invitation. |
| `UI-010` | "Adequate target size" is now at least 24 by 24 CSS pixels. | `shared_context.md` already commits the interface to WCAG 2.2 AA interaction requirements, and success criterion 2.5.8 sets that minimum. The smallest icon-only control today, a collapsed sidebar link, is 39 pixels wide. |
| `PRJ-009` | States first that direct removal is refused while the member owns a worked unfinished Task, pointing to `TSK-022`. | Read alone, the rule promised a transfer that `TSK-022` and the code refuse. |
| Appendix C, UC-02, UC-05, UC-06, UC-14 | UC-14 still described the 17 August exit flow, in which approval itself moved the Tasks. It now describes the flow `PRJ-008`, `PRJ-010` and `PRJ-022` require: replacement first, Leader transfer batches, approval refused until nothing is left. UC-05 and UC-06 gained the worked-Task refusal, the estimate, the forecast and the `D12` deletion; UC-02 no longer says "normal write access". | The use cases were kept almost word for word from 17 August while 52 of their traced rules changed. `ProjectService#approveExit` and `#directRemoveMember` confirm the current rules. |
| §5.2 and Appendix F | The permission matrix gained rows for deleting a `PLANNED` Project and for the Task estimate. Two message families no longer offer the old "assisted transfer to current Leader". | Same cause as the row above. |

## What acceptance of the specification still requires

Answering the questions above closes the gaps that are visible from inside the
document. It does not make the document correct.

Most of the 269 numbered rules were written by earlier authors, and this review
changed their wording only where the wording was wrong. They are internally
consistent and they match the code. Neither of those facts establishes that they
describe what the laboratory actually wants. Only a reading by the authority named
in section 1.1 can establish that, and section 22.2 already requires it.

## Judgement calls about the laboratory, open to the instructor's review

These were settled from evidence under the rule that the most recent earlier decision
applies. Each is a judgement about the laboratory rather than a fact about the code, so
the instructor is the person most likely to revise one. None of them is waiting: all were
confirmed for build on 16 September 2026, and a revision arrives as a new decision.

| Item | Current answer | What a revision would move |
|---|---|---|
| `D1` | **Confirmed 14 September 2026.** An Admin views and exports every report read-only. | Nothing open. |
| `D12` | Decided 14 September 2026, confirmed for build 16 September. Only an empty draft is deleted, with its notifications; everything else is cancelled and stays in reports. | `PRJ-002`, `PRJ-023`, and how cancelled Projects appear in `RPT-003` and `RPT-011`. |
| `ACC-024` | **Confirmed 14 September 2026.** A withdrawn Intern cannot sign in; a completed Intern keeps a read-only account and may change their password. | Nothing open. |
| `COR-006` and `D14` | An approved correction confirms the time only. Excused violations are written as `EXC-001`–`EXC-007`, confirmed for build 16 September, with LAB-POLICY limits. | The 48-hour limits of `D23`, the five-day grace of `D24`, and the `V3` tables those rules need. |
| Daily Project Work Report | Decided 14 September 2026 under `D15`, confirmed for build 16 September: a Task view and an Intern view, so variance is shown once per Task. | `RPT-011`–`RPT-014` and `UC-16`. |
| `D13` | Decided 14 September 2026, confirmed for build 16 September. Block, unblock to the previous status, and reopen with a reason. | `TSK-023`, `TSK-025`, `NOT-003`, and step 2 of the platform plan. |

## Earlier record: Task estimate and remaining-effort forecasts

Formerly `.sdd/rfcs/ADR-001-task-effort-forecast.md`, moved here unchanged on 14
September 2026 because it records a business decision rather than an architectural
one. `D15` has since widened when a forecast may be recorded.

> The original Task estimate remains the immutable whole-Task planning baseline after the first work log. When an unfinished Task with retained work is reassigned, the current Project Leader records an append-only Remaining effort forecast with the actual-effort snapshot and reassignment context; this preserves both original planning variance and the latest delivery forecast without overwriting history. We rejected replacing the baseline because it would compare lifetime multi-author effort with the latest assignee context, and rejected per-assignment estimates because the product does not retain assignment periods and does not use estimates to evaluate individual Interns.
