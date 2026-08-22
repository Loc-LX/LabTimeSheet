# Development Guide

This guide explains how to prepare and run Lab Timesheet on a developer
computer. The application runs from Java. PostgreSQL and Mailpit run in Docker
containers.

The root Dockerfile and Compose file are production-only. They are not part of
the development loop. Development still runs Java from the IDE or Maven while
PostgreSQL and Mailpit run as separate local containers.

## 1. Install the required tools

Install:

- Java 25
- Docker Desktop or OrbStack
- Node.js 24 and npm 11
- Git
- IntelliJ IDEA, if you want to run the application from the IDE

Confirm the tools are available:

```bash
java -version
docker version
node --version
npm --version
```

On macOS, select an installed Java 25 JDK with:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@24/bin:$PATH"
```

Java 25 is the supported project baseline. A newer local JDK can compile the
project but is not the shared team baseline.

## 2. Prepare the project

Clone the repository, open a terminal in its root directory, then create your
local environment file:

```bash
cp .env.example .env
```

Edit `.env` and replace the database password placeholder. Generate the
encryption master key with:

```bash
openssl rand -base64 32
```

Copy that output into `LAB_SECURITY_MASTER_KEY`. Never commit `.env` or share a
real key in chat, screenshots, test evidence, or documentation.

Install and build the local frontend assets:

```bash
npm ci
npm run build
```

### Use an isolated repair branch

For a targeted repair, start a clean worktree from the taskmaster-verified
current `main` on `work/fix/<feature>/<what-fix>`. Keep it separate from the
five persistent `work/<feature>` branches. Do not use
`work/<feature>/fix/<what-fix>` because the persistent `work/<feature>` ref
already occupies that Git ref prefix.

Every targeted repair starts from the taskmaster-verified latest `main`, uses TDD RED → GREEN, adds Javadoc during implementation, records companion evidence, undergoes independent review, and uses a normal, non-force merge only when separately authorized.

## 3. Start the development containers

### PostgreSQL 18.4

Create a named volume once. The volume keeps your development data when the
container is stopped or replaced.

```bash
docker volume create labtimesheet-postgres-data
```

Start PostgreSQL:

```bash
docker run -d \
  --name labtimesheet-postgres \
  --restart unless-stopped \
  -e POSTGRES_DB=labtimesheet \
  -e POSTGRES_USER=labtimesheet \
  -e POSTGRES_PASSWORD=replace-with-same-password-as-env \
  -p 127.0.0.1:55432:5432 \
  -v labtimesheet-postgres-data:/var/lib/postgresql \
  postgres:18.4
```

Use the same password for `POSTGRES_PASSWORD` and `LAB_DB_PASSWORD` in `.env`.
PostgreSQL stores the original password in the volume. Changing only `.env`
later will not change the database password.

### Mailpit

Mailpit receives development email without sending it to real people.

```bash
docker run -d \
  --name labtimesheet-mailpit \
  --restart unless-stopped \
  -p 127.0.0.1:1025:1025 \
  -p 127.0.0.1:8025:8025 \
  axllent/mailpit:v1.27.4
```

Confirm both containers are running:

```bash
docker ps
```

Useful container commands:

```bash
docker logs labtimesheet-postgres
docker logs labtimesheet-mailpit
docker stop labtimesheet-postgres labtimesheet-mailpit
docker start labtimesheet-postgres labtimesheet-mailpit
```

Stopping the containers keeps the database volume. Do not remove the volume
unless you intentionally want to discard your local development data.

## 4. Run from a terminal

The `dev` profile imports the ignored root `.env` file automatically. From the
repository root, run:

```bash
./mvnw spring-boot:run
```

Shell environment variables still override values from `.env`, which is useful
for a one-off local override. If you run from another working directory, set
`LAB_DEV_ENV_FILE` to the absolute path of your `.env` file.

Open:

- First-Admin setup: open `http://localhost:8080` and follow the automatic
  redirect to `/bootstrap`.
- Login: `http://localhost:8080/login`
- Mailpit inbox: `http://localhost:8025`

At first setup, configure SMTP through the Admin console with:

| Setting | Development value |
|---|---|
| Host | `localhost` |
| Port | `1025` |
| Security | `NONE` |
| Username | leave empty |
| Password | leave empty |
| From address | a local address such as `labtimesheet@example.test` |
| From name | `Lab Timesheet` |

Test the draft before activating it. Mailpit's web inbox shows activation and
other development messages.

### Deterministic local E2E clock

Date-sensitive browser journeys may opt into the `e2e` profile, which uses an
immutable Vietnam-zone clock. The profile is absent by default, has no runtime
mutation or endpoint, and is rejected when combined with `prod`:

```bash
LAB_E2E_FIXED_INSTANT=2026-08-22T02:00:00Z \
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=e2e
```

Use this only for local E2E startup. Keep the normal `dev` profile for ordinary
development and never activate `e2e` in production.

Stop the application with `Control+C`.

## 5. Run with IntelliJ IDEA

### Open the project

1. Open IntelliJ IDEA.
2. Choose **Open** and select the repository root.
3. Allow IntelliJ to import the Maven project.
4. Open **File > Project Structure > Project**.
5. Select a Java 25 SDK. Add the JDK installation if it is not listed.

### Create the run configuration

1. Open **Run > Edit Configurations**.
2. Select **+**, then **Spring Boot**.
3. Use the name `Lab Timesheet (dev)`.
4. Set **Main class** to
   `com.lab.labtimesheet.LabtimesheetApplication`.
5. Set **Use classpath of module** to the main `labtimesheet` module.
6. Set **JRE** to Java 25.
7. Set **Active profiles** to `dev`.
8. Set **Working directory** to the repository root.
9. Leave **Environment variables** empty. With the repository root as the
   working directory, `application-dev.yaml` imports the ignored `.env` file.
10. Apply the configuration and run it.

If company policy requires IntelliJ to inject the values instead, select the
local `.env` in the **Environment variables** field. Environment variables take
precedence over the imported file. Do not store real secrets in a shared or
committed run configuration.

Run `npm ci` and `npm run build` in IntelliJ's terminal before the first launch
and after changing Tailwind or icon sources.

## 6. Common problems

### The application cannot connect to PostgreSQL

Run:

```bash
docker ps
docker logs labtimesheet-postgres
```

Check that `.env` uses port `55432`, database `labtimesheet`, user
`labtimesheet`, and the password used when the PostgreSQL volume was first
created.

### Port 8080, 55432, 1025, or 8025 is already in use

Stop the other program or container using that port. Keep `.env` and the Docker
port mapping consistent if you intentionally select another development port.

### Mail does not appear in Mailpit

Check that Mailpit is running and that the active Admin SMTP configuration uses
host `localhost`, port `1025`, and security `NONE`. A container health warning
does not by itself prove that SMTP is unavailable; use the Admin SMTP test.

### IntelliJ uses the wrong Java version

Check both **Project SDK** and the run configuration's **JRE**. They should both
be Java 25.

### Spring reports an unresolved `LAB_*` placeholder

Confirm the run configuration uses the repository root as its working
directory and that `.env` exists there. If the working directory must differ,
set `LAB_DEV_ENV_FILE` to the absolute `.env` path in the run configuration's
environment variables.

### Styles or icons are missing

Run:

```bash
npm ci
npm run build
```

For test setup, commands, TDD, and test evidence rules, read
[TESTING.md](TESTING.md).

For production image and Compose operation, read [DEPLOYMENT.md](DEPLOYMENT.md).
Do not use the production Compose file as a replacement for this development
setup.
