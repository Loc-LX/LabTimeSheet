# Partial Jira/Tempo Decision Record — Q1–Q34

| Field | Value |
|---|---|
| Status | Approved product and delivery decisions |
| Decision date | 26 August 2026 (`Asia/Ho_Chi_Minh`) |
| Latest revision | 27 August 2026 — reporting roles and Admin dashboard |
| Feature | Local Partial Jira/Tempo capability for Lab Timesheet |
| Intended reader | Developer or independent specification-compliance review agent |
| Review target | Observable behavior and invariants, not one reference implementation's structure |

## Purpose

This document consolidates the approved outcomes of all thirty-four questions from the Partial
Jira/Tempo grilling session, including the reopened Q8 and Q22 decisions and the implementation
clarifications approved immediately after Q34. The reporting-role and Admin-dashboard revision
dated 27 August 2026 is the latest explicit clarification and supersedes the earlier Admin
reporting scope wherever this record or its evidence still describes it. It is intended to let
another developer or review agent compare an independently implemented feature with the behavior
that was actually approved.

This is a decision record, not an instruction to copy one particular implementation. A review
should compare observable behavior, authorization, persistence semantics, historical meaning,
and concurrency boundaries. Different class names, endpoints, or internal designs are acceptable
only when they preserve the same product rules.

## Authority and conflict handling

The repository's numbered requirements remain the system-wide normative specification. This
record is the authoritative detailed account of the decisions that produced the Partial
Jira/Tempo amendments, especially where the original stakeholder draft was ambiguous or
contradictory.

Use this precedence when reviewing this feature:

1. Later explicit revisions in this record, especially Q8-R, Q22-R, the post-Q34 direct-removal
   clarification, and the 27 August reporting-role/Admin-dashboard clarification.
2. Numbered requirements in `docs/labtimesheet-docs-hub/requirements-specification.md`.
3. The remaining approved decisions and clarifications in this record.
4. Canonical terminology in `CONTEXT.md`.
5. The estimate/forecast rationale in
   `docs/adr/0001-preserve-task-estimate-and-remaining-effort-forecasts.md`.
6. The Iteration 4 tracker, migration, and executable tests as implementation evidence, not as
   permission to narrow the product contract.
7. The original `lab-timesheet-jira-tempo-requirements.md` proposal as background only.

If the first six sources appear to conflict after applying an explicit later revision, the
reviewer must report the conflict rather than silently selecting an interpretation. The original
proposal never overrides an approved decision.

### Latest reporting-role and Admin-dashboard revision

The 27 August 2026 clarification supersedes any earlier Admin-positive reporting rule in this
record, the numbered requirements, or historical test evidence. Admin is a configuration and
account-lifecycle role, not an operational reporting role:

- Admin has no Attendance, Project/Task, or Daily Project Work Report scope. This includes the
  HTML page, XLSX export, PDF export, report navigation entry, service-level dataset, and export
  service for each family.
- Attendance-report Admin requests and Project/Task-report Admin requests are rejected as
  authenticated access-denied responses (`403 Forbidden`) before target or Project option
  resolution, Attendance/Task/work-log reads, dataset construction, or exporter invocation.
  The existing non-disclosing `Project unavailable` behavior remains the Daily-report response
  for Admin requests.
- The following non-Admin report scopes remain in force: an active Intern sees only their own
  Attendance report; an active Mentor may inspect authorized Intern Attendance; owning Mentors
  retain their authorized Project/Task and Daily scopes; current Leaders retain per-member
  Project/Task detail for Projects they currently lead and may use the Daily report for each
  currently-led `PLANNED` or `ACTIVE` Project; ordinary members retain aggregate-only Project/Task
  reporting.
- The Daily report remains one authorized immutable dataset rendered by HTML, XLSX, and PDF. Its
  Leader entry is discoverable in role-aware navigation only when the active Intern currently
  leads at least one eligible Project. With no eligible Project the response is `Project unavailable`;
  one eligible Project redirects to its locked report; multiple eligible Projects
  show only an authorized selector. A Leader report request still requires and re-authorizes the
  exact `projectId`, and a valid selected date survives selection or redirect.
- The Admin dashboard is account/configuration-only. It retains active-account,
  pending-activation, and active-internship counts, pending-activation guidance, account creation,
  and system/configuration notifications. It does not show an Active Projects metric, query
  Project data solely for a dashboard metric, expose report links, or describe operational
  Project/Task work. Admin navigation for Accounts, SMTP settings, Attendance Policy, Global
  Calendar, and Holiday Import remains available.
- A Mentor's request for additional Daily reporting remains out-of-system operational
  communication. No persisted delegation request, toggle, notification, report artifact, audit
  event, schema, or migration is introduced.

Historical evidence that predates this revision remains factual about the behavior it executed;
it must not be read as proof of the revised Admin denial or Leader-navigation contract. New
verification must cover the revised boundaries explicitly.

This record specifies behavior, authorization, persistence meaning, history, atomicity, and output
parity. It does not require another implementation to use the same packages, class names,
endpoints, tables, templates, or test organization.

## Consolidated answers

