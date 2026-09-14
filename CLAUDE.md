# CLAUDE.md — Lab Timesheet project map

Context for working in this codebase. Rules live in
[AGENTS.md](AGENTS.md); invariants live in [`.sdd/constitution.md`](.sdd/constitution.md).
This file is what the system *is*.

## In sixty seconds

A university laboratory runs an internship programme. It needs to know two
separate things: whether interns showed up, and what they produced. This
application answers both without letting either answer stand in for the other.

Server-rendered Spring MVC with Thymeleaf. No SPA, no REST API for the browser,
no JWT. Spring Security sessions. PostgreSQL through Spring Data JPA, schema
owned by Flyway. Tailwind builds through Node but ships as static assets.

Four iterations are complete. What remains is the final integrated review.

## The two rules that explain the design

**Attendance and Task work never derive each other** (`GOV-004`). An intern who
logs eight hours of Task work from home is still absent. An intern who checks in
every day with no work logs still shows zero Project progress. These are
different tables, in different features, deliberately never joined.

**History does not move** (`GOV-005`). When an Admin changes the workday
schedule today, last month's attendance report must produce the same numbers it
produced last month. This is why the schema has twenty-four tables rather than
about twelve: policy versions are effective-dated, memberships and leadership
are intervals that close rather than delete, and approved leave freezes the
policy snapshot it was judged under.

If a change would make either rule false, it is wrong, however convenient.

## Code layout

```
com.lab.labtimesheet
├── LabtimesheetApplication      root package, do not move
├── config/                      shared wiring, security, time, filters
└── feature/
    ├── account/                 users, internships, tokens, bootstrap, login throttle
    ├── attendance/              punches, policy versions, calendar, leave, corrections
    ├── integration/             SMTP and HolidayAPI configuration, encrypted
    ├── notification/            in-app inbox and email delivery with retry
    ├── project/                 Projects, membership, leadership, invitations, exits
    ├── reporting/               attendance, Project/Task, and Daily reports plus exports
    └── task/                    Tasks, comments, work logs, transfers, effort forecasts
```

Each feature carries only the layers it needs from `controller`, `exception`,
`model`, `model.dto`, `model.entity`, `repository`, `service`. Tests under
`src/test/java` mirror the same packages.

A feature may call another feature's **service and DTOs**. It may never touch
another feature's repository or entity. `LayerStructureTest` fails the build on
violation, using a regex over imports.

## The trap that catches everyone

**Project Leader is not a role.** `app_users` has exactly three immutable global
roles: `ADMIN`, `MENTOR`, `INTERN`. There is no `ROLE_LEADER` and searching for
one wastes an hour.

Leadership is a row in `project_leadership_terms` with a start and an end, bound
to one Project. The same intern can lead Project A while being an ordinary
member of Project B. Authorization for Leader actions therefore resolves from
the stored term inside the transaction, not from the security context.

The same pattern governs membership. `project_memberships` holds intervals.
Removing a member closes the interval and leaves completed Tasks pointing at the
closed membership so history still shows who did the work.

## Data model

Twenty-four tables. `V1__baseline.sql` creates twenty-three;
`V2__add_task_effort_planning.sql` adds `task_remaining_effort_forecasts` and
alters `tasks`.

| Group | Tables |
|---|---|
| Platform | `system_state`, `app_users`, `user_action_tokens`, `intern_profiles`, `smtp_configurations`, `holiday_api_configurations` |
| Policy and calendar | `attendance_policy_versions`, `attendance_policy_workdays`, `global_calendar_events` |
| Project and Task | `projects`, `project_memberships`, `project_leadership_terms`, `project_invitations`, `project_membership_exit_requests`, `tasks`, `task_comments`, `task_work_logs`, `task_remaining_effort_forecasts` |
| Attendance and leave | `attendance_records`, `attendance_corrections`, `attendance_correction_events`, `leave_requests`, `leave_request_days` |
| Delivery | `notifications` |

`leave_request_days` is the clearest illustration of `GOV-005`: it materializes
each quota-consuming date together with the policy version and monthly quota
snapshot in force when the request was decided. Later policy edits cannot
retroactively change how much quota a past request consumed.

## Authorization model

Global role alone never grants access. Every check resolves stored context:
ownership of the Project, active membership, a current leadership term, being
the current assignee, and the lifecycle state of the aggregate.

- **Admin** manages accounts, internship lifecycle, SMTP, HolidayAPI, attendance policy, and the global calendar. Read-only on Projects. Holds Attendance report scope, and no Project/Task or Daily report scope. See [`.sdd/rfcs/ADR-002-attendance-report-scope.md`](.sdd/rfcs/ADR-002-attendance-report-scope.md).
- **Mentor** owns the Projects they created, decides every membership exit, and decides leave and missed-checkout corrections. Cannot create or assign Tasks.
- **Intern** checks in and out, works assigned Tasks, records work logs, requests leave and corrections, answers their own invitations.
- **Current Leader** invites eligible interns, defines and assigns Tasks within the led Project, and redistributes unfinished Tasks in confirmed batches before a pending exit is approved.

A guessed identifier for a record the caller cannot see must be denied without
revealing that the record exists.

## Time

Business timezone is `Asia/Ho_Chi_Minh`, resolved from the applicable attendance
policy version rather than the server default. Persisted instants are
`timestamptz`, treated as UTC. Server time is authoritative for every punch,
submission, decision, and expiry. Browser-supplied timestamps are never trusted.

Displayed dates use `dd/MM/yyyy` and 24-hour local time. Product language is
English.

## Things learned the hard way

**There is exactly one plan.** `plan.md`. A duplicate once sat
at the repository root, drifted, and misreported finished work as `TODO`. Do not
recreate it.

**The requirements have been lost twice and recovered from git both times.** If
the specification is missing, recover it from history rather than rewriting it.

**Admin Attendance report scope has changed more than once.** The code is
correct and [`.sdd/rfcs/ADR-002-attendance-report-scope.md`](.sdd/rfcs/ADR-002-attendance-report-scope.md)
explains why. Do not "fix" the code to match older wording.

**Verify a path before trusting its name.** The specification lives in
`.sdd/specs/`, one spec per feature, indexed by `.sdd/requirements.md`. Some evidence records cite an
older location; they are dated records and are left as written.

## Useful commands

```bash
npm ci && npm run build          # frontend assets, required before first run
./mvnw spring-boot:run           # app on :8080, redirects to /bootstrap when empty
./mvnw test                      # needs Docker for PostgreSQL Testcontainers
npm run test:ui                  # UI contract tests
```

Development expects PostgreSQL on `55432` and Mailpit SMTP on `1025`. Details in
[DEVELOPMENT.md](DEVELOPMENT.md).

## Tooling

This repository is indexed by GitNexus for impact analysis, symbol context, and
graph change detection. The required commands and the rules for using them are
in [AGENTS.md](AGENTS.md), which is where agent operating rules belong.
