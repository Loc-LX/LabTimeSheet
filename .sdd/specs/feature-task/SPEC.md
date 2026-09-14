# Task Spec

**Version:** 1.0.0 · **Owner:** Loc-LX · **Status:** APPROVED · **Date:** 2026-09-14

Part of the Lab Timesheet specification. [`.sdd/requirements.md`](../../requirements.md) indexes every
spec, rule prefix, and original section number. Rules every feature shares, including the
glossary, the authorization model, the domain model, and failure handling, are in
[the platform spec](../feature-platform/SPEC.md). Section numbers marked `§` are the
numbers the rules carried in the single-file specification and are kept so existing
references still resolve.

## 1. Context & Goal

Single-assignee Tasks inside a Project, with comments, work logs, and effort planning, part of the third area named by the product objective (§1.2 of the platform spec).
In code this is `feature/task`: Tasks, comments, work logs, exit transfers, and Remaining effort forecasts.

## 2. Actors & Roles

Primary actors named by this feature's use cases:

- **UC-06 — Manage Tasks as current Project Leader:** Current Project Leader
- **UC-07 — Perform assigned Project work:** Current Task assignee; active Project member; owning Mentor for comments only

Every capability by role is in the permission matrix, [platform spec](../feature-platform/SPEC.md) §5.2. Authorization is resolved from stored context, never from the global role alone (§5.1).

## 3. Functional Requirements

### §7. Tasks, comments, and task work

#### §7.1 Task lifecycle

| ID | Requirement |
|---|---|
| TSK-001 | THE system SHALL give each Task exactly one Project and exactly one current assignee, referencing a membership in that same Project. |
| TSK-002 | THE system SHALL permit one Intern to be the current assignee of several Tasks across several Projects. |
| TSK-003 | THE system SHALL permit the current Leader to create a Task assigned to any eligible active member of the same Project. THE system SHALL permit any other eligible active member to create a Task only WHERE its initial assignee is that creating membership. WHILE a member is the target of a pending exit, THE system SHALL treat them as ineligible for a new assignment and for self-Task creation. |
| TSK-004 | THE system SHALL store, for each Task, a title, an optional description, a status, an optional due date, the current assignee, the creator membership, the current assignment actor and time, lifecycle timestamps, an optional deletion actor, and an optimistic-lock version. In an authorized view, THE system SHALL resolve the creator, the current or final assignee, comment authors, work-log authors, and the deletion actor to the retained human-readable identity. |
| TSK-005 | WHERE a due date is given, THE system SHALL require it to fall within the Project date range and SHALL refuse a date that is a currently configured global day off at the moment it is created or changed. |
| TSK-006 | WHEN an Admin creates a later global day off, THE system SHALL leave existing Task due dates unchanged, and SHALL identify the affected Tasks in the calendar preview so their Leader can reschedule them. |
| TSK-007 | THE system SHALL permit only the current assignee to change a Task status, and SHALL permit only these transitions: `TODO → IN_PROGRESS|BLOCKED`, `IN_PROGRESS → DONE|BLOCKED`, `BLOCKED → TODO|IN_PROGRESS`, and `DONE → IN_PROGRESS`. |
| TSK-008 | WHERE any other status transition is requested, THE system SHALL reject it. THE system SHALL NOT provide a configurable workflow engine in v1. |
| TSK-009 | THE system SHALL permit only the current Leader to reassign an unfinished Task, singly or within an exit-transfer batch, and SHALL require every target to be an eligible active current member who is not the target of a pending exit. WHEN a reassignment commits, THE system SHALL update the current assignment actor and time while preserving creator attribution, status, comments, work logs, and applicable lifecycle timestamps. WHILE a Task is `DONE`, THE system SHALL refuse transfer and reassignment until its current assignee reopens it to `IN_PROGRESS`. |
| TSK-010 | THE system SHALL permit the current Leader to edit or soft-delete any unfinished Task in the Project. THE system SHALL permit a non-Leader creator to edit or soft-delete an unfinished Task only WHILE the creator and the current assignee remain the same eligible active membership. WHEN a Task is soft-deleted, THE system SHALL exclude it from progress and ordinary lists and SHALL keep it visible in authorized history with its deletion attribution. |

#### §7.2 Comments and work logs

