# Lab Timesheet — Three-Iteration Delivery Plan

**Artifact purpose:** Local agent coordination and progress tracking  
**Implementation branches:** `work/platform`, `work/projects`, `work/tasks`, `work/attendance`, `work/reports-ui`  
**Requirements authority:** [`.sdd/requirements.md`](.sdd/requirements.md), tracked in this repository since 10 September 2026  
**SRS:** `labtimesheet-docs-hub/software-requirements-specification.md`, generated in the separate documentation repository and not tracked here  
**Current status:** Iterations 1 and 2 implemented and locally verified; Iteration 3 producer reviews and Reports/UI consumer candidate are complete locally, with final integrated review pending coordination. Candidate `ba1682ef6e295beb7ddc435abf9f39df759f5d27` consumes the reviewed Platform/Projects/Tasks/Attendance heads and advancing-clock merge; post-review parity evidence and managed report-download rerun are green.

This file divides the approved product scope across three iterations and five persistent work branches. It is a coordination artifact, not an alternative requirements source. When this plan and a numbered requirement disagree, the numbered requirement wins.

## 1. Progress rules

Use these exact status values:

| Status | Meaning |
|---|---|
| `TODO` | No implementation work has started. |
| `IN_PROGRESS` | One named owner is actively working on the item. |
| `BLOCKED` | Work cannot continue; the tracker must name the evidence and required decision/dependency. |
| `DONE` | Required RED/GREEN evidence exists, affected tests pass, and the integrated behavior satisfies the requirement. |

Before editing production code, an agent shall:

1. Read the applicable numbered requirements and acceptance scenarios.
2. Claim one bounded tracker item by setting its status to `IN_PROGRESS` and recording owner/date.
3. Identify the test level and the class that will protect the behavior.
4. Write and run the failing test before production code.
5. Confirm the test fails because the required behavior is missing, not because the test or environment is broken.

When completing an item, the agent shall record:

- the exact RED command and expected failure;
- the exact GREEN and affected-suite commands;
- the test class and method, and the requirement identifiers it names;
- the implementation commit or final local commit SHA;
- any remaining limitation that is explicitly allowed by the requirements.

No item becomes `DONE` based only on compilation, an isolated happy path, screenshots, or a verbal claim.

## 2. Branch ownership and conflict boundaries

| Branch | Sole or primary ownership |
|---|---|
| `work/platform` | Maven/application baseline, feature-package foundation, Flyway migration files, account/security/bootstrap, internship lifecycle, integration credential lifecycle, notification delivery infrastructure, Docker, and CI. |
| `work/projects` | Projects, membership intervals, invitations, membership-exit requests/readiness, leadership terms, Project lifecycle, Project-history authorization, transfer orchestration, and Project completion. |
| `work/tasks` | Tasks, generic creator/assignment actors, pending-exit assignment exclusion, member self-Task rules, comments, work logs, fixed status transitions, batch reassignment/direct-removal transfer operations, Task soft deletion, and Project Task-progress/history queries. |
| `work/attendance` | Attendance-policy versions/history, configured workdays, global calendar/history, HolidayAPI import interpretation, attendance, corrections, leave, deadline schedulers, and attendance/compliance metrics. |
| `work/reports-ui` | Shared Thymeleaf shell/fragments, Tailwind tokens/assets, dashboards, invitation/exit/transfer screens, Project and Admin-setting History tabs, shared report datasets, Chart.js presentation, XLSX/PDF exports, and cross-product UI/accessibility consistency. |

Conflict-prevention rules:

- `LabtimesheetApplication` shall remain in `com.lab.labtimesheet`, shared wiring in `config`, and business code in `feature.account`, `feature.integration`, `feature.project`, `feature.task`, `feature.attendance`, `feature.notification`, or `feature.reporting`. Each feature repeats only the controller/model/model.dto/model.entity/repository/service/exception layers it needs, and tests mirror that feature/layer shape.
- Cross-feature code may call another feature's service contract and DTOs but shall not import that feature's repository or JPA entity. Do not create empty `utils`, `common`, or `core` packages.
- Business persistence shall use Spring Data JPA repositories. Direct SQL is limited to Flyway migrations and schema/catalog verification; services shall not use `JdbcTemplate` or embed SQL.

- `work/platform` owns `src/main/resources/db/migration/**`, Maven/dependency configuration, Compose, container build files, and CI workflow files. Other branches request schema changes instead of independently allocating migration versions.
- `work/reports-ui` owns shared templates/fragments, shared design tokens, and general UI assets. Each domain branch owns its module-specific controllers and pages while consuming those shared fragments.
- A targeted repair shall use a clean, isolated `work/fix/<feature>/<what-fix>` branch and worktree from the taskmaster-verified current `main`. Do not use `work/<feature>/fix/<what-fix>`: the persistent `work/<feature>` ref already occupies that Git ref prefix.
- `work/tasks` exposes focused Task eligibility, batch-transfer, unfinished-count, and retained-history operations required by Project workflows. `work/projects` owns pending-exit readiness/approval, direct-removal orchestration, and Project completion transactions.
- `work/platform` owns HolidayAPI credential storage and the tested HTTP client. `work/attendance` owns preview interpretation, selection, deduplication, import, and day-off effects.
- Attendance time and Task work time remain separate. No branch may make one mutate or prove the other.
- Do not introduce a generic workflow engine, generic event-sourcing layer, multi-assignee Task model, Project-level day-off model, or speculative cross-module abstraction.

## 3. Iteration overview

| Iteration | Theme | Required demonstration | Status | Integration commit |
|---|---|---|---|---|
| 1 | Working vertical slice | Bootstrap users, create/activate Project, assign/change a Task, and check in/out with role-correct UI. | `DONE` | `b9b150ff8ca9333e3b46d77537ec91875a970d57` |
| 2 | Complete business workflows | Policy/calendar changes, leave/corrections, leadership/member transfer, work logs, notifications, and full HTML workflows. | `DONE` | `c4f039663f86370b865df036e1756338529e47c9` |
| 3 | Hardening and delivery | Historical/concurrency proof, production security, HTML/XLSX/PDF parity, accessibility, containers, and CI publication boundary. | `IN_PROGRESS` | — |

## 4. Iteration 1 — Working vertical slice

### 4.1 `work/platform`

| ID | Deliverable | Test/evidence emphasis | Status | Owner/date | Result/commit |
|---|---|---|---|---|---|
| I1-PLAT-01 | Establish the Maven/Spring Boot modular baseline and root/config/feature package boundaries. | Context/load test; root-package and package-by-feature/layer check; no cross-feature repository/entity access or direct-SQL business services. | `DONE` | platform_agent / 2026-08-15 | Approved Platform head `692b23e9b9891d360882671d8247965b44920b2f`; integrated architecture/full-suite gates passed. |
| I1-PLAT-02 | Promote the reviewed 23-table/56-foreign-key PostgreSQL baseline into the platform-owned initial Flyway migration after explicit approval. | Fresh PostgreSQL 18.4 migration replay, catalog assertions, and invitation/exit same-Project constraints. | `DONE` | platform_agent / 2026-08-15 | Fresh local replay produced exactly 23 application tables and 56 foreign keys at the integrated head. |
| I1-PLAT-03 | Configure PostgreSQL Testcontainers and shared test-only encryption/clock facilities. | Affected integration tests require no developer database or SMTP. | `DONE` | platform_agent / 2026-08-15 | Taskmaster full PostgreSQL 18.4 Testcontainers suite passed 197/197. |
| I1-PLAT-04 | Implement atomic first-Admin bootstrap and permanent bootstrap closure. | Concurrent submissions create exactly one first Admin; restart keeps bootstrap closed. | `DONE` | platform_agent / 2026-08-15 | Bootstrap/restart/concurrency evidence approved at `692b23e9b9891d360882671d8247965b44920b2f`; fresh local bootstrap passed. |
| I1-PLAT-05 | Implement initial SMTP draft/test/active path sufficient for Mailpit onboarding. | Failed test cannot activate; tested revision supports delivery. | `DONE` | platform_agent / 2026-08-15 | Real local Mailpit draft, test delivery, activation, active-state, and health checks passed at the integrated head. |
| I1-PLAT-06 | Create Mentor/Intern accounts, deliver activation, set first password, and authenticate/logout. | SMTP gate, single-use token, expiry, normalized email, role/state access. | `DONE` | platform_agent / 2026-08-15 | Activation/authentication evidence and independent review approved at `692b23e9b9891d360882671d8247965b44920b2f`. |
| I1-PLAT-07 | Provide development Compose with PostgreSQL and Mailpit plus initial Gitea verification workflow. | Fresh developer start and branch/main verification. | `DEFERRED` | taskmaster / 2026-08-15 | Explicitly excluded from this exit gate; application containerization, Compose, and CI remain future work. |

