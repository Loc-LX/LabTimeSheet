# Iteration 2 Git ledger — `work/tasks`

## Immutable scope

- Branch/worktree: `work/tasks` / `/private/tmp/labtimesheet-iteration2/tasks`
- Verified Iteration 2 base: `58a087b118cc955748d7df1aa47d2bbc3ca0371b`
- Final independently reviewed head: `265fdab44091d378fe023146f2b60f4ef0f39125`
- Reports/UI integration merge: `955f0253e535b0600dc87a892d0abaef7b7651f3`
- Accepted integrated implementation: `b761290e0bb586f1a9242b52a2c669ff1f4b888f`
- Reviewed pre-publication candidate: `97ad3396047442152af761e4217bd10a5ca5f3c3`
- Publication commit: the commit containing this file; discover it with `git log -1 -- docs/iterations/iteration-2/git-ledgers/work-tasks.md`.

## Chronology

1. **2026-08-20 18:01 +07 — Branch setup.** The persistent branch fast-forwarded from `7bac9988669ac68e9091bb96ac1cacbb32965c40` to verified `main` `58a087b118cc955748d7df1aa47d2bbc3ca0371b`. Baseline checks passed 217/217 Maven tests, 12/12 architecture/Flyway checks, the frontend build, and clean-tree checks.
2. **2026-08-20 18:57–18:58 +07 — Task lifecycle/query producer.** `15920f38ae40e8457dce1eb6e1624483e2d1edf6` added Task definition, reassignment, soft-delete/history, transfer, progress, and member queries; `c9f93e0f2c40185b79095e8d000b27effbc10966` pinned evidence. Repairs added ordered transfer IDs, a single-statement progress aggregate, and reassignment-away/back attribution coverage. Review returned no material findings; affected Task tests passed 72/72.
3. **2026-08-20 21:38 +07 — Platform lifecycle dependency.** Tasks merged reviewed Platform producer `aff763d94371814611a2ad26487811270cbdd060` at `2d84f00204c6ec9186db0d0569102e3d433052d0` without overwriting Task files.
4. **2026-08-20 23:37 +07 — Account and Project dependencies.** Tasks merged Platform scalar routing at `19d91d391f5d17a44a48a8878060480a442b9732`, then Project membership/pending-exit producer `2cd0fa401ac41377cbee5ab0971cd756acd4e5f0` at `8b142df3e2050e529c5a2df74d04b63c54be12b1`. Six in-progress Task paths were preserved; Git autostash reapplied without conflict or residue.
5. **2026-08-21 00:32–00:38 +07 — Work logs and exit rights.** `1967f211e25aefb40ddb42c077abbd6db210ffba` added work logs, serialized daily totals, correction, pending-exit rights, and transfer behavior; `c55604c7dae68ea8769e9435ab6885706516d46c` pinned evidence. Repairs covered pending-exit rights/transfer-away, reassignment-before-correction, and canonical lock order. Re-review returned 0 Critical/Important; focused 14/14, work-log 9/9, affected 89/89, full 309/309, compile/doclint/diff passed.
6. **2026-08-21 01:51 +07 — Notification ancestry.** Tasks merged Platform notification and active-Mentor ancestry through `1627a5a3fa512c3cf44afed56459333f9ad11904` at `caad2d66d337c68602a509d6764ed4fc781a6ba4`.
7. **2026-08-21 03:08–03:13 +07 — Notification producers.** `a6b257f7f23a9db07da964093000d96a51d0a0c0` added assignment, reassignment, transfer, comment, status, and self-Task notification production; `265fdab44091d378fe023146f2b60f4ef0f39125` pinned evidence and became the final head. Repairs preserved historical assignee delivery without foreign persistence and retained lock ordering. Re-review returned 0 Critical/Important; notification 53/53, Task 94/94, full 323/323, boundary 9/9, compile/doclint/diff passed.
8. **2026-08-21 04:10 +07 — Consumer integration.** Reports/UI merged exact Task head second, as required, at `955f0253e535b0600dc87a892d0abaef7b7651f3`.
9. **2026-08-21 09:44 +07 — Integrated acceptance.** Candidate `97ad3396047442152af761e4217bd10a5ca5f3c3` retained exact Task ancestry and passed the recorded 444/444 Maven, 13/13 architecture/Flyway, frontend/UI, compile/doclint, parity, runtime, and browser gates.

## Public handoffs

- `TaskTransferService#transferBatch`, `transferAllUnfinished`, and `unfinishedCount` support Project exit/direct-removal orchestration with atomic ordered locking.
- `TaskQueryService#projectProgress`, `memberWork`, and `history` expose immutable progress, work-total, and retained-history DTOs.
- Task mutations preserve creator/current-assignee rules, pending-exit existing rights, author-only work-log correction, assignment attribution, comments, work logs, and lifecycle timestamps.
- Notification-producing mutations call the Platform boundary inside their authorized domain transaction.

No Project or Account repository/entity, Task-assignment-history table, audit layer, or direct production SQL was introduced.

## Remote actions

All recorded Iteration 2 commits and dependency/integration merges were local. No push, merge request, deployment, force operation, history rewrite, or merge to `main` occurred through this pre-publication candidate.

## What the team should do next

Review the exact ledger-publication head, fetch and verify upstream `main`, merge that immutable candidate normally into local `main`, then run the complete merged-main gate. Do not push without separate authorization.