| Question | Approved answer | Consolidated outcome |
|---|---|---|
| Q1 | A | Build local Jira/Tempo-inspired behavior; do not integrate with external Jira or Tempo APIs. |
| Q2 | A | Treat the attached document as a change proposal; preserve numbered repository requirements unless an amendment is explicitly approved. |
| Q3 | B | Treat approximately three development days as a scope target, not permission to omit safety, migrations, or tests. |
| Q4 | D | Deliver a phased bundle: complete Task effort planning first, then add report-period presentation only within the approved remaining scope; do not add a submission/review state machine. |
| Q5 | A | Define one whole-Task estimate in minutes; it is not an assignee estimate, attendance duration, elapsed time, or Project budget allocation. |
| Q6 | A | Only the current Project Leader may create, replace, or clear the Task estimate. |
| Q7 | A | The estimate is optional and must never block existing Task or Project workflows. |
| Q8 | Reopened Q8-R C | Freeze the original Task estimate at the first retained work log. Preserve it across reassignment and record a separate append-only Remaining effort forecast for worked reassignment. |
| Q9 | A | Actual Task effort is the lifetime sum of all retained work-log minutes across every author and assignment. |
| Q10 | A | Store an optional estimate as integer minutes from 1 through 527040. |
| Q11 | B | Show signed variance only while an estimated Task is `DONE`; unfinished/reopened variance is `Pending`, and unestimated variance is `N/A`. |
| Q12 | A | Do not implement efficiency percentages, rankings, or productivity judgments. |
| Q13 | A, clarified by final Q8 | Expose estimate entry in role-correct Task planning workflows, but enforce estimate-specific authorization and the first-log freeze independently of ordinary Task editing. |
| Q14 | A | Show estimate, lifetime actual, and variance to authorized Task viewers on Task detail and the Project/Task report; keep broad lists and dashboards unchanged initially. |
| Q15 | A | Keep lifetime planning values separate from selected-period logged effort; maintain identical row semantics in HTML, XLSX, and PDF. |
| Q16 | A, with post-Q34 removal revision | Require an atomic Remaining effort forecast for every worked unfinished reassignment; reject unsolicited forecasts for unworked reassignment. Direct Mentor removal blocks instead of collecting or inventing forecasts. |
| Q17 | A | Preserve a complete append-only forecast snapshot, including Task, incoming membership, forecasting Leader membership, time, remaining minutes, and lifetime-actual snapshot. |
| Q18 | A | Permit an append-only superseding correction only before the incoming assignee creates the first new work log for that reassignment. |
| Q19 | B | Initial forecast note is optional; a correcting forecast requires a nonblank normalized reason. |
| Q20 | A | Show full forecast history on Task detail/history and only the latest applicable forecast in reports and exports. |
| Q21 | A | Do not calculate forecast-accuracy metrics, percentages, rankings, or judgments. |
| Q22 | A, superseded by Q22-R | The initial answer deferred all presets after forecast scope grew. |
| Q22-R | A | First delivery includes one Daily Project Work Report; Weekly and Monthly presets are deferred. |
| Q23 | A | Record the immutable-baseline versus append-only-forecast choice in an ADR. |
| Q24 | A | Admin-configured effective attendance workdays annotate the Daily report but never filter legitimate Task work logs. |
| Q25 | C | Default to all authorized Projects grouped by Project, with an optional one-Project filter. |
| Q26 | A | Group Daily work by retained work-log author and retain individual descriptions; do not attribute historical work to the current assignee. |
| Q27 | A | Default to today in `Asia/Ho_Chi_Minh`; allow today and past dates, reject future dates, and allow valid non-workday/empty dates. |
| Q28 | A | Ship Daily HTML, XLSX, and PDF together from one authorized dataset without requiring unrelated due-date filters. |
| Q29 | B | Implement vertically on one isolated `codex/...` feature branch as an explicit exception to normal persistent workstream ownership. |
| Q30 | A | Amend numbered requirements and acceptance scenarios, add bounded Iteration 4 tracking, and retain the glossary and ADR before production code. |
| Q31-R | A, superseded by 27 August reporting-role revision | Owning Mentors retain all-owned or selected-owned-Project Daily scope; current Leaders receive discoverable Daily scope for each currently-led `PLANNED`/`ACTIVE` Project; Admin has no Attendance, Project/Task, or Daily reporting scope; all other unauthorized contexts remain denied. |
| Q32 | A | Establish a clean baseline; stop and request explicit authorization before proceeding past independent baseline failures. |
| Q33 | A | Implement test-first with focused local commits and an integrated candidate; do not push or merge to `main` without separate authorization. |
| Q34 | A | Confirm the complete contract and authorize documentation, baseline verification, and test-first implementation on `codex/partial-jira-tempo`. |

## Detailed decisions

### Q1 — Local capability, not external synchronization

**Approved option:** A — local Jira/Tempo-inspired behavior.

“Partial Jira/Tempo” means extending Lab Timesheet's existing Project, Task, work-log, and
reporting capabilities. It does not mean connecting to Atlassian Jira, Tempo, or another external
service.

The approved feature scope therefore excludes:

- Jira issue identifiers or Jira project synchronization;
- Tempo accounts, worklog synchronization, or Tempo APIs;
- OAuth/API-token credential management for Jira or Tempo;
- import/export conflict resolution with an external system;
- webhooks, polling, or background synchronization jobs;
- a duplicate Jira/Tempo bounded context alongside the existing Lab Timesheet features.

An implementation fails Q1 if its core behavior depends on an external Jira/Tempo account or if it
duplicates existing local Project, Task, and work-log concepts instead of extending them.

### Q2 — Existing numbered requirements remain authoritative

**Approved option:** A — the attached stakeholder document is a change proposal.

The original proposal may identify useful additions, but it does not silently replace established
Lab Timesheet behavior. Each conflict must be explicitly reviewed and approved before it becomes a
requirement.

This preserves the following existing boundaries:

- the Project Leader remains a Project-scoped authority, not a global role;
- the existing Task statuses remain `TODO`, `IN_PROGRESS`, `BLOCKED`, and `DONE`;
- Mentor Task authority is not expanded merely because the proposal describes Mentors as planning
  work;
- Task work logs remain the source of actual Task effort;
- Task work and attendance remain separate domains;
- current authorization and non-disclosure rules still apply to guessed or cross-Project IDs.

The original proposal's `SUBMITTED`/`COMPLETED` lifecycle, Mentor estimate mutation, efficiency
formula, and minimal replacement schema are therefore not authoritative.

### Q3 — Three days is a scope target, not a safety exception

**Approved option:** B — favor a three-day-sized feature while preserving correctness.

The time estimate is used to control scope. It is not permission to:

- skip a required database migration;
- overwrite historical planning data for convenience;
- weaken authorization checks;
- omit transaction or concurrency protection;
- leave HTML and exported reports with contradictory meanings;
- claim a partially proven behavior is complete;
- replace executable tests with manual assumptions.

When the complete approved behavior does not fit the target, adjacent functionality should be
deferred explicitly. The implementation must not keep extra breadth by weakening the core
invariants.

### Q4 — Phased delivery, with effort planning first

**Approved option:** D — phased bundle.

The first complete vertical slice is Task effort planning:

1. optional whole-Task estimate;
2. lifetime actual Task effort from retained work logs;
3. neutral signed variance with explicit state semantics;
4. correct authorization and first-log immutability;
5. coherent persistence, UI, and report behavior.

Report-period presentation follows as a separate bounded phase. The later grilling rounds narrowed
that phase to one Daily Project Work Report in HTML, XLSX, and PDF; Weekly and Monthly presets are
deferred.

The following workflow change is explicitly excluded from this feature:

```text
TODO -> IN_PROGRESS -> SUBMITTED -> COMPLETED
```

