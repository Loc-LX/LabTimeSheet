# Authorization Spec

**Version:** 1.0.0 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-23

**Module:** `platform` · **Shared contract:** [MODULE.md](../../MODULE.md)

## 1. Context & Goal

Decide every business permission on the server, from the stored context of the request,
through one policy, and never let a refusal reveal that a record the caller may not see
exists.

This is one cohesive planning scope. Operations below are sections of this feature,
not separate modules or mandatory branches. Its rules moved here from the platform shared
contract unchanged (`D40`).

## 2. Actors & Roles

The Admin, the owning and the responsible Mentor, the current Leader, and the active member
or current assignee, as the §5.2 matrix distinguishes them. A Project Leader is a stored
leadership term, not a global role. No use case is defined here: every feature's use cases
apply this model.

### §5. Authorization model

**Part F2.**

#### §5.1 Authorization evaluation

`AUTH-004`–`AUTH-009` and `AUTH-011` are in [the project spec](../../../project/MODULE.md).

| ID | Requirement |
|---|---|
| AUTH-001 | WHEN a state-changing operation is requested, THE system SHALL authorize it on the server from the global role, account and internship state, record ownership, active membership, current leadership term, current Task assignee, and aggregate lifecycle, as applicable to that operation. |
| AUTH-002 | THE system SHALL NOT treat a hidden Thymeleaf control as authorization. WHERE the caller is not authenticated, THE system SHALL redirect to the sign-in page. WHERE the caller is authenticated but holds the wrong role for the whole route, THE system SHALL return an authenticated access-denied response, which implies no record. WHERE the caller may reach the route but the record is not theirs or does not exist, THE system SHALL return the same not-found response in both cases, so that the response cannot be used to discover which records exist. |
| AUTH-003 | WHILE a Mentor account is active, THE system SHALL permit it to view any Intern's attendance, and SHALL permit only an Intern's responsible Mentor under `ACC-026` to decide that Intern's leave, correction, and attendance exception requests. THE system SHALL restrict Project-management authority to the Project's owning Mentor. |

#### §5.2 Permission matrix

Legend: **Yes** = permitted within the stated scope; **Own** = own Project or own record; **Assigned** = only while current assignee; **No** = forbidden.

| Capability | Admin | Owning Mentor | Current Leader | Active member / assignee |
|---|---:|---:|---:|---:|
| Manage accounts/global roles at creation | Yes | No | No | No |
| Lock/deactivate accounts; manage internship lifecycle | Yes | No | No | No |
| Manage SMTP, HolidayAPI, attendance policy, global calendar | Yes | No | No | No |
| View all Projects/tasks/progress | Yes, read-only | Own | Own | Membership scope |
| Create Project | No | Yes | No | No |
| Edit/activate/complete Project | No | Own | No | No |
| Delete an empty `PLANNED` Project (`PRJ-002`) | No | Own | No | No |
| Cancel a `PLANNED` or `ACTIVE` Project (`PRJ-023`) | No | Own | No | No |
| Directly add/remove Project members | No | Own | No | No |
| Issue/revoke Project invitation | No | Revoke any in Own | Issue/revoke own in Own | Accept/decline own invitation |
| Request/decide membership exit | No | Decide or remove directly in Own | Request another member's removal or own leave | Request/cancel own leave |
| Appoint/change Project Leader | No | Own | No | No |
| Redistribute unfinished Tasks for pending exit | No | No | Own, in confirmed batches | No |
| Create Task | No | No | Own; any active assignee | Self-assigned only |
| Assign/reassign Task | No | No | Own | No |
| Set/replace/clear Task estimate before the first work log | No | No | Own | No |
| Record a Remaining effort forecast (`TSK-022`, `TSK-024`) | No | No | Own | No |
| Edit/soft-delete Task | No | No | Unfinished in Own | Unfinished self-created while still self-assigned |
| Change the status of one's own assigned Task (any `TSK-007` transition) | No | No | Assigned | Assigned |
| Block, unblock, or reopen another member's Task (`TSK-023`) | No | Own, `ACTIVE` Project | Own, `ACTIVE` Project | No |
| Start another member's Task or mark it `DONE` | No | No | No | No |
| Comment on Task | No | Own | Own | Membership scope |
| Create/edit own Task work log | No | No | Assigned/own log | Assigned/own log |
| View aggregate Project progress | Yes | Own | Own | Membership scope |
| View Project/Task retained history | Yes, read-only | Own | Own | Current membership; former membership after completion or cancellation (`AUTH-006`) |
| View per-member Project hours | Yes, read-only | Own | Own | No |
| View Intern attendance | Yes | Yes | Own history only | Own history only |
| Decide leave, correction, or attendance exception; amend or reverse that decision where its rule permits (`ATT-024`) | No | Responsible Mentor only (`ACC-026`) | No | No |
| Submit own leave, correction, or exception request | No | No | If active Intern | If active Intern |
| Withdraw own `PENDING` or `OVERDUE` leave request (`LEV-013`) | No | No | If active Intern | If active Intern |
| Ask to reopen a finalized attendance period (`ATT-022`) | No | Responsible Mentor only (`ACC-026`) | Own, if active Intern | Own, if active Intern |
| Approve or reject a request to reopen a finalized attendance period (`ATT-022`) | Yes | No | No | No |
| View and export the Attendance report (`RPT-004`) | Yes | Yes | Own history only | Own history only |
| View and export the Project/Task report (`RPT-005`) | Yes, read-only | Own | Project scope | Aggregate only |
| View and export the Daily Project Work Report (`RPT-011`) | Yes, read-only | Own | Led `PLANNED` or `ACTIVE` Project | No |
| View own attendance for a date (`RPT-015`) | No | No | Own, if active Intern | Own, if active Intern |

