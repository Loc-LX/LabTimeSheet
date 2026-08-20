# Lab Timesheet — Three-Iteration Delivery Plan

**Artifact purpose:** Local agent coordination and progress tracking  
**Implementation branches:** `work/platform`, `work/projects`, `work/tasks`, `work/attendance`, `work/reports-ui`  
**Requirements authority:** `labtimesheet-docs-hub/requirements-specification.md`  
**SRS:** `labtimesheet-docs-hub/software-requirements-specification.md`  
**Initial status:** Planning complete; implementation remains subject to requirements approval

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
3. Identify the test level and evidence file that will protect the behavior.
4. Write and run the failing test before production code.
5. Confirm the test fails because the required behavior is missing, not because the test or environment is broken.

When completing an item, the agent shall record:

- the exact RED command and expected failure;
- the exact GREEN and affected-suite commands;
- the evidence Markdown path under `docs/tests/`;
- the implementation commit or final local commit SHA;
- any remaining limitation that is explicitly allowed by the requirements.

No item becomes `DONE` based only on compilation, an isolated happy path, screenshots, or a verbal claim.

## 2. Branch ownership and conflict boundaries

| Branch | Sole or primary ownership |
|---|---|
| `work/platform` | Maven/application baseline, package/module foundations, Flyway migration files, account/security/bootstrap, internship lifecycle, integration credential lifecycle, notification delivery infrastructure, Docker, and CI. |
| `work/projects` | Projects, membership intervals, invitations, membership-exit requests, leadership terms, Project lifecycle, Project-scoped authorization, member-removal orchestration, and Project completion. |
| `work/tasks` | Tasks, generic creator/assignment actors, member self-Task rules, comments, work logs, fixed status transitions, assignment/reassignment, Task soft deletion, and Project Task-progress calculations. |
| `work/attendance` | Attendance-policy versions, configured workdays, global calendar, HolidayAPI import interpretation, attendance, corrections, leave, deadline schedulers, and attendance/compliance metrics. |
| `work/reports-ui` | Shared Thymeleaf shell/fragments, Tailwind tokens/assets, dashboards, invitation/exit-request screens, shared report datasets, Chart.js presentation, XLSX/PDF exports, and cross-product UI/accessibility consistency. |

Conflict-prevention rules:

- `work/platform` owns `src/main/resources/db/migration/**`, Maven/dependency configuration, Compose, container build files, and CI workflow files. Other branches request schema changes instead of independently allocating migration versions.
- `work/reports-ui` owns shared templates/fragments, shared design tokens, and general UI assets. Each domain branch owns its module-specific controllers and pages while consuming those shared fragments.
- `work/tasks` exposes focused Task query/transfer operations required by Project workflows. `work/projects` owns the transaction that removes a member or completes a Project.
- `work/platform` owns HolidayAPI credential storage and the tested HTTP client. `work/attendance` owns preview interpretation, selection, deduplication, import, and day-off effects.
- Attendance time and Task work time remain separate. No branch may make one mutate or prove the other.
- Do not introduce a generic workflow engine, generic event-sourcing layer, multi-assignee Task model, Project-level day-off model, or speculative cross-module abstraction.

## 3. Iteration overview

| Iteration | Theme | Required demonstration | Status | Integration commit |
|---|---|---|---|---|
| 1 | Working vertical slice | Bootstrap users, create/activate Project, assign/change a Task, and check in/out with role-correct UI. | `TODO` | — |
| 2 | Complete business workflows | Policy/calendar changes, leave/corrections, leadership/member transfer, work logs, notifications, and full HTML workflows. | `TODO` | — |
| 3 | Hardening and delivery | Historical/concurrency proof, production security, HTML/XLSX/PDF parity, accessibility, containers, and CI publication boundary. | `TODO` | — |

## 4. Iteration 1 — Working vertical slice

### 4.1 `work/platform`