### 4.2 `work/projects`

| ID | Deliverable | Test/evidence emphasis | Status | Owner/date | Result/commit |
|---|---|---|---|---|---|
| I1-PRJ-01 | Atomically create a Mentor-owned `PLANNED` Project, eligible initial Leader membership, and first leadership term. | No committed Project is empty or leaderless; non-owner, ineligible Leader, and guessed-ID access are denied. | `DONE` | projects_agent / 2026-08-15 | Approved Project head `baa0695c60153bc997ebf8b11adcfbdd2cbc1962`; Project suite and review passed. |
| I1-PRJ-02 | Directly add eligible Interns through owning-Mentor-controlled interval memberships. | Multiple concurrent Projects per Intern; duplicate active membership rejected; no acceptance step for direct add. | `DONE` | projects_agent / 2026-08-15 | Membership/eligibility/IDOR evidence approved at `baa0695c60153bc997ebf8b11adcfbdd2cbc1962`. |
| I1-PRJ-03 | Appoint and change one current Leader from active same-Project members. | One current Leader; non-member/ineligible selection rejected. | `DONE` | projects_agent / 2026-08-15 | Leadership-term invariants and completed-history behavior approved at `baa0695c60153bc997ebf8b11adcfbdd2cbc1962`. |
| I1-PRJ-04 | Activate a Project when initial member, Leader, date, and assignee guards pass. | Missing Leader/member or invalid assignee blocks activation. | `DONE` | projects_agent / 2026-08-15 | Task-bound activation guard approved at `baa0695c60153bc997ebf8b11adcfbdd2cbc1962`. |
| I1-PRJ-05 | Provide Project list/detail/member/leadership pages with owning-Mentor and member visibility. | MockMvc authorization plus direct-ID denial. | `DONE` | projects_agent / 2026-08-15 | Authorized pages, retained errors, and former-member history approved at `baa0695c60153bc997ebf8b11adcfbdd2cbc1962`. |

### 4.3 `work/attendance`

| ID | Deliverable | Test/evidence emphasis | Status | Owner/date | Result/commit |
|---|---|---|---|---|---|
| I1-ATT-01 | Resolve the seeded attendance policy, timezone, configured workdays, schedule, and separate check-in/checkout grace boundaries. | Both grace defaults are 30; values are 0–720; checkout cutoff must stay before local midnight. | `DONE` | attendance_agent / 2026-08-15 | Approved Attendance head `01b8095e9459417e2cf5bd1079c796d4f01ec549`; fresh seed verified both grace values at 30. |
| I1-ATT-02 | Manage manual future global calendar events and day-off decisions. | Admin-only mutation; past-event immutability; workday/day-off distinction. | `DONE` | attendance_agent / 2026-08-15 | Calendar authorization and day-off evidence approved at `01b8095e9459417e2cf5bd1079c796d4f01ec549`. |
| I1-ATT-03 | Check in once on an eligible day using server time and the effective policy. | Off-day, approved-leave, duplicate, lifecycle rejection, and inclusive 09:00 check-in-grace boundary. | `DONE` | attendance_agent / 2026-08-15 | Boundary, eligibility, leave-day, and real duplicate-race evidence approved at `01b8095e9459417e2cf5bd1079c796d4f01ec549`. |
| I1-ATT-04 | Check out once through the attached-policy checkout cutoff and derive basic daily classification. | Default 16:00 succeeds; first later instant and zero-grace late attempt fail; no checkout becomes only `MISSING_CHECKOUT` with raw checkout unchanged. | `DONE` | attendance_agent / 2026-08-15 | Historical-policy cutoff, eligibility recheck, and missing-checkout-only evidence approved at `01b8095e9459417e2cf5bd1079c796d4f01ec549`. |
| I1-ATT-05 | Provide own-attendance history and authorized Mentor/Admin inspection. | Own/global-view authorization and historical applied-policy display. | `DONE` | attendance_agent / 2026-08-15 | Policy-local history, all violations, and role authorization approved at `01b8095e9459417e2cf5bd1079c796d4f01ec549`. |

### 4.4 `work/tasks`

| ID | Deliverable | Test/evidence emphasis | Status | Owner/date | Result/commit |
|---|---|---|---|---|---|
| I1-TSK-01 | Create one-assignee Tasks in `PLANNED` or `ACTIVE`: any active member for self, current Leader for any active same-Project member. Store generic creator/assigner/assignee actors. | Self-Task actor equality, other-assignee denial for members, Leader allowance, and cross-Project actor rejection. | `DONE` | tasks_agent / 2026-08-15 | Approved Task head `e38e2cdea912160b183c65398c4e8d5682c1b00e`; actor/assignee/IDOR evidence passed review. |
| I1-TSK-02 | Validate optional due dates against Project dates and current global days off. | Boundary dates accepted; outside/day-off dates rejected. | `DONE` | tasks_agent / 2026-08-15 | Due-date/calendar validation and retained field-error behavior approved at `e38e2cdea912160b183c65398c4e8d5682c1b00e`. |
| I1-TSK-03 | Enforce the complete fixed Task status graph through current-assignee authorization. | Parameterized allowed/forbidden transition matrix and ID denial. | `DONE` | tasks_agent / 2026-08-15 | Server graph and legal UI choices approved at `e38e2cdea912160b183c65398c4e8d5682c1b00e`. |
| I1-TSK-04 | Add append-only Task comments for active members, current Leader, and owning Mentor. | Unauthorized/non-member and completed-Project mutation denial. | `DONE` | tasks_agent / 2026-08-15 | Comment authorization, lock order, and completed-history behavior approved at `e38e2cdea912160b183c65398c4e8d5682c1b00e`. |
| I1-TSK-05 | Show Task list/detail and initial DONE/non-deleted progress/status counts. | Empty Project renders `N/A`; soft/deleted data not yet exposed as current. | `DONE` | tasks_agent / 2026-08-15 | Progress/N/A, assignee display, visibility, and dashboard ordering approved at `e38e2cdea912160b183c65398c4e8d5682c1b00e`. |

### 4.5 `work/reports-ui`

| ID | Deliverable | Test/evidence emphasis | Status | Owner/date | Result/commit |
|---|---|---|---|---|---|
| I1-UI-01 | Establish Tailwind tokens and reusable Thymeleaf shell/fragments. | Fragment rendering, local assets, no unauthorized navigation items. | `DONE` | reports_ui_agent / 2026-08-15 | Shared local-asset shell and persistent SMTP restriction contract verified at `039fe25c7c2622a015c8962892dd99ff58be321d`. |
| I1-UI-02 | Implement the supported desktop sidebar/header, forms, tables, badges, alerts, confirmations, empty/error states, and theme bootstrap. | Keyboard labels/focus, no desktop page-level overflow, pre-paint theme application. | `DONE` | reports_ui_agent / 2026-08-15 | Edge/Chromium 1365x900 focus, collapse tooltip, theme pre-paint, and overflow gates verified at `039fe25c7c2622a015c8962892dd99ff58be321d`. |
| I1-UI-03 | Build basic Admin, Mentor, and Intern dashboards from real queries. | Role-correct metrics/actions; illustrative data never leaks into production paths. | `DONE` | reports_ui_agent / 2026-08-15 | Public service/DTO dashboards and role-correct navigation verified at `039fe25c7c2622a015c8962892dd99ff58be321d`. |
| I1-UI-04 | Integrate bootstrap, authentication, Project, Task, and attendance pages into the shared shell. | Critical MockMvc web flows and server-side authorization. | `DONE` | reports_ui_agent / 2026-08-15 | Integrated shell, SMTP five-step journey, forms, history, errors, and authorization verified at `039fe25c7c2622a015c8962892dd99ff58be321d`. |

### 4.6 Iteration 1 integration gate

Integration order:

1. `work/platform`
2. `work/projects`
3. `work/attendance`
4. `work/tasks`
5. `work/reports-ui`

Exit demonstration:

