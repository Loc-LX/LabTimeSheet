# Project Spec

**Version:** 1.3.2 · **Owner:** Loc-LX · **Status:** APPROVED · **Date:** 2026-09-17

Part of the Lab Timesheet specification. Rules every feature shares, including the
glossary, the authorization model, the domain model, and failure handling, are in
[the platform spec](../feature-platform/SPEC.md). Section numbers marked `§` are the
numbers the rules carried in the single-file specification and are kept so existing
references still resolve.

## 1. Context & Goal

Mentor-owned Projects with contextual Intern leadership, and the single-assignee Tasks inside them with comments, work logs, and effort planning: the third area named by the product objective (§1.2 of the platform spec).
In code this is `feature/project`: Projects, membership intervals, leadership terms, invitations, and membership exits. Tasks, comments, work logs, exit transfers, and Remaining effort forecasts join it from `feature/task` in step 6 of `D28`.

## 2. Actors & Roles

Primary actors named by this feature's use cases:

- **UC-05 — Manage a Project, membership, and leadership:** Owning Mentor
- **UC-06 — Manage Tasks as current Project Leader:** Current Project Leader
- **UC-07 — Perform assigned Project work:** Current Task assignee; active Project member; owning Mentor for comments only
- **UC-13 — Respond to a Project invitation:** Current Project Leader; invited Intern; owning Mentor
- **UC-14 — Request and decide Project membership exit:** Current Leader; current Project member; owning Mentor

Every capability by role is in the permission matrix, [platform spec](../feature-platform/SPEC.md) §5.2. Authorization is resolved from stored context, never from the global role alone (§5.1).

## 3. Functional Requirements

### §6. Project, membership, and leadership

