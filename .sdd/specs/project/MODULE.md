# Project Module

<a id="project-spec"></a>

**Version:** 1.6.0 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-21

Part of the Lab Timesheet specification. Rules every feature shares, including the
glossary, the authorization model, the domain model, and failure handling, are in
[the platform spec](../platform/MODULE.md). Section numbers marked `§` are one
numbering shared by all the specs, so a reference such as §5.2 names the same section
wherever it appears.

## 1. Context & Goal

Mentor-owned Projects with contextual Intern leadership, and the single-assignee Tasks inside them with comments, work logs, and effort planning: the third area named by the product objective (§1.2 of the platform spec).
Its module is `project` (`ARC-005`): Projects, membership intervals, leadership terms, invitations, and membership exits, together with Tasks, comments, work logs, exit transfers, and Remaining effort forecasts.

### Parts within project

These six business scopes become sections of the project `PLAN.md`; they keep one module
and one canonical copy of every rule (`D33`). Each scope includes user actions, authorization,
transactions, retained history and acceptance evidence. A part is not a new package or a
standalone release. The dependency checks below apply even when parts are planned separately.
Progress is tracked only in [plan.md](../../../plan.md).

| Part and outcome | Core rules | Use cases and acceptance scenarios |
|---|---|---|
| [P1 — Project lifecycle](MODULE.md#project-p1): create, activate, complete or cancel and expose progress | `PRJ-001`–`PRJ-002`, `PRJ-012`–`PRJ-016`, `PRJ-023`–`PRJ-024`; `DB-019` | `UC-05`; `AC-PRJ-006`–`AC-PRJ-009`, `AC-PRJ-014`–`AC-PRJ-016` |
| [P2 — Membership and leadership](MODULE.md#project-p2): maintain eligible members and one current Leader with retained intervals | `PRJ-003`–`PRJ-007`, `PRJ-011`, `PRJ-017` | `UC-05`; `AC-PRJ-001`–`AC-PRJ-003` |
| [P3 — Invitations](MODULE.md#project-p3): invite and accept, decline or revoke without duplicate membership | `PRJ-018`–`PRJ-019`, `DB-011` | `UC-13`; `AC-PRJ-010`–`AC-PRJ-011` |
| [P4 — Exits and transfers](MODULE.md#project-p4): prepare departure, transfer work and close membership when ready | `PRJ-008`–`PRJ-010`, `PRJ-020`–`PRJ-022`, `DB-012` | `UC-14`, removal in `UC-05`; `AC-PRJ-004`–`AC-PRJ-005`, `AC-PRJ-012`–`AC-PRJ-013`, `AC-TSK-014` |
| [P5 — Task definition, comments and status](MODULE.md#project-p5): manage the Task and its permitted transitions | `TSK-001`–`TSK-012`, `TSK-018`–`TSK-019`, `TSK-023`, `TSK-025`, `DB-020` | `UC-06`–`UC-07`, Mentor interventions in `UC-05`; `AC-TSK-001`–`AC-TSK-006`, `AC-TSK-010`–`AC-TSK-011`, `AC-TSK-015`–`AC-TSK-016`, `AC-TSK-018`–`AC-TSK-021` |
| [P6 — Work logs and effort](MODULE.md#project-p6): record actual work and preserve estimate/forecast history | `TSK-013`–`TSK-017`, `TSK-020`–`TSK-022`, `TSK-024`, `DB-013` | `UC-06`–`UC-07`, forecasts in `UC-14`; `AC-TSK-007`–`AC-TSK-009`, `AC-TSK-012`–`AC-TSK-013`, `AC-TSK-017` |

**Shared contracts:** `AUTH-004`–`AUTH-009` and `AUTH-011` apply wherever a part uses
membership, leadership, assignee rights or history. `NOT-003` and `NOT-010` retain one
recipient definition across parts. Integrity rules remain together after the business
rules; §7 groups the shared authorization and database scenarios separately.
A use case crossing parts remains one use case; its repeated reference is not a copy.


### Feature index

Read this shared contract together with the relevant feature SPEC. Rules are canonical
in exactly one of these documents; references do not create copies. Cross-feature use
cases, shared data constraints and unresolved decisions remain here (`D34`).

| Feature | Operations |
|---|---|
| [Project lifecycle](features/lifecycle/SPEC.md) | Create and start; Complete; Cancel or delete |
| [Membership and leadership](features/membership-and-leadership/SPEC.md) | Add eligible members; Assign or replace Leader |
| [Project invitations](features/invitations/SPEC.md) | Issue invitation; Respond, revoke or supersede |
| [Membership exit and transfer](features/membership-exit/SPEC.md) | Request or cancel exit; Prepare leadership and Task transfers; Approve, reject or supersede |
| [Task management](features/task-management/SPEC.md) | Create, edit and soft-delete; Assign and reassign; Start, finish, block, unblock and reopen; Comment |
| [Work logs and effort](features/work-logs-and-effort/SPEC.md) | Record and correct work; Set estimate; Forecast remaining effort; Calculate effort and variance |

The earlier A/P/F labels remain navigation aliases for the same scopes, not extra features.

## 2. Actors & Roles

Primary actors named by this feature's use cases:

- **UC-05 — Manage a Project, membership, and leadership:** Owning Mentor
- **UC-06 — Manage Tasks as current Project Leader:** Current Project Leader
- **UC-07 — Perform assigned Project work:** Current Task assignee; active Project member; owning Mentor for comments only
- **UC-13 — Respond to a Project invitation:** Current Project Leader; invited Intern; owning Mentor
- **UC-14 — Request and decide Project membership exit:** Current Leader; current Project member; owning Mentor

Every capability by role is in the permission matrix, [platform spec](../platform/MODULE.md) §5.2. Authorization is resolved from stored context, never from the global role alone (§5.1).

## 3. Functional Requirements

### §6. Project, membership, and leadership

<a id="project-p1"></a>

#### P1 — Project lifecycle

Feature contracts: [Project lifecycle](features/lifecycle/SPEC.md).

#### State transitions: Project

Canonical workflow: [feature contract](features/lifecycle/SPEC.md).

<a id="project-p2"></a>

#### P2 — Membership and leadership

Feature contracts: [Membership and leadership](features/membership-and-leadership/SPEC.md).

<a id="project-p3"></a>

#### P3 — Invitations

Feature contracts: [Project invitations](features/invitations/SPEC.md).

<a id="project-p4"></a>

#### P4 — Exits and transfers

Feature contracts: [Membership exit and transfer](features/membership-exit/SPEC.md).

### §7. Tasks, comments, and task work

<a id="project-p5"></a>
<a id="71-task-lifecycle"></a>

#### §7.1 P5 — Task definition, comments and status

Feature contracts: [Task management](features/task-management/SPEC.md).

<a id="project-p6"></a>
<a id="72-comments-and-work-logs"></a>

#### §7.2 P6 — Work logs and effort

Feature contracts: [Work logs and effort](features/work-logs-and-effort/SPEC.md).

### Authorization and integrity (from platform §5 and §19.3)

| ID | Requirement |
|---|---|
| AUTH-004 | WHILE a leadership term is current, THE system SHALL grant its holder Leader permissions, and WHEN that term ends, THE system SHALL withdraw Task-management permission immediately. WHERE a Leader exit is pending, THE system SHALL require the owning Mentor to appoint the replacement before any exit-transfer work, and SHALL NOT move a Task merely because leadership changed. |
| AUTH-005 | THE system SHALL resolve current-assignee permission independently of leadership: a Leader MAY log work on a Task, and MAY make any `TSK-007` transition on it, only WHILE assigned to it, and so MAY an ordinary member. The transitions a current Leader MAY make on another member's Task are those of `TSK-023` and no others. |
| AUTH-006 | WHILE a Project is `COMPLETED` or `CANCELLED`, THE system SHALL make it read-only to every role. An Admin MAY read every Project and its retained history. WHILE a Project is open, THE system SHALL grant history access to its owning Mentor and current members. WHEN a member is removed, THE system SHALL withdraw open-Project access and SHALL restore read-only access only after that Project completes or is cancelled. |
| AUTH-007 | THE system SHALL keep Admin Project access read-only, and SHALL NOT let it imply commenting, membership, leadership, Task, or status authority. |
| AUTH-008 | THE system SHALL permit a Mentor to view a Task, comment on it, and read its retained history. THE system SHALL refuse a Mentor's attempt to create, assign, reassign, edit, or soft-delete a Task. WHILE a Project is `ACTIVE`, THE system SHALL permit its owning Mentor only the block, unblock, and reopen transitions of `TSK-023`, and SHALL refuse every other status change by a Mentor. The automatic transfer performed by direct Mentor removal is a guarded Project-domain operation rather than Task-management authority. |
| AUTH-009 | WHILE a Project is open, THE system SHALL permit its active members to view every non-deleted Task, assignee, status, aggregate progress, comment thread, and authorized history entry in that Project, and to comment on any non-deleted Task. |
| AUTH-011 | WHEN an invitation, membership-exit, exit-transfer batch, self-Task, Task-definition, or history-read operation is requested, THE system SHALL authorize it inside the transaction or read boundary from the authenticated user, the owning Project, active membership, pending-exit state, the issuing or current leadership term, the Task creator or current assignee, and aggregate state. WHERE the identifier is cross-Project or stale, THE system SHALL disclose no protected record and SHALL commit no part of the change. |

Feature contracts: [Project invitations](features/invitations/SPEC.md), [Membership exit and transfer](features/membership-exit/SPEC.md), [Work logs and effort](features/work-logs-and-effort/SPEC.md), [Project lifecycle](features/lifecycle/SPEC.md), [Task management](features/task-management/SPEC.md).

### Notifications (from notification §12.2)

| ID | Requirement |
|---|---|
| NOT-003 | WHEN a Task comment is added or a Task status changes, THE system SHALL create an in-app notification only. WHEN someone other than the assignee blocks, unblocks, or reopens a Task, THE system SHALL notify the assignee, and WHERE that actor is the owning Mentor, SHALL also notify the current Leader. |
| NOT-010 | WHEN an invitation is created, THE system SHALL notify the invitee. WHEN it is answered, THE system SHALL notify the issuing Leader and the owning Mentor. WHEN it is revoked or superseded, THE system SHALL notify the invitee, the issuing Leader, and the owning Mentor, collapsing duplicate recipients. WHEN a Leader requests a removal, THE system SHALL notify the owning Mentor and the target; WHEN a member requests their own leave, THE system SHALL notify the owning Mentor and the current Leader; WHEN such a request is decided or cancelled, THE system SHALL notify the requester, the target, and the current Leader, collapsing duplicate recipients. WHEN a member creates a self-Task, THE system SHALL send no notification. |

### Use cases

#### UC-05 — Manage a Project, membership, and leadership

**Parts P1/P2/P4, with P5 Mentor interventions.**

| Field | Specification |
|---|---|
| Primary actor(s) | Owning Mentor |
| Trigger | A Mentor creates or manages an owned Project. |
| Preconditions | The Mentor is active and owns the Project for every operation after creation. |
| Postconditions | Intervals and historical attribution remain intact; a completed Project is terminal and read-only. |
| Traced requirements | PRJ-001–PRJ-024, AUTH-003–AUTH-011, TSK-023, TSK-025 |

**Main success flow**

1. Create a PLANNED Project, initial Leader membership, and first leadership term atomically.
2. Directly add eligible active Intern members when needed.
3. Keep exactly one current Leader throughout PLANNED and ACTIVE.
4. Activate the Project when membership, Leader, dates, and assignees are valid.
5. Inspect Task progress and comment; on an ACTIVE Project, block, unblock or reopen under `TSK-023`, without Task-definition authority.
6. Decide membership exits through the approved transfer workflow.
7. Complete the Project only after every non-deleted Task is DONE.

**Alternatives and exceptions**

- A Project may start in the past, today, or in the future; an end date before its start is refused, and a past start does not open Task work before a member joined.
- Leadership reassignment leaves the former Leader’s Task assignments unchanged.
- Direct removal moves the member's unfinished Tasks to the current Leader, or to the replacement when the member leads, and is refused while the member owns an unfinished Task that already carries work logs.
- Removing or approving leave for the current Leader requires a replacement first.
- Project completion revokes pending invitations and supersedes pending exit requests.
- An empty PLANNED Project may be deleted by its owning Mentor. A PLANNED Project with history, or an ACTIVE one, is cancelled instead: the Mentor gives a reason, members are released and notified, and the Project stays read-only.

#### UC-06 — Manage Tasks as current Project Leader

**Parts P5/P6.**

| Field | Specification |
|---|---|
| Primary actor(s) | Current Project Leader |
| Trigger | The current Leader creates, edits, assigns, reassigns, or soft-deletes a Task. |
| Preconditions | The Leader has a current leadership term and active membership in a non-completed Project. |
| Postconditions | The Task definition or assignment changes without falsifying creator or assignee-controlled history. |
| Traced requirements | TSK-001–TSK-025, AUTH-004–AUTH-011 |

**Main success flow**

1. Create or edit a Task with one eligible active same-Project assignee.
2. Choose an optional due date inside Project dates and not on a current global day off.
3. Set, replace, or clear an optional estimate before the Task's first work log.
4. Reassign an unfinished Task while preserving creator, status, comments, and work logs, recording a Remaining effort forecast when the Task already carries work.
5. On an ACTIVE Project, block a team member's Task, unblock it back to the status it had, or reopen a DONE Task with a reason for the assignee to correct.
6. Record a new Remaining effort forecast whenever the effort still needed changes.
7. Inspect status counts, progress, logged minutes, and per-member work.

**Alternatives and exceptions**

- A DONE Task must be reopened before reassignment.
- The Leader cannot start another member's Task or mark it DONE.
- A member targeted by a pending exit cannot receive a new or reassigned Task.
- Once a work log is retained, the estimate cannot change.
- A stale leadership term or guessed Project/Task identifier is denied.
- Soft deletion removes the Task from current lists and progress but preserves history.

#### UC-07 — Perform assigned Project work

**Parts P5/P6.**

| Field | Specification |
|---|---|
| Primary actor(s) | Current Task assignee; active Project member; owning Mentor for comments only |
| Trigger | A user opens a Task, comments, changes assigned status, or records work. |
| Preconditions | The Project and membership/context authorize the selected action. |
| Postconditions | Task creator, status, comments, and work logs retain separate, attributable history. |
| Traced requirements | TSK-007–TSK-019 |

**Main success flow**

1. View every non-deleted Task and comment in an authorized Project.
2. As an active member, optionally create a Task assigned only to self.
3. As current assignee, follow the fixed Task status graph.
4. As current assignee, add a dated 1–1440 minute work entry.
5. As an active member, Leader, or owning Mentor, append a Task comment.
6. View aggregate progress appropriate to the user’s authorization.

**Alternatives and exceptions**

- The daily total across all Projects cannot exceed 1440 minutes.
- Task work on a global day off is allowed and never creates attendance.
- A former assignee may correct their own earlier log while the Project is active and membership remains current.

#### UC-13 — Respond to a Project invitation

Canonical workflow: [feature contract](features/invitations/SPEC.md).

#### UC-14 — Request and decide Project membership exit

Canonical workflow: [feature contract](features/membership-exit/SPEC.md).

## 4. Non-functional Requirements

System-wide non-functional rules apply unchanged: architecture §3 (`ARC`), authentication and security §13 (`SEC`), interface and accessibility §15 (`UI`), delivery §16 (`OPS`), and test evidence §17 (`TST`), all in the [platform spec](../platform/MODULE.md).

## 5. Data

All these records belong to the project module, including Tasks. The parts below identify
which behavior changes them; sharing a table does not create another owner or service.

| Part | Records it controls or uses | Inputs and dependencies |
|---|---|---|
| P1 | `projects`; closes membership/leadership intervals and pending P3/P4 records on terminal transitions | P2 initial Leader; P5 Task readiness/progress; identity Mentor state; reporting reads retained results |
| P2 | `project_memberships`, `project_leadership_terms` | Identity account and internship eligibility; P1 lifecycle; P3 supersession on direct add; P4 direct-removal guards |
| P3 | `project_invitations`; creates a P2 membership on acceptance | P1 open Project; P2 current issuing Leader and membership; identity/internship eligibility; notification delivery |
| P4 | `project_membership_exit_requests`; closes a P2 membership and changes P5 assignments | P2 replacement Leader; P5 unfinished Tasks and eligible recipients; P6 forecast for worked transfers; notification delivery |
| P5 | `tasks`, `task_comments`, `task_status_transitions` | P1 lifecycle/dates; P2 membership/leadership; P4 pending-exit restrictions; calendar due-date eligibility; notification delivery |
| P6 | `task_work_logs`, `task_remaining_effort_forecasts`; estimate on `tasks` | P5 current assignment/status; P2 membership interval; P4 worked-transfer transaction; platform per-Intern daily limit |

These dependencies describe facts and coordinated transactions, not a new module dependency
graph. `ARC-005` and `ARC-006` still govern imports and the internship-owned lifecycle port.

### Checks across parts

| Boundary | Existing scenarios | Planning consequence |
|---|---|---|
| P1 + P2 + P3 | `AC-PRJ-009`–`AC-PRJ-010` | Project creation includes the first Leader; invitation acceptance racing direct add commits only one membership. |
| P2 + P4 + P5 + P6 | `AC-PRJ-012`–`AC-PRJ-013`, `AC-TSK-013`–`AC-TSK-014` | Replace a departing Leader before transfer; commit each worked transfer with its forecast; a later rejected exit keeps transfers. No isolated P4 plan can defer these guards. |
| P1 → P3/P4/P5/P6 | `AC-PRJ-007`, `AC-PRJ-015`, `AC-AUTH-007` | Completion/cancellation retains history, resolves pending requests and stops mutation; a cancelled Task keeps its status and work. |
| P5 ↔ P6 | `AC-TSK-011`–`AC-TSK-013`, `AC-TSK-017` | Reassignment keeps attribution; the first retained log freezes the estimate; forecasts remain append-only and current values use the latest effective forecast. |
| P1 → internship completion/withdrawal | [AC-ACC-018](../internship/features/lifecycle/SPEC.md#canonical-acceptance-scenarios), [AC-ACC-019](../internship/features/lifecycle/SPEC.md#canonical-acceptance-scenarios) | `D35`/`D36`: exclude soft-deleted Tasks and unfinished Tasks in CANCELLED Projects from readiness; preserve history, terminal immutability and all other guards. |
| All parts → history and access | `AC-AUTH-003`–`AC-AUTH-007`, `AC-AUTH-010`, `AC-DB-002`–`AC-DB-003`, `AC-DB-007` | Re-evaluate stored scope at the transaction/read boundary; preserve one shared authorization and integrity contract. |

The conceptual model, the table inventory, the integrity rules (`DB`), and both diagrams are §19 of the [platform spec](../platform/MODULE.md).

## 6. Error Handling

Rules in section 3 whose text names a refusal, rejection, denial, or failure: `PRJ-002`, `PRJ-008`, `PRJ-009`, `PRJ-010`, `PRJ-013`, `PRJ-021`, `PRJ-022`, `PRJ-024`, `TSK-005`, `TSK-008`, `TSK-009`, `TSK-014`, `TSK-015`, `TSK-016`, `TSK-019`, `TSK-020`, `TSK-022`, `TSK-023`, `AUTH-008`, `DB-020`.

System-wide failure behavior is §21 (`ERR-001`–`ERR-007`), and the interface message families are Appendix F, both in the [platform spec](../platform/MODULE.md).

## 7. Acceptance Criteria

Scenarios from the §20 acceptance catalogue whose identifiers start with `AC-PRJ`, `AC-TSK`, `AC-AUTH` or `AC-DB`. The catalogue's introduction, including the rules deliberately written without a scenario, is in the [platform spec](../platform/MODULE.md).

### P1 — Project lifecycle

Feature contracts: [Project lifecycle](features/lifecycle/SPEC.md).

### P2 — Membership and leadership

Feature contracts: [Membership and leadership](features/membership-and-leadership/SPEC.md).

### P3 — Invitations

Feature contracts: [Project invitations](features/invitations/SPEC.md).

### P4 — Exits and transfers

Feature contracts: [Membership exit and transfer](features/membership-exit/SPEC.md).

### P5 — Task definition, comments and status

Feature contracts: [Task management](features/task-management/SPEC.md).

### P6 — Work logs and effort

Feature contracts: [Work logs and effort](features/work-logs-and-effort/SPEC.md).

### Authorization and integrity across parts

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-AUTH-003 | AUTH-006–AUTH-007 | Admin opens a Project and its History tab | Admin sees read-only Project/tasks/progress and retained history but cannot comment, assign, change status, or manage membership. |
| AC-AUTH-007 | AUTH-006 | Removed member opens the Project while open, then after completion or cancellation in separate cases | Open-Project access is denied after removal; after either terminal transition the former member receives authorized read-only Project/Task history and no mutation is accepted. |
| AC-AUTH-004 | AUTH-008, TSK-023 | Owning Mentor opens a Task on an `ACTIVE` Project, then one on a `PLANNED` Project | Mentor can view and comment; every Task definition mutation is denied; on the `ACTIVE` Project block, unblock, and reopen succeed while starting the Task and marking it `DONE` are denied; on the `PLANNED` Project every status change is denied. |
| AC-AUTH-005 | AUTH-004–AUTH-005, TSK-023 | Leader opens one assigned and one unassigned Task on an `ACTIVE` Project | Leader manages definitions for both; on the assigned Task every `TSK-007` transition and work logging succeed; on the unassigned Task only block, unblock, and reopen succeed, and starting it, marking it `DONE`, and logging work are denied. |
| AC-AUTH-006 | AUTH-005, AUTH-009 | Ordinary member opens Project Tasks | Member sees and comments on all Tasks; status/log controls exist only on their assigned Task. |
| AC-AUTH-010 | AUTH-011 | A stale former Leader or unrelated member submits an invitation, exit, self-Task, or Task-management request by direct identifier | Authorization is re-evaluated inside the transaction; the request is denied without existence leakage or partial mutation. |
| AC-DB-002 | DB-011–DB-012 | SQL probes attempt duplicate pending invitations/exits, cross-Project references, invalid request participants, and unsupported resolution combinations | PostgreSQL rejects each invalid row while valid accepted/revoked/superseded and approved/rejected/cancelled histories commit. |
| AC-DB-003 | DB-013 | SQL probes insert a Task estimate of 0, one of 527 041, a forecast whose Task and membership belong to different Projects, a forecast with negative lifetime-actual, and an attempt to update an existing forecast row | Each is rejected by a constraint; estimates accept `NULL` and the inclusive bounds 1 and 527 040; forecasts accept inserts only, and no derived total column exists on any table. |
| AC-DB-007 | DB-019–DB-020 | SQL probes cancel a Project without a reason, record a reopen transition without a reason, and update and delete an existing Task transition | Each is refused by the database; a cancellation with Mentor, time and reason, and a block transition without a reason, both commit. |

## 8. Out of Scope

System-wide exclusions are §1.3 of the [platform spec](../platform/MODULE.md): `GOV-007` through `GOV-010` and `GOV-015`.

Exclusions stated inside this spec's own rules: `TSK-008`, `TSK-011`.

## Notes / Open Questions

The feature split is organizational; read the feature-specific notes as well as the shared
notes below. No question affecting this module is open (`D38`). Should one be recorded here
later, this module and every feature it affects return to the inherited-baseline status.


### Clarifications needed before the affected plan is approved

The approved rule baseline remains in force. These questions identify what that baseline
does not yet settle; neither a part label nor a passing document check supplies an answer.
The maintainer decides, and [plan.md](../../../plan.md) alone tracks the follow-up.

| Affected part | Unclear point and choices | Consequence of an assumption |
|---|---|---|
| None pending | All earlier Project planning clarifications are settled in D12, D35, D36, and D37. | Dependent technical plans may proceed under approved feature contracts. |

### Existing decisions and design notes

- `D37`: assignee Task status transitions require a non-deleted Task (`TSK-007`); AC-TSK-021 covers rejection of status requests on soft-deleted Tasks; `DB-011` and `DB-012` constrain invitation and exit-request status sets matching `V1__baseline.sql` with invitation resolution codes aligned to revoked status; state transition tables for Project invitations and exit requests are established with public Azure/Jira comparison points.
- `D35`: new Tasks start at TODO; every authorized actor unblocks to the latest pre-block state; unfinished Tasks in CANCELLED Projects do not block internship completion/withdrawal. Canonical rules and scenarios live in Task management and Internship lifecycle. These three former questions are settled. `D36` additionally excludes soft-deleted Tasks from internship readiness; the canonical predicate remains ACC-022.

- `D12`: only an empty `PLANNED` draft is deleted, with the notifications raised for it; any other Project that will not run is cancelled under `PRJ-023` and keeps its history. `ARCHIVED` was not added, because nothing yet gives it a meaning `COMPLETED` and `CANCELLED` lack. How cancelled Projects appear in reports is `RPT-003` and `RPT-011`.
- Deleting an empty draft leaves no audit record, since `GOV-009` forbids generic domain events.
- `D13` and `D15`: status changes follow role, scope, current status, and target status (`TSK-023`); unblocking restores the earlier status and every block, unblock, and reopen is recorded, a reopen with its reason (`TSK-025`). The estimate is the baseline and the current Remaining effort comes from the latest effective forecast (`TSK-021`, `TSK-024`).
- A forecast recorded outside a reassignment can use the existing forecast columns with the current assignee and assignment start; the implementation plan confirms it.