| ID | Deliverable | Test/evidence emphasis | Status | Owner/date | Result/commit |
|---|---|---|---|---|---|
| I1-PLAT-01 | Establish the Maven/Spring Boot modular baseline and module/package boundaries. | Context/load test; architecture/package-boundary check. | `TODO` | — | — |
| I1-PLAT-02 | Promote the reviewed 23-table/56-foreign-key PostgreSQL baseline into the platform-owned initial Flyway migration after explicit approval. | Fresh PostgreSQL 18.4 migration replay, catalog assertions, and invitation/exit same-Project constraints. | `TODO` | — | — |
| I1-PLAT-03 | Configure PostgreSQL Testcontainers and shared test-only encryption/clock facilities. | Affected integration tests require no developer database or SMTP. | `TODO` | — | — |
| I1-PLAT-04 | Implement atomic first-Admin bootstrap and permanent bootstrap closure. | Concurrent submissions create exactly one first Admin; restart keeps bootstrap closed. | `TODO` | — | — |
| I1-PLAT-05 | Implement initial SMTP draft/test/active path sufficient for Mailpit onboarding. | Failed test cannot activate; tested revision supports delivery. | `TODO` | — | — |
| I1-PLAT-06 | Create Mentor/Intern accounts, deliver activation, set first password, and authenticate/logout. | SMTP gate, single-use token, expiry, normalized email, role/state access. | `TODO` | — | — |
| I1-PLAT-07 | Provide development Compose with PostgreSQL and Mailpit plus initial Gitea verification workflow. | Fresh developer start and branch/main verification. | `TODO` | — | — |

### 4.2 `work/projects`

| ID | Deliverable | Test/evidence emphasis | Status | Owner/date | Result/commit |
|---|---|---|---|---|---|
| I1-PRJ-01 | Atomically create a Mentor-owned `PLANNED` Project, eligible initial Leader membership, and first leadership term. | No committed Project is empty or leaderless; non-owner, ineligible Leader, and guessed-ID access are denied. | `TODO` | — | — |
| I1-PRJ-02 | Directly add eligible Interns through owning-Mentor-controlled interval memberships. | Multiple concurrent Projects per Intern; duplicate active membership rejected; no acceptance step for direct add. | `TODO` | — | — |
| I1-PRJ-03 | Appoint and change one current Leader from active same-Project members. | One current Leader; non-member/ineligible selection rejected. | `TODO` | — | — |
| I1-PRJ-04 | Activate a Project when initial member, Leader, date, and assignee guards pass. | Missing Leader/member or invalid assignee blocks activation. | `TODO` | — | — |
| I1-PRJ-05 | Provide Project list/detail/member/leadership pages with owning-Mentor and member visibility. | MockMvc authorization plus direct-ID denial. | `TODO` | — | — |

### 4.3 `work/attendance`

| ID | Deliverable | Test/evidence emphasis | Status | Owner/date | Result/commit |
|---|---|---|---|---|---|
| I1-ATT-01 | Resolve the seeded attendance policy, timezone, configured workdays, schedule, and separate check-in/checkout grace boundaries. | Both grace defaults are 30; values are 0–720; checkout cutoff must stay before local midnight. | `TODO` | — | — |
| I1-ATT-02 | Manage manual future global calendar events and day-off decisions. | Admin-only mutation; past-event immutability; workday/day-off distinction. | `TODO` | — | — |
| I1-ATT-03 | Check in once on an eligible day using server time and the effective policy. | Off-day, approved-leave, duplicate, lifecycle rejection, and inclusive 09:00 check-in-grace boundary. | `TODO` | — | — |
| I1-ATT-04 | Check out once through the attached-policy checkout cutoff and derive basic daily classification. | Default 16:00 succeeds; first later instant and zero-grace late attempt fail; no checkout becomes only `MISSING_CHECKOUT` with raw checkout unchanged. | `TODO` | — | — |
| I1-ATT-05 | Provide own-attendance history and authorized Mentor/Admin inspection. | Own/global-view authorization and historical applied-policy display. | `TODO` | — | — |

### 4.4 `work/tasks`

