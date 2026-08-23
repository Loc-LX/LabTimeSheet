# Iteration 2 Git ledger — orchestrator/integration

## Immutable scope

- Candidate branch: `work/reports-ui`
- Verified base: `58a087b118cc955748d7df1aa47d2bbc3ca0371b`
- Accepted integrated implementation: `b761290e0bb586f1a9242b52a2c669ff1f4b888f`
- Reviewed pre-publication candidate: `97ad3396047442152af761e4217bd10a5ca5f3c3`
- Publication commit: the commit containing this file; discover it with `git log -1 -- docs/iterations/iteration-2/git-ledgers/orchestrator-integration.md`.

## Chronology

1. **2026-08-20 17:56 +07 — Base verification.** The taskmaster fetched `origin` read-only and verified local `main` and `origin/main` at `58a087b118cc955748d7df1aa47d2bbc3ca0371b` with no divergence or remote mutation. Root preserved the pre-existing `.DS_Store` modification.
2. **2026-08-20 17:57 +07 — Stale registration cleanup.** Seventeen registered `/private/tmp` worktrees were individually confirmed absent before `git worktree prune --verbose` removed only stale metadata. Branch refs and user data were unchanged.
3. **2026-08-20 18:01 +07 — Five branch worktrees aligned.** Fresh worktrees under `/private/tmp/labtimesheet-iteration2/` fast-forwarded to exact base `58a087b118cc955748d7df1aa47d2bbc3ca0371b`. Shared baseline checks passed Maven 217/217, architecture/Flyway 12/12, `npm ci`/frontend build, and `git diff --check`.
4. **2026-08-20 through 2026-08-21 — Branch production and review.** Each owner used production-shaped RED/GREEN evidence, affected/full verification, and independent review. Final accepted producer heads were Platform `3bc7824ab87d6ecac2464fb0b4ed1600b55f5ea9`, Tasks `265fdab44091d378fe023146f2b60f4ef0f39125`, Projects `ea5f8ce6788abf7cb47343dcf32ab5da1b31f2d7`, and Attendance `b37cf5f47e7eb7f15674adc617047e54001a98ac`; each closed at 0 Critical/Important.
5. **2026-08-21 04:05–04:56 +07 — Ordered integration.** Reports/UI merged the exact reviewed producer heads in required Platform → Tasks → Projects → Attendance order at `9e87bb18a2f246e2ccccca7c57b0051d454edcd9`, `955f0253e535b0600dc87a892d0abaef7b7651f3`, `ebfa245955482ab389e5e887cc37ad924b6cdb64`, and `aa1d0b14d55b14d9e1aedcca53dd4b76b342ba13`. The Project autostash retry preserved all fingerprints and left no conflict/residue.
6. **After ordered producer integration — Integrated implementation/review chain.** Reports/UI completed integration at `19d835da96ed7074d0b6065a285f85adcb1a750a`, then resolved review findings through `5c6e0c67cd7be7bfb914e81743538e7f7d9ae47d`, `126b3e4f4caa400e4ac8cabc9eb55387091f23e9`, `8c3582f9f1f7a362b7a0a044594302e92ddbc868`, `8821ff53bdc7c2350d1071f90326154f74a51a82`, `789a637e35af9e162b0f683547a1c1087d9696ef`, `9925b0bb93fb0a8dc47b2a022acaa49446238683`, `691a35eecafbebbe91a35275f8ed2d089d7089d7`, and accepted implementation `b761290e0bb586f1a9242b52a2c669ff1f4b888f`. Final implementation review returned 0 Critical and 0 Important.
7. **2026-08-21 09:22 +07 — Integrated implementation accepted.** Exact implementation passed Java/PostgreSQL 444/444, architecture/Flyway 13/13 with 23 tables/56 foreign keys, frontend build/UI 7/7, compile/doclint, 260-ID/14-use-case parity, real Java health/readiness, and role-correct local Chromium journeys including SMTP-unavailable persistence.
8. **2026-08-21 09:34 +07 — Plan and exit evidence committed.** `f74a636693526307d0af51c31f90b5998559f211` changed the plan and two workflow/E2E evidence files. Review returned one Important reproducibility finding.
9. **2026-08-21 09:44 +07 — Reproducibility repaired.** `97ad3396047442152af761e4217bd10a5ca5f3c3` changed only E2E evidence to add sanitized runtime commands, 444/444 duration, architecture/Flyway, parity, 30 DONE rows, and Git checks. Same-reviewer re-review returned `APPROVE`, 0 Critical/Important; candidate tree/index were clean.
10. **2026-08-21 — Final artifact audit.** A new read-only audit found the six mandated tracked ledger snapshots absent and the README/plan handoff stale. This bounded publication repair was therefore required before the authorized local `main` merge.

## Dependency and boundary result

- All four producer heads are ancestors of the candidate and appear in required first-parent merge order.
- Cross-feature consumers use public service/DTO boundaries; the architecture/Flyway gate found no foreign persistence or schema drift.
- All 30 Iteration 2 tracker rows are `DONE` and requirement/use-case catalogues remain in parity.
- Root’s pre-existing `.DS_Store`, producer worktrees, detached replay evidence, and Platform’s out-of-scope untracked coordination directory remain protected.

## Runtime and remote state

The prior disposable PostgreSQL 18.4 and Mailpit 1.27.4 runtime was stopped and removed; ports `55441`, `1026`, `8026`, and `18082` were clear. No push, merge request, deployment, force operation, history rewrite, or merge to `main` occurred through the pre-publication candidate. The only network Git action was the initial read-only fetch.

## Local main integration

- A fresh `git fetch origin` left `origin/main` at exact verified base `58a087b118cc955748d7df1aa47d2bbc3ca0371b`.
- Independently approved candidate `f334f13594de49f4b34318d8a3e8bc0556a8063d` merged normally with `--no-ff` into local `main` at `c4f039663f86370b865df036e1756338529e47c9`.
- Merge parents are exact base `58a087b118cc955748d7df1aa47d2bbc3ca0371b` and exact candidate `f334f13594de49f4b34318d8a3e8bc0556a8063d`; all reviewed producer/implementation heads remain ancestors.
- The root index is clean. At merge time the protected `.DS_Store` remained 10,244 bytes with SHA-256 `bf6f1f27ea596b8a0dfd8795ccc9ba41629abb79e75b536d2964146e8815aee8`; the already-uncommitted root `.DS_Store` and `docs/.DS_Store` later changed during exit verification, and neither was staged or restored.
- No push, merge request, deployment, force operation, rebase/squash, history rewrite, branch deletion, or worktree cleanup occurred.
- Exact merged-main tree `600e5fda478f1893d386f32fbf7db3ba19228cff` passed PostgreSQL 18.4 Java 444/444, architecture/Flyway 13/13, Node 24 build/UI 7/7, compile/doclint, 260-ID/14-use-case parity, 30/30 Iteration 2 tracker parity, and real-process aggregate/liveness/readiness checks. The disposable runtime shut down cleanly and was removed.

## What the team should do next

Keep all five persistent branches/worktrees for the next iteration. Stop without pushing; any remote integration requires separate authorization.