No Leader submission/acceptance/rejection workflow is introduced. Adding it would change Task
status constraints, authorization, notifications, progress calculations, Project completion,
migration behavior, and existing tests, and requires a separate requirements amendment.

### Q5 — Canonical concept: Task estimate

**Approved option:** A — one estimate belongs to the Task.

Canonical definition:

> **Task estimate:** The planned human effort, expressed in minutes, for completing one Task. It
> belongs to the Task rather than an assignee and is independent of attendance, calendar duration,
> due date, assignee capacity, and elapsed clock time.

Consequences:

- reassignment does not change what the original estimate means;
- the estimate is compared with lifetime Task effort, not one person's effort;
- hours and minutes may be used for display, but minutes are canonical for storage and arithmetic;
- the estimate is not derived from due dates or attendance schedules;
- there is no required Project-level effort budget or sum-of-estimates control;
- no per-assignment estimate model is implied.

Terms such as “assignee estimate,” “estimated attendance,” “time budget,” or “Project budget
allocation” must not be used as synonyms for the Task estimate.

### Q6 — Estimate mutation belongs only to the current Project Leader

**Approved option:** A — current Project Leader only.

The Project's current Leader may set, replace, or clear an eligible Task estimate. This permission
is Project-scoped and follows the current leadership term.

The following actors are read-only for this field even when they may view the Task:

- the owning Mentor;
- the current assignee;
- the Task creator when that creator is not the current Leader;
- another ordinary Project member;
- an Admin, who is read-only for any retained non-reporting Project/Task view and has no estimate
  mutation authority.

An ordinary member who creates a self-assigned Task must not gain estimate authority. Hiding the
field in the browser is insufficient: the application service must reject a forged estimate
parameter or direct mutation request.

When leadership changes, the former Leader immediately loses estimate-mutation authority and the
new current Leader receives it, subject to the remaining Q7, Q8, and Q13 eligibility rules.

### Q7 — Estimate is optional and absence is not zero

**Approved option:** A — optional planning data.

A Task may exist without an estimate. At the persistence boundary, absence is represented as
`NULL`, not zero.

A missing estimate must not block:

- Task creation;
- ordinary-member self-Task creation;
- Project activation;
- Task status transitions;
- work logging;
- Task reassignment;
- Task reopening;
- Project completion.

Display semantics:

- missing estimate: `Not estimated` or `N/A`, according to the surface;
- missing estimate is never displayed as `0m`;
- variance for an unestimated Task is `N/A`;
- no estimate is invented for historical Tasks or backfilled during migration.

An implementation fails Q7 if it requires an estimate before work begins or silently assigns a
default estimate.

### Q8 — Final revised decision: frozen baseline plus Remaining effort forecast

**Final approved option:** reopened **Q8-R C**.

This clarification is essential. The user initially selected original Q8 A, which froze the
estimate at the first work log. Q8 was later reopened after considering reassignment. The final
selection called “Q8 C” refers to **Q8-R C**, not original Q8 option C.

The rejected original Q8 option C would have allowed the Leader to replace the Task estimate while
work was underway. That behavior is not approved.

#### Original estimate freeze

The current Leader may set, replace, or clear the Task estimate only while the Task has no retained
work logs. Creation of the first retained work log permanently freezes the original estimate.

If the Task is unestimated when the first log is retained, it remains unestimated. Work logging is
still accepted.

The estimate does not become editable again because of:

- reassignment;
- a new Project Leader;
- work-log correction;
- Task reopening;
- Project completion;
- deletion or restoration of a UI form;
- the discovery that the original estimate was inaccurate.

Estimate mutation and first-work-log creation must lock and recheck the same Task boundary so that
only one valid ordering commits. A stale estimate request must not overwrite a newly frozen value.

#### Reassignment after work exists

When an unfinished Task with retained work is reassigned, the original whole-Task estimate remains
unchanged. The current Project Leader records a separate, dated, append-only **Remaining effort
forecast** for the incoming assignee's assignment.

Canonical definition:

> **Remaining effort forecast:** A current Project Leader's dated prediction of the additional
> effort needed to finish an unfinished Task from a reassignment or replanning point. It does not
> replace the Task estimate and may be lower, equal to, or higher than the unused arithmetic portion
> of that estimate.

The two values answer different questions:

- original Task estimate: how much effort was originally planned for the whole Task;
- Remaining effort forecast: how much additional effort is currently expected from this
  reassignment point.

Example:

```text
Original Task estimate                 720 minutes
Lifetime actual before reassignment    300 minutes
Remaining effort forecast              480 minutes
Forecast total effort                  780 minutes
Final lifetime actual                  540 minutes
Final baseline variance              540 - 720 = -180 minutes
```

The final variance still compares lifetime actual with the original estimate. It must not compare
the 540-minute lifetime actual with the 480-minute remaining forecast.

This decision rejects:

- silently replacing the original estimate at reassignment;
- resetting actual effort for the next assignee;
- treating the remaining forecast as the new whole-Task baseline;
- claiming per-assignment variance without durable assignment-period accounting.

Detailed forecast creation, correction, and removal rules are specified under Q16–Q20 and the
post-Q34 implementation clarification below.

### Q9 — Actual Task effort is lifetime retained work

**Approved option:** A — lifetime retained Task effort.

Canonical definition:

> **Actual Task effort:** The lifetime sum of retained work-log minutes for one Task across all
> authors and assignments. It is independent of attendance and does not reset when the Task is
> reassigned.

Required arithmetic:

```text
Actual Task effort = SUM(minutes of every retained work log for the Task)
```

Consequences:

- work from a previous assignee remains in the total;
- work from multiple membership intervals remains in the total;
- reassignment does not reset or partition the value;
- an authorized work-log correction changes the lifetime total;
- the total is not restricted by a report date filter;
- attendance check-in/checkout duration is not substituted for Task effort;
- actual effort is not assigned exclusively to the current assignee.

Reports must distinguish the lifetime value from **logged effort in the selected range**. A daily,
weekly, monthly, or custom-range subtotal is not Actual Task effort for variance purposes.

### Q10 — Estimate representation and bounds

**Approved option:** A — nullable integer minutes from 1 through 527040.

When supplied, the estimate must be a whole number of minutes satisfying:

```text
1 <= estimated minutes <= 527040
```

The system rejects:

- zero;
- negative values;
- fractional minutes;
- non-numeric input;
- values greater than 527040.

