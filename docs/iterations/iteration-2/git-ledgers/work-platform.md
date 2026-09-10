# Iteration 2 Git ledger — `work/platform`

## Immutable scope

- Branch/worktree: `work/platform` / `/private/tmp/labtimesheet-iteration2/platform`
- Verified Iteration 2 base: `58a087b118cc955748d7df1aa47d2bbc3ca0371b`
- Final independently reviewed head: `3bc7824ab87d6ecac2464fb0b4ed1600b55f5ea9`
- Reports/UI integration merge: `9e87bb18a2f246e2ccccca7c57b0051d454edcd9`
- Accepted integrated implementation: `b761290e0bb586f1a9242b52a2c669ff1f4b888f`
- Reviewed pre-publication candidate: `97ad3396047442152af761e4217bd10a5ca5f3c3`
- Publication commit: the commit containing this file; discover it with `git log -1 -- docs/iterations/iteration-2/git-ledgers/work-platform.md`.

## Chronology

1. **2026-08-20 18:01 +07 — Branch setup.** The persistent branch fast-forwarded from `200a17e5bd909db7ac9ce6641bc7eae73a08e2a1` to verified `main` `58a087b118cc955748d7df1aa47d2bbc3ca0371b`. The shared baseline passed 217/217 Maven tests, 12/12 architecture/Flyway checks, the frontend build, and clean-tree checks.
2. **2026-08-20 21:33 +07 — Account and internship lifecycle.** `aff763d94371814611a2ad26487811270cbdd060` added account security/recovery, session invalidation, internship lifecycle, and locked work-window behavior. Review found session-fixation, lock-order, password-reset, lifecycle-time, and lock-retention defects; TDD repairs closed all Critical/Important findings. Final checks included 4/4 focused, 35/35 affected, 244/244 full PostgreSQL tests, compile, doclint, and diff checks.
3. **2026-08-20 23:14 +07 — Integration revision lifecycle.** `89b99e3d161a63f5b9f140da7e6d8b89a7ebdbc8` added encrypted SMTP/HolidayAPI revisions and VN HolidayAPI preview. Review repairs fixed the provider origin, finite timeouts, response classification, and provider-I/O/optimistic-lock concurrency proof. Re-review returned 0 Critical/Important; focused 12/12, affected 62/62, full 255/255, architecture/Flyway 12/12, compile/doclint/diff passed.
4. **2026-08-20 23:34–23:36 +07 — Scalar Account routing.** `b90ede8c59f6df56e807a402edb98e6aff78333e` added authenticated-email scalar routing; `1f55c3a078494ab88673b27bbf66b09a0c4e1bba` pinned evidence. Checks passed 1/1 focused, 44/44 Account, 256/256 full, and 12/12 architecture/Flyway.
5. **2026-08-21 01:00–01:03 +07 — Notification delivery boundary.** `461e93d53bddc6260c03e649fd9ad5483d016c1e` added transaction-bound notification persistence/delivery; `ea2d071a15d81518d4512dbe34d7329584be853c` pinned evidence. Repairs enforced caller transactions, safe internal action URLs, and type-derived channels. Re-review returned 0 Critical/Important; focused 10/10, affected 73/73, full 266/266, architecture/Flyway 12/12, compile/doclint/diff passed.
6. **2026-08-21 01:40–01:44 +07 — Mentor identity handoff.** `a6338fec71f60e45462145d56f0d97de3083f7e8` added the immutable active-global-Mentor projection; `1627a5a3fa512c3cf44afed56459333f9ad11904` pinned evidence. Review returned 0 Critical/Important; focused 1/1, Account 45/45, full 267/267, and architecture/Flyway 12/12 passed.
7. **2026-08-21 03:59–04:01 +07 — Notification inbox.** `47fe16dbdee1980e420fa73b52becc8eb20f8eb1` added the recipient-scoped inbox; `3bc7824ab87d6ecac2464fb0b4ed1600b55f5ea9` pinned evidence and became the final branch head. The repair removed foreign Account persistence access. Final checks passed 2/2 focused, 76/76 affected, 269/269 full, architecture/Flyway 12/12, compile/doclint/diff; review returned 0 Critical/Important.
8. **2026-08-21 04:05 +07 — Consumer integration.** Reports/UI merged exact Platform head first, as required, at `9e87bb18a2f246e2ccccca7c57b0051d454edcd9`.
9. **2026-08-21 09:44 +07 — Integrated acceptance.** Candidate `97ad3396047442152af761e4217bd10a5ca5f3c3` retained exact Platform ancestry. Recorded gates passed 444/444 Maven tests, 13/13 architecture/Flyway checks, frontend build and 7/7 UI tests, compile/doclint, requirement parity, real Java/PostgreSQL health, and role-correct browser journeys.

## Public handoffs

- Account lifecycle and locking: `AccountService#lockedInternWorkWindow`, `AccountService#lockedAccountMutationEligibility`, and `AccountService#requireAccountIdByEmail`.
- Attendance integration: `HolidayApiConfigurationService#preview` and immutable HolidayAPI preview/candidate DTOs.
- Notifications: `NotificationService#publish`, `inboxFor`, and recipient-qualified `markRead` with immutable DTOs and caller-transaction enforcement.
- Global Mentor routing: `AccountService#activeGlobalMentorIdentities`.

No consumer received Platform repositories or entities. No new generic provider abstraction, audit/event store, or unrequested dependency was introduced.

## Remote actions

All recorded Iteration 2 commits and producer merges were local. No push, merge request, deployment, force operation, history rewrite, or merge to `main` occurred through this pre-publication candidate.

## What the team should do next

Keep the producer branch/worktree for Iteration 3 handoff. Local merged-main verification is complete; stop without pushing unless separately authorized.