| ID | Deliverable | Test/evidence emphasis | Status | Owner/date | Result/commit |
|---|---|---|---|---|---|
| I1-TSK-01 | Create one-assignee Tasks in `PLANNED` or `ACTIVE`: any active member for self, current Leader for any active same-Project member. Store generic creator/assigner/assignee actors. | Self-Task actor equality, other-assignee denial for members, Leader allowance, and cross-Project actor rejection. | `TODO` | — | — |
| I1-TSK-02 | Validate optional due dates against Project dates and current global days off. | Boundary dates accepted; outside/day-off dates rejected. | `TODO` | — | — |
| I1-TSK-03 | Enforce the complete fixed Task status graph through current-assignee authorization. | Parameterized allowed/forbidden transition matrix and ID denial. | `TODO` | — | — |
| I1-TSK-04 | Add append-only Task comments for active members, current Leader, and owning Mentor. | Unauthorized/non-member and completed-Project mutation denial. | `TODO` | — | — |
| I1-TSK-05 | Show Task list/detail and initial DONE/non-deleted progress/status counts. | Empty Project renders `N/A`; soft/deleted data not yet exposed as current. | `TODO` | — | — |

### 4.5 `work/reports-ui`

| ID | Deliverable | Test/evidence emphasis | Status | Owner/date | Result/commit |
|---|---|---|---|---|---|
| I1-UI-01 | Establish Tailwind tokens and reusable Thymeleaf shell/fragments. | Fragment rendering, local assets, no unauthorized navigation items. | `DONE` | `work/reports-ui`, 2026-08-20 | `f5eeb0b`; `docs/tests/web/ui-shell-components.md`, `docs/tests/web/desktop-overflow-and-frontend-source.md` |
| I1-UI-02 | Implement the supported desktop sidebar/header, forms, tables, badges, alerts, confirmations, empty/error states, and theme bootstrap. | Keyboard labels/focus, no desktop page-level overflow, pre-paint theme application. | `DONE` | `work/reports-ui`, 2026-08-20 | `f5eeb0b`; `docs/tests/web/ui-shell-components.md`, `docs/tests/web/theme-token-contrast.md`, `docs/tests/web/dark-icon-sprite-presentation.md`, `docs/tests/web/desktop-overflow-and-frontend-source.md` |
| I1-UI-03 | Build basic Admin, Mentor, and Intern dashboards from real queries. | Role-correct metrics/actions; illustrative data never leaks into production paths. | `DONE` | `work/reports-ui`, 2026-08-20 | `f5eeb0b`; `docs/tests/web/dashboard-template-contract.md`, `docs/tests/web/role-dashboard-routing.md`, `docs/tests/web/authenticated-dashboard-landing.md`; `docs/tests/unit/task-dashboard-query.md`, `docs/tests/unit/attendance-current-state.md` |
| I1-UI-04 | Integrate bootstrap, authentication, Project, Task, and attendance pages into the shared shell. | Critical MockMvc web flows and server-side authorization. | `DONE` | `work/reports-ui`, 2026-08-20 | `f5eeb0b`; `docs/tests/web/account-shell-integration.md`, `docs/tests/web/project-task-shell-integration.md`, `docs/tests/web/attendance-shell-integration.md`, `docs/tests/web/project-login-flow.md`, `docs/tests/web/authenticated-dashboard-landing.md`, `docs/tests/web/review-round-1-shared-ui.md`, `docs/tests/web/review-round-2-smtp-shell.md` |

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

## 5. Iteration 2 — Complete business workflows

Iteration 2 starts only after every continuing branch incorporates the integrated Iteration 1 `main`.

### 5.1 `work/platform`

| ID | Deliverable | Test/evidence emphasis | Status | Owner/date | Result/commit |
|---|---|---|---|---|---|
| I2-PLAT-01 | Complete account lock/unlock/deactivation and session invalidation. | State graph, authentication denial, retained attribution. | `TODO` | — | — |
| I2-PLAT-02 | Implement activation failure/resend and forgot/reset-password workflows. | Failed token invalidation, single-use/expiry, generic enumeration-safe responses. | `TODO` | — | — |
| I2-PLAT-03 | Implement internship start activation, completion, and withdrawal guards. | Scheduler plus request-time activation; Leader/unfinished-Task terminal guards. | `TODO` | — | — |
| I2-PLAT-04 | Complete encrypted SMTP and HolidayAPI draft/test/active revision lifecycles. | AES-GCM round trip, wrong-key failure, one active revision, no browser secret disclosure. | `TODO` | — | — |
| I2-PLAT-05 | Expose the tested VN HolidayAPI client to the attendance module. | Valid preview, invalid key/rate limit/unavailable responses without local-data outage. | `TODO` | — | — |
| I2-PLAT-06 | Persist in-app notifications and initial ordinary-email delivery states, including Project invitation and membership-exit created/resolved types. | Recipient deduplication, domain commit independent of SMTP, self-Task silence, and `UNAVAILABLE`/sent/failed behavior. | `TODO` | — | — |