No estimate is represented by `NULL`. Browser controls, request binding, application validation,
domain validation, and database constraints should enforce equivalent behavior. Aggregation and
subtraction should use a sufficiently wide type, such as `long`, even though the stored estimate
fits in an integer.

The maximum is a defensive product ceiling derived from the repository's existing 366-day report
limit multiplied by the 1440-minute daily Task-work limit. It is not a recommendation that normal
student Tasks should approach that size.

User-facing surfaces may display a human-readable duration such as `12 h 30 m`, but the canonical
stored and compared value remains minutes.

### Q11 — Variance is final only for DONE Tasks

**Approved option:** B — final signed variance only while `DONE`.

For a Task that has an estimate and whose current status is `DONE`:

```text
Task effort variance = Actual Task effort - Task estimate
```

Interpretation:

- positive variance: actual effort exceeded the original estimate;
- zero variance: actual effort matched the original estimate;
- negative variance: actual effort was below the original estimate.

State semantics:

| Task condition | Variance presentation |
|---|---|
| Estimated and `DONE` | Signed numeric value |
| Estimated and unfinished | `Pending` |
| Estimated, previously `DONE`, then reopened | `Pending` |
| Unestimated, regardless of status | `N/A` |

An unfinished Task with one hour logged against a ten-hour estimate is not “nine hours under
estimate.” Its outcome is not final, so variance is pending.

The implementation must not use absolute variance or period-filtered actual effort. Reopening a
Task removes the final-variance presentation until the Task is `DONE` again; it does not erase the
estimate or actual history.

### Q12 — Efficiency and productivity scoring are excluded

**Approved option:** A — omit efficiency.

Do not implement:

- `estimate / actual * 100` efficiency;
- continuous efficiency while work is unfinished;
- per-assignee efficiency;
- Project Leader efficiency;
- member rankings based on estimate and actual effort;
- color-coded judgments such as “good,” “poor,” “fast,” or “slow”;
- aggregate productivity scores derived from planning variance.

Reasons:

- the percentage can exceed 100 percent without having a stable product meaning;
- it is undefined when actual effort is zero;
- estimates belong to Tasks while actual effort may span several people and assignments;
- faster completion does not prove higher quality;
- planning accuracy is not employee or Intern productivity.

Neutral estimate, actual, and signed variance facts are sufficient. The original proposal's
efficiency formula is explicitly rejected.

### Q13 — Role-correct estimate entry and correction surfaces

**Approved option:** A, clarified by the final Q8 model.

When the current Project Leader creates a Task, the Task planning form may accept an optional
estimate. The current Leader must also have a focused way to set, replace, or clear that estimate
while all estimate-specific eligibility rules remain true:

- the Project is open;
- the Task is non-deleted;
- the Task is unfinished;
- no retained Task work log exists;
- the request is not stale;
- the actor is the current Project Leader.

Ordinary-member self-Task creation and editing must not expose an estimate input. Crafted requests
from an ordinary member, Mentor, former Leader, unauthorized actor, or cross-Project actor must be
rejected by the server without changing the estimate.

“Integrate with existing Task-definition workflows” is a user-experience decision, not permission
to reuse broader ordinary-edit authorization. The implementation may use a distinct estimate
mutation endpoint or application method when that produces a clearer authorization and locking
boundary. It must still present the estimate as Task planning data rather than as a disconnected
administrative setting.

The final Q8 decision narrows Q13 in one important way: after the first retained work log, the
original estimate can no longer be “corrected” through this surface. A later reassignment uses a
separate Remaining effort forecast; it never reopens baseline estimate editing.

The first-log race must be explicit:

1. estimate mutation and first-log creation contend on the same Task mutation boundary;
2. each operation rechecks retained-work state after acquiring the required lock;
3. only the valid ordering commits;
4. a stale browser submission produces a conflict/reload result rather than overwriting the
   frozen estimate.

### Q14 — Focused visibility for all authorized Task viewers

**Approved option:** A — all authorized Task viewers on focused surfaces.

Task estimate, lifetime Actual Task effort, and variance value/state belong on:

- Task detail; and
- the authorized Project/Task report and its exports.

Dashboards and general Task lists are unchanged in the first delivery. Visibility does not widen
mutation authority:

- current Project Leader: view and conditionally mutate the estimate;
- owning Mentor: view only;
- Admin: no dedicated Project/Task report view or export; any retained generic Project read view
  is read-only;
- ordinary authorized Project member: view only;
- unauthorized or guessed-ID requester: no disclosure.

The same authorization boundary applies to forecast facts. An implementation does not satisfy Q14
merely by hiding controls in HTML; server-side reads and writes must be scoped from stored Project,
membership, leadership, and ownership context.

### Q15 — Lifetime planning values stay separate from selected-period work

**Approved option:** A — separate lifetime comparison from period effort.

A report row may contain all of the following, but each field must be labeled according to its
different time scope:

```text
Original Task estimate
Actual Task effort — lifetime
Task effort variance — DONE only, otherwise Pending/N/A
Latest Remaining effort forecast
Actual effort snapshot at latest forecast
Latest forecast total effort
Logged effort in the selected range
```

Selected-period work must never be subtracted from the lifetime estimate to manufacture variance.
The first delivery has no aggregate estimate, aggregate variance, or aggregate forecast-accuracy
summary card. HTML, XLSX, and PDF must preserve identical row meanings even if their layouts differ.

### Q16 — Forecast trigger and reassignment atomicity

**Approved option:** A — required for a worked unfinished reassignment.

Every reassignment of an unfinished Task with one or more retained work logs requires one valid
Remaining effort forecast for the incoming membership. This applies even when the Task has no
original estimate.

If an unfinished Task has no retained work logs:

- reassignment does not require a forecast;
- the original Task estimate, if present, remains the applicable baseline; and
- unsolicited forecast data is rejected rather than stored as invented history.

The rule covers manual reassignment and multi-Task transfer. A batch requires one forecast per
worked unfinished Task. Missing or invalid input causes the entire operation to fail without
partial assignments or forecast rows. Remaining minutes use the same supplied range as an estimate:
`1..527040`; zero is not a valid remaining forecast for a Task still being reassigned as unfinished.

The original Q16 discussion assumed direct Mentor removal could receive forecasts. That assumption
was superseded after the clean baseline exposed the real authorization seam. The final rule is in
the post-Q34 clarification: direct Mentor removal blocks while worked unfinished Tasks remain; the
current Leader must first perform forecast-aware transfers.

