# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

- A server-rendered Spring Boot modular monolith on PostgreSQL. Versions and the reason for each choice are in [`shared_context.md`](shared_context.md); what version 1 deliberately excludes is `GOV-007`.

## Users

- **Admins** operate accounts, internship lifecycles, attendance policy, the global calendar, SMTP, HolidayAPI, and system configuration. Their dashboard is account/configuration-only. For now they may view and export every report read-only, covering Attendance, Project/Task, and Daily Project Work, but they edit no Project or Task and decide no leave, correction, or attendance exception. The instructor expects to withdraw part of this access later.
- **Mentors** own Projects, directly manage membership and leadership, decide membership exits, monitor Project and Intern progress/history, comment on Tasks, inspect attendance, and decide leave and missed-checkout corrections.
- **Interns** check in and out, request leave and missed-checkout corrections, respond to their own Project invitations, request/cancel their own Project exit, participate in multiple Projects, create self-assigned Tasks when eligible, perform assigned Tasks, comment, update their own assigned Task status, record Task work, and inspect authorized Project history.
- A **Project Leader** is an Intern with a current leadership term for one Project. It is contextual authority, never a global account role. The Leader may invite eligible Interns, request a member's removal, manage Task definitions/assignment, and redistribute unfinished Tasks away from a pending exit target in confirmed batches.
- The product is reviewed and maintained by a university project team and its instructor or appointed maintainer.

## Product Purpose

Lab Timesheet supports a university laboratory or internship program by bringing account administration, attendance, leave, missed-checkout correction, Project work, Task progress, notifications, and authorized reporting into one working system.

Success means each role can complete its permitted work without spreadsheets or informal message trails, while deadlines, decisions, historical attribution, and report totals remain explainable and auditable.

## Positioning

The product joins attendance oversight and Project delivery without pretending they are the same measurement. Check-in and checkout establish attendance; dated Task work logs establish Project effort. Effective-dated policy and frozen historical allocations prevent later configuration changes from rewriting past results.

## Operating Context

- The business timezone is `Asia/Ho_Chi_Minh`; business dates use the applicable attendance-policy timezone and persisted event instants are treated as UTC.
- The system is operated as one server-rendered web application with PostgreSQL. Desktop browsers are the supported interface target; mobile and tablet behavior is best-effort and is not guaranteed to expose every workflow optimally.
- Initial installation uses a one-time first-Admin bootstrap. Later account creation and password recovery depend on a tested SMTP configuration.
- Admins may preview and import Vietnamese holiday candidates from HolidayAPI, while the stored Admin decision remains authoritative. Manual calendar management remains available.
- Mentors review global leave and correction queues and separately oversee only the Projects they own.
- Reports cover attendance/compliance and Project/Task progress in HTML, Excel, and PDF from one shared dataset definition, with role-scoped access: active Mentors and Admins may inspect detailed Intern Attendance, Interns retain own-Attendance scope, and owning Mentors/current Leaders retain authorized Project/Task detail. Admins may view and export every report read-only. Current Leaders can discover Daily reporting only for currently-led open Projects.
- The authoritative requirements are the eight specs under `.sdd/specs/`, indexed by `.sdd/requirements.md`. They document a system that is built: four iterations have shipped and the schema lives in Flyway. They were approved as version 1.0.0 on 14 September 2026; the questions still to confirm with the instructor are listed in the Notes section of each spec, and §22.2 of the platform spec records the approval.

## Capabilities and Constraints

- Global account roles are exactly `ADMIN`, `MENTOR`, and `INTERN`, and are immutable after account creation.
- Project membership is many-to-many and interval-based. The owning Mentor may add/remove directly and makes every exit decision; the current Leader may invite; only the intended authenticated Intern may accept/decline; members may request but cannot unilaterally leave. Pending exit keeps existing rights but blocks new/self-assignment to the target; the Leader redistributes unfinished Tasks before approval, while direct Mentor removal retains its atomic automatic-transfer shortcut.
- A Project that will not run is cancelled with a reason and kept read-only; only an empty draft can be deleted.
- Every `PLANNED` or `ACTIVE` Project has exactly one current Intern Leader. Any active member may create a Task assigned only to themselves; only the current Leader may create for another member or reassign broader Task work.
- Each Task has one current assignee, who alone records work on it and moves it through its execution states. The current Leader and the owning Mentor may only block, unblock, or reopen a Task; nobody starts or finishes a Task for its assignee.
- Attendance uses server-time check-in and checkout. Effective-dated policy stores separate check-in and checkout grace periods, both defaulting to 30 minutes; with the default 15:30 end, normal checkout closes immediately after the inclusive 16:00:00 cutoff. Task work is a separate dated-minute record and never proves attendance.
- Leave is full-day. Only frozen eligible workdays consume quota, and pending or approved requests reserve it.
- Corrections apply only to missing checkout after the attendance row's historical checkout cutoff. Submission remains open through scheduled end plus 24 hours, and the Mentor then receives a separate 24-hour decision window.
- Global attendance policy is effective-dated; historical attendance and leave allocations must not drift after later policy or calendar changes. Admin-only setting History tabs and authorized Project History read the retained domain rows already present; they never expose secrets or invent previous-assignee/status/edit timelines that are not stored.
- SMTP and HolidayAPI secrets are Admin-managed and encrypted with a deployment-provided master key. Email-dependent account actions fail closed when SMTP is unavailable; other domain actions retain in-app delivery.
- HTML, Excel, and PDF reports must agree on the same hand-checkable totals and render undefined denominators as `N/A`.
- The product language is English in v1. Displayed business dates use `dd/MM/yyyy` and times use 24-hour local time.
- Features absent from the reviewed requirements are not silently in scope.