| ID | Requirement |
|---|---|
| AUTH-010 | Per-member Task-hour visibility is defined by `RPT-005`. This entry exists so that a reader of the authorization model reaches that rule; it adds nothing of its own. |
| AUTH-012 | THE system SHALL decide every business permission through one authorization policy that takes the actor's role, the actor's scope in stored context, the current state of the record, and, for a transition, the target state. THE system SHALL NOT infer from a higher role a capability the §5.2 matrix does not grant, SHALL give each capability of that matrix its own entry in the policy so that it can be withdrawn from one role alone, and SHALL NOT decide a business permission from a role check outside the policy. Route protection MAY remain coarse; the service SHALL enforce the policy's decision; a template SHALL ask the same policy only to decide what to show. The decision is `ADR-005`. |

## 3. Functional Requirements

The operation summaries below are navigation. The numbered rules remain authoritative;
they do not acquire additional behavior from a heading or summary.

### Authorize each request

AUTH-001 to AUTH-003 decide on the server, from stored context, without disclosing a record; AUTH-010 points to RPT-005 for per-member Task hours.

### Decide through one policy

AUTH-012 routes every business permission through one policy, with one entry per capability of the matrix.

### Canonical feature rules

The numbered rules of this feature stay with the model and the matrix in section 2 under §5,
where the platform shared contract held them.

## 4. Non-functional Requirements

