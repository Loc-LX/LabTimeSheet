# Security Plan

**Owner:** Loc-LX · each part carries its own state in the table below.

How the rules of [SPEC.md](SPEC.md) will be built. It is a technical design, not a tracker:
progress belongs in [`plan.md`](../../../../../plan.md). Where this plan and a spec disagree, the spec wins.
Its tasks are in [TASKS.md](TASKS.md).

| Part | Subject | Rules | State |
|---|---|---|---|
| E | Evidence for the production profile, below | `SEC-011`, `SEC-013`, `AC-SEC-008` | Draft of 22 September 2026, moved from part B of the platform plan under `D40`; for approval on its own |

## Part E — Evidence for the production profile

**Implements** the evidence `SEC-011` and `SEC-013` lack, gaps the constitution lists. It was
task B-05 of part B of the platform plan, drafted on 22 September 2026, until `D40` placed it with
the rules it builds. It starts after part A is done.

### E.1 Order of work

The task is E-01 in [TASKS.md](TASKS.md). It depends on nothing in part B and may run at any time
after part A.

### E.2 Tests this part changes

E-01 adds a test. Any other test changes only as part A's section A.7 allows.

### E.3 When the part is done

- The test of E-01 passes.
- The full Maven suite, the end-to-end suite and `npm run test:ui` pass.
- The constitution's gap rows for `SEC-011` and `SEC-013` are closed in the same change as the test that closes each.
