# Decisions behind the specification

Each entry is a place where [the specification](../requirements.md) stated
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

## Where these stand

| | Question | Answer | Rules it changes |
|---|---|---|---|
| D1 | Admin access to detailed Intern attendance | Permitted; confirmed by the instructor on 14 September 2026, with the fields listed | `RPT-004`, `RPT-011` |
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
| D12 | Whether a Project may be deleted | Only a `PLANNED` Project, only by its owning Mentor; pending confirmation with the instructor | `GOV-014`, `PRJ-002` |
| D13 | Whether an owning Mentor may change a Task's status | Open: the code permits it on an `ACTIVE` Project, the rules refuse it | `AUTH-008`, `TSK-007` |

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
commit `bf19a25`. Everything else either matched the shipped behavior already or
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
[ADR 0002](../rfcs/ADR-002-attendance-report-scope.md) reconstructs the
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
Still open: whether the instructor's "full access" also covers the Project/Task and
Daily reports, which `RPT-005` and `RPT-011` deny to Admin.

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
record removed from the working tree in commit `d5b1754` and still readable at
`d5b1754^`. Question 1 of that record was answered "build local Jira/Tempo-inspired
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
longer in the working tree and is readable at `d5b1754^`.

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

**Carried out on 14 September 2026.** The maintainer chose to follow the playbook's
layout, and the specification now sits in eight specs under `.sdd/specs/`, one per
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
does bound the neighbouring field. Grace minutes are pinned to "an integer from 0
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

**Implemented on 12 September 2026 in commit `bf19a25`.** This was the one
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
nothing shows that anyone weighed it against the rule it breaks. Either answer is
cheap to apply: the rules gain a Mentor exception, or the code loses one branch and
two tests.

**Status:** open.

## What the audit checked and found sound

Listing what passed matters as much as what failed, because a reader otherwise
cannot tell how much of the rule set was examined.

- **Internal contradiction.** A term-overlap sweep across all 269 rules produced 96 candidate pairs where one rule grants and another withholds. The highest-scoring pairs were read; each turned out to be complementary rather than contradictory. `PRJ-007` and `TSK-019` both describe a former Leader's retained assignee rights from different angles.
- **Numeric bounds.** Every other bound in the schema is stated with its subject in the rules: passwords 12 through 128, grace 0 through 720, daily work 1 through 1440, a Task estimate 1 through 527040, a token hash of exactly 32 bytes, an encryption nonce of 12.
- **Actors.** The section 5.2 permission matrix was compared against the rules that name a role. No rule grants a capability the matrix withholds.
- **State and timestamp coherence.** Thirty-seven schema constraints require a status and its timestamp to agree, such as a `LOCKED` account having a lock time. Each follows from a lifecycle rule the specification already states.
- **Meaning kept through the EARS rewrite.** On 14 September 2026 every rule was compared before and after commit `1376ced` for changed numbers, negations and named roles. Ninety-seven rules were flagged and all were read. One had changed meaning, `TSK-021`, corrected in the table below; the rest reword the same rule. `COR-006` also changed meaning in that rewrite and was found earlier, by checking the rewrite's twelve uses of MAY, which this comparison would not have caught. A change that alters none of numbers, negations, roles or MAY would pass both checks.
- **Old wording left behind by later decisions.** For each of the 52 rules changed since 17 August, the phrases the change removed were searched for everywhere else in the specification, and every acceptance scenario and use case still worded as on 17 August was read against the rules it traces. The acceptance scenarios were sound. The use cases, the permission matrix and Appendix F were not, and are corrected below.

The audit could not check one of the six failure kinds the playbook names.
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
| `UI-010` | "Adequate target size" is now at least 24 by 24 CSS pixels. | `product.md` already commits the interface to WCAG 2.2 AA interaction requirements, and success criterion 2.5.8 sets that minimum. The smallest icon-only control today, a collapsed sidebar link, is 39 pixels wide. |
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

## To confirm with the instructor

These were settled from evidence under the rule that the most recent earlier
decision applies. Each is a judgement about the laboratory rather than a fact about
the code, and each is the current candidate until the instructor confirms or
reverses it.

| Item | Current answer | Why it needs the instructor |
|---|---|---|
| `D1` | **Confirmed 14 September 2026.** An Admin sees every field a Mentor sees, listed in `RPT-004`. | Still open: whether "full access" covers the Project/Task and Daily reports. |
| `D12` | Only a `PLANNED` Project may be deleted, only by its owning Mentor, with everything it owns. | The decision exists only in code, Javadoc and tests. The four questions recorded under `D12` are still open. |
| `ACC-024` | **Confirmed 14 September 2026.** A withdrawn Intern cannot sign in; a completed Intern keeps a read-only account. | Still open: whether read-only lets a completed Intern change their password and manage sessions, as `ACC-023` permits. |
| `COR-006` | A checkout set by an approved correction still counts as early departure when it falls before scheduled end. Approval confirms the time; it does not excuse the departure. | Whether the laboratory excuses early departures or late arrivals at all, whether an excused violation lowers compliance, and who approves it. No excuse exists in the rules or the code. |
| Daily Project Work Report | A `DONE` Task logged by two authors on one date shows its lifetime actual, estimate and variance under each author. | `RPT-012` forbids productivity claims, but repeating one variance per author may read as one. No record exercised this case. |
| `D13` | Open: the code lets an owning Mentor change a Task's status; the rules refuse it. | Whether a Mentor should be able to move a Task's status at all. |