### Q17 — Complete immutable forecast snapshot

**Approved option:** A — store the complete planning snapshot.

Each initial forecast durably preserves:

- Task identity;
- incoming assignee membership, not merely the account or current assignee name;
- forecasting current-Leader membership;
- the server-controlled reassignment/forecast time associated with the assignment change;
- remaining forecast minutes;
- nonnegative lifetime Actual Task effort at that moment; and
- optional normalized initial note.

The forecast total at that moment is:

```text
lifetime actual snapshot + remaining forecast minutes
```

That total is derived with wide arithmetic; it need not be persisted. Later logs or corrections to
historical work must not make an old forecast snapshot appear to change. Membership exit,
leadership change, reassignment, or account-name change must not erase the retained provenance.

### Q18 — Append-only correction window

**Approved option:** A — append a superseding correction before the incoming assignee's first new
work log.

The current Project Leader may append a correction only for the current reassignment context and
only while the incoming membership has not created a new retained work log for the Task since that
reassignment. A correction:

- appends a successor row;
- leaves the predecessor immutable and visible;
- carries a new remaining-minutes value, current snapshot/time, current Leader provenance, and the
  required correction reason;
- becomes the latest applicable forecast for that context.

The first newly created work log by the incoming assignee freezes the latest forecast for that
assignment context. Correcting an older work log does not freeze, unfreeze, or reopen the forecast
window. A later reassignment creates a new context. If leadership changes before the window closes,
the new current Leader may correct it and the previous Leader may not.

Correction and first-new-log creation must lock and recheck the same Task/assignment boundary.
Late, stale, wrong-assignment, superseded-predecessor, unauthorized, and concurrent-loser requests
are rejected without partial mutation.

### Q19 — Optional initial note; mandatory correction reason

**Approved option:** B.

- An initial reassignment forecast may include an optional note.
- Blank or whitespace-only optional notes normalize to absence.
- A correcting forecast requires a nonblank reason.
- Stored note/reason text is trimmed and consistently bounded across browser, application, and
  database validation.
- Notes and reasons are visible in detailed history, not compact reports or exports.

The grilling session required a reasonable consistent bound but did not choose the exact number.
Therefore an independent implementation must have a defensible bounded value, but Q19 alone does
not require a particular character count. If the repository migration or a later numbered
requirement establishes an exact limit, that later normative contract controls.

### Q20 — Full detail history; latest snapshot in reports

**Approved option:** A.

Task detail/history shows every forecast and correction chronologically, including:

- incoming membership;
- forecasting Leader membership;
- event/reassignment time;
- lifetime-actual snapshot;
- remaining minutes;
- derived forecast total;
- optional initial note or mandatory correction reason; and
- predecessor/superseded state.

Project/Task reports and HTML/XLSX/PDF exports show only the latest applicable forecast and its
planning facts. They exclude notes and reasons. A Task without a worked reassignment shows forecast
fields as `N/A`. No aggregate forecast card or person ranking is introduced.

### Q21 — No forecast-accuracy metric

**Approved option:** A — show facts without a score.

Authorized viewers may see the forecast total beside later/final lifetime actual effort, but the
system does not calculate or name:

- latest-forecast variance;
- accuracy percentage;
- per-forecast accuracy;
- per-assignee forecast performance;
- rankings or color-coded judgments.

This prohibition is distinct from the approved original-estimate variance for a `DONE` Task.

### Q22 and Q22-R — Report delivery scope was reopened

**Original Q22 answer:** A — defer Daily, Weekly, and Monthly presets because the append-only
forecast model consumed the earlier contingency.

**Final Q22-R answer:** A — include Daily only.

Q22-R supersedes the original Q22 delivery boundary. The first delivery includes:

- the complete estimate/actual/variance/forecast slice; and
- one Daily Project Work Report for the approved Mentor and current-Leader scopes.

Weekly and Monthly presets remain deferred. The Daily report reuses the established report and
authorization model; it must not become a second reporting subsystem.

### Q23 — Record the baseline-versus-forecast choice in an ADR

**Approved option:** A.

The repository must retain an ADR explaining why the original whole-Task estimate stays immutable
and why worked reassignment appends a separate Remaining effort forecast. The ADR records rejected
alternatives such as overwriting the estimate, per-assignment estimates, no forecast, and continuous
replanning. The glossary defines the terms; the ADR explains the costly, non-obvious choice.

### Q24 — Attendance workdays provide context only

**Approved option:** A.

Reuse the existing Admin-configured, effective-dated attendance policy as the sole workday
configuration. Do not create a second report-workday setting.

For the selected Report date, historically applicable policy/calendar data may label the date as a
configured attendance workday, policy non-workday, or global day off. None of those classifications
filters otherwise valid Task work logs. The report must not infer attendance from work logs or
claim that logged work occurred during scheduled attendance hours.

### Q25 — All authorized Projects by default; optional one-Project filter

**Approved option:** C — support both scopes.

For a Mentor, the default Daily view covers all Projects currently authorized by ownership and is
grouped by Project. The Mentor may narrow the report to one owned Project. The current Leader
scope is finalized by Q31-R and is intentionally one mandatory Project rather than a global
preset.

In all-Projects mode, Projects with no retained logs for the selected date are omitted so the
result remains useful. In explicit one-Project mode, an authorized Project with no matching logs
renders a clear empty state rather than disappearing or returning an access error. Unauthorized
Projects are denied, not presented as empty.

### Q26 — Attribute work to the retained log author

**Approved option:** A.

Within each Project, group by the membership that authored each retained work log, never by the
Task's current assignee. Preserve every individual log and show:

- author;
- Task title;
- retained work description;
- logged minutes;
- Task's clearly labeled **current** status;
- optional Task subtotal when several logs target the same Task;
- member subtotal; and
- Project subtotal.

Repeated logs are not collapsed in a way that discards descriptions. Reassignment cannot
retroactively attribute earlier work to the incoming assignee. Logs for a soft-deleted Task remain
reportable and are clearly marked as belonging to a deleted Task.

### Q27 — Report date and empty-date behavior

**Approved option:** A — today or past dates only.

- Default to the current business date in `Asia/Ho_Chi_Minh`.
- Accept today and historical dates.
- Reject future dates with a clear validation result.
- Accept policy non-workdays and global days off as valid report dates.
- Do not reject a date solely because it lies outside a selected Project's active interval.