| ID | Requirement |
|---|---|
| PRJ-001 | WHEN an active Mentor creates a Project, THE system SHALL create the Project, one eligible initial Leader membership, and its first leadership term in a single transaction. THE system SHALL NOT commit a Project with no membership. |
| PRJ-024 | WHEN a Mentor creates a Project, THE system SHALL accept a start date in the past, today, or in the future, and SHALL refuse an end date earlier than the start date. THE system SHALL NOT treat a past start date as permission to record Task work before a member joined; the dates a work log may carry are set by `TSK-014`. |
| PRJ-002 | THE system SHALL permit only these Project transitions: `PLANNED → ACTIVE → COMPLETED`, and `PLANNED → CANCELLED` or `ACTIVE → CANCELLED` under `PRJ-023`. THE system SHALL NOT reopen a `COMPLETED` or `CANCELLED` Project. WHILE a Project is `PLANNED` and empty, holding exactly the one membership and one leadership term created with it and no Task (soft-deleted Tasks included), invitation, or exit request, THE system SHALL permit only its owning Mentor to delete it together with those rows and every notification raised for it. WHERE a Project is not empty, or is `ACTIVE`, `COMPLETED`, or `CANCELLED`, THE system SHALL refuse deletion and SHALL NOT offer a delete action. |
| PRJ-003 | THE system SHALL permit an Intern to hold active memberships in several Projects at once, and SHALL represent each membership explicitly with a join timestamp and an optional leave timestamp. |
| PRJ-004 | THE system SHALL permit only the owning Mentor to add or remove a member directly and to decide a membership exit. THE system SHALL permit the current Leader to invite an eligible Intern, request another member's removal, and redistribute unfinished Tasks away from a pending exit target. THE system SHALL permit an Intern to join only by accepting their own invitation, and to leave only after the owning Mentor approves. |
| PRJ-005 | WHILE a Project is `PLANNED` or `ACTIVE`, THE system SHALL maintain exactly one current Leader who is an active member of that Project. WHEN the Project completes, THE system SHALL close the final leadership term rather than delete it. |
| PRJ-006 | WHEN leadership changes, THE system SHALL close the current term and open a new term for another active member in one transaction, so that the Project never exposes two current Leaders and never exposes an active period without one. |
| PRJ-007 | WHEN leadership changes and nothing else, THE system SHALL NOT reassign any Task. The former Leader SHALL remain a normal member and SHALL retain assignee rights for Tasks still assigned to them. |
| PRJ-008 | WHILE an exit request targets the current Leader, THE system SHALL refuse Task transfer and exit approval until the owning Mentor appoints an eligible replacement. WHEN the replacement is appointed, THE system SHALL grant it current leadership and the authority to perform remaining transfer batches, and SHALL NOT move any Task merely because leadership changed. |
| PRJ-009 | WHERE the member owns a worked unfinished Task, THE system SHALL refuse direct removal under `TSK-022`. WHEN an owning Mentor removes a member directly, THE system SHALL transfer that member's unfinished Tasks to the current Leader in the same transaction. WHERE the removed member is the current Leader, THE system SHALL require an eligible replacement and SHALL transfer the unfinished Tasks to that replacement. WHERE any replacement, transfer, authorization, or lock check fails, THE system SHALL leave membership and Tasks unchanged. |
| PRJ-010 | WHILE an exit request is pending, THE system SHALL permit the current Leader to redistribute work in confirmed batches, each naming one or more unfinished `TODO`, `IN_PROGRESS`, or `BLOCKED` Tasks and one eligible active current member. THE system SHALL apply each batch immediately and atomically, SHALL permit the batches to repeat, and SHALL NOT undo a completed batch when the request is later cancelled or rejected. |
| PRJ-011 | WHEN a membership closes, THE system SHALL leave completed Tasks, Task creator attribution, comments, work logs, invitations, exit requests, and leadership history unchanged. A completed Task SHALL remain assigned to the closed historical membership and SHALL display that Intern's name in authorized history. |
| PRJ-012 | WHEN a Mentor activates a Project, THE system SHALL require a current Leader, at least one active member, valid Project dates, and a valid active-member assignee on every non-deleted Task. |
| PRJ-013 | WHILE a Project is `PLANNED`, THE system SHALL permit the current Leader to prepare a Task for any active member and any active member to prepare a self-assigned Task, and SHALL refuse Task status changes and work logging until the Project is `ACTIVE`. |
| PRJ-014 | THE system SHALL permit only the owning Mentor to complete a Project, and only WHILE every non-deleted Task is `DONE`. WHEN completion commits, THE system SHALL close the current leadership and membership intervals, revoke pending invitations, supersede pending exit requests, and make the aggregate read-only. |
| PRJ-015 | THE system SHALL compute Project progress as `DONE non-deleted Tasks / all non-deleted Tasks`. WHERE a Project has no non-deleted Task, THE system SHALL render `N/A` rather than 0%. |
| PRJ-016 | THE system SHALL show, alongside Project progress, the counts of `TODO`, `IN_PROGRESS`, `BLOCKED`, and `DONE` Tasks and the total logged minutes. |
| PRJ-017 | THE system SHALL treat an Intern as eligible for direct addition or invitation only WHILE their account and internship are `ACTIVE` and they hold no active membership in that Project. WHEN an owning Mentor adds a member directly, THE system SHALL require no acceptance and SHALL record that Mentor as `added_by_user_id`; WHEN an invitation is accepted, THE system SHALL record the accepting Intern. WHERE a matching invitation is pending at the moment of a direct add, THE system SHALL mark it `SUPERSEDED` with `MENTOR_DIRECT_ADD` in the same transaction. |
| PRJ-018 | WHILE a Project is `PLANNED` or `ACTIVE`, THE system SHALL permit the current Leader at most one pending invitation per eligible Intern. THE system SHALL NOT expire an invitation by time, SHALL preserve the issuing leadership term, and SHALL require the intended Intern to authenticate before any response. An emailed URL SHALL open only the authenticated response page. |
| PRJ-019 | THE system SHALL permit only the intended Intern to accept or decline their pending invitation. WHEN acceptance is submitted, THE system SHALL recheck Project, invitee, issuing leadership, and membership state and create one membership, all in one transaction. THE system SHALL permit the issuing Leader to revoke their own pending invitations and the owning Mentor to revoke any. WHEN the issuing leadership ends, the Project completes, or the invitee becomes ineligible, THE system SHALL revoke the now-unusable pending invitation without deleting it. |
| PRJ-020 | THE system SHALL permit a current Leader to request removal of another current member, and any current member including the Leader to request their own leave. THE system SHALL require a nonblank reason, SHALL NOT expire the request, and SHALL allow at most one pending request per membership. WHILE a request is pending, THE system SHALL show every authorized Project viewer whether a replacement Leader is required, how many unfinished Tasks remain, and whether the request is ready for a Mentor decision. |
| PRJ-021 | WHILE an exit request is pending, THE system SHALL keep the target's membership, existing assignments, and existing Task rights active, and SHALL refuse to give that target a newly created or reassigned Task or to let them create a self-Task. THE system SHALL permit the requester to cancel and only the owning Mentor to approve or reject. WHEN the request is cancelled or rejected, THE system SHALL preserve completed transfer batches and restore new-assignment eligibility. WHEN an owning Mentor removes the target directly, THE system SHALL resolve the matching request as `APPROVED`; WHEN the Project completes, THE system SHALL mark unresolved requests `SUPERSEDED`. |
| PRJ-022 | WHEN exit approval is submitted, THE system SHALL lock and recheck the request, the target membership, current leadership, and the unfinished Task count. WHILE the target is the current Leader or owns any unfinished Task, THE system SHALL refuse approval. WHEN both guards pass, THE system SHALL commit membership closure and request approval together, leaving completed Tasks and all retained attribution unchanged. |
| PRJ-023 | WHEN the owning Mentor cancels a `PLANNED` or `ACTIVE` Project, THE system SHALL require a nonblank cancellation reason and SHALL record the Mentor, the server time, and the reason. In the same transaction THE system SHALL close the current leadership and membership intervals while retaining them, revoke pending invitations with `PROJECT_CANCELLED`, mark pending exit requests `SUPERSEDED`, leave every Task, comment, work log, estimate, and forecast unchanged, and make the aggregate read-only. THE system SHALL notify every member whose interval it closes under `NOT-002`. |

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
| TSK-007 | THE system SHALL permit only these Task status transitions: `TODO → IN_PROGRESS|BLOCKED`, `IN_PROGRESS → DONE|BLOCKED`, `BLOCKED → TODO|IN_PROGRESS`, and `DONE → IN_PROGRESS`. WHILE the Project is `ACTIVE`, THE system SHALL permit the current assignee any of them on their own Task; which of them any other actor may make is decided by `TSK-023`. |
| TSK-008 | WHERE any other status transition is requested, THE system SHALL reject it. THE system SHALL NOT provide a configurable workflow engine in v1. |
| TSK-009 | THE system SHALL permit only the current Leader to reassign an unfinished Task, singly or within an exit-transfer batch, and SHALL require every target to be an eligible active current member who is not the target of a pending exit. WHEN a reassignment commits, THE system SHALL update the current assignment actor and time while preserving creator attribution, status, comments, work logs, and applicable lifecycle timestamps. WHILE a Task is `DONE`, THE system SHALL refuse transfer and reassignment until it is reopened to `IN_PROGRESS` under `TSK-007` or `TSK-023`. |
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
| TSK-021 | THE system SHALL compute Actual Task effort as the lifetime sum of retained work-log minutes across every author and assignment. THE system SHALL compute the current Remaining effort as zero for a `DONE` Task and otherwise as the latest effective Remaining effort forecast, which is the most recently recorded forecast that no correction has superseded, less the Actual Task effort added since that forecast's actual-effort snapshot, never below zero, and SHALL compute Current Work as Actual Task effort plus current Remaining effort. WHERE a Task carries an estimate and is `DONE` or has a Remaining effort forecast, THE system SHALL compute variance as Current Work minus the estimate. WHERE a Task carries an estimate, is unfinished, and has no forecast, THE system SHALL leave variance undefined and render it as `Pending`. WHERE a Task carries no estimate, THE system SHALL leave variance undefined and render it as `N/A`. Earlier forecasts SHALL remain as history and SHALL NOT feed the current value. THE system SHALL NOT derive an efficiency or productivity score from any of these. |
| TSK-022 | WHERE an unfinished Task carrying retained work is reassigned, THE system SHALL require an append-only Remaining effort forecast from the current Leader containing the reassignment snapshot. THE system SHALL accept a correcting successor only before the incoming assignee's first newly created work log. WHILE a member owns a worked unfinished Task, THE system SHALL refuse direct Mentor removal, and SHALL NOT fabricate Leader provenance for a forecast. |
| TSK-023 | THE system SHALL decide every Task status change from the actor's role, the actor's scope, the Task's current status, and the target status, and SHALL NOT infer from a higher role that an actor may set any status. WHILE a Project is `ACTIVE`, THE system SHALL permit the current Leader, on any non-deleted Task of the Project they lead, and the owning Mentor, on any non-deleted Task of their Project, to block a Task (`TODO` or `IN_PROGRESS` → `BLOCKED`), to unblock it back to the status it held immediately before it was blocked, and to reopen it (`DONE` → `IN_PROGRESS`) so that its assignee can correct it. THE system SHALL refuse a Leader's or Mentor's attempt to unblock a Task to any other status, to start another member's Task, or to mark it `DONE`, and SHALL refuse every status change by an Admin. |
| TSK-024 | WHILE a Project is `ACTIVE`, THE system SHALL permit its current Leader to append a Remaining effort forecast for any unfinished Task whenever the prediction of the effort still needed changes. THE system SHALL record with each forecast the Actual Task effort at that moment, SHALL NOT edit or replace an earlier forecast, and SHALL use the latest one under `TSK-021`. A forecast SHALL NOT change the estimate. |
| TSK-025 | WHEN a Task is blocked, unblocked, or reopened, THE system SHALL retain a transition record holding the actor, the server time, the previous status, the new status, and, for a reopen, a nonblank reason the actor must supply. THE system SHALL take the status a Task returns to on unblock from the record of its latest block. THE system MAY show a reopen reason as a Task comment, but the transition record SHALL remain its authoritative copy. |

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
| DB-011 | THE schema SHALL preserve, in `project_invitations`, the Project, intended Intern, issuing leadership term, status, optional accepted membership, resolution code, actor and time, and an optimistic version. THE schema SHALL constrain the resolution code to exactly these nine values, each recording why the invitation stopped being pending: `INVITEE_ACCEPTED` when the intended Intern accepted and became a member, `INVITEE_DECLINED` when they declined, `INVITER_REVOKED` when the issuing Leader withdrew it, `MENTOR_REVOKED` when the owning Mentor withdrew it, `LEADER_CHANGED` when the issuing leadership term ended before any response, `PROJECT_COMPLETED` when Project completion superseded it, `PROJECT_CANCELLED` when Project cancellation superseded it, `INVITEE_INELIGIBLE` when the intended Intern stopped satisfying membership eligibility, and `MENTOR_DIRECT_ADD` when a direct Mentor addition superseded it. THE schema SHALL use partial uniqueness and same-Project composite foreign keys so that a duplicate pending invitation and cross-Project provenance are both impossible. |
| DB-012 | THE schema SHALL preserve, in `project_membership_exit_requests`, the Project, requester and target memberships, request type and reason, status, optional decision details, and an optimistic version. THE schema SHALL enforce same-Project participants, the participant shape each request type requires, and one pending request per target, and SHALL name Task actor columns generically. |
| DB-013 | THE schema SHALL store a Task estimate as nullable whole-Task integer minutes constrained to `1..527040`, with no backfill. THE schema SHALL store Remaining effort forecasts as append-only rows carrying same-Project Task and membership references, the start of the assignment the forecast applies to, remaining minutes, a nonnegative lifetime-actual snapshot, an optional initial note, the correction reason and supersession shape, linear successors, and indexes for Task history and latest lookup. THE schema SHALL NOT persist a derived forecast total. |

