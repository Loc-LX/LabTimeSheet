# Lab Timesheet

Server-rendered Spring Boot application for a university laboratory internship
programme. It manages accounts and internship lifecycle, attendance with leave
and missed-checkout corrections, Mentor-owned Projects with contextual Intern
leadership, single-assignee Tasks with dated work logs, notifications, and
authorized reports in HTML, XLSX, and PDF.

Two rules shape most of the design. Attendance time and Task work time are
separate domains and neither proves the other (`GOV-004`). Historical business
results must not change when an Admin later edits policy or the calendar
(`GOV-005`). Together they explain why the schema carries twenty-four tables
that close intervals instead of overwriting rows.

## Status

All tracked deliverables for Iterations 1 through 4 are marked `DONE` in
[`.agents/PROJECT_PLAN.md`](.agents/PROJECT_PLAN.md), each with an implementation
commit and an evidence path. The Iteration 3 heading itself stays `IN_PROGRESS`
because the final integrated review has not been run.

| Iteration | Scope | Tracked items |
|---|---|---:|
| 1 | Working vertical slice | complete |
| 2 | Complete business workflows | complete |
| 3 | Hardening, security, reports, containers, accessibility | 31 of 31 |
| 4 | Partial Jira/Tempo slice: Task effort planning and Daily reports | 6 of 6 |

**`.agents/PROJECT_PLAN.md` is the only live plan.** A stale duplicate at the
repository root was removed; it had stopped being updated on 21 August 2026 and
still showed every Iteration 3 item as `TODO`.

Remaining before release: the Iteration 3 integrated review, covering historical
stability after policy changes, concurrency safety, HTML/XLSX/PDF total parity,
production configuration refusal, the container image against bundled and
external PostgreSQL, and the desktop accessibility pass. Mobile and tablet
layouts stay best effort; desktop is the supported target.

## Documentation map

| Question | Read |
|---|---|
| What must always be true of this system | [CONSTITUTION.md](CONSTITUTION.md) |
| How do I set up and contribute | [CONTRIBUTING.md](CONTRIBUTING.md) |
| What is the product and who are its users | [PRODUCT.md](PRODUCT.md) |
| What do the domain terms mean | [CONTEXT.md](CONTEXT.md) |
| What are the numbered requirements | [`docs/labtimesheet-docs-hub/requirements-specification.md`](docs/labtimesheet-docs-hub/requirements-specification.md) |
| Why was a decision made | [`docs/adr/`](docs/adr/) |
| Which libraries and versions, and why | [TECH_STACK.md](TECH_STACK.md) |
| What are the UI design tokens | [DESIGN.md](DESIGN.md) |
| How do I run the app locally | [DEVELOPMENT.md](DEVELOPMENT.md) |
| How do I run and write tests | [TESTING.md](TESTING.md) |
| How do I deploy | [DEPLOYMENT.md](DEPLOYMENT.md) |
| What is planned and who owns it | [`.agents/PROJECT_PLAN.md`](.agents/PROJECT_PLAN.md) |
| What evidence backs a behavior | [`docs/tests/`](docs/tests/README.md) |
| Rules for AI agents working here | [AGENTS.md](AGENTS.md), [CLAUDE.md](CLAUDE.md) |

The requirements document carries 267 numbered IDs. It was deleted in error on
29 August 2026 and restored from history on 10 September 2026.

## What works today

### Accounts and onboarding

- Atomic first-Admin bootstrap that stays closed after initialization and restart.
- Optional SMTP onboarding with five distinct deferral warnings and a persistent restricted-state notice.
- Admin SMTP draft, connection test, and activation against Mailpit or another configured server.
- Admin creation of Admin, Mentor, and Intern accounts through single-use email activation.
- Activation resend and failure handling, forgot and reset password, account lock, unlock, deactivation, and session invalidation.
- Admin account directory with server-side search and role filter, plus controlled email-identity correction.
- Internship start activation, guarded completion and withdrawal, recipient-scoped notification inbox, and non-secret integration history.

### Projects

- Owning Mentors create `PLANNED` Projects with an eligible initial Leader.
- Mentor-controlled direct membership with historical membership and leadership intervals.
- Invitation accept and decline, safe direct-add supersession, assisted member and Leader exits, repeated Task-transfer batches, and atomic direct removal.
- Leader reassignment and guarded `PLANNED` to `ACTIVE` activation.
- Guarded Project completion plus role-correct current and retained Project History views.
- Role-correct Project lists, details, member views, and guessed-ID concealment.

### Tasks

- One current assignee per Task.
- Active members create self-assigned Tasks; the current Leader may assign another active member.
- Due dates are checked against Project dates and current global days off.
- The fixed `TODO`, `IN_PROGRESS`, `BLOCKED`, and `DONE` transition graph is enforced.
- Authorized edits, reassignment, soft deletion, dated work logs and corrections, comments, retained history, status counts, and completion progress.
- Nullable whole-Task estimates, lifetime actual effort, `DONE`-only variance, and append-only Remaining effort forecasts on reassignment.
- Daily work is serialized across Projects and capped at 1,440 minutes.

### Attendance and calendar

- Effective attendance-policy resolution with Vietnam business time, configured workdays, and separate 30-minute check-in and checkout grace defaults.
- Admin-managed manual global calendar days off, with separate focused pages for Policy, Calendar, Holiday Import, and SMTP, each carrying its own feature-owned history.
- Effective-dated policy and calendar history, Vietnam HolidayAPI preview and import, leave decisions, missed-checkout corrections and reverts, and deadline schedulers.
- Server-time check-in and checkout with duplicate, off-day, leave-day, lifecycle, and cutoff rejection.
- `MISSING_CHECKOUT` classification without a second early-departure violation.
- Separate Intern My Leave and My Corrections pages, and Mentor Leave Decisions and Correction Decisions queues.