- First Admin bootstraps the installation.
- SMTP is tested through Mailpit.
- Admin creates and activates Mentor and Intern accounts.
- Mentor atomically creates a Project with its first Leader, directly adds another member, and activates it.
- Ordinary member creates a self-assigned Task; Leader creates and assigns another Task.
- Assignee changes Task status and comments.
- Intern checks in and checks out.
- Every role sees only authorized navigation, actions, and records.
- Full integrated tests pass at the iteration integration commit.

Taskmaster exit result (2026-08-15): `DONE` at main integration commit `b9b150ff8ca9333e3b46d77537ec91875a970d57`, incorporating reviewed candidate `039fe25c7c2622a015c8962892dd99ff58be321d` and the verified development configuration/documentation follow-up. The final merged-main PostgreSQL 18.4 suite passed 197/197; Flyway produced 23 application tables and 56 foreign keys; Node 24/Tailwind assets built successfully; compile and full Javadoc/doclint passed on the reviewed candidate. A real local Java 25 process completed fresh bootstrap, login, five-step SMTP deferral, persistent restriction recovery, and a separate real Mailpit draft/test/activate flow with aggregate health `UP`. Temporary exit databases and Mailpit were removed; the existing development PostgreSQL service remained intact. Project/Task/Attendance role flows are protected by the approved branch suites and real desktop E2E evidence. Application containerization, Compose, CI, and production liveness/readiness hardening remain deferred as recorded above.

## 5. Iteration 2 — Complete business workflows

Iteration 2 starts only after every continuing branch incorporates the integrated Iteration 1 `main`.

### 5.1 `work/platform`

| ID | Deliverable | Test/evidence emphasis | Status | Owner/date | Result/commit |
|---|---|---|---|---|---|
| I2-PLAT-01 | Complete account lock/unlock/deactivation and session invalidation. | State graph, authentication denial, retained attribution. | `DONE` | `i2_platform` / 2026-08-21 | Independently reviewed head `3bc7824ab87d6ecac2464fb0b4ed1600b55f5ea9`; integrated in `b761290e0bb586f1a9242b52a2c669ff1f4b888f`. |
| I2-PLAT-02 | Implement activation failure/resend and forgot/reset-password workflows. | Failed token invalidation, single-use/expiry, generic enumeration-safe responses. | `DONE` | `i2_platform` / 2026-08-21 | Independently reviewed head `3bc7824ab87d6ecac2464fb0b4ed1600b55f5ea9`; integrated in `b761290e0bb586f1a9242b52a2c669ff1f4b888f`. |
| I2-PLAT-03 | Implement internship start activation, completion, and withdrawal guards. | Scheduler plus request-time activation; Leader/unfinished-Task terminal guards. | `DONE` | `i2_platform` / 2026-08-21 | Independently reviewed head `3bc7824ab87d6ecac2464fb0b4ed1600b55f5ea9`; integrated in `b761290e0bb586f1a9242b52a2c669ff1f4b888f`. |
| I2-PLAT-04 | Complete encrypted SMTP and HolidayAPI draft/test/active revision lifecycles. | AES-GCM round trip, wrong-key failure, one active revision, no browser secret disclosure. | `DONE` | `i2_platform` / 2026-08-21 | Independently reviewed head `3bc7824ab87d6ecac2464fb0b4ed1600b55f5ea9`; integrated in `b761290e0bb586f1a9242b52a2c669ff1f4b888f`. |
| I2-PLAT-05 | Expose the tested VN HolidayAPI client to the attendance module. | Valid preview, invalid key/rate limit/unavailable responses without local-data outage. | `DONE` | `i2_platform` / 2026-08-21 | Independently reviewed head `3bc7824ab87d6ecac2464fb0b4ed1600b55f5ea9`; integrated in `b761290e0bb586f1a9242b52a2c669ff1f4b888f`. |
| I2-PLAT-06 | Persist in-app notifications and initial ordinary-email delivery states, including Project invitation and membership-exit created/resolved types. | Recipient deduplication, domain commit independent of SMTP, self-Task silence, and `UNAVAILABLE`/sent/failed behavior. | `DONE` | `i2_platform` / 2026-08-21 | Independently reviewed head `3bc7824ab87d6ecac2464fb0b4ed1600b55f5ea9`; integrated in `b761290e0bb586f1a9242b52a2c669ff1f4b888f`. |

### 5.2 `work/tasks`

| ID | Deliverable | Test/evidence emphasis | Status | Owner/date | Result/commit |
|---|---|---|---|---|---|
| I2-TSK-01 | Add dated 1–1440-minute Task work logs and author corrections. | Membership/date boundaries and author-only editing. | `DONE` | `i2_tasks` / 2026-08-21 | Independently reviewed head `265fdab44091d378fe023146f2b60f4ef0f39125`; integrated in `b761290e0bb586f1a9242b52a2c669ff1f4b888f`. |
| I2-TSK-02 | Enforce the combined 1440-minute daily total across all Projects. | PostgreSQL integration and concurrent over-allocation proof. | `DONE` | `i2_tasks` / 2026-08-21 | Independently reviewed head `265fdab44091d378fe023146f2b60f4ef0f39125`; integrated in `b761290e0bb586f1a9242b52a2c669ff1f4b888f`. |
| I2-TSK-03 | Reassign unfinished Tasks while preserving creator, state, comments, work logs, lifecycle timestamps, and assignment actor/time; exclude pending exit targets from new/self-assignment. | DONE requires assignee reopen; prior attribution remains; pending target keeps existing assignee rights but receives no new work. | `DONE` | `i2_tasks` / 2026-08-21 | Independently reviewed head `265fdab44091d378fe023146f2b60f4ef0f39125`; integrated in `b761290e0bb586f1a9242b52a2c669ff1f4b888f`. |
| I2-TSK-04 | Implement Task edit/soft deletion and authorized historical inspection for Leader/self-Task creator plus Project History projection. | Eligible creator controls only while current assignee; deleted/completed Tasks expose retained attribution without invented previous-assignee or edit/status timelines. | `DONE` | `i2_tasks` / 2026-08-21 | Independently reviewed head `265fdab44091d378fe023146f2b60f4ef0f39125`; integrated in `b761290e0bb586f1a9242b52a2c669ff1f4b888f`. |
| I2-TSK-05 | Provide repeatable multi-Task/one-recipient transfer batches, unfinished-count, direct-removal transfer, and completion-query operations to Projects. | Each batch is atomic; recipient/pending state rechecked; direct removal transfers all unfinished Tasks atomically; non-deleted/DONE counts remain correct. | `DONE` | `i2_tasks` / 2026-08-21 | Independently reviewed head `265fdab44091d378fe023146f2b60f4ef0f39125`; integrated in `b761290e0bb586f1a9242b52a2c669ff1f4b888f`. |
| I2-TSK-06 | Complete Project status counts, percentage, total minutes, and per-member work queries. | Empty `N/A`, authorization scopes, hand-checkable totals. | `DONE` | `i2_tasks` / 2026-08-21 | Independently reviewed head `265fdab44091d378fe023146f2b60f4ef0f39125`; integrated in `b761290e0bb586f1a9242b52a2c669ff1f4b888f`. |

### 5.3 `work/projects`

