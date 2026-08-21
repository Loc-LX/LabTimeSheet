# Iteration 2 Git ledger — `work/projects`

## Immutable scope

- Branch/worktree: `work/projects` / `/private/tmp/labtimesheet-iteration2/projects`
- Verified Iteration 2 base: `58a087b118cc955748d7df1aa47d2bbc3ca0371b`
- Final independently reviewed head: `ea5f8ce6788abf7cb47343dcf32ab5da1b31f2d7`
- Reports/UI integration merge: `ebfa245955482ab389e5e887cc37ad924b6cdb64`
- Accepted integrated implementation: `b761290e0bb586f1a9242b52a2c669ff1f4b888f`
- Reviewed pre-publication candidate: `97ad3396047442152af761e4217bd10a5ca5f3c3`
- Completed rows: `I2-PRJ-01` through `I2-PRJ-06`
- Publication commit: the commit containing this file; discover it with `git log -1 -- docs/iterations/iteration-2/git-ledgers/work-projects.md`.

## Chronology

1. **2026-08-20 18:01 +07 — Branch setup.** The persistent branch fast-forwarded from `7bac9988669ac68e9091bb96ac1cacbb32965c40` to verified `main` `58a087b118cc955748d7df1aa47d2bbc3ca0371b`. Baseline checks passed 217/217 Maven tests, 12/12 architecture/Flyway checks, the frontend build, and clean-tree checks.
2. **2026-08-20 18:18 +07 — Invitation and exit RED/GREEN.** Added Project-owned invitation issuance/response, direct-add supersession, leadership provenance, pending exit request/cancellation, retained membership intervals, and Task-context DTOs. The first production-shaped RED was missing service behavior; first PostgreSQL GREEN passed 5/5.
3. **2026-08-20 21:39 +07 — Platform lifecycle dependency.** Projects consumed reviewed Platform producer `aff763d94371814611a2ad26487811270cbdd060` for locked Account/Intern mutation eligibility. A host-metadata permission failure occurred before mutation; the authorized retry succeeded without overwriting Project work.
4. **2026-08-20 22:57–23:24 +07 — First reviewed producer.** Review found authorization-before-terminal, completed-Intern mutation, lifecycle-lock, invitation-cleanup, and premature completion API defects. The unsafe invitation sweep was removed, and actor preauthorization was proved before Account/Project locking. Reviewed producer `2cd0fa401ac41377cbee5ab0971cd756acd4e5f0` passed focused 20/20, affected 71/71, full 263/263, architecture 10/10, compile/doclint/diff; review returned 0 Critical/Important.
5. **2026-08-21 00:46 +07 — Task lifecycle dependency.** Projects fast-forwarded to Task producer `c55604c7dae68ea8769e9435ab6885706516d46c`, consuming public unfinished-count, transfer, completion-readiness, progress, and retained-history services without Task persistence access.
6. **2026-08-21 01:40 +07 — Platform notification dependency.** Projects merged Platform notification producer `ea2d071a15d81518d4512dbe34d7329584be853c` at `5d392a69550dc5eb6f11d321e6a45c95b5b5d2db`, then added invitation/exit consumers through mandatory transactional publication.
7. **2026-08-21 02:25–02:39 +07 — Lock-order repairs.** PostgreSQL deadlock evidence moved recipient routing and ascending Account/profile locks before the Project lock and added the owning Mentor to the prelock set. Verification reached invitation/exit 22/22, lifecycle 4/4, affected 84/84, architecture 11/11, boundary 7/7, and full 330/330.
8. **2026-08-21 03:25 +07 — Task notification dependency.** Projects merged Task notification producer `265fdab44091d378fe023146f2b60f4ef0f39125` at `7f37666a7165599e063d82e4bf80b9bee807bb10`. Combined verification passed affected 84/84, full 334/334, architecture 11/11, boundary 7/7, compile/doclint/diff.
9. **2026-08-21 04:35 +07 — Final reviewed head.** `91f74146a15e24bac3d22aae05e052cdfec8c76c` repaired historical-requester Account-before-Project ordering and retained membership/leadership actor provenance; `ea5f8ce6788abf7cb47343dcf32ab5da1b31f2d7` pinned evidence. Final checks passed focused 28/28, affected 86/86, architecture 11/11, boundary 7/7, full 336/336, compile/doclint/diff; same-reviewer checks returned 0 Critical/Important.
10. **2026-08-21 04:44 +07 — Consumer integration.** Reports/UI merged exact Project head third, as required, at `ebfa245955482ab389e5e887cc37ad924b6cdb64`.
11. **2026-08-21 09:44 +07 — Integrated acceptance.** Candidate `97ad3396047442152af761e4217bd10a5ca5f3c3` retained exact Project ancestry and passed the recorded 444/444 Maven, 13/13 architecture/Flyway, frontend/UI, compile/doclint, parity, runtime, and browser gates.

## Public handoffs

- `ProjectTaskContext` exposes current Leader membership, active members with `joinedAt`, and pending-exit membership IDs.
- `ProjectQueryService.membershipIntervals(long)` exposes retained membership intervals.
- Project services own invitation/exit decisions, leadership replacement, direct removal, completion, and authorized retained history.
- Projects consume only Platform Account/notification and Task transfer/readiness/progress/history service/DTO boundaries.

No foreign repository/entity, direct business SQL, or generic workflow/audit layer was introduced.

## Remote actions

All recorded Iteration 2 commits and dependency/integration merges were local. No push, merge request, deployment, force operation, history rewrite, or merge to `main` occurred through this pre-publication candidate.

## What the team should do next

Keep the producer branch/worktree for Iteration 3 handoff. Local merged-main verification is complete; stop without pushing unless separately authorized.