### Reports

- Intern own attendance history, plus authorized active-Mentor and active-Admin detailed Intern attendance and compliance reports using the historically applied policy and schedule.
- Owning-Mentor and current-Leader Project and Task reports with per-member hours.
- Daily Project Work Reports for owning Mentors and current Leaders, with XLSX and PDF export parity.
- Admin holds Attendance report scope only, with no Project/Task or Daily report page, export, or dataset. See [`docs/adr/0002-admin-attendance-report-scope.md`](docs/adr/0002-admin-attendance-report-scope.md).
- Undefined denominators render as `N/A`, and all three formats agree on hand-checkable totals.

### Security and operations

- Login throttling keyed on normalized email plus source IP, expiring token cleanup, and generic non-disclosing authentication responses.
- Ordinary email retry schedule with terminal failure handling.
- Production startup refuses an unsafe public origin, proxy policy, datasource, or missing master key.
- Non-root Java 25 container image that runs against a bundled or external PostgreSQL.

### Desktop UI

- Shared Thymeleaf and Tailwind shell with role-aware navigation and dashboards.
- Light, dark, and system themes applied before paint.
- Collapsible desktop sidebar, accessible forms and errors, tables, badges, empty states, and local Lucide icons.
- Local Chart.js trends keep adjacent text or table alternatives and honour reduced motion.

## Quick start

Full setup, container commands, and an IntelliJ IDEA walkthrough are in
[DEVELOPMENT.md](DEVELOPMENT.md). The short version:

```bash
cp .env.example .env
# Edit .env. Generate LAB_SECURITY_MASTER_KEY with: openssl rand -base64 32

export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"

npm ci
npm run build
./mvnw spring-boot:run
```

Development defaults to the application on port `8080`, PostgreSQL on `55432`,
and Mailpit SMTP on `1025`, configured in
[`application-dev.yaml`](src/main/resources/application-dev.yaml).

On first launch open `http://localhost:8080`. The application redirects to
`/bootstrap` where you create the first Admin, then asks you to configure and
test SMTP or acknowledge all five deferral warnings.

The committed [`.env.example`](.env.example) holds placeholders only. Real
database passwords and the AES-256 master key belong in an untracked `.env`.
Product SMTP and HolidayAPI credentials are configured in the Admin console, not
through environment variables.

## Architecture at a glance

- Java 25, Spring Boot 4.1.0, Maven, Spring MVC, Security, Data JPA, Validation, Thymeleaf, Flyway, PostgreSQL 18.4.
- Node 24 and npm 11 build frontend assets only, with Tailwind CSS 4 and `lucide-static`.
- Package-by-feature modular monolith under `com.lab.labtimesheet.feature`, one package each for `account`, `attendance`, `integration`, `notification`, `project`, `reporting`, and `task`.
- Features talk through public services and DTOs. No cross-feature repository or entity access, and no business SQL. `LayerStructureTest` enforces this.
- Flyway owns the schema and Hibernate validates it with `ddl-auto=validate`.

Version-by-version detail and the reason each library was chosen are in
[TECH_STACK.md](TECH_STACK.md).

## Testing

Tests need Docker for PostgreSQL Testcontainers.

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export DOCKER_HOST=unix:///Users/your-name/.orbstack/run/docker.sock # OrbStack only
./mvnw test
```

The 21 August 2026 Iteration 2 gate recorded 444 of 444 Maven tests passing, the
focused architecture and Flyway gate at 13 of 13 with a replay producing 23
tables and 56 foreign keys, and 7 of 7 UI contract tests. Migration `V2` has
since added a twenty-fourth table for Task effort planning, so that table count
is a record of the gate rather than a current figure.

Every behavior test has a companion Markdown record under
[`docs/tests/`](docs/tests/README.md). [TESTING.md](TESTING.md) covers the
required TDD cycle, evidence format, and common failures.

## Continuous integration and production containers

Gitea Actions verifies every pull request and push. The separate container
workflow runs only on manual dispatch or a push to `main`, and its own
verification job must pass before either image build starts. Manual runs build
without publishing. Pushes to `main` publish Linux AMD64, adding native Linux
ARM64 only when the repository declares its ARM runner online. Every published
revision carries an immutable `sha-<full-commit>` tag with `main` as a
convenience alias.

The production image is a non-root Java 25 image. The root
[compose.yaml](compose.yaml) supports either a persistent PostgreSQL 18.4
sidecar or an external database, and is not used for development. Follow
[DEPLOYMENT.md](DEPLOYMENT.md) and start from
[`.env.compose.example`](.env.compose.example), keeping the real production
environment file outside the repository.

## Branch ownership

| Branch | Primary area |
|---|---|
| `work/platform` | Application baseline, schema, accounts, security, integrations |
| `work/projects` | Projects, membership, leadership, lifecycle |
| `work/tasks` | Tasks, comments, status, progress |
| `work/attendance` | Policy, calendar, attendance workflows |
| `work/reports-ui` | Shared UI, dashboards, reporting presentation |

For a targeted repair, create a clean isolated branch and worktree from the
taskmaster-verified current `main` named
`work/fix/<feature>/<what-fix>`. Do not nest it as
`work/<feature>/fix/<what-fix>`: the persistent `work/<feature>` ref already
uses that Git ref prefix.

Every targeted repair starts from the taskmaster-verified latest `main`, uses TDD RED → GREEN, adds Javadoc during implementation, records companion evidence, undergoes independent review, and uses a normal, non-force merge only when separately authorized.

[CONTRIBUTING.md](CONTRIBUTING.md) has the step-by-step workflow.