| ID | Deliverable | Test/evidence emphasis | Status | Owner/date | Result/commit |
|---|---|---|---|---|---|
| I2-PRJ-01 | Issue and revoke non-expiring Leader invitations while retaining the issuing leadership term. | Eligibility, one pending Project/Intern pair, Leader-own versus Mentor-any revocation, ordinary notification behavior. | `DONE` | `i2_projects` / 2026-08-21 | Independently reviewed head `ea5f8ce6788abf7cb47343dcf32ab5da1b31f2d7`; integrated in `b761290e0bb586f1a9242b52a2c669ff1f4b888f`. |
| I2-PRJ-02 | Let only the intended authenticated Intern accept or decline; support Mentor direct-add supersession. | Email is not a bearer join token; exactly one membership; terminal status/code and provenance retained. | `DONE` | `i2_projects` / 2026-08-21 | Independently reviewed head `ea5f8ce6788abf7cb47343dcf32ab5da1b31f2d7`; integrated in `b761290e0bb586f1a9242b52a2c669ff1f4b888f`. |
| I2-PRJ-03 | Create/cancel Leader-removal and member-leave requests, expose persistent readiness warnings, and keep existing rights while excluding the target from new/self-assignment. | Nonblank reason, same-Project/type shape, one pending request per target, requester-only cancellation, replacement/remaining/ready state for every authorized viewer. | `DONE` | `i2_projects` / 2026-08-21 | Independently reviewed head `ea5f8ce6788abf7cb47343dcf32ab5da1b31f2d7`; integrated in `b761290e0bb586f1a9242b52a2c669ff1f4b888f`. |
| I2-PRJ-04 | Let only the owning Mentor approve/reject exits after Leader-managed redistribution; preserve the direct-removal automatic-transfer shortcut. | Leader exit requires replacement first; repeated batches persist; cancel/reject keeps them and restores eligibility; approval waits for non-Leader/zero unfinished Tasks; closure/request resolution commit together. | `DONE` | `i2_projects` / 2026-08-21 | Independently reviewed head `ea5f8ce6788abf7cb47343dcf32ab5da1b31f2d7`; integrated in `b761290e0bb586f1a9242b52a2c669ff1f4b888f`. |
| I2-PRJ-05 | Retain leadership history, change Leader without moving assignments, and complete only when every non-deleted Task is `DONE`. | Exactly one current Leader in PLANNED/ACTIVE; completion closes intervals, revokes invitations, and supersedes exits. | `DONE` | `i2_projects` / 2026-08-21 | Independently reviewed head `ea5f8ce6788abf7cb47343dcf32ab5da1b31f2d7`; integrated in `b761290e0bb586f1a9242b52a2c669ff1f4b888f`. |
| I2-PRJ-06 | Provide one authorized Project History view for membership, leadership, invitation, exit decision, completed/soft-deleted Task, comment, work-log, and retained attribution data. | Admin all read-only; owning Mentor/current member on open Project; removed member denied until completion; no fabricated previous-assignee/status/edit timeline. | `DONE` | `i2_projects` / 2026-08-21 | Independently reviewed head `ea5f8ce6788abf7cb47343dcf32ab5da1b31f2d7`; integrated in `b761290e0bb586f1a9242b52a2c669ff1f4b888f`. |

### 5.4 `work/attendance`

| ID | Deliverable | Test/evidence emphasis | Status | Owner/date | Result/commit |
|---|---|---|---|---|---|
| I2-ATT-01 | Schedule future-month attendance-policy versions, including separate grace values, preserve effective history, and expose Admin-only Policy History. | First-of-future-month rule; effective immutability; old cutoff/report stability; safe version/actor/effective metadata only. | `DONE` | `i2_attendance` / 2026-08-21 | Independently reviewed head `b37cf5f47e7eb7f15674adc617047e54001a98ac`; integrated in `b761290e0bb586f1a9242b52a2c669ff1f4b888f`. |
| I2-ATT-02 | Preview/import VN HolidayAPI candidates with Admin selection, override, provenance, deduplication, and Admin-only Calendar History. | Public suggestion not authority; manual fallback; repeated import safety; past/current event metadata remains read-only and non-secret. | `DONE` | `i2_attendance` / 2026-08-21 | Independently reviewed head `b37cf5f47e7eb7f15674adc617047e54001a98ac`; integrated in `b761290e0bb586f1a9242b52a2c669ff1f4b888f`. |
| I2-ATT-03 | Materialize frozen full-day leave allocations and monthly/cross-month quota reservations. | Workday/day-off classification, policy snapshot, quota per month. | `DONE` | `i2_attendance` / 2026-08-21 | Independently reviewed head `b37cf5f47e7eb7f15674adc617047e54001a98ac`; integrated in `b761290e0bb586f1a9242b52a2c669ff1f4b888f`. |
| I2-ATT-04 | Implement leave submit/approve/reject/cancel and overlap protection. | Same-day boundary, pending/approved reservations, concurrent overlap/quota. | `DONE` | `i2_attendance` / 2026-08-21 | Independently reviewed head `b37cf5f47e7eb7f15674adc617047e54001a98ac`; integrated in `b761290e0bb586f1a9242b52a2c669ff1f4b888f`. |
| I2-ATT-05 | Implement missed-checkout correction submission and effective-checkout derivation. | Reject before/at checkout cutoff; accept afterward through scheduled end +24 hours; raw checkout remains null. | `DONE` | `i2_attendance` / 2026-08-21 | Independently reviewed head `b37cf5f47e7eb7f15674adc617047e54001a98ac`; integrated in `b761290e0bb586f1a9242b52a2c669ff1f4b888f`. |
| I2-ATT-06 | Implement Mentor approve/reject/revert and separate decision-window locking. | Valid state graph, concurrent decision, expired pending auto-rejection. | `DONE` | `i2_attendance` / 2026-08-21 | Independently reviewed head `b37cf5f47e7eb7f15674adc617047e54001a98ac`; integrated in `b761290e0bb586f1a9242b52a2c669ff1f4b888f`. |
| I2-ATT-07 | Add idempotent schedulers and equivalent request-time deadline guards. | Late/multiple scheduler invocation cannot duplicate transitions/notifications. | `DONE` | `i2_attendance` / 2026-08-21 | Independently reviewed head `b37cf5f47e7eb7f15674adc617047e54001a98ac`; integrated in `b761290e0bb586f1a9242b52a2c669ff1f4b888f`. |

### 5.5 `work/reports-ui`

| ID | Deliverable | Test/evidence emphasis | Status | Owner/date | Result/commit |
|---|---|---|---|---|---|
| I2-UI-01 | Complete Admin/Mentor/Intern/Leader dashboards and notification UI. | Authorization-correct actions and deadline/state summaries. | `DONE` | `i2_reports_ui` / 2026-08-21 | Independently reviewed integrated head `b761290e0bb586f1a9242b52a2c669ff1f4b888f`; final browser evidence in `docs/tests/e2e/iteration2-integrated-exit-journeys.md`. |
| I2-UI-02 | Complete account, integration, Project, invitation, membership-exit/transfer, Task, policy/calendar, attendance, correction, leave, and History desktop workflows using shared fragments. | Persistent warning; Leader-only multi-Task/one-recipient drawer; Mentor readiness decision; Project History; Admin-only Policy/Calendar/SMTP/HolidayAPI History; keyboard operation, redaction, and clear conflicts. | `DONE` | `i2_reports_ui` / 2026-08-21 | Independently reviewed integrated head `b761290e0bb586f1a9242b52a2c669ff1f4b888f`; final browser evidence in `docs/tests/e2e/iteration2-integrated-exit-journeys.md`. |
| I2-UI-03 | Build one authorized attendance/compliance HTML report dataset. | Date filters, detailed versus own scope, formulas and `N/A`. | `DONE` | `i2_reports_ui` / 2026-08-21 | Independently reviewed integrated head `b761290e0bb586f1a9242b52a2c669ff1f4b888f`; final browser evidence in `docs/tests/e2e/iteration2-integrated-exit-journeys.md`. |
| I2-UI-04 | Build one authorized Project/Task HTML report dataset. | Project/member/status/date filters and per-member visibility rules. | `DONE` | `i2_reports_ui` / 2026-08-21 | Independently reviewed integrated head `b761290e0bb586f1a9242b52a2c669ff1f4b888f`; final browser evidence in `docs/tests/e2e/iteration2-integrated-exit-journeys.md`. |
| I2-UI-05 | Add only meaningful Chart.js trends with adjacent text/table alternatives. | Accessible label, equivalent data, theme tokens, reduced motion. | `DONE` | `i2_reports_ui` / 2026-08-21 | Independently reviewed integrated head `b761290e0bb586f1a9242b52a2c669ff1f4b888f`; final browser evidence in `docs/tests/e2e/iteration2-integrated-exit-journeys.md`. |

### 5.6 Iteration 2 integration gate

Integration order:

1. `work/platform`
2. `work/tasks`
3. `work/projects`
4. `work/attendance`
5. `work/reports-ui`

Exit demonstration:

