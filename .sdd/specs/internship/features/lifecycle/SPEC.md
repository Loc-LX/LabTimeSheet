# Internship lifecycle Spec

**Version:** 1.3.0 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-21

**Module:** `internship` · **Shared contract:** [MODULE.md](../../MODULE.md)

## 1. Context & Goal

Manage the Intern profile and move an internship from preparation through activation to completion or withdrawal.

This is one cohesive planning scope. Operations below are sections of this feature,
not separate modules or mandatory branches. The original split changed document ownership only; `D35` and `D36` record the subsequent approved behavior decisions.

## 2. Actors & Roles

Admin; scheduled activation and the request-time activation guard.

Use cases defined here: UC-18.

The [platform permission matrix](../../../platform/MODULE.md#52-permission-matrix)
and the module's shared authorization rules apply to every operation.

## 3. Functional Requirements

The operation summaries below are navigation. The numbered rules remain authoritative;
they do not acquire additional behavior from a heading or summary.

### Prepare and correct profile

ACC-019 governs Student Code and the permitted date/profile corrections.

### Start internship

ACC-020 and ACC-021 govern states, the business-date trigger and the responsible-Mentor precondition.

### Complete or withdraw

ACC-022 through ACC-025 govern outstanding Project work, read-only completion, withdrawal and retained attendance.

### Canonical feature rules

| ID | Requirement |
|---|---|
| ACC-019 | THE system SHALL give every `INTERN` account exactly one Intern profile carrying a case-insensitively unique Student Code, internship start and end dates, and an internship status, and SHALL NOT create a profile for a non-Intern account. An Admin MAY correct the Student Code WHILE the internship is `NOT_STARTED` or `ACTIVE`, and MAY correct the dates only WHILE it is `NOT_STARTED`. WHILE a profile is `COMPLETED` or `WITHDRAWN`, THE system SHALL keep it read-only. |
| ACC-020 | THE system SHALL permit only these internship transitions: `NOT_STARTED → ACTIVE → COMPLETED` and `NOT_STARTED/ACTIVE → WITHDRAWN`. A `SUSPENDED` state SHALL NOT exist. |
| ACC-021 | WHEN the configured internship start date is reached, THE system SHALL activate an eligible `NOT_STARTED` Intern through both a scheduled guard and a guard applied at request time, so that correctness does not depend on scheduler timing. WHILE an Intern has no responsible Mentor under `ACC-026`, THE system SHALL NOT activate the internship. |
| ACC-022 | WHEN an Admin marks an Intern `COMPLETED` or `WITHDRAWN`, THE system SHALL apply it only through that explicit action. WHILE that Intern holds a current leadership term or owns an unfinished Task, THE system SHALL refuse both actions until the leader-transfer and Task-reassignment workflows have succeeded, except that soft-deleted Tasks and Tasks belonging to a `CANCELLED` Project SHALL be excluded from this readiness check. For this check, an unfinished Task SHALL mean a Task still assigned to that Intern whose status is `TODO`, `IN_PROGRESS` or `BLOCKED`; both exclusions SHALL apply regardless of that retained status. THE system SHALL leave those excluded Tasks, their statuses and retained history unchanged; excluding them SHALL NOT mark them `DONE`, restore a soft-deleted Task or make a terminal Project writable. Every other lifecycle guard SHALL still apply. |
| ACC-023 | WHILE an Intern is `COMPLETED`, THE system SHALL allow authentication in read-only mode to view retained history and manage password and session security, and SHALL refuse any attempt to create or mutate attendance, leave, correction, Project, Task, comment, or work-log data. |
| ACC-024 | WHEN an Admin withdraws an Intern, THE system SHALL set the account to `DEACTIVATED` in the same transaction, SHALL end every session of that account, SHALL thereafter refuse its login under `ACC-016`, SHALL issue it no password-reset token and refuse any it already holds, and SHALL keep their historical memberships, Tasks, work logs, attendance, leave, and corrections attributable. |
| ACC-025 | WHEN a terminal lifecycle action is applied, THE system SHALL enforce it for authorization from that instant. Attendance already recorded on that local date SHALL remain reportable, and an otherwise empty terminal date SHALL NOT be newly classified as an absence. |

#### UC-18 — Administer the internship lifecycle

| Field | Specification |
|---|---|
| Primary actor(s) | Admin |
| Trigger | An Admin creates an Intern or changes internship lifecycle state. |
| Preconditions | The Admin is active; SMTP is active for Intern account creation. |
| Postconditions | Internship state changes transactionally while historical attribution remains. |
| Traced requirements | ACC-019–ACC-025, AUTH-001–AUTH-002 |

**Main success flow**

1. For an Intern, enter unique student code and internship dates.
2. Inspect membership, leadership, and unfinished-task context, excluding soft-deleted Tasks and Tasks of CANCELLED Projects from readiness under ACC-022.
3. Complete or withdraw only when guards pass.

**Alternatives and exceptions**

- Completion or withdrawal is blocked while the Intern holds a current leadership term or owns non-deleted unfinished Tasks outside CANCELLED Projects. Soft-deleted Tasks and retained work in CANCELLED Projects do not themselves block either action.
- Completed Interns retain read-only historical access; withdrawal deactivates the account, ends its sessions, and refuses further login.

#### State transitions: internship

The transitions the rules above allow. The table adds nothing to them: where it and a rule differ, the rule wins.

| From | Action | To | Who | Rules |
|---|---|---|---|---|
| `NOT_STARTED` | the start date is reached and a responsible Mentor is assigned | `ACTIVE` | system, by schedule and at request time | `ACC-020`, `ACC-021` |
| `ACTIVE` | complete, when the Intern holds no current leadership term and owns no blocking unfinished Task under ACC-022 | `COMPLETED` | Admin | `ACC-020`, `ACC-022` |
| `NOT_STARTED`, `ACTIVE` | withdraw, under the same guard; the account becomes `DEACTIVATED` | `WITHDRAWN` | Admin | `ACC-020`, `ACC-022`, `ACC-024` |

`NOT_STARTED` is the only status no permitted transition leads to, so an internship starts in it. No transition leaves `COMPLETED` or `WITHDRAWN` (`ACC-020`), and both keep the profile read-only (`ACC-019`).

## 4. Non-functional Requirements

Inherit [module constraints](../../MODULE.md#4-non-functional-requirements) and
[platform constraints](../../../platform/MODULE.md#4-non-functional-requirements),
including authorization, history, concurrency, server time and test evidence.

## 5. Data

`intern_profiles`; account effects cross the identity service boundary.

The [module data contract](../../MODULE.md#5-data) retains ownership, constraints and
checks across features. The full physical model stays in platform §19. A feature folder
does not grant repository/entity access across the module boundaries of ARC-006.

### Required contracts

These are required facts or behaviors, not prescribed cross-module imports or an
automatic implementation order. PLAN resolves delivery predecessors against what
already exists and preserves ARC-005/ARC-006.

| Provider | Required fact or behavior | Rule trace |
|---|---|---|
| [internship/responsible-mentor](../responsible-mentor/SPEC.md) | Assigned active Mentor before activation | [ACC-026](../responsible-mentor/SPEC.md) |
| [identity/account-lifecycle](../../../identity/features/account-lifecycle/SPEC.md) | Account state affected by completion/withdrawal | [ACC-014](../../../identity/features/account-lifecycle/SPEC.md), [ACC-015](../../../identity/features/account-lifecycle/SPEC.md), [ACC-016](../../../identity/features/account-lifecycle/SPEC.md) |
| [project](../../../project/MODULE.md) | Readiness excludes soft-deleted Tasks and unfinished Tasks in CANCELLED Projects under ACC-022 while retaining their history; the existing internship-owned port remains the boundary | [ACC-022](SPEC.md), [PRJ-023](../../../project/features/lifecycle/SPEC.md) |
| [calendar/attendance-policy](../../../calendar/features/attendance-policy/SPEC.md) | Applicable business-date policy | [ATT-001](../../../calendar/features/attendance-policy/SPEC.md) |

### Related workflows and joint checks

- [Responsible Mentor](../responsible-mentor/SPEC.md): Assignment is required before activation.

## 6. Error Handling

Apply each refusal, deadline, conflict and delivery-failure clause in the numbered rules
above, together with [module error handling](../../MODULE.md#6-error-handling).
Platform §21 supplies common failure behavior; an operation summary does not override it.

## 7. Acceptance Criteria

### Operation and acceptance map

This map traces existing behavior; its gap column does not define a new rule or
claim full test coverage. Actors and outcomes are summaries of the canonical rules.

| Operation | Actor and observable outcome | Canonical rules | Existing acceptance scenarios | Acceptance boundary or open decision |
|---|---|---|---|---|
| [Prepare and correct profile](#prepare-and-correct-profile) | Admin edits only the Intern profile fields allowed in its current state | [ACC-019](SPEC.md) | [AC-ACC-015](SPEC.md) | Keep unique Student Code and terminal-profile refusal checks. |
| [Start internship](#start-internship) | Eligible internship activates once when its start date and Mentor preconditions hold | [ACC-020](SPEC.md), [ACC-021](SPEC.md), [ACC-026](../responsible-mentor/SPEC.md) | [AC-ACC-010](SPEC.md), [AC-ACC-013](../../MODULE.md) | Include scheduler/access-guard concurrency and the absent-Mentor case. |
| [Complete or withdraw](#complete-or-withdraw) | Admin completes or withdraws an internship with the specified access and history effects | [ACC-022](SPEC.md), [ACC-023](SPEC.md), [ACC-024](SPEC.md), [ACC-025](SPEC.md) | [AC-ACC-010](SPEC.md), [AC-ACC-018](SPEC.md), [AC-ACC-019](SPEC.md) | `D35` settles cancelled-Project readiness; `D36` excludes soft-deleted Tasks for both terminal actions. Retain other lifecycle and account-access guards. |

### Canonical acceptance scenarios

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-ACC-010 | ACC-019–ACC-025 | Start date arrives, then Admin completes an Intern | Scheduler/access guard activates once; completion is blocked until transfer guards pass; completed login is read-only. |
| AC-ACC-015 | ACC-019 | Admin corrects the Student Code and the internship dates of Interns whose internships are `NOT_STARTED`, `ACTIVE`, `COMPLETED`, and `WITHDRAWN` | Student Code edits succeed only while `NOT_STARTED` or `ACTIVE`; date edits succeed only while `NOT_STARTED`; completed and withdrawn profiles remain read-only and unchanged. |
| AC-ACC-018 | ACC-022, ACC-023, ACC-024, PRJ-023, AUTH-006 | For separate completion and withdrawal cases, an active Admin acts on an otherwise eligible Intern whose only unfinished Tasks are TODO, IN_PROGRESS and BLOCKED in a CANCELLED Project; repeat while the Intern also holds a current leadership term or a non-deleted unfinished Task on a PLANNED/ACTIVE Project | Cancelled-Project Tasks alone do not block either action. Completion gives read-only access; withdrawal deactivates the account and ends every session. Any current leadership term or otherwise blocking unfinished Task still refuses the action atomically. Cancelled Tasks keep their statuses, attribution and work history; no Task becomes DONE and later mutation of the cancelled Project remains denied. |
| AC-ACC-019 | ACC-022, ACC-023, ACC-024, TSK-010, PRJ-014, AUTH-006 | In separate fixtures for completion and withdrawal, a Project has one DONE Task and an Intern-assigned unfinished Task (repeat TODO, IN_PROGRESS and BLOCKED); an authorized Leader soft-deletes the unfinished Task and the owning Mentor completes the Project. An active Admin then ends the otherwise eligible internship. Repeat readiness with soft-deleted Tasks on PLANNED/ACTIVE Projects, with a non-deleted unfinished Task on another open Project, and with a current leadership term | The soft-deleted Task contributes zero blocking work before and after Project completion, so the otherwise eligible internship can complete or withdraw. Adding one non-deleted unfinished Task on a non-cancelled Project contributes one blocker and refuses either action; current leadership independently refuses either action even when blocking work is zero. An excluded Task retains status, assignee, deletion attribution, comments and work logs: it is neither restored nor marked DONE, and terminal Project mutation stays denied. Completion retains read-only access and withdrawal applies ACC-024 session/account effects. |

Shared and cross-feature scenarios in [MODULE.md](../../MODULE.md#7-acceptance-criteria)
also apply. Scenario ownership follows the behavior exercised, not every prerequisite
named by its Requirements cell. The original relocation preserved existing expected results; `D35` and `D36` record the approved amendments.

## 8. Out of Scope

Inherit [module exclusions](../../MODULE.md#8-out-of-scope). Adjacent feature behavior
is a dependency, not a duplicated contract. `D35` and `D36` change only the stated behavior contracts. This revision adds no schema, dependency, PLAN.md or TASKS.md.

## Notes / Open Questions

`D35` settles the ACC-022/PRJ-023 interaction: unfinished Tasks in CANCELLED Projects do not block completion or withdrawal. `D36` also excludes soft-deleted Tasks, including retained unfinished Tasks on COMPLETED Projects. Current leadership and non-deleted unfinished work outside CANCELLED Projects still block both actions. Existing guards elsewhere remain binding; this does not implement the readiness port or approve a technical plan.

Read [shared open questions](../../MODULE.md#notes--open-questions) before approving
the technical plan. [plan.md](../../../../../plan.md) is the only progress tracker.
