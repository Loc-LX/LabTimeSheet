# ADR-005 — One authorization policy decides every business permission

- **Status:** Accepted
- **Date:** 2026-09-14
- **Adds:** `AUTH-012`, `AC-AUTH-011`
- **Implemented:** not yet; the code change belongs to the implementation plan

## Context

Business permissions are decided today wherever a developer needed one. Admin
access to the Attendance report alone is settled in three places: a URL rule in
`SecurityConfiguration`, a branch in `AttendanceReportQueryService#authorize`
that Admin shares with Mentor, and `sec:authorize="hasRole('ADMIN')"` in the
shared layout. None of them can grant or withdraw a single field.

Two decisions of 14 September 2026 make that unworkable.

- The instructor granted Admin read access to every report for now and expects to
  withdraw parts of it later (`D1`). Withdrawing one capability should not mean
  finding every place a role is checked.
- Task status changes are now decided by who acts, in which scope, from which
  status, to which status (`D13`, `TSK-023`). A role check cannot express that,
  and the existing code shows the failure it invites: `TaskService#changeStatus`
  lets an owning Mentor set any status because the Mentor is "above" the assignee.

## Decision

One authorization policy is the source of truth for business permission
decisions. It takes the actor's role, the actor's scope resolved from stored
context (ownership, membership, leadership term, assignment), the current state of
the record, and, for a transition, the target state.

- Each capability in the permission matrix of the platform spec, §5.2, is its own
  entry in the policy. A capability granted to several roles is still one entry
  per role, so one can be withdrawn without touching another.
- A higher role never implies a capability the matrix does not grant.
- Report datasets are divided into field groups, and each group is a capability.

The layers keep their jobs:

| Layer | Job |
|---|---|
| `SecurityConfiguration` | Coarse route protection: authenticated or not, and broad role gates. |
| Service, through the policy | The authoritative decision, taken inside the transaction from stored context. |
| Template | Asks the same policy only to decide what to show. Never enforcement. |

No business permission is decided by a role check outside the policy, including
`hasRole('ADMIN')` in a template or `role == ADMIN` in a service.

## Rationale

The matrix is already the specification of who may do what. Making the policy
mirror it row for row means a change to the matrix has exactly one place to land
in code, and a test can read the matrix and exercise every cell.

Keeping route protection in `SecurityConfiguration` is deliberate. It is cheap
defence in depth. What changes is that it is no longer where a business rule is
decided.

## Consequences

- Existing role checks in services and templates move behind the policy. The known
  ones are the three Admin Attendance checks above, the Admin denials in the
  Project/Task and Daily report paths, and the Mentor branch in
  `TaskService#changeStatus`.
- The two tests that assert an owning Mentor may set any Task status,
  `TaskCreationIntegrationTest#owningMentorCanChangeStatusForAnyTaskOnAnActiveProject`
  and `TaskMutationBoundaryTest#owningMentorCanChangeStatusForAnyProjectTask`,
  contradict `TSK-023` and change with it.
- A matrix-driven test replaces scattered per-role assertions for the capabilities
  it covers.

## What this does not decide

- Which library or Spring mechanism implements the policy. That is a planning
  choice.
- Any individual permission. Those stay in the specs and the §5.2 matrix.
