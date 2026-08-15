# Production Container Deployment

These files deploy Lab Timesheet in production. They are not the development workflow; continue using [DEVELOPMENT.md](DEVELOPMENT.md) for IDE work.

## 1. Prepare the host

Install Docker Engine and Docker Compose v2.20 or newer. Put an HTTPS reverse proxy in front of the application. By default, Compose binds the application only to `127.0.0.1:8080`.

Copy [`.env.compose.example`](.env.compose.example) to a protected path outside the repository:

```bash
sudo install -d -m 0700 /etc/labtimesheet
sudo install -m 0600 .env.compose.example /etc/labtimesheet/compose.env
sudo editor /etc/labtimesheet/compose.env
```

Generate `LAB_SECURITY_MASTER_KEY` with `openssl rand -base64 32`. Use an immutable `sha-<full-commit>` application image tag. Never place Admin-managed SMTP or HolidayAPI credentials in this file.

## 2. Choose the database topology

### Bundled PostgreSQL 18.4

Keep the example JDBC host `postgres`, then run:

```bash
docker compose --env-file /etc/labtimesheet/compose.env --profile bundled-db up -d
docker compose --env-file /etc/labtimesheet/compose.env ps
```

The application waits for PostgreSQL health and stores database files in the `postgres_data` named volume.

### External PostgreSQL

Set `LAB_DB_URL`, `LAB_DB_USERNAME`, and `LAB_DB_PASSWORD` for the external database. Do not enable the `bundled-db` profile:

```bash
docker compose --env-file /etc/labtimesheet/compose.env up -d app
docker compose --env-file /etc/labtimesheet/compose.env ps
```

The same application image is used in both modes.

## 3. Health and operation

- Liveness: `GET /actuator/health/liveness`
- Readiness: `GET /actuator/health/readiness` (includes PostgreSQL)
- Logs: `docker compose --env-file /etc/labtimesheet/compose.env logs -f app`

The container runs as UID/GID `10001`, with a read-only root filesystem, no Linux capabilities, and only `/tmp` writable. TLS termination is intentionally outside this Compose example.

Back up PostgreSQL with database-aware tooling such as `pg_dump`. The named volume survives container replacement, but it is not a backup. Test restore procedures before upgrades.

To update or roll back, change `LAB_IMAGE` to the required immutable SHA tag and run `docker compose ... up -d` again. Keep the previous SHA recorded until the new image is healthy.

## 4. Gitea Actions setup

Configure these repository settings:

| Kind | Name | Value |
|---|---|---|
| Variable | `ARM64_RUNNER_AVAILABLE` | `true` only while a trusted `ubuntu-latest-arm` runner is registered and online; otherwise omit it or set `false` |
| Secret | `REGISTRY_TOKEN` | Token for the triggering Gitea account with package read/write access |

The workflow publishes `git.sechmachine.io.vn/sechmachine/labtimesheet` and authenticates as the triggering Gitea account. `verify.yml` runs for every pull request and push. `container.yml` runs only when manually dispatched or when `main` is pushed, and it repeats verification before either architecture build. Manual runs build without publishing. A push to `main` publishes immutable `sha-<commit>` and convenience `main` tags.

When ARM64 is disabled, those canonical tags remain valid AMD64 images and the workflow succeeds. When it is enabled, the native ARM runner publishes an architecture tag and the final job replaces the canonical tags with a combined AMD64/ARM64 manifest. Gitea cannot discover an unavailable runner from inside an unscheduled job, so the repository variable is the deliberate availability gate.

The workflows stop at verification and image publication. They do not contain SSH deployment or receive host deployment secrets.