- Admin schedules a future policy without changing historical output.
- Admin previews/imports holidays and overrides a suggested day-off decision.
- Intern submits cross-month leave and Mentor decides it.
- Intern submits a missed-checkout correction; Mentor decides and may revert it inside the window.
- Leader reassigns an unfinished Task while preserving creator, comments, work logs, and current assignment attribution.
- Leader invites an eligible Intern; the signed-in Intern accepts or declines; Mentor direct-add safely supersedes a pending invite.
- Mentor changes Leader without moving the former Leader's Tasks.
- Member requests to leave and Leader requests removal; all authorized viewers see readiness, replacement is appointed first when needed, Leader redistributes unfinished Tasks in repeated batches, cancellation/rejection keeps completed batches, and approval waits for zero unfinished Tasks.
- Mentor directly removes an ordinary member and a Leader in separate cases; automatic transfer remains atomic and completed Tasks keep the removed Intern's displayed name.
- Admin, owning Mentor, current member, and removed member before/after completion receive the exact Project History visibility defined by AUTH-006.
- Admin opens read-only Attendance Policy, Calendar, SMTP, and HolidayAPI History tabs; non-Admins are denied and no secret/internal retry data appears.
- Mentor completes a Project after all non-deleted Tasks are done.
- Ordinary domain actions retain in-app notifications when SMTP is unavailable.
- Full integrated tests pass at the iteration integration commit.

Taskmaster exit result (2026-08-21): `DONE` at local `main` integration commit `c4f039663f86370b865df036e1756338529e47c9`, whose parents are verified base `58a087b118cc955748d7df1aa47d2bbc3ca0371b` and independently approved candidate `f334f13594de49f4b34318d8a3e8bc0556a8063d`. Exact producer heads `3bc7824ab87d6ecac2464fb0b4ed1600b55f5ea9`, `265fdab44091d378fe023146f2b60f4ef0f39125`, `ea5f8ce6788abf7cb47343dcf32ab5da1b31f2d7`, and `b37cf5f47e7eb7f15674adc617047e54001a98ac` remain ancestors in required order through accepted implementation `b761290e0bb586f1a9242b52a2c669ff1f4b888f`. Exact post-merge record tree `600e5fda478f1893d386f32fbf7db3ba19228cff` passed PostgreSQL 18.4 Java 444/444, architecture/Flyway 13/13 with 23 tables/56 foreign keys, Node 24 build/UI 7/7, compile/doclint, 260-ID/14-use-case parity, all 30 Iteration 2 rows `DONE`, and real Java aggregate/liveness/readiness checks. Exact commands and results are in the companion merge-readiness evidence. No push is authorized.

## 6. Iteration 3 — Hardening, reports, and delivery readiness

Iteration 3 starts only after every continuing branch incorporates the integrated Iteration 2 `main`.

### 6.1 `work/platform`

| ID | Deliverable | Test/evidence emphasis | Status | Owner/date | Result/commit |
|---|---|---|---|---|---|
| I3-PLAT-01 | Add normalized-email-plus-source-IP login throttling. | Five-in-15 and 15-minute throttle boundaries; successful-login clearing. | `DONE` | `i3_platform / 2026-08-22` | Reviewed Platform head `45faa562b051023ee1c69f7cae541185d68de6fd`; implementation `091361eb73c7d7118d8212df630a83aca4ad5f9e`; evidence `docs/tests/unit/login-throttling.md`. |
| I3-PLAT-02 | Finish ordinary email retry schedule and terminal failure handling. | Exact 1m/5m/30m/2h/12h attempts; idempotent bounded worker. | `DONE` | `i3_platform / 2026-08-22` | Reviewed Platform head `45faa562b051023ee1c69f7cae541185d68de6fd`; implementation `1c762021aa5025ce90a96259a36ea4db1af262b6`; evidence `docs/tests/integration/notification-retry.md`. |
| I3-PLAT-03 | Add token cleanup and production-safe operational/status views. | Expired token behavior, no secret/stack/SQL disclosure. | `DONE` | `i3_platform / 2026-08-22` | Reviewed Platform head `45faa562b051023ee1c69f7cae541185d68de6fd`; implementation `2eaa4b67bdf7ac1a378c3c72341b5664ec18ff8b`; evidence `docs/tests/integration/account-token-cleanup.md`. |
| I3-PLAT-04 | Enforce production HTTPS/origin/proxy/header/cookie/master-key readiness. | Prod fails unsafe configuration; dev/test relax only transport/origin controls. | `DONE` | `i3_platform / 2026-08-22` | Reviewed Platform head `45faa562b051023ee1c69f7cae541185d68de6fd`; implementation `c52b375c41f40ba758b4121a85e185ba69e34f21`; evidence `docs/tests/unit/production-readiness.md`. |
| I3-PLAT-05 | Produce the non-root application image and bundled/external PostgreSQL deployment modes. | Same immutable image becomes healthy in both configurations. | `DONE` | `i3_platform / 2026-08-22` | Reviewed Platform head `45faa562b051023ee1c69f7cae541185d68de6fd`; implementation `1f2590f9654fc670e0cf93d0c4960777db2cb5f7`; evidence `docs/tests/integration/platform-image-and-compose.md`. |
| I3-PLAT-06 | Finalize Gitea verification, main-only OCI publication, and disabled SSH deployment/rollback template. | Work branches never publish; disabled deploy receives no secrets; exact-SHA flow is testable when enabled. | `DONE` | `i3_platform / 2026-08-22` | Reviewed Platform head `45faa562b051023ee1c69f7cae541185d68de6fd`; implementation `1f2590f9654fc670e0cf93d0c4960777db2cb5f7`; evidence `docs/tests/integration/dormant-deployment.md`. |
| I3-PLAT-07 | Complete the Admin account directory and controlled identity-correction service. | Normalized display-name/email/Student-Code search, immutable-role filter, SMTP-gated atomic email correction across pending/active/locked states, token/session invalidation, Student-Code/date lifecycle boundaries, deactivated/terminal denial, and no fabricated session history. | `DONE` | `i3_platform / 2026-08-22` | Reviewed Platform head `45faa562b051023ee1c69f7cae541185d68de6fd`; implementation `9348b6fabe9fd904a2aaf8cdbabc4890467f06ed`; evidence `docs/tests/integration/account-identity-correction.md`; review pin `7d3ad6a4722df410c5791176b3368309910d44bb`. |

### 6.2 `work/projects`

| ID | Deliverable | Test/evidence emphasis | Status | Owner/date | Result/commit |
|---|---|---|---|---|---|
| I3-PRJ-01 | Harden concurrent membership, invitation acceptance/direct-add, exit approval, and leadership operations. | One valid winner; locks/rechecks cover invitation, Project, membership, leadership, request, and unfinished Tasks; stale requests produce explicit conflict without partial history. | `DONE` | `i3_projects / 2026-08-22` | Reviewed Projects head `aebc974e41247a3054690bf8bf22d3364e43760e`; round-6 PASS and affected/concurrency evidence recorded. |
| I3-PRJ-02 | Complete the global-role/context/ownership authorization matrix for direct membership, invitations, exits, and leadership. | Direct-ID, stale Leader, wrong invitee, cross-Mentor/member, requester, target, and decision-maker negative cases. | `DONE` | `i3_projects / 2026-08-22` | Reviewed Projects head `aebc974e41247a3054690bf8bf22d3364e43760e`; round-6 authorization/IDOR matrix PASS. |
| I3-PRJ-03 | Prove completed/historical read-only behavior and terminal Intern guards. | No mutation through UI or direct request after lifecycle closure. | `DONE` | `i3_projects / 2026-08-22` | Reviewed Projects head `aebc974e41247a3054690bf8bf22d3364e43760e`; historical/terminal guard evidence PASS. |
| I3-PRJ-04 | Verify Project list/progress query indexes and bounded performance. | Explain plan/catalog evidence for actual report paths. | `DONE` | `i3_projects / 2026-08-22` | Reviewed Projects head `aebc974e41247a3054690bf8bf22d3364e43760e`; query/index and bounded-performance evidence PASS. |

### 6.3 `work/tasks`