### 5.2 `work/tasks`

| ID | Deliverable | Test/evidence emphasis | Status | Owner/date | Result/commit |
|---|---|---|---|---|---|
| I2-TSK-01 | Add dated 1–1440-minute Task work logs and author corrections. | Membership/date boundaries and author-only editing. | `TODO` | — | — |
| I2-TSK-02 | Enforce the combined 1440-minute daily total across all Projects. | PostgreSQL integration and concurrent over-allocation proof. | `TODO` | — | — |
| I2-TSK-03 | Reassign unfinished Tasks while preserving creator, state, comments, and work history and updating generic assignment actor/time. | DONE requires assignee reopen; creator attribution and prior logs/comments remain unchanged. | `TODO` | — | — |
| I2-TSK-04 | Implement Task edit/soft deletion and authorized historical inspection for Leader and self-Task creator. | Leader controls any unfinished Task; creator controls only while still current assignee; deleted Task leaves normal progress but remains historical. | `TODO` | — | — |
| I2-TSK-05 | Provide Task transfer and completion-query operations to the Projects module. | Atomic bulk transfer behavior and non-deleted/DONE counts. | `TODO` | — | — |
| I2-TSK-06 | Complete Project status counts, percentage, total minutes, and per-member work queries. | Empty `N/A`, authorization scopes, hand-checkable totals. | `TODO` | — | — |

### 5.3 `work/projects`

| ID | Deliverable | Test/evidence emphasis | Status | Owner/date | Result/commit |
|---|---|---|---|---|---|
| I2-PRJ-01 | Issue and revoke non-expiring Leader invitations while retaining the issuing leadership term. | Eligibility, one pending Project/Intern pair, Leader-own versus Mentor-any revocation, ordinary notification behavior. | `TODO` | — | — |
| I2-PRJ-02 | Let only the intended authenticated Intern accept or decline; support Mentor direct-add supersession. | Email is not a bearer join token; exactly one membership; terminal status/code and provenance retained. | `TODO` | — | — |
| I2-PRJ-03 | Create/cancel Leader-removal and member-leave requests without changing current rights. | Nonblank reason, same-Project/type shape, one pending request per target, requester-only cancellation. | `TODO` | — | — |
| I2-PRJ-04 | Let only the owning Mentor approve/reject exits, using the assisted unfinished-Task transfer and Leader-replacement transaction. | Transfer/replacement/interval/request resolution commit together; reject/cancel changes request only. | `TODO` | — | — |
| I2-PRJ-05 | Retain leadership history, change Leader without moving assignments, and complete only when every non-deleted Task is `DONE`. | Exactly one current Leader in PLANNED/ACTIVE; completion closes intervals, revokes invitations, and supersedes exits. | `TODO` | — | — |
| I2-PRJ-06 | Provide invitation, exit, former-member, and completed-Project historical read-only views. | Historical visibility and attribution without stale mutation authority. | `TODO` | — | — |

### 5.4 `work/attendance`

