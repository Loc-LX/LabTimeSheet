# Task management Spec

**Version:** 1.2.0 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-21

**Module:** `project` · **Shared contract:** [MODULE.md](../../MODULE.md)

## 1. Context & Goal

Create and maintain Task definition, assignment, comments and permitted status transitions.

This is one cohesive planning scope. Operations below are sections of this feature,
not separate modules or mandatory branches. The original split changed document ownership only; `D35` records the subsequent approved behavior decisions.

## 2. Actors & Roles

Eligible member/assignee; current Leader; owning Mentor only for the explicitly permitted interventions.

The [platform permission matrix](../../../platform/features/authorization/SPEC.md#52-permission-matrix)
and the module's shared authorization rules apply to every operation.

## 3. Functional Requirements

The operation summaries below are navigation. The numbered rules remain authoritative;
they do not acquire additional behavior from a heading or summary.

### Create, edit and soft-delete

TSK rules distinguish Leader management from eligible self-created Tasks and preserve creator/deletion attribution.

### Assign and reassign

TSK-009 and TSK-019 preserve history and apply membership/exit eligibility at the time of the change.

### Start, finish, block, unblock and reopen

TSK-007, TSK-008, TSK-023 and TSK-025 constrain actor, source/target status and retained transition evidence.

### Comment

TSK-011 and TSK-012 govern append-only comments and participant scope.

### Canonical feature rules

| ID | Requirement |
|---|---|
| TSK-001 | THE system SHALL give each Task exactly one Project and exactly one current assignee, referencing a membership in that same Project. |
| TSK-002 | THE system SHALL permit one Intern to be the current assignee of several Tasks across several Projects. |
| TSK-003 | THE system SHALL permit the current Leader to create a Task assigned to any eligible active member of the same Project. THE system SHALL permit any other eligible active member to create a Task only WHERE its initial assignee is that creating membership. WHILE a member is the target of a pending exit, THE system SHALL treat them as ineligible for a new assignment and for self-Task creation. WHEN a Task is created, THE system SHALL set its initial status to `TODO`, both on a `PLANNED` and on an `ACTIVE` Project, and SHALL refuse a creation request specifying any other initial status. |
| TSK-004 | THE system SHALL store, for each Task, a title, an optional description, a status, an optional due date, the current assignee, the creator membership, the current assignment actor and time, lifecycle timestamps, an optional deletion actor, and an optimistic-lock version. In an authorized view, THE system SHALL resolve the creator, the current or final assignee, comment authors, work-log authors, and the deletion actor to the retained human-readable identity. |
| TSK-005 | WHERE a due date is given, THE system SHALL require it to fall within the Project date range and SHALL refuse a date that is a currently configured global day off at the moment it is created or changed. |
| TSK-006 | WHEN an Admin creates a later global day off, THE system SHALL leave existing Task due dates unchanged, and SHALL identify the affected Tasks in the calendar preview so their Leader can reschedule them. |
| TSK-007 | THE system SHALL permit only these Task status transitions: `TODO → IN_PROGRESS|BLOCKED`, `IN_PROGRESS → DONE|BLOCKED`, `BLOCKED → TODO|IN_PROGRESS`, and `DONE → IN_PROGRESS`. WHILE the Project is `ACTIVE`, THE system SHALL permit the current assignee these transitions on their own non-deleted Task, subject to `TSK-025`: an unblock SHALL restore only the status immediately before the latest block, and a reopen SHALL require a nonblank reason. Which transitions any other actor may make is decided by `TSK-023`. |
| TSK-008 | WHERE any other status transition is requested, THE system SHALL reject it. THE system SHALL NOT provide a configurable workflow engine in v1. |
| TSK-009 | THE system SHALL permit only the current Leader to reassign an unfinished Task, singly or within an exit-transfer batch, and SHALL require every target to be an eligible active current member who is not the target of a pending exit. WHEN a reassignment commits, THE system SHALL update the current assignment actor and time while preserving creator attribution, status, comments, work logs, and applicable lifecycle timestamps. WHILE a Task is `DONE`, THE system SHALL refuse transfer and reassignment until it is reopened to `IN_PROGRESS` under `TSK-007` or `TSK-023`. |
| TSK-010 | THE system SHALL permit the current Leader to edit or soft-delete any unfinished Task in the Project. THE system SHALL permit a non-Leader creator to edit or soft-delete an unfinished Task only WHILE the creator and the current assignee remain the same eligible active membership. WHEN a Task is soft-deleted, THE system SHALL exclude it from progress and ordinary lists and SHALL keep it visible in authorized history with its deletion attribution. |
| TSK-011 | THE system SHALL store Task comments as separate append-only records, and SHALL NOT provide comment editing or deletion in v1. |
| TSK-012 | WHILE a Project is not completed, THE system SHALL permit its owning Mentor, its current Leader, and every active member to comment on any non-deleted Task in that Project. |
| TSK-018 | WHEN a member creates a self-Task, THE system SHALL set the creator, the assignment actor, and the assignee to the authenticated membership in one transaction, and SHALL NOT raise a notification to that same member. The current Leader's broader creation authority SHALL remain scoped to the Project. |
| TSK-019 | THE system SHALL authorize Task definition from currently stored context: the current Leader MAY manage any unfinished Task, and an eligible member creator MAY manage only an unfinished Task still assigned to them. WHILE a member is the target of a pending exit, THE system SHALL refuse new and self-assignment to them without removing their existing assignee rights. WHEN a Task is reassigned away from its creator, THE system SHALL withdraw creator control while leaving historical creator attribution unchanged, and SHALL restore that control on reassignment back only WHERE creator and current assignee are equal and currently eligible. |
| TSK-023 | THE system SHALL decide every Task status change from the actor's role, the actor's scope, the Task's current status, and the target status, and SHALL NOT infer from a higher role that an actor may set any status. WHILE a Project is `ACTIVE`, THE system SHALL permit the current Leader, on any non-deleted Task of the Project they lead, and the owning Mentor, on any non-deleted Task of their Project, to block a Task (`TODO` or `IN_PROGRESS` → `BLOCKED`), to unblock it back to the status it held immediately before it was blocked, and to reopen it (`DONE` → `IN_PROGRESS`) so that its assignee can correct it. THE system SHALL refuse a Leader's or Mentor's attempt to unblock a Task to any other status, to start another member's Task, or to mark it `DONE`, and SHALL refuse every status change by an Admin. |
| TSK-025 | WHEN a Task is blocked, unblocked, or reopened, THE system SHALL retain a transition record holding the actor, the server time, the previous status, the new status, and, for a reopen, a nonblank reason the actor must supply. For every authorized actor, including the current assignee, THE system SHALL take the status a Task returns to on unblock from the record of its latest block and SHALL refuse any other target. A later change from that restored status SHALL be a separate authorized transition under `TSK-007` or `TSK-023`. THE system MAY show a reopen reason as a Task comment, but the transition record SHALL remain its authoritative copy. |
| DB-020 | THE schema SHALL store every Task block, unblock and reopen as an append-only transition carrying the previous status, the new status, the actor, the server time and, for a reopen, a nonblank reason, and SHALL refuse an update or deletion of such a transition. |



#### State transitions: Task

Creation follows TSK-003. Every status change requires an ACTIVE Project, a non-deleted
Task and current authorization under TSK-007/TSK-023. The table adds no permissions.

| From | Action | To | Who | Rules |
|---|---|---|---|---|
| (none) | create on a PLANNED or ACTIVE Project | `TODO` | eligible creator under TSK-003 | `TSK-003`, `PRJ-013` |
| `TODO` | start | `IN_PROGRESS` | current assignee | `TSK-007` |
| `TODO`, `IN_PROGRESS` | block and record the previous status | `BLOCKED` | current assignee, current Leader or owning Mentor | `TSK-007`, `TSK-023`, `TSK-025` |
| `BLOCKED` | unblock only if the latest block came from TODO | `TODO` | current assignee, current Leader or owning Mentor | `TSK-007`, `TSK-023`, `TSK-025` |
| `BLOCKED` | unblock only if the latest block came from IN_PROGRESS | `IN_PROGRESS` | current assignee, current Leader or owning Mentor | `TSK-007`, `TSK-023`, `TSK-025` |
| `IN_PROGRESS` | finish | `DONE` | current assignee | `TSK-007` |
| `DONE` | reopen with a nonblank reason | `IN_PROGRESS` | current assignee, current Leader or owning Mentor | `TSK-007`, `TSK-023`, `TSK-025` |

## 4. Non-functional Requirements

Inherit [module constraints](../../MODULE.md#4-non-functional-requirements) and
[platform constraints](../../../platform/MODULE.md#4-non-functional-requirements),
including authorization, history, concurrency, server time and test evidence.

## 5. Data

`tasks`, `task_comments`, `task_status_transitions`; work logs and effort forecasts belong to the adjacent feature.

The [module data contract](../../MODULE.md#5-data) retains ownership, constraints and
checks across features. The full physical model stays in platform §19. A feature folder
does not grant repository/entity access across the module boundaries of ARC-006.

### Required contracts

These are required facts or behaviors, not prescribed cross-module imports or an
automatic implementation order. PLAN resolves delivery predecessors against what
already exists and preserves ARC-005/ARC-006.

| Provider | Required fact or behavior | Rule trace |
|---|---|---|
| [project/lifecycle](../lifecycle/SPEC.md) | Project mutation state and date bounds | [PRJ-002](../lifecycle/SPEC.md), [PRJ-023](../lifecycle/SPEC.md), [PRJ-024](../lifecycle/SPEC.md) |
| [project/membership-and-leadership](../membership-and-leadership/SPEC.md) | Leader and eligible current memberships | [PRJ-005](../membership-and-leadership/SPEC.md), [PRJ-017](../membership-and-leadership/SPEC.md) |
| [project/membership-exit](../membership-exit/SPEC.md) | Pending-exit assignment restriction | [PRJ-021](../membership-exit/SPEC.md) |
| [project/work-logs-and-effort](../work-logs-and-effort/SPEC.md) | Worked-transfer forecast | [TSK-022](../work-logs-and-effort/SPEC.md) |
| [calendar/global-calendar](../../../calendar/features/global-calendar/SPEC.md) | Current day-off constraint on due dates | [CAL-009](../../../calendar/features/global-calendar/SPEC.md) |

### Related workflows and joint checks

- [Project lifecycle](../lifecycle/SPEC.md): Project status gates mutations.
- [Membership and leadership](../membership-and-leadership/SPEC.md): Stored participant context determines capability.
- [Membership exit and transfer](../membership-exit/SPEC.md): Pending exit changes assignment eligibility.
- [Work logs and effort](../work-logs-and-effort/SPEC.md): Worked-task transfer and estimates cross this boundary.

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
| [Create, edit and soft-delete](#create-edit-and-soft-delete) | Eligible creator or Leader manages only a permitted unfinished Task | [TSK-001](SPEC.md), [TSK-002](SPEC.md), [TSK-003](SPEC.md), [TSK-004](SPEC.md), [TSK-005](SPEC.md), [TSK-006](SPEC.md), [TSK-010](SPEC.md), [TSK-018](SPEC.md), [TSK-019](SPEC.md) | [AC-TSK-001](SPEC.md), [AC-TSK-002](SPEC.md), [AC-TSK-005](SPEC.md), [AC-TSK-010](SPEC.md), [AC-TSK-011](SPEC.md), [AC-TSK-019](SPEC.md) | `D35` fixes creation at TODO; AC-TSK-019 checks both eligible creation paths and rejects another initial status. Keep ownership, due-date and soft-delete history negatives. |
| [Assign and reassign](#assign-and-reassign) | Current Leader reassigns eligible unfinished work while preserving attribution | [TSK-009](SPEC.md), [TSK-019](SPEC.md) | [AC-TSK-001](SPEC.md), [AC-TSK-004](SPEC.md), [AC-TSK-011](SPEC.md), [AC-TSK-015](SPEC.md) | Coordinate the forecast for worked transfers and pending-exit target refusal. |
| [Start, finish, block, unblock and reopen](#start-finish-block-unblock-and-reopen) | Authorized actor performs only the permitted status edge with retained transition evidence | [TSK-007](SPEC.md), [TSK-008](SPEC.md), [TSK-023](SPEC.md), [TSK-025](SPEC.md) | [AC-TSK-003](SPEC.md), [AC-TSK-004](SPEC.md), [AC-TSK-016](SPEC.md), [AC-TSK-018](SPEC.md), [AC-TSK-020](SPEC.md), [AC-TSK-021](SPEC.md) | `D35` requires every authorized actor to restore the latest pre-block state; AC-TSK-020 covers the assignee and repeated blocks; AC-TSK-021 verifies soft-deleted status transitions are rejected. |
| [Comment](#comment) | Authorized participant appends a retained comment | [TSK-011](SPEC.md), [TSK-012](SPEC.md) | [AC-TSK-006](SPEC.md) | Exercise edit/delete refusal and the shared Project terminal-state guard. |

### Canonical acceptance scenarios

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-TSK-001 | TSK-001, DB-004 | Leader attempts to assign a membership from another Project | Application and composite foreign key reject it. |
| AC-TSK-002 | TSK-005–TSK-006 | Leader chooses due date outside Project or on current day off | Save is rejected; a later Admin day-off addition leaves existing due date unchanged but flags impact. |
| AC-TSK-003 | TSK-007–TSK-008, TSK-023, TSK-025 | On an ACTIVE Project, the assignee attempts the TSK-007 edges and a forbidden edge; the current Leader, owning Mentor, unrelated member and Admin try status changes as non-assignees | Assignee start, block, completion and reopen with a reason succeed; edges outside TSK-007 fail. The current Leader and owning Mentor may only block, restore the pre-block state or reopen with a reason under TSK-023 and TSK-025; the unrelated member and Admin are denied. The assignee also restores only the latest pre-block state; requesting another unblock target is refused without mutation. |
| AC-TSK-004 | TSK-009, TSK-023, TSK-025 | Leader tries to reassign a DONE Task, then an authorized actor reopens it with a reason | Reassignment fails while DONE; a reopen authorized under TSK-007 or TSK-023 records its reason under TSK-025. A later valid reassignment preserves IN_PROGRESS and old logs. |
| AC-TSK-005 | TSK-010, UI-019 | Leader soft-deletes an unfinished Task | Task disappears from normal lists/progress, remains visible in authorized Project History with deletion actor/time, and is not physically deleted. |
| AC-TSK-006 | TSK-011–TSK-012 | Member and Mentor comment; then try to edit/delete comments | Authorized creates succeed; edit/delete routes do not exist or deny mutation. |
| AC-TSK-010 | TSK-003–TSK-004, TSK-018 | Ordinary member creates a Task for self, then attempts to create one for another member | Self-Task stores creator/assigner/assignee as the authenticated membership and sends no self-notification; other-assignee creation is denied. |
| AC-TSK-011 | TSK-004, TSK-009–TSK-010, TSK-019 | Leader reassigns an unfinished member-created Task away, then back to its creator and opens Task history | Creator attribution, status, comments, work logs, and assignment actor/time survive; creator controls follow current eligibility/assignment; the UI shows only the current/final assignee and does not invent previous-assignee or status/edit timelines. |
| AC-TSK-015 | TSK-002 | One Intern is an active member of two Projects and is made current assignee of Tasks in both, then of a second Task in the first Project | Every assignment succeeds; each Task keeps exactly one current assignee; the Intern's assignment count is not capped by Project membership. |
| AC-TSK-016 | TSK-007, TSK-023, TSK-025 | On an `ACTIVE` Project the assignee, the current Leader, the owning Mentor, another member, and an Admin each attempt every `TSK-007` transition on one Task, then the same on a `PLANNED` Project | The assignee may make every transition subject to latest-pre-block restoration and the nonblank reopen reason under TSK-025; the Leader and the owning Mentor may block, unblock, and reopen only, and are refused starting the Task and marking it `DONE`; another member and the Admin are refused every change; on the `PLANNED` Project every status change is refused. |
| AC-TSK-018 | TSK-023, TSK-025, NOT-003 | On an `ACTIVE` Project the Leader blocks a `TODO` Task and an `IN_PROGRESS` Task, then unblocks both; the owning Mentor reopens a `DONE` Task first without a reason and then with one | The `TODO` Task returns to `TODO` and the `IN_PROGRESS` Task to `IN_PROGRESS`, and unblocking either to another status is refused; the reasonless reopen is refused; each block, unblock, and reopen leaves a transition record with actor, time, both statuses, and the reopen reason; the assignee receives an in-app notification for each, and the current Leader also for the Mentor's reopen. |
| AC-TSK-019 | TSK-003, TSK-018, PRJ-013 | In separate PLANNED and ACTIVE Projects, an eligible Leader creates an assigned Task and an eligible ordinary member creates a self-Task; repeat with each non-TODO initial status | Each valid creation starts at TODO with the existing ownership/attribution guarantees. Requests specifying IN_PROGRESS, BLOCKED or DONE as the initial status are refused without creating a Task. A PLANNED Task cannot start until its Project is ACTIVE. |
| AC-TSK-020 | TSK-007, TSK-008, TSK-023, TSK-025, DB-020 | On an ACTIVE Project, the current assignee blocks a TODO Task and attempts to unblock it to IN_PROGRESS, then restores TODO; separately block/restore an IN_PROGRESS Task; then start the first Task, block it again and try both targets | A TODO-origin block restores only TODO; an IN_PROGRESS-origin block restores only IN_PROGRESS. After the later block, the latest origin IN_PROGRESS wins over the earlier TODO origin. A wrong target changes neither Task nor transition history. Every successful block/unblock retains actor, server time and both statuses; starting after restoration is a separate authorized operation. Existing Leader/Mentor restrictions and reopen-reason requirements remain in force. |
| AC-TSK-021 | TSK-007, TSK-008, TSK-010, TSK-023 | On an ACTIVE Project, a Task is soft-deleted, and its current assignee directly requests a status transition (such as start, block, unblock, or complete), or a Leader/Mentor attempts a status transition on it | The transition request is rejected; the soft-deleted Task's status, attributes, and transition history remain unchanged, and no transition record is created. |

Shared and cross-feature scenarios in [MODULE.md](../../MODULE.md#7-acceptance-criteria)
also apply. Scenario ownership follows the behavior exercised, not every prerequisite
named by its Requirements cell. The original relocation preserved existing expected results; `D35` records the approved changes below.

## 8. Out of Scope

Inherit [module exclusions](../../MODULE.md#8-out-of-scope). Adjacent feature behavior
is a dependency, not a duplicated contract. `D35` changes only the stated behavior contracts. This revision adds no schema, dependency, PLAN.md or TASKS.md.

## Notes / Open Questions

`D35` settles initial status as TODO and requires latest-pre-block restoration for every authorized actor. These decisions are ready for technical planning; executable business validation still belongs to implementation. UC-06 and UC-07 remain in MODULE.md because both include work/forecast behavior.

Read [shared open questions](../../MODULE.md#notes--open-questions) before approving
the technical plan. [plan.md](../../../../../plan.md) is the only progress tracker.
