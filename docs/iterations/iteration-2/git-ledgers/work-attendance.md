# Iteration 2 Git ledger — `work/attendance`

## Immutable scope

- Branch/worktree: `work/attendance` / `/private/tmp/labtimesheet-iteration2/attendance`
- Verified Iteration 2 base: `58a087b118cc955748d7df1aa47d2bbc3ca0371b`
- Final independently reviewed head: `b37cf5f47e7eb7f15674adc617047e54001a98ac`
- Reports/UI integration merge: `aa1d0b14d55b14d9e1aedcca53dd4b76b342ba13`
- Accepted integrated implementation: `b761290e0bb586f1a9242b52a2c669ff1f4b888f`
- Reviewed pre-publication candidate: `97ad3396047442152af761e4217bd10a5ca5f3c3`
- Completed rows: `I2-ATT-01` through `I2-ATT-07`, plus Attendance-owned `I2-UI-ATT-01`
- Publication commit: the commit containing this file; discover it with `git log -1 -- docs/iterations/iteration-2/git-ledgers/work-attendance.md`.

## Chronology

1. **2026-08-20 18:01 +07 — Branch setup.** The persistent branch fast-forwarded from `7bac9988669ac68e9091bb96ac1cacbb32965c40` to verified `main` `58a087b118cc955748d7df1aa47d2bbc3ca0371b`. Baseline checks passed 217/217 Maven tests, 12/12 architecture/Flyway checks, the frontend build, and clean-tree checks.
2. **2026-08-20 18:12–21:35 +07 — Policy/calendar/leave/correction core.** Implemented effective policy/calendar behavior, leave, corrections, expiry schedulers, retained allocations/history, and correction-adjusted history. Review-driven repairs covered rollback, effective checkout, bounded expiry, post-lock server time, authorization order, N+1 access, canonical locking, transaction-pool safety, ambient rollback, and quota release. Final core milestone `e8d523c67083c721d1bbb1c1c4194e5b1bd9b7c4` passed persistence 21/21, concurrency 5/5, Attendance 63/63, architecture/time 7/7, compile/doclint/diff; review returned 0 Critical/Important.
3. **2026-08-20 21:40–22:36 +07 — Platform internship-window dependency.** Attendance merged Platform producer `aff763d94371814611a2ad26487811270cbdd060` at `c3454a599ea9c8e34c5c2a21d96cc815472aa283` and consumed only `AccountService.lockedInternWorkWindow`. Consumer `76d0a1c30ede54a664bf3cd1bbe8a854f14d8b2e` passed focused 1/1, persistence 26/26, Attendance 68/68, Account contract 9/9, architecture/time 7/7, and full 277/277.
4. **2026-08-20 23:19–2026-08-21 01:08 +07 — HolidayAPI consumer.** Attendance merged Platform HolidayAPI producer at `6f5060a46e590c23fbfb1908f6337bc6958bf4af`. Review found client-supplied provider provenance; `0eb57691265c4afda67e9a60b4d3387dd57d0d46` reduced the request to source/day-off selection and refreshed canonical provider facts server-side, with evidence pin `c462690288a4f4a808713e66dd412bb0e9352ad7`. Checks passed focused 10/10, trust 3/3, affected 48/48, Attendance 77/77, architecture 7/7, full 297/297, compile/doclint/diff; review returned 0 Critical/Important.
5. **2026-08-21 01:08–03:38 +07 — Notification dependencies and consumer.** Attendance merged Platform notification producer at `9e55e569e5c85011f38845c516d9d7af83f34616` and active-Mentor projection at `b41f401958e05b3aa02e59c3b6fd71601737f753`. `7101082b2ea9651e4d6b7ebc25d2d380474a8065` added leave/correction recipients and canonical lock ordering; `c3fcaf917a4885df3d71bf21f27b54662a440408` pinned evidence. Checks passed focused 5/5, affected 85/85, architecture/structure 13/13, full 313/313, compile/doclint/diff; review returned 0 Critical/Important.
6. **2026-08-21 03:40–04:56 +07 — Attendance report producer.** Added bounded date-range daily/summary reporting with historical schedule/policy, raw/effective checkout, violations, denominator, rate, and score. `2fbbf0e84d9a0dab4ebd046e9f99432baa506c53` repaired precision, target-enumeration authorization, and historical-schedule proof; `b37cf5f47e7eb7f15674adc617047e54001a98ac` pinned evidence and became the final head. Checks passed focused 6/6, persistence 38/38, affected 91/91, architecture/Flyway 12/12, full 319/319, compile/doclint/diff; review returned 0 Critical/Important.
7. **2026-08-21 04:56 +07 — Consumer integration.** Reports/UI merged exact Attendance head fourth, as required, at `aa1d0b14d55b14d9e1aedcca53dd4b76b342ba13`.
8. **2026-08-21 09:44 +07 — Integrated acceptance.** Candidate `97ad3396047442152af761e4217bd10a5ca5f3c3` retained exact Attendance ancestry and passed the recorded 444/444 Maven, 13/13 architecture/Flyway, frontend/UI, compile/doclint, parity, runtime, and browser gates.

## Public handoffs

- Attendance publishes `AttendanceReportQueryService` and immutable daily/summary DTOs.
- Attendance consumes Platform locked Intern work-window, HolidayAPI preview, transactional notification, and active-global-Mentor identity DTO/service boundaries.

No Account/Notification repository or entity import, direct business SQL, migration, new dependency, or generic reporting framework was introduced.

## Remote actions

All recorded Iteration 2 commits and dependency/integration merges were local. No push, merge request, deployment, force operation, history rewrite, or merge to `main` occurred through this pre-publication candidate.

## What the team should do next

Keep the producer branch/worktree for Iteration 3 handoff. Complete and pin the local merged-main verification, then stop; do not push without separate authorization.