### Notifications (from notification §12.2)

| ID | Requirement |
|---|---|
| NOT-003 | WHEN a Task comment is added or a Task status changes, THE system SHALL create an in-app notification only. WHEN someone other than the assignee blocks, unblocks, or reopens a Task, THE system SHALL notify the assignee, and WHERE that actor is the owning Mentor, SHALL also notify the current Leader. |
| NOT-010 | WHEN an invitation is created, THE system SHALL notify the invitee. WHEN it is answered, THE system SHALL notify the issuing Leader and the owning Mentor. WHEN it is revoked or superseded, THE system SHALL notify the invitee, the issuing Leader, and the owning Mentor, collapsing duplicate recipients. WHEN a Leader requests a removal, THE system SHALL notify the owning Mentor and the target; WHEN a member requests their own leave, THE system SHALL notify the owning Mentor and the current Leader; WHEN such a request is decided or cancelled, THE system SHALL notify the requester, the target, and the current Leader, collapsing duplicate recipients. WHEN a member creates a self-Task, THE system SHALL send no notification. |

### Use cases

#### UC-05 — Manage a Project, membership, and leadership

| Field | Specification |
|---|---|
| Primary actor(s) | Owning Mentor |
| Trigger | A Mentor creates or manages an owned Project. |
| Preconditions | The Mentor is active and owns the Project for every operation after creation. |
| Postconditions | Intervals and historical attribution remain intact; a completed Project is terminal and read-only. |
| Traced requirements | PRJ-001–PRJ-023, AUTH-003–AUTH-011 |