| ID | Deliverable | Test/evidence emphasis | Status | Owner/date | Result/commit |
|---|---|---|---|---|---|
| I3-TSK-01 | Harden concurrent status, reassignment, deletion, and daily-minute operations. | Optimistic conflicts and serialized daily total. | `DONE` | `i3_tasks / 2026-08-22` | Reviewed Tasks head `a6261eb0ccad46ee6eac028d7202b5c06967fe13`; round-5 PASS with concurrency/conflict evidence. |
| I3-TSK-02 | Complete due-date impact behavior for later-created global days off. | Existing due date retained and disclosed; new/changed due date rejected. | `DONE` | `i3_tasks / 2026-08-22` | Reviewed Tasks head `a6261eb0ccad46ee6eac028d7202b5c06967fe13`; due-date/calendar impact evidence PASS. |
| I3-TSK-03 | Complete former-assignee work-log correction and historical-deletion boundaries. | Author/member/Project lifecycle matrix. | `DONE` | `i3_tasks / 2026-08-22` | Reviewed Tasks head `a6261eb0ccad46ee6eac028d7202b5c06967fe13`; lifecycle/history matrix evidence PASS. |
| I3-TSK-04 | Complete Task authorization/ID-guessing matrix and progress query verification. | Admin/Mentor/Leader/member/creator/current-assignee distinctions, creator-right loss after reassignment, generic same-Project actors, and real query indexes. | `DONE` | `i3_tasks / 2026-08-22` | Reviewed Tasks head `a6261eb0ccad46ee6eac028d7202b5c06967fe13`; authorization/query/index evidence PASS. |

### 6.4 `work/attendance`

| ID | Deliverable | Test/evidence emphasis | Status | Owner/date | Result/commit |
|---|---|---|---|---|---|
| I3-ATT-01 | Finalize attendance-rate and compliance formulas plus `N/A` denominators. | Hand-derived expected values across absence, leave, days off, and violations. | `DONE` | `i3_attendance` / 2026-08-22 | Reviewed Attendance head `bab36cbbe960672ba3dec963c2d8d4fb00c2bd7a`; existing formula implementation freshly verified by PostgreSQL report tests; no new production delta required. |
| I3-ATT-02 | Prove historical stability after workday, schedule, check-in/checkout grace, quota, penalty, and calendar changes. | Before/after report equality plus unchanged cutoff for an older attendance row. | `DONE` | `i3_attendance` / 2026-08-22 | Reviewed Attendance head `bab36cbbe960672ba3dec963c2d8d4fb00c2bd7a`; historical policy/row/allocation implementation freshly verified by PostgreSQL persistence tests; no new production delta required. |
| I3-ATT-03 | Harden concurrent leave quota/overlap and correction-decision races. | PostgreSQL exclusion plus transactional locking/optimistic conflicts. | `DONE` | `i3_attendance` / 2026-08-22 | Reviewed Attendance head `bab36cbbe960672ba3dec963c2d8d4fb00c2bd7a`; PostgreSQL exclusion/locking implementation freshly verified by `AttendanceConcurrencyIntegrationTest`; no new production delta required. |
| I3-ATT-04 | Prove request-time and scheduler equivalence at checkout/correction boundaries and both expiry windows. | Pre-cutoff correction rejection; inclusive submission deadline; delayed/repeated scheduler produces one final transition. | `DONE` | `i3_attendance` / 2026-08-22 | Reviewed Attendance head `bab36cbbe960672ba3dec963c2d8d4fb00c2bd7a`; request-time/scheduler guards freshly verified by persistence, deadline, and concurrency tests; no new production delta required. |
| I3-ATT-05 | Complete terminal-Intern and date-classification edge cases. | Terminal date with/without attendance, approved leave, and global day off. | `DONE` | `i3_attendance` / 2026-08-22 | Reviewed Attendance head `bab36cbbe960672ba3dec963c2d8d4fb00c2bd7a`; terminal-date regression `5c08db6d3dc4a2962ee83ba9d10eb62b4a09b373` plus classification tests green on PostgreSQL 18.4. |
| I3-ATT-06 | Bind new policies from a native future-month value and expose focused Attendance Policy and Global Calendar workflow data without duplicating history. | Month-to-day-1 conversion plus crafted invalid-date denial; route/authorization/history/redaction coverage for `/admin/attendance-policies`, `/attendance/calendar`, and the legacy settings redirect. | `DONE` | `i3_attendance` / 2026-08-22 | Reviewed Attendance head `bab36cbbe960672ba3dec963c2d8d4fb00c2bd7a`; producer milestone `5c08db6d3dc4a2962ee83ba9d10eb62b4a09b373`; focused, affected, branch gates and Javadoc/doclint recorded. |
| I3-ATT-07 | Split Leave and Correction read models and expose reservation-aware monthly Leave balance. | Intern ownership, global Mentor queues, actionable-before-history ordering, correction events, current/month-selected reserved/quota/remaining, status release, frozen cross-month allocations, and legacy request redirect. | `DONE` | `i3_attendance` / 2026-08-22 | Reviewed Attendance head `bab36cbbe960672ba3dec963c2d8d4fb00c2bd7a`; producer milestone `5c08db6d3dc4a2962ee83ba9d10eb62b4a09b373`; focused, affected, branch gates and Javadoc/doclint recorded. |

### 6.5 `work/reports-ui`

| ID | Deliverable | Test/evidence emphasis | Status | Owner/date | Result/commit |
|---|---|---|---|---|---|
| I3-UI-01 | Export attendance/compliance and Project/Task datasets to XLSX. | Parse workbook and compare filters, rows, totals, and `N/A` with HTML. | `DONE` | `i3_reports_ui / 2026-08-22` | Candidate `ba1682ef6e295beb7ddc435abf9f39df759f5d27`; actual-template/XLSX selected and omitted Project/member/status/due/work filters, distinct 60/90/45 minutes, and scoped empty Completion `N/A` parity passed in `ReportExportServiceTest` 8/8; managed non-empty XLSX download rerun passed. Final integrated review remains pending coordination. |
| I3-UI-02 | Export the same datasets to PDF through a print-safe template and embedded Unicode font. | Vietnamese sample text and HTML/XLSX/PDF total parity. | `DONE` | `i3_reports_ui / 2026-08-22` | Candidate `ba1682ef6e295beb7ddc435abf9f39df759f5d27`; actual-template/PDF selected and omitted Project filter block, distinct 60/90/45 minutes, Vietnamese, embedded font, and scoped empty `N/A` parity passed in `ReportExportServiceTest` 8/8; managed non-empty PDF download rerun passed. Final integrated review remains pending coordination. |
| I3-UI-03 | Complete desktop light/dark themes and all required desktop screens/states. | Theme before paint, system override, contrast, error/empty/stale/unavailable states. | `DONE` | `i3_reports_ui / 2026-08-22` | Final-review candidate `4ce56cd506391d84c2c93a1461d5cce72864f4ca`; executable/E2E milestone `e8bf331`; automated theme/palette evidence and orchestrator 1280x720 visual sign-off recorded. Final integrated review remains pending coordination. |
| I3-UI-04 | Complete WCAG-focused keyboard, focus, labels, icon names, and chart alternatives. | Web/accessibility evidence for representative critical pages. | `DONE` | `i3_reports_ui / 2026-08-22` | Final-review candidate `4ce56cd506391d84c2c93a1461d5cce72864f4ca`; executable/E2E milestone `e8bf331`; Node accessibility contracts and automated 3px focus/keyboard evidence green. Final integrated review remains pending coordination. |
| I3-UI-05 | Establish exact-pinned Playwright/Chromium automation, then add critical end-to-end flows across the integrated application. | First automate Iteration 3 account/settings/Leave/Correction/report journeys; after they pass, add a representative Iteration 1/2 bootstrap/onboarding, Project/Task, attendance/correction/leave, History, and report regression set. | `DONE` | `i3_reports_ui / 2026-08-22` | Candidate `ba1682ef6e295beb7ddc435abf9f39df759f5d27`; runtime-derived dates, validated `E2E_BUSINESS_DATE`, real Account/Policy/Calendar/Leave/Mentor/Correction mutations, Project History, and post-exporter-change report-download rerun are green: focused critical 1/1 in 2.1m; prior full 3 passed/2 explicit Intern-credential skips. Final integrated review remains pending coordination. |
| I3-UI-06 | Perform best-effort narrow-screen smoke checks only. | Prevent catastrophic corruption where practical; no mobile parity or mobile mockup gate. | `DONE` | `i3_reports_ui / 2026-08-22` | Final-review candidate `4ce56cd506391d84c2c93a1461d5cce72864f4ca`; executable/E2E milestone `e8bf331`; initialized-bootstrap and 390x844 smoke green. Final integrated review remains pending coordination. |
| I3-UI-07 | Complete Account Directory/Detail/Edit and role-dependent account forms from the reviewed Platform contract. | Search/filter, readable detail, permitted edits only, Intern-field disable/clear plus crafted-request denial, lifecycle actions, focus/errors/confirmation, and no role/display-name/session-history edit surface. | `DONE` | `i3_reports_ui / 2026-08-22` | Final-review candidate `4ce56cd506391d84c2c93a1461d5cce72864f4ca`; executable/E2E milestone `e8bf331`; reviewed Platform `45faa562b051023ee1c69f7cae541185d68de6fd` and clock pin `c59ddd1c262121be25a734ca081b80fd0ce93371` consumed; real browser Account correction passes. Final integrated review remains pending coordination. |
| I3-UI-08 | Separate focused Attendance Policy, Global Calendar, Holiday Import, and SMTP pages with their feature-owned History and clear Admin navigation, reusing the reviewed Attendance and existing Platform integration contracts. | Exact routes/redirect, Admin-only access, no duplicate storage/history, non-secret rendering, native month input, and keyboard/focus/error states. | `DONE` | `i3_reports_ui / 2026-08-22` | Final-review candidate `4ce56cd506391d84c2c93a1461d5cce72864f4ca`; executable/E2E milestone `e8bf331`; reviewed Attendance `bab36cbbe960672ba3dec963c2d8d4fb00c2bd7a` consumed; Policy scheduling and Calendar working-day mutation are real and visible. Final integrated review remains pending coordination. |
| I3-UI-09 | Provide separate Intern My Leave/My Corrections and Mentor Leave Decisions/Correction Decisions pages. | Owned/global scope, actionable queues before retained history, dashboard/current-month and My-Leave/month-selected balance, cross-month labels, no mixed forms, and deadline/status accessibility. | `DONE` | `i3_reports_ui / 2026-08-22` | Final-review candidate `4ce56cd506391d84c2c93a1461d5cce72864f4ca`; executable/E2E milestone `e8bf331`; reviewed Attendance `bab36cbbe960672ba3dec963c2d8d4fb00c2bd7a` consumed; real Leave submit/approval and missed-checkout Correction submit/Mentor decision pass. Final integrated review remains pending coordination. |