| ID | Requirement |
|---|---|
| TSK-011 | THE system SHALL store Task comments as separate append-only records, and SHALL NOT provide comment editing or deletion in v1. |
| TSK-012 | WHILE a Project is not completed, THE system SHALL permit its owning Mentor, its current Leader, and every active member to comment on any non-deleted Task in that Project. |
| TSK-013 | THE system SHALL permit only the current assignee to create a work log for a Task, and SHALL require each entry to carry a work date, minutes from 1 through 1440, and an optional nonblank note. |
| TSK-014 | THE system SHALL refuse a work date that is in the future, outside the Project dates, or outside the logging member's membership interval. |
| TSK-015 | THE system SHALL refuse a work log that would take an Intern's combined Task work across all Projects above 1440 minutes on one local date, and SHALL serialize that check on the Intern so that concurrent submissions cannot over-allocate. |
| TSK-016 | WHILE the Project is active and the author remains a member, THE system SHALL permit the log author to correct their own work log, even after the Task has been reassigned. THE system SHALL refuse any other user's attempt to edit that log. |
| TSK-017 | Day-off effects on Task activity are defined by `CAL-009`, and the separation of Task work from attendance by `GOV-004`. This entry exists so that a reader of the Task domain reaches those rules; it adds nothing of its own. |
| TSK-018 | WHEN a member creates a self-Task, THE system SHALL set the creator, the assignment actor, and the assignee to the authenticated membership in one transaction, and SHALL NOT raise a notification to that same member. The current Leader's broader creation authority SHALL remain scoped to the Project. |
| TSK-019 | THE system SHALL authorize Task definition from currently stored context: the current Leader MAY manage any unfinished Task, and an eligible member creator MAY manage only an unfinished Task still assigned to them. WHILE a member is the target of a pending exit, THE system SHALL refuse new and self-assignment to them without removing their existing assignee rights. WHEN a Task is reassigned away from its creator, THE system SHALL withdraw creator control while leaving historical creator attribution unchanged, and SHALL restore that control on reassignment back only WHERE creator and current assignee are equal and currently eligible. |
| TSK-020 | THE system SHALL permit a Task to carry one optional whole-Task estimate in integer minutes from 1 through 527040. THE system SHALL permit only the current Project Leader to set, replace, or clear it, and only before the first retained work log. WHEN the first work log is retained, THE system SHALL make the estimate immutable. THE system SHALL treat estimate mutation as separate from ordinary Task editing and SHALL reject it from any other actor. |
| TSK-021 | THE system SHALL compute Actual Task effort as the lifetime sum of retained work-log minutes across every author and assignment. WHERE a Task is `DONE` and carries an estimate, THE system SHALL compute variance as actual effort minus estimate. WHERE a Task carries an estimate and is unfinished or reopened, THE system SHALL leave variance undefined and render it as `Pending`. WHERE a Task carries no estimate, THE system SHALL leave variance undefined and render it as `N/A`. THE system SHALL NOT derive an efficiency or productivity score from any of these. |
| TSK-022 | WHERE an unfinished Task carrying retained work is reassigned, THE system SHALL require an append-only Remaining effort forecast from the current Leader containing the reassignment snapshot. THE system SHALL accept a correcting successor only before the incoming assignee's first newly created work log. WHILE a member owns a worked unfinished Task, THE system SHALL refuse direct Mentor removal, and SHALL NOT fabricate Leader provenance for a forecast. |

### Use cases

#### UC-06 — Manage Tasks as current Project Leader

| Field | Specification |
|---|---|
| Primary actor(s) | Current Project Leader |
| Trigger | The current Leader creates, edits, assigns, reassigns, or soft-deletes a Task. |
| Preconditions | The Leader has a current leadership term and active membership in a non-completed Project. |
| Postconditions | The Task definition or assignment changes without falsifying creator or assignee-controlled history. |
| Traced requirements | TSK-001–TSK-022, AUTH-004–AUTH-011 |

**Main success flow**

1. Create or edit a Task with one eligible active same-Project assignee.
2. Choose an optional due date inside Project dates and not on a current global day off.
3. Set, replace, or clear an optional estimate before the Task's first work log.
4. Reassign an unfinished Task while preserving creator, status, comments, and work logs, recording a Remaining effort forecast when the Task already carries work.
5. Inspect status counts, progress, logged minutes, and per-member work.

**Alternatives and exceptions**

- A DONE Task must be reopened by its current assignee before reassignment.
- A member targeted by a pending exit cannot receive a new or reassigned Task.
- Once a work log is retained, the estimate cannot change.
- A stale leadership term or guessed Project/Task identifier is denied.
- Soft deletion removes the Task from current lists and progress but preserves history.

#### UC-07 — Perform assigned Project work

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

## 4. Non-functional Requirements

System-wide non-functional rules apply unchanged: architecture §3 (`ARC`), authentication and security §13 (`SEC`), interface and accessibility §15 (`UI`), delivery §16 (`OPS`), and test evidence §17 (`TST`), all in the [platform spec](../feature-platform/SPEC.md).

## 5. Data

Tables this feature's entities map to: `tasks`, `task_comments`, `task_work_logs`, `task_remaining_effort_forecasts`.

The conceptual model, the table inventory, the integrity rules (`DB`), and both diagrams are §19 of the [platform spec](../feature-platform/SPEC.md).

## 6. Error Handling