**Main success flow**

1. Create a PLANNED Project, initial Leader membership, and first leadership term atomically.
2. Directly add eligible active Intern members when needed.
3. Keep exactly one current Leader throughout PLANNED and ACTIVE.
4. Activate the Project when membership, Leader, dates, and assignees are valid.
5. Inspect Task progress and comment without changing Task definitions or status.
6. Decide membership exits through the approved transfer workflow.
7. Complete the Project only after every non-deleted Task is DONE.

**Alternatives and exceptions**

- Leadership reassignment leaves the former Leader’s Task assignments unchanged.
- Direct removal moves the member's unfinished Tasks to the current Leader, or to the replacement when the member leads, and is refused while the member owns an unfinished Task that already carries work logs.
- Removing or approving leave for the current Leader requires a replacement first.
- Project completion revokes pending invitations and supersedes pending exit requests.
- An empty PLANNED Project may be deleted by its owning Mentor. A PLANNED Project with history, or an ACTIVE one, is cancelled instead: the Mentor gives a reason, members are released and notified, and the Project stays read-only.

#### UC-06 — Manage Tasks as current Project Leader

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

| Field | Specification |
|---|---|
| Primary actor(s) | Current Project Leader; invited Intern; owning Mentor |
| Trigger | A Leader invites an eligible Intern, or the intended Intern opens their pending invitation. |
| Preconditions | The Project is PLANNED or ACTIVE, the issuing leadership term is current, and the invitee is an active eligible Intern with no active membership in that Project. |
| Postconditions | The invitation reaches one terminal state with retained provenance; acceptance creates at most one active membership. |
| Traced requirements | AUTH-011, PRJ-017–PRJ-019, NOT-010, UI-019, DB-011 |