## Brand Commitments

- The working product name is **Lab Timesheet & Project Management System**, shortened to **Lab Timesheet** where space is constrained.
- The supplied Vercel/shadcn-style operations-shell images are illustrative references for a compact permission-aware application shell with supported light and dark modes. They do not define fields, workflows, authorization, or persistence and never override numbered requirements or reviewed DDL.
- Interface copy must be direct, operational, and honest about permissions, deadlines, destructive consequences, unavailable integrations, and illustrative data.

## Evidence on Hand

- `.sdd/specs/` holds the approved requirements, eight specs of numbered rules, indexed by `.sdd/requirements.md`.
- The companion `database-schema.sql` and the `assets/ui-reference-*.png` visual references remain in the separate documentation repository and are not tracked here. The live schema is the Flyway migration set under `src/main/resources/db/migration`, which creates twenty-four tables.
- The repository contains the implemented product. All tracked deliverables for Iterations 1 through 4 are marked `DONE` in `plan.md`; only the Iteration 3 integrated review remains.
- No production data, customer testimonials, adoption metrics, institutional endorsements, or performance claims are available. Future design work must not fabricate them.

## Assumptions

Each item below is believed true but has not been confirmed with the instructor or
product owner. Each is a risk if it turns out false, and the consequence column
says what breaks. Confirm or retire them rather than letting them stay implicit;
every one of the first four has already caused a real failure.

| # | Assumption | Consequence if false |
|---:|---|---|
| A1 | Development and verification happen on macOS. | 104 of 155 evidence records embed `/Users/sechmachine` paths and 124 embed `/opt/homebrew`. On another platform they cannot be reproduced, and `./mvnw test` needs `-Duser.timezone=Asia/Ho_Chi_Minh` before PostgreSQL will accept the JVM default. |
| A2 | The separate `labtimesheet-docs-hub` repository stays reachable. | The design DDL and the reference and mockup images live only there. The specification names them and cannot render them. |
| A3 | Test fixtures may hold fixed future dates. | Three integration classes seed `LocalDate.of(2026, 8, 1)` and now fail with `Project start date cannot be in the past`. Any fixed date eventually expires. |
| A4 | A single operator drives the multi-branch iteration workflow. | The orchestration skill the evidence cites is not on `main`, and the Iteration 2 ledgers reference 65 commits that do not exist in this repository. |
| A5 | The programme runs on one server in one timezone. | `GOV-011` resolves business dates in the policy timezone and `SEC-007` keeps login throttle state in memory. Neither survives multi-node deployment. |
| A6 | Interns hold at most one internship at a time. | `intern_profiles` carries one lifecycle per account; a second concurrent internship has no representation. |
| A7 | The instructor or product owner is available to decide requirement conflicts. | `GOV-001` puts the primary implementor at the top of the authority order. With nobody in that role, a requirement conflict has no tiebreaker and stalls. |

## Product Principles

1. **Authorization follows stored context.** Global role alone is insufficient; ownership, membership, leadership, assignment, lifecycle, and record scope determine access.
2. **History does not move or pretend.** Later policy, calendar, membership, invitation, exit decision, leadership, assignment, or assignee changes must not silently rewrite past results, completed-Task assignee names, creator attribution, or provenance. History views expose only retained domain facts and non-secret metadata; they do not fabricate event timelines the schema never stored.
3. **Attendance and Project work stay distinct.** The product may report them together, but one never derives or proves the other.
4. **Deadlines are enforced at every path.** Scheduled workers improve timeliness, while request-time guards preserve correctness when scheduling is late.
5. **Prefer explicit, reviewable operations.** Feature-owned controller/service/repository flows, constrained state transitions, focused integrations, and shared report datasets serve clarity over speculative machinery.
6. **Fixes preserve branch ownership.** A targeted repair uses a clean `work/fix/<feature>/<what-fix>` branch from verified `main`, not `work/<feature>/fix/<what-fix>`; persistent `work/<feature>` refs already occupy that Git ref prefix.

## Accessibility & Inclusion

- The web interface must meet WCAG 2.2 AA contrast and interaction requirements in both light and dark themes.
- Controls require associated labels or accessible names, visible keyboard focus, keyboard operation, and adequate target sizes.
- Status and validation cannot depend on color alone. Forms retain safe input, identify field errors, and provide an error summary.
- Charts are supplemental: every canvas requires an accessible label and an adjacent textual or tabular alternative.
- Desktop navigation and data tables must remain fully operable without page-level horizontal overflow. Mobile and tablet layouts should avoid preventable breakage on a best-effort basis but are not a fully supported v1 target.