| ID | Deliverable | Test/evidence emphasis | Status | Owner/date | Result/commit |
|---|---|---|---|---|---|
| I2-ATT-01 | Schedule future-month attendance-policy versions, including separate grace values, and preserve effective history. | First-of-future-month rule; effective immutability; old checkout cutoff/report stability. | `TODO` | — | — |
| I2-ATT-02 | Preview/import VN HolidayAPI candidates with Admin selection, override, provenance, and deduplication. | Public suggestion not authority; manual fallback; repeated import safety. | `TODO` | — | — |
| I2-ATT-03 | Materialize frozen full-day leave allocations and monthly/cross-month quota reservations. | Workday/day-off classification, policy snapshot, quota per month. | `TODO` | — | — |
| I2-ATT-04 | Implement leave submit/approve/reject/cancel and overlap protection. | Same-day boundary, pending/approved reservations, concurrent overlap/quota. | `TODO` | — | — |
| I2-ATT-05 | Implement missed-checkout correction submission and effective-checkout derivation. | Reject before/at checkout cutoff; accept afterward through scheduled end +24 hours; raw checkout remains null. | `TODO` | — | — |
| I2-ATT-06 | Implement Mentor approve/reject/revert and separate decision-window locking. | Valid state graph, concurrent decision, expired pending auto-rejection. | `TODO` | — | — |
| I2-ATT-07 | Add idempotent schedulers and equivalent request-time deadline guards. | Late/multiple scheduler invocation cannot duplicate transitions/notifications. | `TODO` | — | — |

### 5.5 `work/reports-ui`

| ID | Deliverable | Test/evidence emphasis | Status | Owner/date | Result/commit |
|---|---|---|---|---|---|
| I2-UI-01 | Complete Admin/Mentor/Intern/Leader dashboards and notification UI. | Authorization-correct actions and deadline/state summaries. | `TODO` | — | — |
| I2-UI-02 | Complete account, integration, Project, invitation, membership-exit, Task, policy/calendar, attendance, correction, and leave desktop workflows using shared fragments. | Leader invite/revoke/removal request, Intern accept/decline/leave/cancel, and Mentor exit decision forms retain safe input and expose conflicts clearly. | `TODO` | — | — |
| I2-UI-03 | Build one authorized attendance/compliance HTML report dataset. | Date filters, detailed versus own scope, formulas and `N/A`. | `TODO` | — | — |
| I2-UI-04 | Build one authorized Project/Task HTML report dataset. | Project/member/status/date filters and per-member visibility rules. | `TODO` | — | — |
| I2-UI-05 | Add only meaningful Chart.js trends with adjacent text/table alternatives. | Accessible label, equivalent data, theme tokens, reduced motion. | `TODO` | — | — |

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
- Leader reassigns an unfinished Task while preserving work history.
- Leader invites an eligible Intern; the signed-in Intern accepts or declines; Mentor direct-add safely supersedes a pending invite.
- Mentor changes Leader without moving the former Leader's Tasks.
- Member requests to leave and Leader requests removal; Mentor rejects or approves through assisted transfer and required replacement.
- Mentor completes a Project after all non-deleted Tasks are done.
- Ordinary domain actions retain in-app notifications when SMTP is unavailable.
- Full integrated tests pass at the iteration integration commit.

## 6. Iteration 3 — Hardening, reports, and delivery readiness

Iteration 3 starts only after every continuing branch incorporates the integrated Iteration 2 `main`.

### 6.1 `work/platform`

| ID | Deliverable | Test/evidence emphasis | Status | Owner/date | Result/commit |
|---|---|---|---|---|---|
| I3-PLAT-01 | Add normalized-email-plus-source-IP login throttling. | Five-in-15 and 15-minute throttle boundaries; successful-login clearing. | `TODO` | — | — |
| I3-PLAT-02 | Finish ordinary email retry schedule and terminal failure handling. | Exact 1m/5m/30m/2h/12h attempts; idempotent bounded worker. | `TODO` | — | — |
| I3-PLAT-03 | Add token cleanup and production-safe operational/status views. | Expired token behavior, no secret/stack/SQL disclosure. | `TODO` | — | — |
| I3-PLAT-04 | Enforce production HTTPS/origin/proxy/header/cookie/master-key readiness. | Prod fails unsafe configuration; dev/test relax only transport/origin controls. | `TODO` | — | — |
| I3-PLAT-05 | Produce the non-root application image and bundled/external PostgreSQL deployment modes. | Same immutable image becomes healthy in both configurations. | `TODO` | — | — |
| I3-PLAT-06 | Finalize Gitea verification, main-only OCI publication, and disabled SSH deployment/rollback template. | Work branches never publish; disabled deploy receives no secrets; exact-SHA flow is testable when enabled. | `TODO` | — | — |

### 6.2 `work/projects`