Inherit [platform constraints](../../MODULE.md#4-non-functional-requirements). The security
controls of §13 are the [Security](../security/SPEC.md) feature; `ARC-010` in the [Architecture](../architecture/SPEC.md) feature
bounds how often a request asks the policy.

## 5. Data

No table of its own. The [part table](../../MODULE.md#ownership-and-dependencies-by-part) of the
platform shared contract records that F2 adds no authorization-history table. A decision reads the stored facts that
`AUTH-001` names from the module that owns each of them.

### Required contracts

These are required facts or behaviors, not prescribed cross-module imports or an
automatic implementation order.

| Provider | Required fact or behavior | Rule trace |
|---|---|---|
| [identity](../../../identity/MODULE.md) | The actor's global role and account state | [AUTH-001](SPEC.md) |
| [internship](../../../internship/MODULE.md) | Internship state and the responsible Mentor of `ACC-026` | [AUTH-001](SPEC.md), [AUTH-003](SPEC.md) |
| [project](../../../project/MODULE.md) | Ownership, active membership, current leadership term, current assignee and lifecycle, with `AUTH-004`–`AUTH-009` and `AUTH-011` | [AUTH-001](SPEC.md) |

### Related workflows and joint checks

- Feature specs link the §5.2 matrix above for their operations.
- The report rules `RPT-004`, `RPT-005` and `RPT-011` define report scope; the matrix rows for reports follow them.

## 6. Error Handling

`AUTH-002` fixes the three refusal responses. `SEC-009` in the [Security](../security/SPEC.md) feature
forbids disclosure in error pages. Apply [module error handling](../../MODULE.md#6-error-handling).

## 7. Acceptance Criteria

### Operation and acceptance map

This map traces existing behavior; its gap column does not define a new rule or
claim full test coverage. Actors and outcomes are summaries of the canonical rules.

| Operation | Actor and observable outcome | Canonical rules | Existing acceptance scenarios | Acceptance boundary or open decision |
|---|---|---|---|---|
| [Authorize each request](#authorize-each-request) | Every actor: a refused request changes nothing and discloses nothing | [AUTH-001](SPEC.md), [AUTH-002](SPEC.md), [AUTH-003](SPEC.md), [AUTH-010](SPEC.md) | [AC-AUTH-001](SPEC.md), [AC-AUTH-002](SPEC.md), [AC-AUTH-008](SPEC.md), [AC-AUTH-009](SPEC.md) | Each matrix and history-visibility row is exercised for every role context. |
| [Decide through one policy](#decide-through-one-policy) | Each matrix cell is one policy entry; withdrawing one changes only that cell | [AUTH-012](SPEC.md) | [AC-AUTH-011](SPEC.md) | No route or template grants what the service refuses (`ADR-005`). |

### Canonical acceptance scenarios

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-AUTH-001 | AUTH-001–AUTH-002, AUTH-011 | User guesses an unauthorized Intern, Project, invitation, membership, exit request, Task, leave, or correction ID | No record details are disclosed and no mutation occurs. |
| AC-AUTH-002 | AUTH-003, UI-019 | An active Mentor who owns no Project opens leave/correction queues, inspects attendance and attempts decisions for an Intern they are responsible for and another they are not | The Mentor may view Intern attendance; actionable queues contain only their responsible Interns. Eligible decisions for those Interns succeed; decisions for the other Intern and mutations of another Mentor's Project are denied. |
| AC-AUTH-008 | AUTH-010, RPT-005 | An active Admin and an ordinary member request the Project/Task report, per-member hours and exports within their respective scopes | The Admin receives all-Project options, the read-only dataset, per-member hours and XLSX/PDF exports. The ordinary member receives only aggregate Project progress and hours within membership scope; forged per-member detail requests disclose nothing. Admin report access grants no Project or Task mutation. |
| AC-AUTH-009 | AUTH-001–AUTH-010, RPT-004, RPT-005, RPT-011 | A parameterized suite evaluates every permission-matrix and history-visibility row for active Admin, owning/non-owning Mentor, responsible/non-responsible Mentor, current/former Leader, assigned/unassigned member, removed member in open/completed/cancelled Project, and unrelated user contexts | Each result matches the matrix: the active Admin may read and export the three dedicated report families within their report rules, while Project/Task mutation and attendance decisions remain denied. Former members regain read-only Project history after completion or cancellation. Every denial leaves state unchanged and reveals no unauthorized details. |
| AC-AUTH-011 | AUTH-012 | Every cell of the §5.2 permission matrix is exercised for each role, then one Admin capability is withdrawn from the policy | Each granted cell succeeds and each refused cell is denied at the service; no route or template grants what the service refuses; withdrawing the one Admin capability changes that capability's outcome and no other. |

Shared and cross-feature scenarios in [MODULE.md](../../MODULE.md#7-acceptance-criteria)
also apply.

## 8. Out of Scope

Inherit [module exclusions](../../MODULE.md#8-out-of-scope). The security controls
`SEC-001` and `SEC-008`–`SEC-014` are the [Security](../security/SPEC.md) feature, and the account
rules `SEC-002`–`SEC-007` belong to identity.

## Notes / Open Questions

No question affecting this feature is open. Its technical design is [PLAN.md](PLAN.md),
with tasks in [TASKS.md](TASKS.md).

Read [shared open questions](../../MODULE.md#notes--open-questions) before approving
the technical plan. [plan.md](../../../../../plan.md) is the only progress tracker.
