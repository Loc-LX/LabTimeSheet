# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

- Java 25 and Spring Boot 4.1.0.
- Maven-built, server-rendered Spring MVC modular monolith organized by business feature.
- Spring Security, Spring Data JPA, Bean Validation, Thymeleaf, Spring Mail, and Flyway.
- Tailwind CSS 4 with Node 24 LTS used only for frontend build assets.
- PostgreSQL 18.4 across development, integration testing, and production.
- No SPA framework, JWT authentication, microservices, Redis, Kafka, or generic workflow engine in v1.

## Users

- **Admins** operate accounts, internship lifecycles, attendance policy, the global calendar, SMTP, HolidayAPI, and system configuration. They inspect Project and attendance progress but do not perform Mentor or Project Leader work.
- **Mentors** own Projects, directly manage membership and leadership, decide membership exits, monitor Project and Intern progress, comment on Tasks, inspect attendance, and decide leave and missed-checkout corrections.
- **Interns** check in and out, request leave and missed-checkout corrections, respond to their own Project invitations, request/cancel their own Project exit, participate in multiple Projects, create self-assigned Tasks, perform assigned Tasks, comment, update their own assigned Task status, and record Task work.
- A **Project Leader** is an Intern with a current leadership term for one Project. It is contextual authority, never a global account role. The Leader may invite eligible Interns, request a member's removal, and manage Task definitions/assignment inside that Project.
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
- Reports cover attendance/compliance and Project/Task progress in HTML, Excel, and PDF from one shared dataset definition.
- The authoritative requirements are currently a review draft. Product-context initialization does not authorize application implementation or promote the review DDL into Flyway.

## Capabilities and Constraints

- Global account roles are exactly `ADMIN`, `MENTOR`, and `INTERN`, and are immutable after account creation.
- Project membership is many-to-many and interval-based. The owning Mentor may add/remove directly and makes every exit decision; the current Leader may invite; only the intended authenticated Intern may accept/decline; members may request but cannot unilaterally leave.
- Every `PLANNED` or `ACTIVE` Project has exactly one current Intern Leader. Any active member may create a Task assigned only to themselves; only the current Leader may create for another member or reassign broader Task work.
- Each Task has one current assignee. Only that assignee changes its status and records work.
- Attendance uses server-time check-in and checkout. Effective-dated policy stores separate check-in and checkout grace periods, both defaulting to 30 minutes; with the default 15:30 end, normal checkout closes immediately after the inclusive 16:00:00 cutoff. Task work is a separate dated-minute record and never proves attendance.
- Leave is full-day. Only frozen eligible workdays consume quota, and pending or approved requests reserve it.
- Corrections apply only to missing checkout after the attendance row's historical checkout cutoff. Submission remains open through scheduled end plus 24 hours, and the Mentor then receives a separate 24-hour decision window.
- Global attendance policy is effective-dated; historical attendance and leave allocations must not drift after later policy or calendar changes.
- SMTP and HolidayAPI secrets are Admin-managed and encrypted with a deployment-provided master key. Email-dependent account actions fail closed when SMTP is unavailable; other domain actions retain in-app delivery.
- HTML, Excel, and PDF reports must agree on the same hand-checkable totals and render undefined denominators as `N/A`.
- The product language is English in v1. Displayed business dates use `dd/MM/yyyy` and times use 24-hour local time.
- Features absent from the reviewed requirements are not silently in scope.

## Brand Commitments

- The working product name is **Lab Timesheet & Project Management System**, shortened to **Lab Timesheet** where space is constrained.
- The supplied Vercel/shadcn-style operations-shell images are illustrative references for a compact permission-aware application shell with supported light and dark modes. They do not define fields, workflows, authorization, or persistence and never override numbered requirements or reviewed DDL.
- Interface copy must be direct, operational, and honest about permissions, deadlines, destructive consequences, unavailable integrations, and illustrative data.

## Evidence on Hand

- `labtimesheet-docs-hub/requirements-specification.md` is the authoritative requirements review draft.
- `labtimesheet-docs-hub/database-schema.sql` is the companion PostgreSQL design baseline, not yet a production migration.
- `labtimesheet-docs-hub/assets/ui-reference-light.png`, `ui-reference-dark-shell.png`, and `ui-reference-dark-dashboard.png` are the supplied visual references.
- The repository contains an early Spring Boot scaffold matching the recorded Java/Spring/Maven direction but no implemented product interface yet.
- No production data, customer testimonials, adoption metrics, institutional endorsements, or performance claims are available. Future design work must not fabricate them.

## Product Principles

1. **Authorization follows stored context.** Global role alone is insufficient; ownership, membership, leadership, assignment, lifecycle, and record scope determine access.
2. **History does not move.** Later policy, calendar, membership, invitation, exit decision, leadership, assignment, or assignee changes must not silently rewrite past results, Task creator attribution, or provenance.
3. **Attendance and Project work stay distinct.** The product may report them together, but one never derives or proves the other.
4. **Deadlines are enforced at every path.** Scheduled workers improve timeliness, while request-time guards preserve correctness when scheduling is late.
5. **Prefer explicit, reviewable operations.** Feature-owned controller/service/repository flows, constrained state transitions, focused integrations, and shared report datasets serve clarity over speculative machinery.

## Accessibility & Inclusion

- The web interface must meet WCAG 2.2 AA contrast and interaction requirements in both light and dark themes.
- Controls require associated labels or accessible names, visible keyboard focus, keyboard operation, and adequate target sizes.
- Status and validation cannot depend on color alone. Forms retain safe input, identify field errors, and provide an error summary.
- Charts are supplemental: every canvas requires an accessible label and an adjacent textual or tabular alternative.
- Desktop navigation and data tables must remain fully operable without page-level horizontal overflow. Mobile and tablet layouts should avoid preventable breakage on a best-effort basis but are not a fully supported v1 target.