A valid empty date renders an informative empty state and applicable attendance/calendar context.
For an explicitly selected Project outside its interval, the report may explain that interval while
remaining an empty, valid report rather than a system error.

### Q28 — Daily HTML, XLSX, and PDF ship together

**Approved option:** A.

All three formats consume one immutable, authorized Daily dataset. A Daily request sets:

```text
workFrom = Report date
workTo   = Report date
```

Due-date filters remain optional; export must not demand an unrelated complete due-date range.
Every format carries equivalent authorized Projects, authors, Tasks, descriptions, current
statuses, planning fields, row/subtotals, Project totals, and overall total. Layout may differ but
data meaning and totals may not.

All formats exclude forecast notes/reasons, unsupported “completed today” claims, attendance
presence claims, productivity/efficiency judgments, and unauthorized data.

### Q29 — One isolated feature branch

**Approved option:** B.

Use one clean isolated `codex/...` feature branch and worktree for the complete vertical feature.
This is an explicit exception to the repository's usual `work/platform` → `work/tasks` →
`work/reports-ui` ownership sequence. Preserve the same dependency order and keep documentation,
schema, Task behavior, report behavior, and review evidence separable in focused local commits.
Never implement in or absorb unrelated changes from a dirty `main` checkout.

The selected branch is `codex/partial-jira-tempo`. This decision authorizes isolation strategy; it
does not authorize pushing, merging, or rewriting shared branches.

### Q30 — Make the decision normative before production code

**Approved option:** A.

Before production implementation:

- amend the numbered requirements;
- add acceptance scenarios for estimate locking, worked/unworked reassignment, correction,
  removal atomicity, Daily report authorization/date/calendar/deleted-Task behavior, and output
  parity;
- add bounded Iteration 4 tracker entries;
- retain canonical terms in `CONTEXT.md`; and
- retain the estimate/forecast ADR.

Chat history and the original attachment are not sufficient implementation authority by
themselves.

### Q31-R — Daily-report role scopes

**Approved option:** A — replace the original Admin/Leader scope with the current-Leader slice;
the 27 August reporting-role clarification is the latest revision of this decision.

- Owning Mentor: the Daily Project Work Report covers all owned Projects, with an optional one
  selected owned-Project filter. The existing global Mentor Reports entry remains available.
- Current Project Leader: HTML, XLSX, and PDF are available only for one exact Project that the
  actor currently leads. Access covers `PLANNED` and `ACTIVE` Projects, today and permitted past
  dates, and the whole retained Project history, including work before the current leadership
  term. An active Intern sees a Daily-report entry only when the server confirms that the actor
  currently leads at least one eligible Project. With one eligible Project the entry redirects
  directly to its locked report; with multiple eligible Projects it opens a selector containing
  only those Projects; with none it returns the non-disclosing `Project unavailable` result. The
  Project-detail `Generate Daily Report` action remains available for the authorized current
  Leader.
- A current-Leader report request requires `projectId` for HTML, XLSX, and PDF. The report service
  re-authorizes that exact Project from stored current leadership, active membership, and Project
  state even when the ID came from a selector. Leadership replacement transfers access
  immediately; Project completion ends current-Leader access. A valid selected date survives
  selection or redirection and otherwise defaults to today in `Asia/Ho_Chi_Minh`.
- Admin is a configuration and account-lifecycle role with no dedicated Attendance, Project/Task,
  or Daily Project Work Report scope. Admin report navigation is absent, and authenticated Admin
  requests to the Attendance and Project/Task HTML/XLSX/PDF routes are denied with `403 Forbidden`
  before target/Project option resolution, Attendance/Task/work-log reads, dataset construction,
  or exporter invocation. Admin Daily HTML/XLSX/PDF requests retain the non-disclosing `Project
  unavailable` response. Admin cannot obtain a report dataset through a direct service call or
  export path.
- Ordinary Interns, former Leaders, Leaders of another Project, completed Projects, missing
  `projectId` requests, and guessed IDs remain denied before Attendance context, Task queries, or
  exporter invocation. Membership, the global `ROLE_INTERN`, or possession of a Project ID alone
  is insufficient. An Intern who is a current Leader retains that contextual Daily scope, while
  their Attendance scope remains own history only.
- The remaining Attendance and Project/Task scopes are preserved: active Interns retain own
  Attendance, active Mentors retain authorized detailed Intern Attendance, owning Mentors and
  current Leaders retain authorized Project/Task detail, and ordinary Project members retain
  aggregate-only Project/Task progress and hours. Admin has no dedicated report scope.
- Mentor demand for additional Daily reporting is operational communication outside the system.
  No persisted delegation request, toggle, notification, Daily report artifact, audit event,
  schema, or migration is introduced.

Every scope is reduced or denied before the relevant report dataset is constructed and before any
HTML, XLSX, or PDF renderer. Changing the format or guessing identifiers must not bypass
authorization.

### Q32 — Baseline failure gate

**Approved option:** A.

Run the documented baseline from the clean isolated starting point. If an independent failure
exists, stop before feature implementation, preserve exact evidence, and obtain explicit user
authorization before proceeding. Do not mislabel a pre-existing failure as feature RED evidence.

This rule was followed. The user later explicitly authorized proceeding past two demonstrated
independent baseline failures: PostgreSQL rejecting the `Asia/Saigon` timezone alias during one
integration path, and an existing Windows path-separator mismatch in `LayerStructureTest`. That
authorization is specific to those known failures; it is not blanket permission to ignore new
regressions.

### Q33 — Local implementation and publication boundary

**Approved option:** A.

After shared-understanding confirmation, implementation may proceed test-first in the isolated
worktree with focused local commits, review, and an integrated-candidate report. No push and no
merge to `main` are authorized without a separate explicit user instruction. Tests and commits are
evidence gates, not automatic publication authority.

### Q34 — Final shared-understanding and implementation authorization

**Approved option:** A.

The user confirmed the consolidated product contract and authorized:

- normative documentation and bounded tracking;
- clean baseline verification;
- test-first implementation on `codex/partial-jira-tempo`;
- focused local commits and integrated review under Q33.

Q34 did not authorize external Jira/Tempo work, unrelated feature expansion, push, or merge to
`main`.

## Post-Q34 approved implementation clarifications

These clarifications were approved by the user's statement, “Proceed with those three decisions.”
They are later than Q34 and therefore control where they refine an earlier assumption.

### 1. Proceed past two known independent baseline failures