The `work/reports-ui` owner shall consume the exact reviewed `I3-PLAT-07`, `I3-ATT-06`, and `I3-ATT-07` producer SHAs before completing `I3-UI-07` through `I3-UI-09`. These tasks do not add Project or Task branch work and shall not depend on or merge the separate unapproved UI-refinement branch.

`I3-UI-05` execution order is: Playwright harness and baseline smoke, Iteration 3 critical journeys, then the representative Iteration 1/2 regression set. Exhaustive conversion of every retained manual Iteration 1/2 journey is deferred until after Iteration 3 acceptance and is not an Iteration 3 exit condition. The implementation shall update `TESTING.md` with Windows, macOS, Linux/CI, and IntelliJ setup and execution instructions; no browser extension is required.

### 6.6 Iteration 3 integration gate

Integration order:

1. `work/platform`
2. `work/projects`, `work/tasks`, and `work/attendance` after their platform dependencies are available
3. `work/reports-ui`

Exit demonstration:

- Historical results and checkout cutoffs remain stable after later policy/calendar changes.
- Concurrent bootstrap, invitation/direct-add, membership exit/transfer, leadership, Task, leave, and correction operations fail safely.
- HTML, XLSX, and PDF expose identical authorized totals.
- Production refuses unsafe origin, proxy, datasource, or master-key configuration.
- The same non-root image works with bundled and external PostgreSQL.
- Work branches verify without publishing; only `main` publishes immutable SHA and convenience tags.
- SSH deployment remains dormant without secrets or an explicit enable variable.
- Desktop light/dark and accessibility acceptance passes; mobile/tablet remains best-effort only.
- Playwright/Chromium passes the Iteration 3 critical journeys and the selected Iteration 1/2 regression set; exhaustive historical E2E conversion remains deferred.
- Account search/filter/edit obeys immutable role, state, SMTP, token/session, and internship-field boundaries.
- Policy, Calendar, Holiday Import, and SMTP are focused Admin workflows with one feature-owned, non-secret history source each.
- Intern and Mentor Leave/Correction workflows remain separate; monthly Leave balances match frozen reservation data across statuses and months.
- Full integrated tests pass at the final integration commit.

## 7. Iteration 4 — Partial Jira/Tempo vertical slice

This iteration is executed on the single isolated `codex/partial-jira-tempo` branch as an explicit user-approved override to the normal five persistent work-branch split. One **GPT-5.6 Luna subagent at Max reasoning effort** owns all remaining implementation work. The root GPT-5.6 Sol agent is the taskmaster, supervisor, integrator, and reviewer; it shall edit coordination/plan artifacts only and shall not write production code, tests, migrations, templates, or feature evidence on Luna's behalf. Delivery order is schema → Task → Reporting; evidence remains under `docs/tests/integration/`, `docs/tests/web/`, and `docs/tests/e2e/` as applicable.

Iteration 4 uses the following execution and review protocol:

1. Luna first preserves and audits the existing uncommitted `I4-TSK-02` correction slice; it shall not reset, discard, or overwrite that work or unrelated files.
2. Luna works one bounded vertical packet at a time using the mandatory RED → GREEN workflow, meaningful Javadoc, focused and affected verification, evidence updates, self-review, and a focused local commit. Luna shall not spawn subagents, push, merge, or change shared branches.
3. After each packet, Luna reports the exact full commit SHA, changed files, RED/GREEN/affected commands and results, evidence paths, limitations, and clean/dirty worktree state to the root agent.
4. The root agent reviews the immutable packet for specification compliance and code quality against the numbered requirements, ADR, and repository standards. The root agent does not implement review fixes.
5. Critical or Important findings return to the same Luna subagent for a focused test-first fix and scoped re-review. The next packet starts only after the root agent accepts the current packet or records an explicit non-blocking ruling.
6. After all tracker items are complete, the root agent performs the broad integrated-candidate review and verification assessment. No push or merge to `main` occurs without a separate explicit user instruction.

The 29 unpublished Iteration 4 commits were subsequently replayed to normalize commit subjects.
Current commit references in the tracker use the rewritten public SHAs. Historical verification
records retain the original tested SHAs, which do not resolve in this repository because the
branches they were written on were never published here. No mapping document is kept: the one that
existed paired 58 commits from two absent branches, and only six of its entries appeared in any
evidence record. A retained SHA is a record of what was tested that day, not a claim that the same
run happened on a rewritten commit.

