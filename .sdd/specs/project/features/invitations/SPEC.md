# Project invitations Spec

**Version:** 1.1.1 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-21

**Module:** `project` · **Shared contract:** [MODULE.md](../../MODULE.md)

## 1. Context & Goal

Invite an eligible Intern and resolve the invitation with retained provenance.

This is one cohesive planning scope. Operations below are sections of this feature,
not separate modules or mandatory branches. The split changes document ownership only.

## 2. Actors & Roles

Current Leader issues; intended Intern accepts/declines; owning Mentor and issuer have the specified revocation rights.

Use cases defined here: UC-13.

The [platform permission matrix](../../../platform/features/authorization/SPEC.md#52-permission-matrix)
and the module's shared authorization rules apply to every operation.

## 3. Functional Requirements

The operation summaries below are navigation. The numbered rules remain authoritative;
they do not acquire additional behavior from a heading or summary.

### Issue invitation

PRJ-018 and DB-011 require eligible participants, same-Project provenance and pending uniqueness.

### Respond, revoke or supersede

PRJ-019 and DB-011 define resolution and the recorded reason; shared NOT-010 selects recipients.

### Canonical feature rules

| ID | Requirement |
|---|---|
| PRJ-018 | WHILE a Project is `PLANNED` or `ACTIVE`, THE system SHALL permit the current Leader at most one pending invitation per eligible Intern. THE system SHALL NOT expire an invitation by time, SHALL preserve the issuing leadership term, and SHALL require the intended Intern to authenticate before any response. An emailed URL SHALL open only the authenticated response page. |
| PRJ-019 | THE system SHALL permit only the intended Intern to accept or decline their pending invitation. WHEN acceptance is submitted, THE system SHALL recheck Project, invitee, issuing leadership, and membership state and create one membership, all in one transaction. THE system SHALL permit the issuing Leader to revoke their own pending invitations and the owning Mentor to revoke any. WHEN the issuing leadership ends, the Project completes, or the invitee becomes ineligible, THE system SHALL revoke the now-unusable pending invitation without deleting it. |
| DB-011 | THE schema SHALL preserve, in `project_invitations`, the Project, intended Intern, issuing leadership term, status, optional accepted membership, resolution code, actor and time, and an optimistic version. THE schema SHALL constrain the status to `PENDING`, `ACCEPTED`, `DECLINED`, `REVOKED`, or `SUPERSEDED`. THE schema SHALL constrain the resolution code to exactly these nine values, each recording why the invitation stopped being pending: `INVITEE_ACCEPTED` when the intended Intern accepted and became a member, `INVITEE_DECLINED` when they declined, `INVITER_REVOKED` when the issuing Leader withdrew it, `MENTOR_REVOKED` when the owning Mentor withdrew it, `LEADER_CHANGED` when the issuing leadership term ended before any response, `PROJECT_COMPLETED` when Project completion revoked it, `PROJECT_CANCELLED` when Project cancellation revoked it, `INVITEE_INELIGIBLE` when the intended Intern stopped satisfying membership eligibility, and `MENTOR_DIRECT_ADD` when a direct Mentor addition superseded it. THE schema SHALL use partial uniqueness and same-Project composite foreign keys so that a duplicate pending invitation and cross-Project provenance are both impossible. |

#### UC-13 — Respond to a Project invitation

**Parts P3.**

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

#### State transitions: Project invitation

The transitions the rules above allow. The table adds nothing to them: where it and a rule differ, the rule wins.

| From | Action | To | Who | Rules |
|---|---|---|---|---|
| (none) | issue an invitation to an eligible Intern | `PENDING` | current Leader | `PRJ-018`, `DB-011` |
| `PENDING` | accept, creating membership | `ACCEPTED` | intended Intern | `PRJ-019`, `DB-011` |
| `PENDING` | decline | `DECLINED` | intended Intern | `PRJ-019`, `DB-011` |
| `PENDING` | revoke own invitation | `REVOKED` | issuing Leader | `PRJ-019`, `DB-011` |
| `PENDING` | revoke any Project invitation | `REVOKED` | owning Mentor | `PRJ-019`, `DB-011` |
| `PENDING` | leadership ends, Project completes or cancels, or invitee becomes ineligible | `REVOKED` | system | `PRJ-019`, `PRJ-023`, `DB-011` |
| `PENDING` | direct Mentor addition creates membership | `SUPERSEDED` | system | `PRJ-017`, `DB-011` |

No transition leaves `ACCEPTED`, `DECLINED`, `REVOKED`, or `SUPERSEDED`.

Public comparison points: [Azure Boards organization access](https://learn.microsoft.com/en-us/azure/devops/organizations/accounts/add-organization-users?view=azure-devops) and [Atlassian user invitation management](https://support.atlassian.com/user-management/docs/invite-a-user/), checked on 21 September 2026: invitations remain pending until explicitly accepted, declined, or revoked by an authorized role, and direct administrative provisioning supersedes pending invitations without leaving duplicate access.

## 4. Non-functional Requirements

Inherit [module constraints](../../MODULE.md#4-non-functional-requirements) and
[platform constraints](../../../platform/MODULE.md#4-non-functional-requirements),
including authorization, history, concurrency, server time and test evidence.

## 5. Data

`project_invitations`; accepted membership belongs to membership-and-leadership.

The [module data contract](../../MODULE.md#5-data) retains ownership, constraints and
checks across features. The full physical model stays in platform §19. A feature folder
does not grant repository/entity access across the module boundaries of ARC-006.

### Required contracts

These are required facts or behaviors, not prescribed cross-module imports or an
automatic implementation order. PLAN resolves delivery predecessors against what
already exists and preserves ARC-005/ARC-006.

| Provider | Required fact or behavior | Rule trace |
|---|---|---|
| [project/membership-and-leadership](../membership-and-leadership/SPEC.md) | Current issuing Leader and target membership eligibility | [PRJ-005](../membership-and-leadership/SPEC.md), [PRJ-017](../membership-and-leadership/SPEC.md) |
| [project/lifecycle](../lifecycle/SPEC.md) | Project lifecycle and cancellation | [PRJ-002](../lifecycle/SPEC.md), [PRJ-023](../lifecycle/SPEC.md) |
| [project](../../MODULE.md) | Recipients and stored-context authorization | [NOT-010](../../MODULE.md), [AUTH-011](../../MODULE.md) |

### Related workflows and joint checks

- [Membership and leadership](../membership-and-leadership/SPEC.md): Eligibility and leadership changes.
- [Project lifecycle](../lifecycle/SPEC.md): Completion/cancellation supersedes invitations.

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
| [Issue invitation](#issue-invitation) | Current Leader invites an eligible Intern with unique pending provenance | [PRJ-017](../membership-and-leadership/SPEC.md), [PRJ-018](SPEC.md), [DB-011](SPEC.md) | [AC-PRJ-010](SPEC.md), [AC-DB-002](../../MODULE.md) | Exercise duplicate issuance and acceptance racing direct addition. |
| [Respond, revoke or supersede](#respond-revoke-or-supersede) | Intended Intern or authorized revoker resolves an invitation with the recorded reason | [PRJ-019](SPEC.md), [DB-011](SPEC.md), [NOT-010](../../MODULE.md) | [AC-PRJ-011](SPEC.md) | Settled in DB-011 and the invitation state transition table; revocation reasons map to the matching resolution code. |

### Canonical acceptance scenarios

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-PRJ-010 | PRJ-017–PRJ-019, DB-011 | Leader invites an eligible Intern; the Intern accepts while Mentor direct-add races | Exactly one active membership commits. Invitation becomes `ACCEPTED` for the winning acceptance or `SUPERSEDED` for Mentor direct-add; losing request returns conflict without duplicate history. |
| AC-PRJ-011 | PRJ-018–PRJ-019 | Invitee declines, issuing Leader revokes, leadership changes, Project completes, and invitee becomes ineligible in separate cases | Each pending invitation reaches the correct terminal status/code, remains historical, and cannot later be accepted. |

Shared and cross-feature scenarios in [MODULE.md](../../MODULE.md#7-acceptance-criteria)
also apply. Scenario ownership follows the behavior exercised, not every prerequisite
named by its Requirements cell. Relocation preserves every existing expected result.

## 8. Out of Scope

Inherit [module exclusions](../../MODULE.md#8-out-of-scope). Adjacent feature behavior
is a dependency, not a duplicated contract. This split creates no new product behavior,
schema, dependency, PLAN.md or TASKS.md.

## Notes / Open Questions

Invitation statuses (`PENDING`, `ACCEPTED`, `DECLINED`, `REVOKED`, `SUPERSEDED`), resolution codes and the transition graph are settled in `DB-011` and UC-13.

`DB-011` is ahead of the schema in one place, recorded in `D39`: `V1__baseline.sql` accepts
eight resolution codes, not nine, so `PROJECT_CANCELLED` and therefore `AC-PRJ-015` need the
migration that `D32` already requires. The rule is correct as written; the plan must carry
that delta rather than narrow the rule to match today's schema.

Read [shared open questions](../../MODULE.md#notes--open-questions) before approving
the technical plan. [plan.md](../../../../../plan.md) is the only progress tracker.
