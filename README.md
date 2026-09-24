# Lab Timesheet

Server-rendered Spring Boot application for a university laboratory internship
programme. It manages accounts and internship lifecycle, attendance with leave
and missed-checkout corrections, Mentor-owned Projects with contextual Intern
leadership, single-assignee Tasks with dated work logs, notifications, and
authorized reports in HTML, XLSX, and PDF.

Two rules shape most of the design. Attendance time and Task work time are
separate domains and neither proves the other (`GOV-004`). Historical business
results must not change when an Admin later edits policy or the calendar
(`GOV-005`).

## Status

Iterations 1 through 4 are built. The specification is approved and ahead of the code: it
records decisions the code does not implement yet.
[`plan.md`](plan.md) is the only progress tracker and lists that work and
everything else still open.

## Where things live

Two places. **`.sdd/`** holds what the system must do and why. **The repository
root** holds how to work on it.

| Question | Read |
|---|---|
| What must always be true | [`.sdd/constitution.md`](.sdd/constitution.md) |
| What the system must do | [Specification map](.sdd/specs/README.md): module `MODULE.md` plus `features/<feature>/SPEC.md`; system-wide rules in `platform/MODULE.md` and its features; each `PLAN.md` and `TASKS.md` beside its feature `SPEC.md` (`D40`) |
| Why a decision was made | [`.sdd/decisions.md`](.sdd/decisions.md); architectural decisions in [`.sdd/rfcs/`](.sdd/rfcs) |
| Product, users, assumptions, and technology stack | [`.sdd/shared_context.md`](.sdd/shared_context.md) |
| UI design tokens | [`.sdd/design-system.md`](.sdd/design-system.md) |
| What is being worked on now | [`plan.md`](plan.md) |
| How to set up, run, test, and contribute | [CONTRIBUTING.md](CONTRIBUTING.md) |
| Rules and context for AI agents | [AGENTS.md](AGENTS.md), [CLAUDE.md](CLAUDE.md) |

## Quick start

```bash
cp .env.example .env         # then set LAB_SECURITY_MASTER_KEY: openssl rand -base64 32
npm ci && npm run build
./mvnw spring-boot:run
```

This expects JDK 25, PostgreSQL on `55432` and Mailpit on `1025`;
[CONTRIBUTING.md](CONTRIBUTING.md) starts both containers and covers tests. Open
`http://localhost:8080`: an empty database redirects to `/bootstrap`, where you
create the first Admin.

## Deploy

The root [`Dockerfile`](Dockerfile) and [`compose.yaml`](compose.yaml) are for
production only. The image is non-root Java 25 and runs against either a bundled
PostgreSQL 18.4 or an external database.

### Prepare the host

Install Docker Engine and Docker Compose v2.20 or newer, and put an HTTPS reverse
proxy in front; Compose binds the application to `127.0.0.1:8080`. Keep the real
environment file outside the repository:

```bash
sudo install -d -m 0700 /etc/labtimesheet
sudo install -m 0600 .env.compose.example /etc/labtimesheet/compose.env
sudo install -m 0644 compose.yaml /etc/labtimesheet/compose.yaml
sudo editor /etc/labtimesheet/compose.env
```

Generate `LAB_SECURITY_MASTER_KEY` with `openssl rand -base64 32` and use an
immutable `sha-<full-commit>` image tag. SMTP and HolidayAPI credentials never go
in this file; they are entered in the Admin console.

### Run

With the bundled database, keep the example JDBC host `postgres`:

```bash
docker compose --env-file /etc/labtimesheet/compose.env -f /etc/labtimesheet/compose.yaml --profile bundled-db up -d
```

With an external database, set `LAB_DB_URL`, `LAB_DB_USERNAME` and
`LAB_DB_PASSWORD`, and start only the application:

```bash
docker compose --env-file /etc/labtimesheet/compose.env -f /etc/labtimesheet/compose.yaml up -d app
```

Liveness is `GET /actuator/health/liveness`; readiness,
`GET /actuator/health/readiness`, includes PostgreSQL. The container runs as
UID/GID `10001` with a read-only root filesystem and no Linux capabilities.

Back up with database-aware tooling such as `pg_dump`; the named volume survives
container replacement but is not a backup. To update or roll back, change
`LAB_IMAGE` to the required SHA tag and run `up -d` again, keeping the previous
tag recorded until the new image is healthy.

### Gitea Actions

`verify.yml` runs on every pull request and push. `container.yml` runs on manual
dispatch or a push to `main`, repeats verification, and publishes only on a push
to `main`: an immutable `sha-<commit>` tag and a `main` convenience tag, AMD64,
plus ARM64 while a trusted ARM runner is declared available.

| Kind | Name | Value |
|---|---|---|
| Variable | `ARM64_RUNNER_AVAILABLE` | `true` only while a trusted `ubuntu-latest-arm` runner is online |
| Secret | `REGISTRY_TOKEN` | Package read/write token for the triggering Gitea account |
| Variable | `DEPLOY_ENABLED` | Exactly `true` before the dormant deploy job may run |
| Secret | `DEPLOY_HOST`, `DEPLOY_USER` | SSH target of the deploy job |
| Secret | `DEPLOY_PRIVATE_KEY` | Dedicated SSH key for the deployment host |
| Secret | `DEPLOY_KNOWN_HOSTS` | Pinned `known_hosts`; strict host-key checking is always on |

The `deploy` job runs only on `main` with `DEPLOY_ENABLED` set and all four
secrets present, after verification and the AMD64 image. It selects the
`sha-<full-commit>-amd64` image, never a mutable tag, and expects the host to hold
`/etc/labtimesheet/compose.env` and `/etc/labtimesheet/compose.yaml`. It records
the previous image, restarts only `app`, waits for readiness, and restores the
previous image if the rollout fails. No database or volume is ever deleted.