| ID | Deliverable | Test/evidence emphasis | Status | Owner/date | Result/commit |
|---|---|---|---|---|---|
| I4-PLAT-01 | Add nullable whole-Task estimates and append-only Remaining effort forecast persistence. | Fresh PostgreSQL 18.4 Flyway replay and catalog assertions for checks, same-Project FKs, supersession shape, indexes, and no backfill. | `DONE` | GPT-5.6 Luna Max / 2026-08-26 | Current rewritten commit `f9591e3d3d71b0c2252098112e6e875735117da188d4a15f591cc98a9be32aeb`; the focused PostgreSQL 18.4 Flyway verification historically ran at `ab626fe52deb4f8a5d3a72be3eea55c00273ac5fa475fef32ce1c7061fef4901`. Evidence: `docs/tests/integration/task-effort-planning-catalog.md`. |
| I4-TSK-01 | Add estimate, lifetime actual, and DONE-only variance behavior. | Task application seam, authorization, first-log locking, and hand-derived variance scenarios. | `DONE` | GPT-5.6 Luna Max / 2026-08-26 | Current rewritten commit `9aadf2d38611de4f3bff785a60b38b874ebe3324950f5f5159d392b793c789e6`; Packet-2 and the 35/35 PostgreSQL 18.4 rerun are historically attributed to `83dd9247a7905e08a6c78f449b554a7df4ea2931bd54808a9fcb4c4a2c8f2afd`. Later Packet-3A1 changes are not attributed to that historical commit. Evidence: `docs/tests/integration/task-estimate-effort-variance.md`. |
| I4-TSK-02 | Add forecast-aware reassignment/correction and direct-removal guard. | Manual/batch/redistribution atomicity, provenance, stale/concurrent correction, and non-mutation denial. | `DONE` | GPT-5.6 Luna Max / 2026-08-27 | Current rewritten head `96ed5a7ce42ff0653345f92584ced4cb2f750a7151a1261925e4a142a8515035`. Root review and the 101/101 focused plus 99/99 PostgreSQL gates historically ran on original head `dd265a3ea64c1094e13ba6ddb2bec8667b22e5972c6c22ea3de65ac51a13850b`, after original implementation/review-fix commits `0dc08ee`, `ed5fe01`, `43aa45b`, and `6801d07`. Evidence: `docs/tests/integration/task-remaining-effort-forecast.md` and `docs/tests/integration/task-remaining-effort-forecast-correction.md`. |
| I4-UI-01 | Add authorized Daily Project Work Report dataset and HTML presentation. | Mentor/Admin scope, historical retained logs, current status, empty states, date/calendar semantics. | `DONE` | GPT-5.6 Luna Max / 2026-08-27 | Current rewritten commit `a5b37e91b5fefcde9d7832fca5090f54d4b271a9f0483c8305138e85a35577c8`; the 20/20 focused, 49/49 PostgreSQL, compile, Javadoc/doclint, frontend, full Maven, and 17/20 Node UI evidence historically ran at `b70ce241ab85917aae9395ac4ab17e29703d22dac6f0b9ef1bf75710edad4ba4`. The broad baseline remained 5 failures/21 errors with no new failure family. Evidence: `docs/tests/integration/daily-project-work-report.md` and `docs/tests/web/daily-project-work-report.md`. |
| I4-UI-02 | Add XLSX/PDF Daily export parity and final evidence. | Shared dataset, numeric minutes, Unicode PDF, deterministic filenames, parser-level parity. | `DONE` | GPT-5.6 Luna Max / 2026-08-27 | Current rewritten commit `51bc22b6293069506570799bd128292ca1ed6a19a8cb990a29eec278260be86b`; the 32/32 focused, 77/77 PostgreSQL/Flyway, architecture, compile, Javadoc/doclint, frontend, and frozen broad-run evidence historically ran at `4870889415183380a92a03991b94afb62427a6ca6e186e4514391ec1d793f64d`. The historical broad run had the known 5-failure/21-error baseline plus one Daily print-wrapper failure; its targeted correction returned to only the pre-existing `reports/print.html` failure. Evidence: `docs/tests/web/daily-project-work-report-exports.md`. |
| I4-UI-03 | Supersede Daily-report authorization with Q31-R Mentor/current-Leader scope across HTML, XLSX, and PDF, then remove the dedicated report scope from Admin. | Mentor all-or-selected owned Projects; current-Leader exact mandatory currently-led PLANNED/ACTIVE Project via Project detail or server-derived conditional Daily navigation; non-current-Leader roles and Project IDs denied. Admin has no Attendance, Project/Task, or Daily report navigation, dataset, or exporter scope; its dashboard remains account/configuration-only. | `DONE` | GPT-5.6 Luna Max / 2026-08-27 | Current rewritten implementation/review commits are `36b8e0a0c73b9b2bc3ca09aa5f6eaf45a02dadc4774284590a4dd77a28814ea3`, `9af3602e84c1f56bfd6303c418188ded856b6df9334e340022b40904b7a3f966`, `4650b8838ed9f5a234bf9e296418dd68bc96b35688e1cabc77b76913af39dce6`, `8429d7833308bb6028236e8fdacabc2140831bf2e8b3c31a16d0694e2f734414`, `f02ce7953561ba0dfd0caff1b40f6b3c2ee9334a075e0b7878d16b20a48dd0a2`, and `cf7083e3b5a8a4de836008248c8396b031e04d5a757c1807a9ba15aefccfeb44`. The genuine pre-fix result and final 85/85 focused, 51/51 PostgreSQL, architecture, compile, Javadoc, live-browser, and 17/20 Node UI evidence remain historically attributed to original `b2178ba9b6abc755c9b4affb9b537c91b7a79aec8b9c688bd4beaef268ca2274`; the final rewritten candidate is verified separately after the history-map commit. Keyboard traversal, a live multi-Project selector, actual download payloads, and a clean broad Maven run remained unverified at that historical snapshot. No push. |

> **Reporting-role correction — 29 August 2026:** The I4-UI-03 Admin Attendance removal was accidental and is superseded. Active Admins retain Attendance report navigation, detailed-Intern datasets, and XLSX/PDF exports. Admin Project/Task and Daily report denial, and the account/configuration-only dashboard, remain unchanged.

## 8. Mandatory TDD workflow

Every feature follows this sequence:

1. Select a requirement and acceptance scenario.
2. Write the smallest behavioral test.
3. Run it and confirm the intended RED failure.
4. Name the requirement identifiers in the test Javadoc, with the break it catches and the hand-derived expected value.
5. Write the minimum production code required for GREEN and add its meaningful Javadoc in the same implementation milestone.
6. Run the focused test.
7. Run the affected module/integration/web suite.
8. Refactor without weakening assertions.
9. Run the affected suite again.
10. Report the final commands, results, and commit SHA.

Recommended history:

```text
test(attendance): prove 09:00 check-in grace boundary [RED]
feat(attendance): enforce inclusive check-in grace boundary [GREEN]
```

The RED and GREEN commits may be pushed together after the branch head is green. The failing historical commit proves test-first order without leaving the remote branch intentionally broken.

The trace lives in the test source, not in a separate document. See
[`.sdd/rfcs/ADR-004-test-evidence-moves-into-the-test.md`](.sdd/rfcs/ADR-004-test-evidence-moves-into-the-test.md)
for why, and [`.sdd/reviews/traceability.md`](.sdd/reviews/traceability.md) for
the mapping of the classes that predate the rule.

Evidence paths cited in the tracker rows below point at files removed by that
decision. They are dated records of what was run and stay as written; the files
they name are reachable in git history.

| Branch | Non-negotiable evidence |
|---|---|
| `work/platform` | Bootstrap concurrency, token lifecycle, account/security authorization, encryption, SMTP failure, session invalidation, production-profile failure. |
| `work/projects` | Lifecycle graphs, ownership, membership/invitation/exit/leadership intervals, transfer transactions, optimistic locking, guessed-ID denial. |
| `work/tasks` | Parameterized status graph, Leader/member creator/assignee distinction, self-Task and same-Project generic actors, daily-minute concurrency, progress totals. |
| `work/attendance` | Injected-Clock boundaries, PostgreSQL overlap/uniqueness, policy history, quota concurrency, schedulers and request-time guards. |
| `work/reports-ui` | MockMvc forms/authorization, accessible rendering, report query totals, XLSX/PDF parsing/parity, critical browser journeys. |

## 10. Iteration handoff protocol

At each iteration boundary:

1. Each branch owner updates every claimed item to `DONE`, `BLOCKED`, or returns it to `TODO`.
2. The owner provides commit SHA, evidence paths, exact verification commands, and remaining risk.
3. Integrate branches in the iteration's declared order.
4. Resolve cross-module conflicts through the owning branch rather than duplicating code in the integrator.
5. Run the full affected integrated suite.
6. Record the integration commit in Section 3.
7. Bring the integrated `main` into all five persistent work branches before the next iteration begins.

Handoff template:

```markdown
### <tracker ID> — <short title>

- Status: DONE | BLOCKED | TODO
- Owner:
- Requirements/scenarios:
- RED evidence:
- GREEN evidence:
- Focused verification:
- Affected-suite verification:
- Commit SHA:
- Remaining risk/blocker:
- Required next owner/action:
```

## 11. Global definition of done

The list lives in
[`.sdd/constitution.md`](.sdd/constitution.md), under Layer 3, Definition of
done. It was kept in both places until 11 September 2026 and the two copies had
already diverged, so this tracker now points at the one that binds.

## 12. Progress log

Append material coordination events only. Do not duplicate every commit.

| Date/time | Agent/person | Event | Tracker IDs | Evidence/commit | Next action |
|---|---|---|---|---|---|
| — | — | Plan initialized; no implementation item claimed. | — | — | Obtain requirements approval and begin Iteration 1. |
| 2026-08-15 | Taskmaster + five feature owners | Retrofitted targeted Lombok boilerplate after every owner fast-forwarded to the latest `main`; all five exact heads passed independent review and the combined merge passed 201 PostgreSQL 18.4 tests, compile, Javadoc/doclint, deterministic assets, and final integration review. | Iteration 1 maintenance | Platform `c9499732`; Project `58712710`; Task `469d7e27`; Attendance `5ddbd75f`; Reporting `8500cb6e`; integrated `b764707716f54ad164547b087f658c277cf46e0f`; `docs/tests/unit/lombok-*-boilerplate.md` | Push reviewed `main`, then fast-forward all five persistent work branches before Iteration 2 work. |