**Main success flow**

1. The current Leader creates one pending invitation for the eligible Intern.
2. The system commits an in-app notification and attempts ordinary email when SMTP is available.
3. The intended Intern authenticates and opens the invitation response page.
4. The Intern accepts or declines explicitly.
5. Acceptance locks and rechecks invitation, Project, issuing leadership, eligibility, and current membership before creating exactly one membership.

**Alternatives and exceptions**

- The issuing Leader may revoke an invitation they issued; the owning Mentor may revoke any Project invitation.
- Mentor direct-add wins by creating membership and marking the pending invitation SUPERSEDED.
- Leadership change, Project completion, or invitee ineligibility makes the invitation unusable while retaining history.
- A concurrent loser receives a safe conflict and no duplicate membership.

#### UC-14 — Request and decide Project membership exit

| Field | Specification |
|---|---|
| Primary actor(s) | Current Leader; current Project member; owning Mentor |
| Trigger | A Leader requests another member’s removal, a member asks to leave, or the owning Mentor decides the request. |
| Preconditions | Requester and target have active memberships in the same PLANNED or ACTIVE Project; no pending request already targets that membership. |
| Postconditions | Approval closes the membership only once the target neither leads the Project nor owns an unfinished Task; every request and original attribution remains historical. |
| Traced requirements | AUTH-011, PRJ-008, PRJ-010, PRJ-020–PRJ-022, TSK-022, NOT-010, UI-019, DB-012 |

**Main success flow**

1. Create a pending request with the correct type and a nonblank reason.
2. While pending, keep the target's membership, existing assignments, and rights, and give the target no new or reassigned Task and no self-Task.
3. Where the target is the current Leader, the owning Mentor appoints an eligible replacement before any transfer.
4. The current Leader moves the target's unfinished Tasks to eligible current members in confirmed batches, each committed immediately, with a Remaining effort forecast for every worked Task.
5. Allow the requester to cancel, or the owning Mentor to reject, or to approve once the target neither leads the Project nor owns an unfinished Task.
6. On approval, lock and recheck the request, target membership, current leadership, and unfinished Task count, then close the membership and resolve the request in the same transaction.

**Alternatives and exceptions**

- Reject and cancel resolve only the request; completed transfer batches stay, and the target is again eligible for new assignments.
- Approval submitted while the target still leads the Project or owns an unfinished Task is refused and changes nothing.
- Direct Mentor removal resolves a matching pending request as APPROVED.
- Project completion marks unresolved requests SUPERSEDED.
- Optimistic or authorization conflict leaves every membership and historical row unchanged.

## 4. Non-functional Requirements

System-wide non-functional rules apply unchanged: architecture §3 (`ARC`), authentication and security §13 (`SEC`), interface and accessibility §15 (`UI`), delivery §16 (`OPS`), and test evidence §17 (`TST`), all in the [platform spec](../feature-platform/SPEC.md).

## 5. Data