Keep the PostgreSQL timezone-alias and Windows `LayerStructureTest` failures separate from feature
RED/GREEN evidence. A focused feature test must not be called green merely because it avoids those
paths, and a new failure must not be dismissed as part of the known baseline.

### 2. Use five observable TDD seams

The approved behavior seams are:

1. Task application behavior: estimate creation/mutation, Task detail, first-log freeze, server
   authorization, persistence, and stale/race behavior.
2. Manual reassignment: versioned assignment plus atomic initial forecast and observable forecast
   history.
3. Project redistribution: batch transfer and direct-removal guard behavior, including no partial
   mutation.
4. Report dataset: hand-calculated separation of selected-date logged effort from lifetime actual,
   estimate, variance, and latest forecast.
5. Presentation/export: the active HTML route and real XLSX/PDF renderers use the same authorized
   dataset and semantics.

Flyway catalog/constraint verification remains a persistence backstop. Tests aimed only at mocked
internal collaborators or obsolete interfaces do not by themselves prove the product seams.

### 3. Direct Mentor removal blocks before every mutation

An owning Mentor cannot truthfully author a Project Leader forecast. Therefore, if the departing
membership owns any unfinished Task with retained work, direct removal is rejected before changing:

- leadership;
- membership;
- Task assignment;
- exit request/decision state; or
- notifications.

The current Project Leader must first reassign each worked unfinished Task through the normal
forecast-aware workflow. After those transfers, the existing automatic-transfer behavior may
still handle eligible unworked unfinished Tasks during removal. The system must never fabricate a
Leader, forecast, note, reason, timestamp, or actual snapshot to make direct removal succeed.

This rule supersedes the earlier Q16 sub-assumption that direct removal itself would receive
forecast inputs.

## Independent implementation compliance checklist

A reviewer can use the following checklist against an independent implementation.

### Scope and authority

- [ ] The feature works without Jira or Tempo credentials or APIs.
- [ ] Existing Project, Task, work-log, status, and authorization concepts are extended rather than duplicated.
- [ ] Existing `TODO`/`IN_PROGRESS`/`BLOCKED`/`DONE` behavior is preserved.
- [ ] No submission/acceptance workflow was added under this scope.
- [ ] The original stakeholder proposal is not treated as overriding numbered requirements.

### Estimate

- [ ] Estimate is nullable whole-Task planning data stored canonically in minutes.
- [ ] Accepted supplied range is exactly 1 through 527040.
- [ ] Missing estimate is not converted to zero or a fabricated default.
- [ ] Only the current Project Leader can set, replace, or clear it.
- [ ] Ordinary member, assignee, creator, Mentor, former Leader, and unauthorized requests cannot mutate it.
- [ ] Missing estimate does not block Task creation, logging, reassignment, status changes, or Project lifecycle.
- [ ] The first retained work log permanently freezes the original estimate.
- [ ] Estimate mutation and first-log creation are race-safe and stale-write-safe.
- [ ] Reassignment never replaces or clears the original estimate.

### Actual effort and comparison

- [ ] Actual Task effort sums all retained Task work logs across all authors and assignments.
- [ ] Work-log correction changes the lifetime actual total.
- [ ] Reassignment does not reset actual effort.
- [ ] Attendance duration is not used as Task actual effort.
- [ ] Report-period logged effort remains distinct from lifetime actual effort.
- [ ] Signed variance is actual minus original estimate only for estimated `DONE` Tasks.
- [ ] Estimated unfinished or reopened Tasks show `Pending` variance.
- [ ] Unestimated Tasks show `N/A` variance.
- [ ] No efficiency, productivity score, or person ranking exists.

### Reassignment planning

- [ ] A worked reassignment preserves the original Task estimate.
- [ ] Remaining work is represented separately as an append-only Remaining effort forecast.
- [ ] Lifetime actual is never compared with the remaining forecast as if it were a whole-Task estimate.
- [ ] No per-assignment estimate/variance is claimed without a durable assignment-period model.
- [ ] Worked reassignment requires one valid forecast even when the original estimate is absent.
- [ ] Unworked reassignment neither requires nor accepts a fabricated forecast.
- [ ] Reassignment plus required forecast creation is atomic for manual and batch paths.
- [ ] Forecast history retains Task, incoming membership, Leader membership, server-controlled
      reassignment time, remaining minutes, and lifetime-actual snapshot.
- [ ] Forecast-total arithmetic derives from the stored snapshot and does not drift with later logs.
- [ ] Initial note is optional and normalized; correction reason is mandatory, trimmed, and bounded.
- [ ] Correction appends one linear successor and never updates/deletes its predecessor.
- [ ] Only the current Leader may correct the current assignment-context forecast.
- [ ] The incoming membership's first newly created log closes the correction window.
- [ ] Historical log correction does not close, reopen, or move that window.
- [ ] Stale, late, wrong-context, unauthorized, and concurrent-loser corrections fail without
      partial state.
- [ ] Direct Mentor removal blocks before every mutation when worked unfinished Tasks remain.
- [ ] After Leader forecast-aware transfer of worked Tasks, eligible unworked Tasks retain the
      existing automatic-transfer behavior.

### Visibility and reports

- [ ] All authorized Task viewers can read Task-level planning facts on focused surfaces.
- [ ] Read visibility does not grant estimate or forecast mutation authority.
- [ ] Task detail/history shows complete chronological forecast/correction provenance.
- [ ] Compact reports/exports show only the latest applicable forecast and exclude notes/reasons.
- [ ] No forecast-accuracy metric, percentage, ranking, or color judgment exists.
- [ ] Active Interns can report only their own Attendance; active Mentors retain authorized
      detailed Intern Attendance; Admins have no Attendance-report scope.
- [ ] Owning Mentors and current Leaders retain authorized Project/Task detail; ordinary members
      retain aggregate-only Project/Task progress and hours; Admins have no Project/Task-report
      scope or report dataset.
- [ ] Daily Mentor scope defaults to all owned Projects and accepts an optional owned-Project filter.
- [ ] A current Project Leader can discover one or more exact currently-led PLANNED or ACTIVE
      Projects through role-aware navigation, then uses one mandatory Project and the same
      authorized Daily dataset for HTML, XLSX, and PDF, including whole retained Project history
      before the current leadership term.
- [ ] Admins have no Daily-report scope and no Daily navigation; Attendance and Project/Task Admin
      requests are denied before target/Project option resolution, downstream reads, dataset
      construction, or export, while Daily Admin requests retain non-disclosing `Project unavailable`
      behavior.
