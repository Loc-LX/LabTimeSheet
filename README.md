# Lab Timesheet

Server-rendered Spring Boot application for managing laboratory internships,
Projects, Tasks, attendance, notifications, and authorized reports. Iterations
1 and 2 are implemented; the Iteration 2 integrated candidate was verified on
21 August 2026.

## Iteration 2: working now

### Accounts and onboarding

- Atomic first-Admin bootstrap that remains closed after initialization and restart.
- Optional SMTP onboarding with five distinct deferral warnings and a persistent restricted-state notice.
- Admin SMTP draft, connection test, and activation against Mailpit or another configured server.
- Admin creation of Admin, Mentor, and Intern accounts through single-use email activation.
- Activation resend/failure handling, forgot/reset password, account lock/unlock/deactivation, and session invalidation.
- Internship start activation, guarded completion/withdrawal, recipient-scoped notification inbox, and non-secret integration history.
- Password setup, form login, logout, global roles, and role-protected Admin routes.

### Projects

- Owning Mentors create `PLANNED` Projects with an eligible initial Leader.
- Mentor-controlled direct membership with historical membership and leadership intervals.
- Invitation accept/decline, safe direct-add supersession, assisted member/Leader exits, repeated Task-transfer batches, and atomic direct removal.
- Leader reassignment and guarded `PLANNED` to `ACTIVE` activation.
- Guarded Project completion plus role-correct current and retained Project History views.
- Role-correct Project lists, details, member views, and guessed-ID concealment.

### Tasks

- One current assignee per Task.
- Active members create self-assigned Tasks; the current Leader may assign another active member.
- Due dates are checked against Project dates and current global days off.
- The fixed `TODO`, `IN_PROGRESS`, `BLOCKED`, and `DONE` transition graph is enforced.
- Authorized edits, reassignment, soft deletion, dated work logs/corrections, comments, retained history, status counts, and completion progress.
- Daily work is serialized across Projects and capped at 1,440 minutes.

### Attendance and calendar

- Effective attendance-policy resolution with Vietnam business time, configured workdays, and separate 30-minute check-in and checkout grace defaults.
- Admin-managed manual global calendar days off.
- Effective-dated policy/calendar history, Vietnam HolidayAPI preview/import, leave decisions, missed-checkout corrections/reverts, and deadline schedulers.
- Server-time check-in and checkout with duplicate, off-day, leave-day, lifecycle, and cutoff rejection.
- `MISSING_CHECKOUT` classification without a second early-departure violation.
- Intern own history plus authorized active-Mentor/Admin attendance/compliance reports using the historical applied policy and schedule.

### Desktop UI

- Shared Thymeleaf/Tailwind shell with role-aware navigation and dashboards.
- Role-scoped reporting navigation: Admin retains the Attendance report page and XLSX/PDF exports, but has no Project/Task or Daily report pages or exports; current Project Leaders can discover Daily reporting only for currently-led open Projects.
- Light, dark, and system themes applied before paint.
- Collapsible desktop sidebar, accessible forms/errors, tables, badges, empty states, and local Lucide icons.
- Bootstrap, authentication, integration, Project/exit/transfer, Task, policy/calendar, attendance, correction, leave, notification, history, and report pages integrated into the same shell.
- Meaningful local Chart.js trends retain adjacent text/table alternatives and reduced-motion behavior.

## Deliberately not implemented yet

The baseline schema includes later-workflow tables; table presence does not mean
the corresponding feature is complete.

- Iteration 3: XLSX/PDF export parity, notification retry/terminal delivery, login throttling and remaining production security hardening, concurrency qualification, backup/restore, container publication, and deployment readiness.
- Mobile layouts are best-effort. Desktop is the supported interface target.

## Architecture and versions

- Java 25, Spring Boot 4.1.0, Maven, Spring MVC/Security/Data JPA/Validation, Thymeleaf, Flyway, and PostgreSQL 18.4.
- Node 24/npm 11, Tailwind CSS 4.3.3, and `lucide-static` 1.27.0 for local assets.
- Package-by-feature modular monolith under `com.lab.labtimesheet.feature`.
- Cross-feature access through public services and DTOs; no cross-feature repositories, shadow entities, or business SQL.
- Flyway owns the schema; Hibernate validates it with `ddl-auto=validate`.

## Local development

Follow [DEVELOPMENT.md](DEVELOPMENT.md) for the complete beginner-friendly
setup, PostgreSQL and Mailpit container commands, terminal launch steps, and an
IntelliJ IDEA run-configuration walkthrough.

The committed [`.env.example`](.env.example) contains placeholders only. Real
database passwords and the AES-256 master key belong in an untracked `.env`.
Product SMTP and HolidayAPI credentials are configured through the Admin
console, not environment variables.

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
and Mailpit SMTP on `1025`. The exact Spring settings are in
[`application-dev.yaml`](src/main/resources/application-dev.yaml), which imports
the ignored root `.env` file when the `dev` profile is active.

On first launch, open `http://localhost:8080`; the application redirects to
`/bootstrap`, where you create the first Admin. Then configure and test SMTP or
complete all five explicit deferral warnings.

## Verification status

The final Iteration 2 integration gate recorded:

- 444/444 Maven tests passed with PostgreSQL 18.4 Testcontainers.
- The focused architecture/Flyway gate passed 13/13 and replay produced exactly 23 application tables and 56 foreign keys.
- Node/Tailwind built successfully and all 7/7 UI contract tests passed.
- Java compilation and full Javadoc/doclint passed.
- Requirement parity remained 260 IDs across all four catalogues and 14 generated use cases.
- A real Java process reported health/liveness/readiness `UP`; local Chromium completed the policy, calendar, leave, correction, invitation, exit/transfer, direct-removal, leadership, Project completion/history, report, and SMTP-unavailable notification journeys.
- Independent reviews of all five branches and the integrated candidate closed with no remaining Critical or Important findings.

The immutable Iteration 2 branch and integration summaries are under
[`docs/iterations/iteration-2/git-ledgers`](docs/iterations/iteration-2/git-ledgers/).

Tests require Docker for PostgreSQL Testcontainers:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export DOCKER_HOST=unix:///Users/your-name/.orbstack/run/docker.sock # only when using OrbStack
./mvnw test
```

See [TESTING.md](TESTING.md) for setup, test commands, the required TDD cycle,
evidence records, best practices, and common fixes. Every behavior test has a
companion record under [`docs/tests`](docs/tests/README.md).

## Continuous integration and production containers

Gitea Actions verifies every pull request and push. The separate container
workflow runs only for a manual dispatch or a push to `main`, and its verification
job must pass before either image build starts. Manual runs build without publishing;
`main` pushes publish Linux AMD64 and add native Linux ARM64 only when the repository
explicitly declares that its ARM runner is online. Every published revision has an
immutable `sha-<full-commit>` tag, with `main` as a convenience alias.

The production image is a non-root Java 25 image. The root [compose.yaml](compose.yaml)
supports either a persistent PostgreSQL 18.4 sidecar or an external PostgreSQL
database. It is not used for development. Follow [DEPLOYMENT.md](DEPLOYMENT.md)
and start from [`.env.compose.example`](.env.compose.example); keep the real
production environment file outside the repository.

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

Iteration 3 work must start from the merged Iteration 2 `main`, continue with
strict RED-to-GREEN TDD, add Javadoc during implementation, and update the
matching Markdown evidence record before each milestone commit.