Tables this feature's entities map to: `projects`, `project_memberships`, `project_leadership_terms`, `project_invitations`, `project_membership_exit_requests`, `tasks`, `task_comments`, `task_work_logs`, `task_remaining_effort_forecasts`.

The conceptual model, the table inventory, the integrity rules (`DB`), and both diagrams are §19 of the [platform spec](../feature-platform/SPEC.md).

## 6. Error Handling

Rules in section 3 whose text names a refusal, rejection, denial, or failure: `PRJ-002`, `PRJ-008`, `PRJ-009`, `PRJ-010`, `PRJ-013`, `PRJ-021`, `PRJ-022`, `TSK-005`, `TSK-008`, `TSK-009`, `TSK-014`, `TSK-015`, `TSK-016`, `TSK-019`, `TSK-020`, `TSK-022`.

System-wide failure behavior is §21 (`ERR-001`–`ERR-007`), and the interface message families are Appendix F, both in the [platform spec](../feature-platform/SPEC.md).

## 7. Acceptance Criteria

Scenarios from the §20 acceptance catalogue whose identifiers start with `AC-PRJ` or `AC-TSK`, and the authorization and schema scenarios that cover only this spec's rules. The catalogue's introduction, including the rules deliberately written without a scenario, is in the [platform spec](../feature-platform/SPEC.md).

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-PRJ-001 | PRJ-003–PRJ-005 | Same Intern is added to two Projects and appointed Leader of one | Both active memberships coexist; leadership affects only the selected Project. |
| AC-PRJ-002 | PRJ-005, DB-003 | Two transactions appoint different current Leaders | Database/transaction rules allow exactly one current term; no double-Leader state commits. |
| AC-PRJ-003 | PRJ-006–PRJ-007 | Mentor changes Leader while former Leader remains a member | Old term closes, new term opens, every Task assignee remains unchanged, and former Leader loses Task-management controls. |
| AC-PRJ-004 | PRJ-008–PRJ-011 | Mentor directly removes an ordinary member or current Leader with unfinished Tasks | Ordinary-member removal atomically transfers unfinished Tasks to the current Leader; Leader removal requires a replacement and transfers unfinished Tasks to that replacement; completed Tasks retain the removed member's displayed name and attribution. |
| AC-PRJ-005 | PRJ-009 | Mentor removes an ordinary member with no unfinished Tasks | Membership interval closes and member loses active access without deleting history. |
| AC-PRJ-006 | PRJ-012–PRJ-013 | Mentor activates a Project missing Leader or with invalid assignee | Activation is rejected atomically with specific validation; valid planned Tasks remain intact. |
| AC-PRJ-007 | PRJ-014 | Mentor completes Project with a BLOCKED Task | Completion is rejected; after all active Tasks reach DONE, completion succeeds and all mutation becomes read-only. |
| AC-PRJ-008 | PRJ-015–PRJ-016 | Project has zero, then four Tasks with two DONE | Progress changes from `N/A` to 50%, with accurate status counts and minutes. |
| AC-PRJ-009 | PRJ-001, PRJ-005 | Mentor creates a Project while two requests race | One transaction atomically commits Project, initial Leader membership, and first leadership term; no committed `PLANNED`/`ACTIVE` Project has zero or two current Leaders. |
| AC-PRJ-010 | PRJ-017–PRJ-019, DB-011 | Leader invites an eligible Intern; the Intern accepts while Mentor direct-add races | Exactly one active membership commits. Invitation becomes `ACCEPTED` for the winning acceptance or `SUPERSEDED` for Mentor direct-add; losing request returns conflict without duplicate history. |
| AC-PRJ-011 | PRJ-018–PRJ-019 | Invitee declines, issuing Leader revokes, leadership changes, Project completes, and invitee becomes ineligible in separate cases | Each pending invitation reaches the correct terminal status/code, remains historical, and cannot later be accepted. |
| AC-PRJ-012 | PRJ-020–PRJ-022, DB-012 | Leader requests another member's removal; the Project has several unfinished Tasks; Leader repeatedly selects multiple Tasks and one eligible recipient per batch; requester then cancels or Mentor rejects/approves | Pending warning shows remaining count/readiness; target keeps existing rights but cannot receive/create new Tasks; each confirmed batch commits immediately and atomically; cancellation/rejection keeps completed reassignments and restores eligibility; approval remains blocked until zero unfinished Tasks, then closes membership and request together. |
| AC-PRJ-013 | PRJ-008, PRJ-010, PRJ-020–PRJ-022 | Current Leader requests own leave with unfinished Tasks | Warning requires replacement first; owning Mentor appoints one; new Leader performs repeatable transfer batches to eligible current members, including a newly direct-added or invitation-accepted member; approval remains blocked until target is no longer Leader and has zero unfinished Tasks. |
| AC-PRJ-014 | PRJ-002 | An owning Mentor completes a Project, then attempts to move it back to `ACTIVE` or `PLANNED`, and a direct request attempts the same transition | Both attempts are refused; the Project remains `COMPLETED`; no state column value outside `PLANNED`, `ACTIVE`, `COMPLETED`, and `CANCELLED` can be written. Separately, the owning Mentor deletes an empty `PLANNED` Project and its initial membership, leadership term, and the notifications raised for it are removed with it; a `PLANNED` Project holding a Task, a second membership, an invitation, or an exit request refuses deletion and offers none; another Mentor's delete request is denied; a delete request for an `ACTIVE` Project is refused, the Project remains, and its page offers no delete action. |
| AC-PRJ-015 | PRJ-023, AUTH-006, DB-011 | The owning Mentor cancels an `ACTIVE` Project holding unfinished Tasks, a pending invitation, and a pending exit request, first without a reason and then with one, and another Mentor attempts the same | The reasonless attempt and the other Mentor's attempt are refused and change nothing; the cancellation records the Mentor, the time, and the reason, closes the leadership and membership intervals, revokes the invitation with `PROJECT_CANCELLED`, supersedes the exit request, leaves the Tasks and their statuses unchanged, and notifies each closed member; every later mutation of the Project or its Tasks is refused. |
| AC-PRJ-016 | PRJ-024, TSK-014 | On 15 September a Mentor creates a Project that started on 8 September and ends on 14 December, then tries another whose end date precedes its start; after activation the Leader, assigned a Task, logs work dated 10 September and 15 September | The first Project is created with its 8 September start; the reversed range is refused; the 10 September work log, dated before the Leader joined, is refused under `TSK-014`, and the 15 September one is accepted. |
| AC-AUTH-003 | AUTH-006–AUTH-007 | Admin opens a Project and its History tab | Admin sees read-only Project/tasks/progress and retained history but cannot comment, assign, change status, or manage membership. |
| AC-AUTH-007 | AUTH-006 | Removed member opens the Project before and after completion | Open-Project access is denied after removal; after completion the former member receives authorized read-only Project/Task history and no mutation is accepted. |
| AC-DB-002 | DB-011–DB-012 | SQL probes attempt duplicate pending invitations/exits, cross-Project references, invalid request participants, and unsupported resolution combinations | PostgreSQL rejects each invalid row while valid accepted/revoked/superseded and approved/rejected/cancelled histories commit. |
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
| AC-TSK-012 | TSK-020–TSK-021 | Leader creates and edits an estimate before work, then a retained log is created and the Task is completed | Valid optional estimate persists, ordinary-member forged mutation is denied, first retained log freezes the estimate, lifetime actual spans authors/assignments, and a DONE Task's variance is signed actual minus estimate. |
| AC-TSK-013 | TSK-022 | Leader reassigns worked and unworked unfinished Tasks, then corrects the latest forecast | Worked reassignment requires an atomic forecast snapshot; unworked reassignment rejects unsolicited forecast; correction appends one successor before the incoming member's first new log and rejects stale/late/unauthorized corrections. |
| AC-TSK-014 | TSK-022, PRJ-008–PRJ-010 | Mentor attempts direct removal while the target owns worked unfinished Tasks | Removal is rejected before leadership, membership, assignment, request, or notification mutation; after Leader forecast-aware transfers, eligible unworked Tasks retain existing automatic transfer behavior. |
| AC-TSK-015 | TSK-002 | One Intern is an active member of two Projects and is made current assignee of Tasks in both, then of a second Task in the first Project | Every assignment succeeds; each Task keeps exactly one current assignee; the Intern's assignment count is not capped by Project membership. |
| AC-TSK-016 | TSK-007, TSK-023 | On an `ACTIVE` Project the assignee, the current Leader, the owning Mentor, another member, and an Admin each attempt every `TSK-007` transition on one Task, then the same on a `PLANNED` Project | The assignee may make every transition; the Leader and the owning Mentor may block, unblock, and reopen only, and are refused starting the Task and marking it `DONE`; another member and the Admin are refused every change; on the `PLANNED` Project every status change is refused. |
| AC-TSK-017 | TSK-021, TSK-024 | A Task estimated at 600 minutes has 200 logged; the Leader records 300 remaining; 120 more are logged; the Leader records 250 remaining; 220 more are logged and the Task is completed | After the first forecast Current Work is 500 and variance −100; after the next 120 minutes current Remaining is 180 and Current Work stays 500; after the second forecast Current Work is 570 and variance −30; once `DONE`, Current Work is 540 and variance −60. The estimate never changes and both forecasts remain in history. |
| AC-TSK-018 | TSK-023, TSK-025, NOT-003 | On an `ACTIVE` Project the Leader blocks a `TODO` Task and an `IN_PROGRESS` Task, then unblocks both; the owning Mentor reopens a `DONE` Task first without a reason and then with one | The `TODO` Task returns to `TODO` and the `IN_PROGRESS` Task to `IN_PROGRESS`, and unblocking either to another status is refused; the reasonless reopen is refused; each block, unblock, and reopen leaves a transition record with actor, time, both statuses, and the reopen reason; the assignee receives an in-app notification for each, and the current Leader also for the Mentor's reopen. |
| AC-AUTH-004 | AUTH-008, TSK-023 | Owning Mentor opens a Task on an `ACTIVE` Project, then one on a `PLANNED` Project | Mentor can view and comment; every Task definition mutation is denied; on the `ACTIVE` Project block, unblock, and reopen succeed while starting the Task and marking it `DONE` are denied; on the `PLANNED` Project every status change is denied. |
| AC-DB-003 | DB-013 | SQL probes insert a Task estimate of 0, one of 527 041, a forecast whose Task and membership belong to different Projects, a forecast with negative lifetime-actual, and an attempt to update an existing forecast row | Each is rejected by a constraint; estimates accept `NULL` and the inclusive bounds 1 and 527 040; forecasts accept inserts only, and no derived total column exists on any table. |
| AC-AUTH-005 | AUTH-004–AUTH-005, TSK-023 | Leader opens one assigned and one unassigned Task on an `ACTIVE` Project | Leader manages definitions for both; on the assigned Task every `TSK-007` transition and work logging succeed; on the unassigned Task only block, unblock, and reopen succeed, and starting it, marking it `DONE`, and logging work are denied. |
| AC-AUTH-006 | AUTH-005, AUTH-009 | Ordinary member opens Project Tasks | Member sees and comments on all Tasks; status/log controls exist only on their assigned Task. |
| AC-AUTH-010 | AUTH-011 | A stale former Leader or unrelated member submits an invitation, exit, self-Task, or Task-management request by direct identifier | Authorization is re-evaluated inside the transaction; the request is denied without existence leakage or partial mutation. |

