# Authorization Plan

**Owner:** Loc-LX · the part carries its own state in the table below.

How the rules of [SPEC.md](SPEC.md) will be built. It is a technical design, not a tracker:
progress belongs in [`plan.md`](../../../../../plan.md). Where this plan and a spec disagree, the spec wins.
Its tasks are in [TASKS.md](TASKS.md). Part B was written as part of the platform plan and moved
here under `D40`, which also moved two of its tasks to the rules they build; its part letter and section numbers are kept so that existing citations resolve.

| Part | Subject | Rules | State |
|---|---|---|---|
| B | One authorization policy, below | `AUTH-012`, `AC-AUTH-011`, [ADR-005](../../../../rfcs/ADR-005-one-authorization-policy.md), and the gap of `AUTH-002` | Draft of 22 September 2026, for approval on its own; replaces version 1.0 of 16 September |

## Part B — One authorization policy

**Implements** `AUTH-012` and `AC-AUTH-011`, as [ADR-005](../../../../rfcs/ADR-005-one-authorization-policy.md)
decides, and closes the gap of `AUTH-002` the constitution lists. As drafted on 22 September it
also closed the gaps of `SEC-011` and `SEC-013` and the business-SQL half of `ARC-006`; under
`D40` those moved with their tasks to part E of the [Security plan](../security/PLAN.md) and part D
of the [Architecture plan](../architecture/PLAN.md). It starts after part A is done, because the
policy belongs to `platform` and every scope it needs is resolved by the module that owns the
record. This text replaces version 1.0 of 16 September 2026; git keeps that version.

### B.1 What the rule requires

`AUTH-012` takes four inputs for every business permission: the actor's role, the actor's
scope in stored context, the record's current state, and, for a transition, the target state.
A higher role never implies a capability the §5.2 matrix does not grant, and no business
permission is decided by a role check outside the policy.

### B.2 The capability catalogue

The §5.2 matrix has 36 capabilities in 4 actor columns, 144 cells, counted on 22 September
2026. The catalogue is the policy's data: one entry per capability and actor column, so one
entry can be withdrawn alone, which `D1` needs.

| Element | Source | Note |
|---|---|---|
| Capability | one §5.2 row | Named after the row, not after a controller method |
| Actor | one §5.2 column | Not a role: derived from the pair of signed-in user and target record |
| Scope predicate | the rule the row cites | Ownership, active membership, current leadership term, current assignee, own record, responsible Mentor |
| State predicate | the rule the row cites | Project status, Task status, request status, attendance period state |
| Target state | `TSK-007`, `TSK-023`, `PRJ-002`, `ATT-024` | Only for transitions |

**Role** keeps the three values `app_users` stores, which `DB-005` protects. **Actor** names a
§5.2 column, and none of the four columns is a role: "Current Leader" is an `INTERN` account
holding a current leadership term on the Project the record belongs to. Two consequences hold:

- A user can satisfy several columns for the same record. The policy takes the **union** of the cells those columns grant and never ranks columns, because ranking is the inference `AUTH-012` and `TSK-023` forbid. A refusal is the absence of any granting cell.
- `LEADER` never becomes a Spring Security authority, `hasRole`, `hasAnyRole` or `sec:authorize` value, because the term it depends on can end between two requests (`AUTH-004`).

The row "Lock/deactivate accounts; manage internship lifecycle" covers every Admin account
transition of `ACC-014`: lock, unlock, deactivation including that of a pending account
(`ACC-028`), and reinstatement (`ACC-029`). `ACC-014` requires all of them to be explicit Admin
actions; the row names lock and deactivation only, so this mapping is recorded here rather than
left to the implementer.

### B.3 Where the catalogue lives

A resource file of `platform`, read once at startup, one entry per capability and actor column,
shaped like the §5.2 table. The application refuses to start when the file names an unknown
capability or a matrix row has no entry. Withdrawing a capability edits one line, which is still a
commit and a deployment. A database table with an Admin screen would need a permission to manage
permissions, which `GOV-007` never granted; Java constants would break the promise of `D1`. This
is a plan-level choice the spec leaves open.

### B.4 Where the decision is taken

| Layer | May do | May not do |
|---|---|---|
| `SecurityConfiguration` | Authenticated or not, and coarse role gates on routes | Decide a business permission |
| A module's service, inside its transaction | Resolve the actor's scope from stored context, ask the policy, enforce the answer | Read a role to decide |
| Template | Ask the same policy what to show | Count as enforcement (`AUTH-002`) |

