# Iteration 2 Git ledger — `work/reports-ui`

## Immutable scope

- Branch/worktree: `work/reports-ui` / `/private/tmp/labtimesheet-iteration2/reports-ui`
- Verified Iteration 2 base: `58a087b118cc955748d7df1aa47d2bbc3ca0371b`
- Accepted integrated implementation: `b761290e0bb586f1a9242b52a2c669ff1f4b888f`
- Reviewed pre-publication candidate: `97ad3396047442152af761e4217bd10a5ca5f3c3`
- Publication commit: the commit containing this file; discover it with `git log -1 -- docs/iterations/iteration-2/git-ledgers/work-reports-ui.md`.

## Chronology

### 2026-08-20 — Setup and owned milestones

1. The persistent branch fast-forwarded from `7bac9988669ac68e9091bb96ac1cacbb32965c40` to verified base `58a087b118cc955748d7df1aa47d2bbc3ca0371b`. The shared baseline passed 217/217 Maven tests, 12/12 architecture/Flyway checks, frontend build, and clean-tree checks.
2. `49c9075aa6c33656201acc5c654e243d830028eb` added shared accessible error-summary, drawer, and report-chart fragments. Focused 1/1 and affected 51/51 passed.
3. `5308e913dc10248ec4482fa8c85beb3f59934d89` added the attendance/compliance HTML report consumer. Focused 4/4 and affected 56/56 passed.
4. `6c869b65c99903d8b0ac0db19a3c10b06c920e01` added the Project/Task HTML report consumer. Focused 4/4 and affected 60/60 passed.
5. `9172f1b10005a0d13f8b7de1c00b9f964981473a` added dashboard notification and workflow rendering contracts; `c78b2a2be1b175c6d84774da6d41ac144ee1344c` pinned evidence. Dashboard 4/4, workflow 2/2, shared UI 3/3, and affected 63/63 passed.
6. Clean dependency-waiting head `c78b2a2be1b175c6d84774da6d41ac144ee1344c` passed the then-complete 230/230 Maven suite.

Host-managed Git metadata locks stopped several staging/merge attempts before mutation. Authorized retries completed normally without force, rebase, squash, history rewrite, source conflict, or preserved-path loss.

### 2026-08-21 — Reviewed producer integration

The reviewed producer heads were merged in the plan’s required order:

1. Platform `3bc7824ab87d6ecac2464fb0b4ed1600b55f5ea9` → `9e87bb18a2f246e2ccccca7c57b0051d454edcd9`
2. Tasks `265fdab44091d378fe023146f2b60f4ef0f39125` → `955f0253e535b0600dc87a892d0abaef7b7651f3`
3. Projects `ea5f8ce6788abf7cb47343dcf32ab5da1b31f2d7` → `ebfa245955482ab389e5e887cc37ad924b6cdb64`
4. Attendance `b37cf5f47e7eb7f15674adc617047e54001a98ac` → `aa1d0b14d55b14d9e1aedcca53dd4b76b342ba13`

Between Project and Attendance, `96c6cf2866e00062860a0c0b45cb010be6ca30b1` committed the notification-inbox and Project/Task report consumer, and `918db46e5f6834fea7e1be2352cfe9033d150789` pinned evidence. The Project merge used an authorized `--no-ff --autostash` retry after a metadata-lock failure; tracked and untracked fingerprints matched and no stash residue remained.

### Integrated implementation and review repairs

1. `19d835da96ed7074d0b6065a285f85adcb1a750a` completed the remaining account, attendance, Admin history/settings, invitation/exit/transfer/history, Task workflow, report, and local Chart.js integration.
2. Independent review returned 0 Critical and 5 Important findings. `5c6e0c67cd7be7bfb914e81743538e7f7d9ae47d`, pinned by `126b3e4f4caa400e4ac8cabc9eb55387091f23e9`, repaired Account lifecycle composition/lock order, raw versus effective checkout facts, policy timezone rendering, malformed-value retention, and terminal/integration confirmations.
3. Re-review returned 0 Critical and 2 Important findings. `8c3582f9f1f7a362b7a0a044594302e92ddbc868`, pinned by `8821ff53bdc7c2350d1071f90326154f74a51a82`, fixed Javadoc/doclint and routed Project/Calendar inputs through retained-value parsing boundaries.
4. Re-review returned one residual Important finding. `789a637e35af9e162b0f683547a1c1087d9696ef`, pinned by `9925b0bb93fb0a8dc47b2a022acaa49446238683`, restored invitation/removal/transfer selections after producer rejection.
5. `691a35eecafbeb91a35275f8ed2d089d7089d7` aligned the Calendar controller surface audit; `b761290e0bb586f1a9242b52a2c669ff1f4b888f` pinned evidence. Independent review accepted this implementation with 0 Critical and 0 Important findings.

### Integrated exit gate and final evidence

At accepted implementation `b761290e0bb586f1a9242b52a2c669ff1f4b888f`:

- Java 25/PostgreSQL 18.4: 444/444 Maven tests passed in 04:33.
- Architecture/Flyway: 13/13 passed; 23 application tables and 56 foreign keys.
- Node 24/npm 11 build and UI contracts: 7/7 passed.
- Compile and exact repository-wide Javadoc/doclint passed.
- Requirement parity: 260 IDs across all four catalogues and 14 generated use cases.
- Real Java health, liveness, and readiness were `UP`.
- Local Chromium covered policy/calendar, leave, corrections, invitations, exit transfers, direct removal, leadership succession, Project completion/history, authorized reports, and SMTP-unavailable notification persistence.
- The disposable runtime was removed and ports `55441`, `1026`, `8026`, and `18082` were clear.

`f74a636693526307d0af51c31f90b5998559f211` committed plan/workflow/E2E evidence. Its review returned one Important reproducibility finding. `97ad3396047442152af761e4217bd10a5ca5f3c3` added sanitized exact commands, gate counts, parity, and Git checks. Same-reviewer re-review approved exact `97ad3396` with 0 Critical and 0 Important findings.

## Remote actions

All recorded Iteration 2 work remained local. No push, merge request, deployment, force operation, history rewrite, or merge to `main` occurred through this pre-publication candidate.

## What the team should do next

Keep the consumer branch/worktree for Iteration 3 handoff. Local merged-main verification is complete; stop without pushing unless separately authorized.