Rules in section 3 whose text names a refusal, rejection, denial, or failure: `TSK-005`, `TSK-008`, `TSK-009`, `TSK-014`, `TSK-015`, `TSK-016`, `TSK-019`, `TSK-020`, `TSK-022`.

System-wide failure behavior is §21 (`ERR-001`–`ERR-007`), and the interface message families are Appendix F, both in the [platform spec](../feature-platform/SPEC.md).

## 7. Acceptance Criteria

Scenarios from the §20 acceptance catalogue whose identifiers start with `AC-TSK`. The catalogue's introduction, including the rules deliberately written without a scenario, is in the [platform spec](../feature-platform/SPEC.md).

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-TSK-001 | TSK-001, DB-004 | Leader attempts to assign a membership from another Project | Application and composite foreign key reject it. |
| AC-TSK-002 | TSK-005–TSK-006 | Leader chooses due date outside Project or on current day off | Save is rejected; a later Admin day-off addition leaves existing due date unchanged but flags impact. |
| AC-TSK-003 | TSK-007–TSK-008 | Assignee attempts every allowed and forbidden status edge | Four specified edge groups succeed; all other edges and all non-assignee attempts fail. |
| AC-TSK-004 | TSK-009 | Leader tries to reassign DONE Task | Reassignment fails until current assignee reopens it; later reassignment preserves IN_PROGRESS and old logs. |
| AC-TSK-005 | TSK-010, UI-019 | Leader soft-deletes an unfinished Task | Task disappears from normal lists/progress, remains visible in authorized Project History with deletion actor/time, and is not physically deleted. |
| AC-TSK-006 | TSK-011–TSK-012 | Member and Mentor comment; then try to edit/delete comments | Authorized creates succeed; edit/delete routes do not exist or deny mutation. |
| AC-TSK-007 | TSK-013–TSK-016 | Assignee logs work, Task is reassigned, former assignee corrects own prior log | Log remains attributed; correction succeeds only while former assignee is still active member and Project active. |
| AC-TSK-008 | TSK-015, DB-008 | Concurrent logs would total 1,441 minutes across Projects | Serialization permits at most totals through 1,440; one transaction is rejected without partial write. |
| AC-TSK-009 | GOV-004, TSK-017 | Intern logs work on global day off without attendance | Log succeeds; no attendance record or implied presence is created. |
| AC-TSK-010 | TSK-003–TSK-004, TSK-018 | Ordinary member creates a Task for self, then attempts to create one for another member | Self-Task stores creator/assigner/assignee as the authenticated membership and sends no self-notification; other-assignee creation is denied. |
| AC-TSK-011 | TSK-004, TSK-009–TSK-010, TSK-019 | Leader reassigns an unfinished member-created Task away, then back to its creator and opens Task history | Creator attribution, status, comments, work logs, and assignment actor/time survive; creator controls follow current eligibility/assignment; the UI shows only the current/final assignee and does not invent previous-assignee or status/edit timelines. |
| AC-TSK-012 | TSK-020–TSK-021 | Leader creates and edits an estimate before work, then a retained log is created and the Task is completed | Valid optional estimate persists, ordinary-member forged mutation is denied, first retained log freezes the estimate, lifetime actual spans authors/assignments, and DONE variance is signed actual minus estimate only. |
| AC-TSK-013 | TSK-022 | Leader reassigns worked and unworked unfinished Tasks, then corrects the latest forecast | Worked reassignment requires an atomic forecast snapshot; unworked reassignment rejects unsolicited forecast; correction appends one successor before the incoming member's first new log and rejects stale/late/unauthorized corrections. |
| AC-TSK-014 | TSK-022, PRJ-008–PRJ-010 | Mentor attempts direct removal while the target owns worked unfinished Tasks | Removal is rejected before leadership, membership, assignment, request, or notification mutation; after Leader forecast-aware transfers, eligible unworked Tasks retain existing automatic transfer behavior. |
| AC-TSK-015 | TSK-002 | One Intern is an active member of two Projects and is made current assignee of Tasks in both, then of a second Task in the first Project | Every assignment succeeds; each Task keeps exactly one current assignee; the Intern's assignment count is not capped by Project membership. |

## 8. Out of Scope

System-wide exclusions are §1.3 of the [platform spec](../feature-platform/SPEC.md): `GOV-007` through `GOV-010` and `GOV-015`.

Exclusions stated inside this spec's own rules: `TSK-008`, `TSK-011`.

## Notes / Open Questions

- **Open, `D13`.** Since 24 August 2026 the code lets an owning Mentor change a Task's status on an `ACTIVE` Project (`TaskService#changeStatus`, two tests, and `docs/tests/web/task-pages.md`). `AUTH-008`, `TSK-007`, the §5.2 matrix, and `AC-AUTH-004` refuse it. Which one is intended is not decided.
