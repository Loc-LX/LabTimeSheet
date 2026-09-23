# Architecture Plan

**Owner:** Loc-LX · each part carries its own state in the table below.

How the rules of [SPEC.md](SPEC.md) will be built. It is a technical design, not a tracker:
progress belongs in [`plan.md`](../../../../../plan.md). Where this plan and a spec disagree, the spec wins.
Its tasks are in [TASKS.md](TASKS.md).

| Part | Subject | Rules | State |
|---|---|---|---|
| A | Module boundaries, in the [platform plan](../../PLAN.md#part-a--module-boundaries) until `A-12` closes it | `ARC-005`, `ARC-006`, `AC-ARC-001` | Approved on 22 September 2026 |
| D | Business SQL behind the data-access layer, below | `ARC-006`, `D18` | Draft of 22 September 2026, moved from part B of the platform plan under `D40`; for approval on its own |

## Part D — Business SQL behind the data-access layer

**Implements** the business-SQL half of `ARC-006` and `D18`, a gap the constitution lists. It was
task B-07 of part B of the platform plan, drafted on 22 September 2026, until `D40` placed it with
the rule it builds. It starts after part A is done.

### D.1 What the current code offers

Native SQL in a service breaks `ARC-006` and `D18`: `ProjectService` deletes a draft Project
through `nativeDelete`. D-01 places that SQL behind the data-access layer with its behavior
unchanged; the emptiness check and notification deletion that `PRJ-002` requires belong to the
[Project lifecycle](../../../project/features/lifecycle/SPEC.md) plan, since they change behavior.

### D.2 Order of work

The task is D-01 in [TASKS.md](TASKS.md). It depends on nothing in part B and may run at any time
after part A.

### D.3 Tests this part changes

Behavior is unchanged, so a test changes only as part A's section A.7 allows.

### D.4 When the part is done

- The test of D-01 passes.
- The full Maven suite, the end-to-end suite and `npm run test:ui` pass.
- The constitution's business-SQL gap row of `ARC-006` is closed in the same change as the test that closes it.
