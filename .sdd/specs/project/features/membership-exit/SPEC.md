# Membership exit and transfer Spec

**Version:** 1.1.0 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-21

**Module:** `project` · **Shared contract:** [MODULE.md](../../MODULE.md)

## 1. Context & Goal

Request departure, prepare replacement and Task transfers, then close membership only when the exit guards pass.

This is one cohesive planning scope. Operations below are sections of this feature,
not separate modules or mandatory branches. The split changes document ownership only.

## 2. Actors & Roles

Requester or target member; current Leader transfers work; owning Mentor decides.

Use cases defined here: UC-14.

The [platform permission matrix](../../../platform/features/authorization/SPEC.md#52-permission-matrix)
and the module's shared authorization rules apply to every operation.

## 3. Functional Requirements

The operation summaries below are navigation. The numbered rules remain authoritative;
they do not acquire additional behavior from a heading or summary.

### Request or cancel exit

PRJ-020 and PRJ-021 retain existing rights while refusing new assignments to a pending-exit target.

### Prepare leadership and Task transfers

PRJ-008 through PRJ-010 require replacement and eligible transfers before departure.

### Approve, reject or supersede

PRJ-022 rechecks all guards atomically; PRJ-021 governs terminal request outcomes.

### Canonical feature rules

| ID | Requirement |
|---|---|
| PRJ-008 | WHILE an exit request targets the current Leader, THE system SHALL refuse Task transfer and exit approval until the owning Mentor appoints an eligible replacement. WHEN the replacement is appointed, THE system SHALL grant it current leadership and the authority to perform remaining transfer batches, and SHALL NOT move any Task merely because leadership changed. |
| PRJ-009 | WHERE the member owns a worked unfinished Task, THE system SHALL refuse direct removal under `TSK-022`. WHEN an owning Mentor removes a member directly, THE system SHALL transfer that member's unfinished Tasks to the current Leader in the same transaction. WHERE the removed member is the current Leader, THE system SHALL require an eligible replacement and SHALL transfer the unfinished Tasks to that replacement. WHERE any replacement, transfer, authorization, or lock check fails, THE system SHALL leave membership and Tasks unchanged. |
| PRJ-010 | WHILE an exit request is pending, THE system SHALL permit the current Leader to redistribute work in confirmed batches, each naming one or more unfinished `TODO`, `IN_PROGRESS`, or `BLOCKED` Tasks and one eligible active current member. THE system SHALL apply each batch immediately and atomically, SHALL permit the batches to repeat, and SHALL NOT undo a completed batch when the request is later cancelled or rejected. |
| PRJ-020 | THE system SHALL permit a current Leader to request removal of another current member, and any current member including the Leader to request their own leave. THE system SHALL require a nonblank reason, SHALL NOT expire the request, and SHALL allow at most one pending request per membership. WHILE a request is pending, THE system SHALL show every authorized Project viewer whether a replacement Leader is required, how many unfinished Tasks remain, and whether the request is ready for a Mentor decision. |
| PRJ-021 | WHILE an exit request is pending, THE system SHALL keep the target's membership, existing assignments, and existing Task rights active, and SHALL refuse to give that target a newly created or reassigned Task or to let them create a self-Task. THE system SHALL permit the requester to cancel and only the owning Mentor to approve or reject. WHEN the request is cancelled or rejected, THE system SHALL preserve completed transfer batches and restore new-assignment eligibility. WHEN an owning Mentor removes the target directly, THE system SHALL resolve the matching request as `APPROVED`; WHEN the Project completes, THE system SHALL mark unresolved requests `SUPERSEDED`. |
| PRJ-022 | WHEN exit approval is submitted, THE system SHALL lock and recheck the request, the target membership, current leadership, and the unfinished Task count. WHILE the target is the current Leader or owns any unfinished Task, THE system SHALL refuse approval. WHEN both guards pass, THE system SHALL commit membership closure and request approval together, leaving completed Tasks and all retained attribution unchanged. |
| DB-012 | THE schema SHALL preserve, in `project_membership_exit_requests`, the Project, requester and target memberships, request type and reason, status, optional decision details, and an optimistic version. THE schema SHALL constrain the status to `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`, or `SUPERSEDED`, and request type to `LEADER_REMOVAL` or `MEMBER_LEAVE`. THE schema SHALL enforce same-Project participants, the participant shape each request type requires, and one pending request per target, and SHALL name Task actor columns generically. |

#### UC-14 — Request and decide Project membership exit

**Parts P4, with P2 leadership and P6 forecasts.**

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

#### State transitions: Project membership exit request

The transitions the rules above allow. The table adds nothing to them: where it and a rule differ, the rule wins.

| From | Action | To | Who | Rules |
|---|---|---|---|---|
| (none) | request removal of another member | `PENDING` | current Leader | `PRJ-020`, `DB-012` |
| (none) | request own leave | `PENDING` | current member | `PRJ-020`, `DB-012` |
| `PENDING` | cancel own request | `CANCELLED` | requester | `PRJ-021`, `DB-012` |
| `PENDING` | reject request | `REJECTED` | owning Mentor | `PRJ-021`, `DB-012` |
| `PENDING` | approve request once target neither leads nor owns unfinished Tasks | `APPROVED` | owning Mentor | `PRJ-021`, `PRJ-022`, `DB-012` |
| `PENDING` | direct Mentor removal resolves matching request | `APPROVED` | owning Mentor | `PRJ-009`, `PRJ-021`, `DB-012` |
| `PENDING` | Project completion or cancellation marks unresolved requests | `SUPERSEDED` | system | `PRJ-021`, `PRJ-023`, `DB-012` |

No transition leaves `APPROVED`, `REJECTED`, `CANCELLED`, or `SUPERSEDED` (`PRJ-021`).

Public comparison points: [Azure DevOps work transfer](https://learn.microsoft.com/en-us/azure/devops/boards/work-items/move-work-items?view=azure-devops) and [Jira project role membership](https://support.atlassian.com/jira-software-cloud/docs/manage-project-roles/), checked on 21 September 2026: departure and removal workflows require all active work items to be redistributed or completed before member closure, and exit requests finalize into terminal resolution states with history preserved.

## 4. Non-functional Requirements

Inherit [module constraints](../../MODULE.md#4-non-functional-requirements) and
[platform constraints](../../../platform/MODULE.md#4-non-functional-requirements),
including authorization, history, concurrency, server time and test evidence.

## 5. Data

`project_membership_exit_requests`; membership, Task and forecast records participate through their owning services.

The [module data contract](../../MODULE.md#5-data) retains ownership, constraints and
checks across features. The full physical model stays in platform §19. A feature folder
does not grant repository/entity access across the module boundaries of ARC-006.

### Required contracts

These are required facts or behaviors, not prescribed cross-module imports or an
automatic implementation order. PLAN resolves delivery predecessors against what
already exists and preserves ARC-005/ARC-006.

| Provider | Required fact or behavior | Rule trace |
|---|---|---|
| [project/membership-and-leadership](../membership-and-leadership/SPEC.md) | Replacement leadership and participant intervals | [PRJ-005](../membership-and-leadership/SPEC.md), [PRJ-006](../membership-and-leadership/SPEC.md), [PRJ-007](../membership-and-leadership/SPEC.md) |
| [project/task-management](../task-management/SPEC.md) | Unfinished work and eligible reassignment | [TSK-009](../task-management/SPEC.md) |
| [project/work-logs-and-effort](../work-logs-and-effort/SPEC.md) | Worked-task forecast with Leader provenance | [TSK-022](../work-logs-and-effort/SPEC.md) |

### Related workflows and joint checks

- [Membership and leadership](../membership-and-leadership/SPEC.md): Leadership replacement and membership closure.
- [Task management](../task-management/SPEC.md): Reassignment and unfinished-work guards.
- [Work logs and effort](../work-logs-and-effort/SPEC.md): Worked-Task reassignment requires a forecast.

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
| [Request or cancel exit](#request-or-cancel-exit) | Eligible requester starts or cancels an exit while retained assignee rights stay intact | [PRJ-020](SPEC.md), [PRJ-021](SPEC.md), [DB-012](SPEC.md) | [AC-PRJ-012](SPEC.md), [AC-PRJ-013](SPEC.md), [AC-DB-002](../../MODULE.md) | Settled in DB-012 and the exit-request state transition table; target keeps existing rights until departure. |
| [Prepare leadership and Task transfers](#prepare-leadership-and-task-transfers) | Mentor/Leader prepares replacement leadership and permitted Task transfers | [PRJ-008](SPEC.md), [PRJ-009](SPEC.md), [PRJ-010](SPEC.md), [TSK-009](../task-management/SPEC.md), [TSK-022](../work-logs-and-effort/SPEC.md) | [AC-PRJ-004](SPEC.md), [AC-PRJ-005](SPEC.md), [AC-PRJ-012](SPEC.md), [AC-PRJ-013](SPEC.md), [AC-TSK-014](SPEC.md) | Worked-Task transfer requires a Leader forecast; direct Mentor removal cannot fabricate that provenance. |
| [Approve, reject or supersede](#approve-reject-or-supersede) | Owning Mentor resolves exit only after atomic leadership and unfinished-work rechecks | [PRJ-021](SPEC.md), [PRJ-022](SPEC.md) | [AC-PRJ-012](SPEC.md), [AC-PRJ-013](SPEC.md) | Settled terminal status mapping (APPROVED, REJECTED, CANCELLED, SUPERSEDED) preserves completed transfer batches. |

### Canonical acceptance scenarios

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-PRJ-004 | PRJ-008–PRJ-011 | Mentor directly removes an ordinary member or current Leader with unfinished Tasks | Ordinary-member removal atomically transfers unfinished Tasks to the current Leader; Leader removal requires a replacement and transfers unfinished Tasks to that replacement; completed Tasks retain the removed member's displayed name and attribution. |
| AC-PRJ-005 | PRJ-009 | Mentor removes an ordinary member with no unfinished Tasks | Membership interval closes and member loses active access without deleting history. |
| AC-PRJ-012 | PRJ-020–PRJ-022, DB-012 | Leader requests another member's removal; the Project has several unfinished Tasks; Leader repeatedly selects multiple Tasks and one eligible recipient per batch; requester then cancels or Mentor rejects/approves | Pending warning shows remaining count/readiness; target keeps existing rights but cannot receive/create new Tasks; each confirmed batch commits immediately and atomically; cancellation/rejection keeps completed reassignments and restores eligibility; approval remains blocked until zero unfinished Tasks, then closes membership and request together. |
| AC-PRJ-013 | PRJ-008, PRJ-010, PRJ-020–PRJ-022 | Current Leader requests own leave with unfinished Tasks | Warning requires replacement first; owning Mentor appoints one; new Leader performs repeatable transfer batches to eligible current members, including a newly direct-added or invitation-accepted member; approval remains blocked until target is no longer Leader and has zero unfinished Tasks. |
| AC-TSK-014 | TSK-022, PRJ-008–PRJ-010 | Mentor attempts direct removal while the target owns worked unfinished Tasks | Removal is rejected before leadership, membership, assignment, request, or notification mutation; after Leader forecast-aware transfers, eligible unworked Tasks retain existing automatic transfer behavior. |

Shared and cross-feature scenarios in [MODULE.md](../../MODULE.md#7-acceptance-criteria)
also apply. Scenario ownership follows the behavior exercised, not every prerequisite
named by its Requirements cell. Relocation preserves every existing expected result.

## 8. Out of Scope

Inherit [module exclusions](../../MODULE.md#8-out-of-scope). Adjacent feature behavior
is a dependency, not a duplicated contract. This split creates no new product behavior,
schema, dependency, PLAN.md or TASKS.md.

## Notes / Open Questions

The complete exit-request status set (`PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`, `SUPERSEDED`) and transition graph are settled in `DB-012` and UC-14.

Read [shared open questions](../../MODULE.md#notes--open-questions) before approving
the technical plan. [plan.md](../../../../../plan.md) is the only progress tracker.