- [ ] Ordinary Interns without current leadership, former Leaders, Leaders of another Project,
      completed Projects, missing `projectId` requests, and guessed IDs are denied before
      downstream reads.
- [ ] Mentor retains the global Daily entry; the conditional Daily entry is shown to an active
      Intern only when current leadership exists. One eligible Project redirects, multiple show an
      authorized selector, and none returns `Project unavailable`; Project-detail entry remains
      current-Leader-only.
- [ ] Leadership replacement transfers Daily access immediately and Project completion ends it.
- [ ] Mentor demand is out-of-system operational communication with no persisted delegation,
      toggle, notification, report artifact, audit event, schema, or migration.
- [ ] Report date defaults to today in `Asia/Ho_Chi_Minh`; future dates are rejected.
- [ ] Policy non-workdays and global days off annotate but never filter retained Task work.
- [ ] Work is grouped by retained log author, not current Task assignee.
- [ ] Every retained log preserves its description; repeated logs may also have a Task subtotal.
- [ ] Current Task status is labeled as current and is not presented as completion-on-date evidence.
- [ ] Soft-deleted Task logs remain visible with a deletion marker.
- [ ] All-Projects mode omits empty Projects; an explicitly selected authorized empty Project shows
      an explicit empty state; unauthorized scope is denied rather than disguised as empty.
- [ ] Daily selected-date minutes are visibly distinct from lifetime actual and planning values.
- [ ] HTML, XLSX, and PDF use the same authorized rows, fields, calculations, and totals.
- [ ] Daily export works from a complete one-day work range without an unrelated due-date range.
- [ ] No format invents attendance, scheduled-hours, completion-date, efficiency, or productivity
      claims.

### Delivery and evidence

- [ ] Numbered requirements, acceptance scenarios, Iteration 4 tracking, glossary, and ADR express
      the approved behavior.
- [ ] Work is isolated from unrelated dirty-checkout changes.
- [ ] Known independent baseline failures remain separately identified and are not used to excuse
      new failures.
- [ ] Observable tests cover Task application, manual reassignment, Project redistribution, report
      dataset, and real presentation/export seams.
- [ ] Local commits remain focused enough to review schema, Task, and report behavior separately.
- [ ] No push or merge to `main` is treated as implicitly authorized.

## Traceability to the repository specification

| Decision group | Primary normative references | What the references must prove |
|---|---|---|
| Q1–Q4 | Overall requirements authority; Iteration 4 tracker | Local-only scope, preserved Task lifecycle, phased complete delivery. |
| Q5–Q8, Q10, Q13 | `TSK-020`, `DB-013`, `AC-TSK-012` | Optional bounded whole-Task estimate, Leader-only mutation, first-log freeze, no backfill, immutable baseline. |
| Q9, Q11, Q12, Q14–Q15 | `TSK-021`, `RPT-012`–`RPT-013`, `AC-TSK-012`, `AC-RPT-005` | Lifetime actual, DONE-only signed variance, Pending/N/A, no scoring, lifetime/period separation, output parity. |
| Q16–Q21 plus removal clarification | `TSK-022`, `DB-013`, `AC-TSK-013`–`AC-TSK-014` | Worked/unworked reassignment rules, immutable snapshot, append-only correction, concurrency rejection, removal atomicity, no accuracy metric. |
| Q22-R, Q24–Q28, Q31-R, 27 August reporting-role revision | `RPT-004`–`RPT-013`, `AC-RPT-002`, `AC-RPT-004`–`AC-RPT-005`, relevant `RPT-001` and `RPT-006`–`RPT-010` authorization/export rules | Non-Admin Attendance and Project/Task scopes, Admin denial and denial ordering, Daily Mentor/current-Leader role scopes and discoverable entry, date/calendar semantics, author grouping, deleted Tasks, empty states, and identical HTML/XLSX/PDF data. |
| Q23 | `CONTEXT.md`; ADR 0001 | Stable vocabulary and rationale for preserving baseline versus forecast. |
| Q29–Q34 | `.agents/PROJECT_PLAN.md`; branch/test evidence | Isolation, documentation-first order, baseline gate, TDD, local-only integration boundary. |

If one of these repository documents is missing or contradicts this record, the review result is
not automatically a product failure in the teammate's code. Report the specification conflict
separately, then evaluate behavior against the final explicit decision recorded here.

## Instructions for an independent review agent

Review the teammate's implementation against this contract, not against the reference branch's
class layout. For every material rule:

1. Identify the externally observable behavior and authorization boundary.
2. Cite exact implementation files and lines that appear to enforce it.
3. Cite an executable behavioral test, or state that the rule is not proven by a test.
4. Classify the result as:

   - `PASS`: behavior and meaningful test evidence satisfy the rule;
   - `PARTIAL`: part is implemented, but a required path, invariant, format, or role is missing;
   - `FAIL`: observable behavior contradicts the approved rule;
   - `NOT PROVEN`: code may exist, but available evidence cannot establish the behavior safely.

5. Distinguish a different but valid internal design from missing behavior.
6. Check manual, batch, forged-request, stale/concurrent, and export paths where the rule applies;
   a green happy-path UI test is insufficient for an application invariant.
7. Do not modify the teammate's implementation until the comparison report is reviewed and
   approved.

The final review report should contain:

- an executive summary;
- a Q1–Q34 compliance matrix, including Q8-R and Q22-R;
- findings ordered by risk, with file/line/test evidence;
- explicit specification conflicts or unresolved ambiguities;
- missing or weak test coverage;
- a short statement of what was not inspected.

## Final scope boundary

The following remain outside this feature unless separately approved through numbered
requirements:

- external Jira or Tempo integration;
- Jira issue/sprint/story-point mirroring;
- Tempo accounts or synchronization;
- a `SUBMITTED`/Leader acceptance/rejection Task workflow;
- Weekly and Monthly presets;
- continuous replanning unrelated to worked reassignment;
- replacing the original estimate after work begins;
- per-assignment baseline variance;
- forecast accuracy, efficiency, productivity, or employee/Intern ranking;
- deriving attendance from Task work;
- claiming a Task was completed on the Report date without durable status-event history;
- broad dashboard/general-list expansion merely because the planning values exist.

This record is complete for Q1–Q34 and the three explicitly approved post-Q34 implementation
clarifications. Later implementation details are evidence only unless the user approves them as a
new product decision.
