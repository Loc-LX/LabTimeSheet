# Lab Timesheet

Server-rendered Spring Boot application for managing laboratory internships,
Projects, Tasks, and attendance. Iteration 1 is complete and was verified on
15 August 2026.

## Iteration 1: working now

### Accounts and onboarding

- Atomic first-Admin bootstrap that remains closed after initialization and restart.
- Optional SMTP onboarding with five distinct deferral warnings and a persistent restricted-state notice.
- Admin SMTP draft, connection test, and activation against Mailpit or another configured server.
- Admin creation of Admin, Mentor, and Intern accounts through single-use email activation.
- Password setup, form login, logout, global roles, and role-protected Admin routes.

### Projects

- Owning Mentors create `PLANNED` Projects with an eligible initial Leader.
- Mentor-controlled direct membership with historical membership and leadership intervals.
- Leader reassignment and guarded `PLANNED` to `ACTIVE` activation.
- Role-correct Project lists, details, member views, and guessed-ID concealment.

### Tasks

- One current assignee per Task.
- Active members create self-assigned Tasks; the current Leader may assign another active member.
- Due dates are checked against Project dates and current global days off.
- The fixed `TODO`, `IN_PROGRESS`, `BLOCKED`, and `DONE` transition graph is enforced.
- Authorized comments, Task lists/details, assignee display, status counts, and completion progress.

### Attendance and calendar

- Effective attendance-policy resolution with Vietnam business time, configured workdays, and separate 30-minute check-in and checkout grace defaults.
- Admin-managed manual global calendar days off.
- Server-time check-in and checkout with duplicate, off-day, leave-day, lifecycle, and cutoff rejection.
- `MISSING_CHECKOUT` classification without a second early-departure violation.
- Intern history plus authorized Mentor/Admin attendance inspection using the historical applied policy.

### Desktop UI

- Shared Thymeleaf/Tailwind shell with role-aware navigation and dashboards.
- Light, dark, and system themes applied before paint.
- Collapsible desktop sidebar, accessible forms/errors, tables, badges, empty states, and local Lucide icons.
- Bootstrap, authentication, SMTP, Project, Task, calendar, and attendance pages integrated into the same shell.

## Deliberately not implemented yet

The baseline schema includes later-workflow tables; table presence does not mean
the corresponding feature is complete.

- Iteration 2: Project invitations and approved membership exits, broader Project lifecycle transfers, Task edit/delete/reassignment and work logs, leave, missed-checkout corrections, notifications, schedulers, and complete metrics.
- Iteration 3: HTML/XLSX/PDF report parity, Chart.js trends, production security hardening, application containers, Compose, Gitea CI publication, and deployment scaffolding.
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
set -a
source .env
set +a

export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"

npm ci
npm run build
./mvnw spring-boot:run
```

Development defaults to the application on port `8080`, PostgreSQL on `55432`,
and Mailpit SMTP on `1025`. The exact Spring settings are in
[`application-dev.properties`](src/main/resources/application-dev.properties).

On first launch, open `http://localhost:8080/bootstrap`, create the first Admin,
then configure and test SMTP or complete all five explicit deferral warnings.

## Verification status

The final Iteration 1 integration gate recorded:

- 197 Maven tests passed with PostgreSQL 18.4 Testcontainers.
- Flyway replay produced exactly 23 application tables and 56 foreign keys.
- Java compilation and full Javadoc/doclint passed.
- Two consecutive Node/Tailwind/Lucide builds produced identical assets.
- A real Java process completed bootstrap, login, SMTP deferral, persistent warning recovery, and a separate Mailpit draft/test/activate flow with health `UP`.
- Independent reviews of all five work branches closed with no remaining Critical, Important, or Minor findings.

Tests require Docker for PostgreSQL Testcontainers:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export DOCKER_HOST=unix:///Users/your-name/.orbstack/run/docker.sock # only when using OrbStack
./mvnw test
```

See [TESTING.md](TESTING.md) for setup, test commands, the required TDD cycle,
evidence records, best practices, and common fixes. Every behavior test has a
companion record under [`docs/tests`](docs/tests/README.md).

## Branch ownership

| Branch | Primary area |
|---|---|
| `work/platform` | Application baseline, schema, accounts, security, integrations |
| `work/projects` | Projects, membership, leadership, lifecycle |
| `work/tasks` | Tasks, comments, status, progress |
| `work/attendance` | Policy, calendar, attendance workflows |
| `work/reports-ui` | Shared UI, dashboards, reporting presentation |

Iteration 2 work must start from the merged Iteration 1 `main`, continue with
strict RED-to-GREEN TDD, add Javadoc during implementation, and update the
matching Markdown evidence record before each milestone commit.