**The policy decides; it never looks anything up.** The module that owns the record resolves the
actor's scope from stored context inside its transaction and passes it, with the record's state,
to the policy, as `R6` of `D28` decided. The policy belongs to `platform`, which depends on no
feature (`ARC-005`), so it cannot read a membership or a leadership term, and it issues no query
of its own (`ARC-010`). Version 1.0 said the policy resolves scope from stored context, which
contradicted its own risk section and `R6`; this paragraph replaces that sentence.

Each owning module provides the scope its records need: `project` the owning Mentor, the current
Leader, active membership and the current assignee; `internship` the responsible Mentor
(`ACC-026`); `attendance` the owner of a record or request, using the responsible Mentor from
`internship`; `identity` whether the actor is an active account of a given role. A leadership term
that ends between two requests is seen by the next one, because the scope is read in its
transaction.

### B.5 What the current code offers

The code is material, not the design. On 16 September 2026 a text survey counted 50 places in 13
files that compare or gate on a role. Part A changes those file locations, so the survey is not
repeated here: the fixture of B-01 enumerates the work instead, because a text search cannot see a
role decision expressed another way. Two known cases contradict the specification and change in
this part: `TaskService#changeStatus` lets an owning Mentor set any status (`TSK-023`), and the
Attendance report makes three separate Admin checks that `ADR-005` names.

Native SQL in `ProjectService` is part D of the [Architecture plan](../architecture/PLAN.md).

### B.6 Alternatives rejected

| Alternative | Why not |
|---|---|
| Method security annotations per method | Express role, not scope and state; a withdrawal edits dozens of annotations |
| One service-side `if` per capability, no catalogue | `AC-AUTH-011` cannot iterate the matrix, and a withdrawal touches many files |
| Decisions in `SecurityConfiguration` | Route rules cannot see a record's state or the caller's membership |

### B.7 Order of work

The tasks are B-01 to B-04 and B-06 in [TASKS.md](TASKS.md); B-05 became E-01 of the Security plan and B-07 became D-01 of the Architecture plan (`D40`). B-01 comes first because its fixture is the work list of B-02 and B-03, and B-04 last among the policy tasks because a withdrawal proves something only once every cell goes through the policy. B-06 depends on nothing in B-01 to B-04 and may run in any order after part A.

The grant half of `TSK-023`, block, unblock and reopen by the owning Mentor and the Leader, needs
`task_status_transitions` to restore the status held before a block (`TSK-025`), so it belongs to
the [Task management](../../../project/features/task-management/SPEC.md) plan after part C creates that table.

### B.8 Tests this part changes

This part changes behavior on purpose wherever the code grants what the matrix refuses. An
assertion may change only where it asserts such a capability; the commit names, for each changed
assertion, the §5.2 row and the rule that refuse it (`TST-011`). Any other test changes only as
part A's section A.7 allows. A cell that B-01 records as failing is fixed in code, never by
changing the fixture.

### B.9 Risks

| Risk | Handling |
|---|---|
| A capability loses a scope condition when it goes through the policy | B-01 records every cell first, so any change of answer shows in the fixture's result |
| The policy is asked with a stale scope | The owning module resolves scope inside the same transaction as the change it guards |
| Templates keep deciding | B-03 replaces template role checks with policy calls; B-06 tests that a hidden control grants nothing |
| A list asks the policy once per row | A list decides one capability for one scope and applies it to the rows; where a row has its own state the policy receives the loaded rows and issues no query. A test counts policy calls and queries at one row and at fifty (`AC-ARC-002`) |

### B.10 When the part is done

- All 144 cells pass through the policy, and the withdrawal test passes (`AC-AUTH-011`).
- No `LEADER` or `ROLE_LEADER` value appears as an authority or in `hasRole`, `hasAnyRole` or `sec:authorize`.
- The test of B-06 passes, and the query-count test of `AC-ARC-002` passes.
- The full Maven suite, the end-to-end suite and `npm run test:ui` pass.
- The constitution's gap rows for `AUTH-002` and `AUTH-012` are closed in the same change as the test that closes each.

### B.11 Not in this part

Business rules of a single feature, including the grant half of `TSK-023`; an Admin screen for
permissions; the schema change (part C).