| ID | Deliverable | Test/evidence emphasis | Status | Owner/date | Result/commit |
|---|---|---|---|---|---|
| I3-PRJ-01 | Harden concurrent membership, invitation acceptance/direct-add, exit approval, and leadership operations. | One valid winner; locks/rechecks cover invitation, Project, membership, leadership, request, and unfinished Tasks; stale requests produce explicit conflict without partial history. | `TODO` | — | — |
| I3-PRJ-02 | Complete the global-role/context/ownership authorization matrix for direct membership, invitations, exits, and leadership. | Direct-ID, stale Leader, wrong invitee, cross-Mentor/member, requester, target, and decision-maker negative cases. | `TODO` | — | — |
| I3-PRJ-03 | Prove completed/historical read-only behavior and terminal Intern guards. | No mutation through UI or direct request after lifecycle closure. | `TODO` | — | — |
| I3-PRJ-04 | Verify Project list/progress query indexes and bounded performance. | Explain plan/catalog evidence for actual report paths. | `TODO` | — | — |

### 6.3 `work/tasks`

| ID | Deliverable | Test/evidence emphasis | Status | Owner/date | Result/commit |
|---|---|---|---|---|---|
| I3-TSK-01 | Harden concurrent status, reassignment, deletion, and daily-minute operations. | Optimistic conflicts and serialized daily total. | `TODO` | — | — |
| I3-TSK-02 | Complete due-date impact behavior for later-created global days off. | Existing due date retained and disclosed; new/changed due date rejected. | `TODO` | — | — |
| I3-TSK-03 | Complete former-assignee work-log correction and historical-deletion boundaries. | Author/member/Project lifecycle matrix. | `TODO` | — | — |
| I3-TSK-04 | Complete Task authorization/ID-guessing matrix and progress query verification. | Admin/Mentor/Leader/member/creator/current-assignee distinctions, creator-right loss after reassignment, generic same-Project actors, and real query indexes. | `TODO` | — | — |

### 6.4 `work/attendance`

| ID | Deliverable | Test/evidence emphasis | Status | Owner/date | Result/commit |
|---|---|---|---|---|---|
| I3-ATT-01 | Finalize attendance-rate and compliance formulas plus `N/A` denominators. | Hand-derived expected values across absence, leave, days off, and violations. | `TODO` | — | — |
| I3-ATT-02 | Prove historical stability after workday, schedule, check-in/checkout grace, quota, penalty, and calendar changes. | Before/after report equality plus unchanged cutoff for an older attendance row. | `TODO` | — | — |
| I3-ATT-03 | Harden concurrent leave quota/overlap and correction-decision races. | PostgreSQL exclusion plus transactional locking/optimistic conflicts. | `TODO` | — | — |
| I3-ATT-04 | Prove request-time and scheduler equivalence at checkout/correction boundaries and both expiry windows. | Pre-cutoff correction rejection; inclusive submission deadline; delayed/repeated scheduler produces one final transition. | `TODO` | — | — |
| I3-ATT-05 | Complete terminal-Intern and date-classification edge cases. | Terminal date with/without attendance, approved leave, and global day off. | `TODO` | — | — |

### 6.5 `work/reports-ui`

| ID | Deliverable | Test/evidence emphasis | Status | Owner/date | Result/commit |
|---|---|---|---|---|---|
| I3-UI-01 | Export attendance/compliance and Project/Task datasets to XLSX. | Parse workbook and compare filters, rows, totals, and `N/A` with HTML. | `TODO` | — | — |
| I3-UI-02 | Export the same datasets to PDF through a print-safe template and embedded Unicode font. | Vietnamese sample text and HTML/XLSX/PDF total parity. | `TODO` | — | — |
| I3-UI-03 | Complete desktop light/dark themes and all required desktop screens/states. | Theme before paint, system override, contrast, error/empty/stale/unavailable states. | `TODO` | — | — |
| I3-UI-04 | Complete WCAG-focused keyboard, focus, labels, icon names, and chart alternatives. | Web/accessibility evidence for representative critical pages. | `TODO` | — | — |
| I3-UI-05 | Add critical end-to-end flows across the integrated application. | Bootstrap/onboarding, Project/Task, attendance/correction/leave, and reports. | `TODO` | — | — |
| I3-UI-06 | Perform best-effort narrow-screen smoke checks only. | Prevent catastrophic corruption where practical; no mobile parity or mobile mockup gate. | `TODO` | — | — |

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
- Full integrated tests pass at the final integration commit.