## 8. Out of Scope

System-wide exclusions are §1.3 of the [platform spec](../feature-platform/SPEC.md): `GOV-007` through `GOV-010` and `GOV-015`.

Exclusions stated inside this spec's own rules: `TSK-008`, `TSK-011`.

## Notes / Open Questions

- `D12`, decided on 14 September 2026 and confirmed for build on 16 September, is settled. Only an empty `PLANNED` draft is deleted, with the notifications raised for it; any other Project that will not run is cancelled under `PRJ-023` and keeps its history. `ARCHIVED` was not added, because nothing yet gives it a meaning `COMPLETED` and `CANCELLED` lack. How cancelled Projects appear in reports is `RPT-003` and `RPT-011`.
- Deleting an empty draft leaves no audit record, since `GOV-009` forbids generic domain events.
- Code finding, not a rule: `ProjectService#deleteProjectRows` deletes with native SQL, which `ARC-006` forbids (`D18`), and does not delete `task_remaining_effort_forecasts`. An empty Project has no Task, so no forecast can exist for it.
- `D13` and `D15`, decided on 14 September 2026 and confirmed for build on 16 September, are settled. Status changes follow role, scope, current status, and target status (`TSK-023`); unblocking restores the earlier status and every block, unblock, and reopen is recorded, a reopen with its reason (`TSK-025`). The estimate is the baseline and the current Remaining effort comes from the latest effective forecast (`TSK-021`, `TSK-024`).
- The code still lets an owning Mentor set any status (`TaskService#changeStatus`), keeps no transition record, and computes variance for `DONE` Tasks only; two tests assert the Mentor behavior. All of it changes with the implementation.
- A forecast recorded outside a reassignment can use the existing forecast columns with the current assignee and assignment start; the implementation plan confirms it.