## 7. Mandatory TDD workflow

Every feature follows this sequence:

1. Select a requirement and acceptance scenario.
2. Write the smallest behavioral test.
3. Run it and confirm the intended RED failure.
4. Record the RED command/result in the appropriate evidence file.
5. Write the minimum production code required for GREEN.
6. Run the focused test.
7. Run the affected module/integration/web suite.
8. Refactor without weakening assertions.
9. Run the affected suite again.
10. Record final commands/results and commit SHA.

Recommended history:

```text
test(attendance): prove 09:00 check-in grace boundary [RED]
feat(attendance): enforce inclusive check-in grace boundary [GREEN]
```

The RED and GREEN commits may be pushed together after the branch head is green. The failing historical commit proves test-first order without leaving the remote branch intentionally broken.

Required evidence locations:

| Test level | Evidence directory |
|---|---|
| Unit/state/calculation | `docs/tests/unit/` |
| PostgreSQL/module integration | `docs/tests/integration/` |
| MockMvc/Thymeleaf/security web behavior | `docs/tests/web/` |
| Cross-module/browser journey | `docs/tests/e2e/` |

Every evidence Markdown record must include:

- requirement and scenario IDs;
- protected behavior and why it matters;
- test method and hand-derived expected result;
- exact RED command and relevant failure;
- exact GREEN and affected-suite commands/results;
- external dependency or environment boundaries;
- final commit SHA when available.

## 8. Branch-level test emphasis

| Branch | Non-negotiable evidence |
|---|---|
| `work/platform` | Bootstrap concurrency, token lifecycle, account/security authorization, encryption, SMTP failure, session invalidation, production-profile failure. |
| `work/projects` | Lifecycle graphs, ownership, membership/invitation/exit/leadership intervals, transfer transactions, optimistic locking, guessed-ID denial. |
| `work/tasks` | Parameterized status graph, Leader/member creator/assignee distinction, self-Task and same-Project generic actors, daily-minute concurrency, progress totals. |
| `work/attendance` | Injected-Clock boundaries, PostgreSQL overlap/uniqueness, policy history, quota concurrency, schedulers and request-time guards. |
| `work/reports-ui` | MockMvc forms/authorization, accessible rendering, report query totals, XLSX/PDF parsing/parity, critical browser journeys. |

## 9. Iteration handoff protocol

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

## 10. Global definition of done

A tracker item is complete only when:

- the numbered requirement and acceptance behavior are satisfied;
- the test existed and failed for the intended reason before production code;
- focused and affected suites pass;
- authorization and negative cases are covered where applicable;
- PostgreSQL-specific rules are tested against PostgreSQL, not H2;
- concurrency/deadline/history behavior has proportionate evidence;
- UI behavior uses server-side authorization and shared fragments;
- documentation/evidence paths are recorded in this tracker;
- no unrelated files or another branch's ownership area were changed without coordination;
- the final branch head is green;
- integration does not alter totals, state graphs, or historical meaning.

## 11. Progress log

Append material coordination events only. Do not duplicate every commit.

| Date/time | Agent/person | Event | Tracker IDs | Evidence/commit | Next action |
|---|---|---|---|---|---|
| — | — | Plan initialized; no implementation item claimed. | — | — | Obtain requirements approval and begin Iteration 1. |
| 2026-08-20 | `work/reports-ui` | Completed Iteration 1 §4.5 deliverables: shared shell/fragments, desktop components, role dashboards from real queries, and full page integration; restored the clobbered Tailwind source and added desktop overflow evidence. | I1-UI-01, I1-UI-02, I1-UI-03, I1-UI-04 | `f5eeb0b`; `docs/tests/web/desktop-overflow-and-frontend-source.md` | Run iteration-1 integration gate on `main`; note pre-existing Windows `LayerStructureTest` path-separator boundary with platform owner. |
