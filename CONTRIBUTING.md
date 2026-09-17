# Contributing to Lab Timesheet

How to set up, run, test, and change this project. The rules a change must
satisfy are in [`.sdd/constitution.md`](.sdd/constitution.md); what the system must
do is in [`.sdd/specs/`](.sdd/specs).

## Before you start

Read three things, in this order:

1. [README.md](README.md) for what the system is and where things live.
2. [`.sdd/constitution.md`](.sdd/constitution.md) for the rules that are not negotiable.
3. The spec in [`.sdd/specs/`](.sdd/specs) for the feature you will touch. Rules shared by every feature are in `feature-platform`.

Two ideas explain most of the design. Attendance and Task work are separate
domains and neither proves the other. Historical results must not change when
configuration changes later.

## 1. Set up

### Tools

| Tool | Version | Why |
|---|---|---|
| JDK | 25 | Project language baseline; the build refuses older |
| Docker Desktop | current | PostgreSQL for development and for Testcontainers |
| Node and npm | 24 LTS, npm 11 | Builds Tailwind and icon assets only |
| Git | current | |
| IntelliJ IDEA | optional | Run and debug from the IDE |

Check them with `java -version`, `docker version`, `node --version` and
`npm --version`. If Maven picks up another JDK, point `JAVA_HOME` at JDK 25
before any `./mvnw` command.

### Environment file and assets

```bash
cp .env.example .env
openssl rand -base64 32      # paste the output into LAB_SECURITY_MASTER_KEY
npm ci
npm run build
```

Also replace the database password placeholder in `.env`. Never commit `.env` or
share a real key in chat, screenshots, or documentation. Product SMTP and
HolidayAPI secrets are entered in the Admin console and stored encrypted, not in
environment variables.

### Development containers

The root `Dockerfile` and `compose.yaml` are for production only. In development,
Java runs from Maven or the IDE while PostgreSQL and Mailpit run as containers.

```bash
docker volume create labtimesheet-postgres-data

docker run -d \
  --name labtimesheet-postgres \
  --restart unless-stopped \
  -e POSTGRES_DB=labtimesheet \
  -e POSTGRES_USER=labtimesheet \
  -e POSTGRES_PASSWORD=replace-with-same-password-as-env \
  -p 127.0.0.1:55432:5432 \
  -v labtimesheet-postgres-data:/var/lib/postgresql \
  postgres:18.4

docker run -d \
  --name labtimesheet-mailpit \
  --restart unless-stopped \
  -p 127.0.0.1:1025:1025 \
  -p 127.0.0.1:8025:8025 \
  axllent/mailpit:v1.27.4
```

Use the same password for `POSTGRES_PASSWORD` and `LAB_DB_PASSWORD`. PostgreSQL
keeps the first password in the volume, so changing `.env` later does not change
it. Stopping a container keeps the volume; remove the volume only to discard your
local data. `docker logs`, `docker stop` and `docker start` with the container
names above cover everyday use.

### Run the application

```bash
./mvnw spring-boot:run
```

The `dev` profile imports the root `.env` automatically; shell variables override
it, and `LAB_DEV_ENV_FILE` points elsewhere if you run from another directory.

- `http://localhost:8080` redirects to `/bootstrap` on an empty database, where you create the first Admin.
- `http://localhost:8080/login` is the login page.
- `http://localhost:8025` is the Mailpit inbox.

At first setup, configure SMTP in the Admin console with host `localhost`, port
`1025`, security `NONE`, no username or password, a local from-address such as
`labtimesheet@example.test`, and from-name `Lab Timesheet`. Test the draft before
activating it.

Date-sensitive browser journeys may start the application on the `e2e` profile,
which uses an advancing Vietnam-zone clock from a given instant. It is grouped
with `dev`, absent by default, and rejected together with `prod`:

```bash
LAB_E2E_START_INSTANT=2026-08-22T02:00:00Z \
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=e2e
```

### IntelliJ IDEA

Open the repository root, let IntelliJ import Maven, and select a Java 25 SDK
under **File > Project Structure > Project**. Create a **Spring Boot** run
configuration named `Lab Timesheet (dev)` with main class
`com.lab.labtimesheet.LabtimesheetApplication`, JRE 25, active profile `dev`, and
the repository root as working directory. Leave environment variables empty so
`application-dev.yaml` imports `.env`. Never store real secrets in a shared run
configuration.

## 2. Tests

Tests start their own PostgreSQL 18.4 containers and never use the development
database, Mailpit, or `.env`. Docker must be running.

```bash
./mvnw test          # full backend suite
npm run test:ui      # UI and documentation contract tests, including the spec structure
```

**On Windows** no timezone flag is needed. The JVM reports Vietnam as the legacy
alias `Asia/Saigon`, which PostgreSQL refuses. `LabtimesheetApplication.main`
canonicalizes it for the application, and `LegacyTimeZoneTestListener`, registered
in `src/test/resources/META-INF/spring.factories`, does the same when a Spring Boot
test context starts (`D19`).

The full suite keeps several PostgreSQL containers alive at once. On a machine with
16 GB of memory, close browsers and other heavy programs before running it.

Docker also needs free space on the drive holding its storage; a full drive makes
every Testcontainers start fail with `Could not create/start container`. The
background is in
[`.sdd/rfcs/ADR-003-verification-authority.md`](.sdd/rfcs/ADR-003-verification-authority.md).

### Kinds of test

| Kind | Checks | Example |
|---|---|---|
| Unit | One rule or calculation, no application context | `./mvnw -Dtest=TaskDomainRulesTest test` |
| Integration | Spring services, Flyway, JPA, transactions, PostgreSQL constraints | `./mvnw -Dtest=AttendancePersistenceIntegrationTest test` |
| Web | Spring MVC security, validation, Thymeleaf pages, redirects | `./mvnw -Dtest=TaskControllerTest test` |
| Structure | Package boundaries between features | `./mvnw -Dtest=LayerStructureTest test` |
| End-to-end | The running application in desktop Chromium | see below |

Other useful commands:

```bash
./mvnw '-Dtest=TaskControllerTest#validCreateFormUsesAuthenticatedIdentityAndRedirectsToCreatedTask' test
./mvnw -Dtest='*Attendance*Test' test
./mvnw -DskipTests -Ddoclint=all javadoc:javadoc
git diff --check
```

Maven reports are written to `target/surefire-reports/`.

### End-to-end

Install the lockfile dependencies and Chromium:

```bash
npm ci
npx playwright install chromium
```

On Linux or in CI, install the browser's OS dependencies as well with
`npx playwright install --with-deps chromium`.

Start PostgreSQL, Mailpit and the application as above, then run:

```bash
npm run test:e2e
npm run test:e2e:headed
npm run test:e2e:ui
npm run test:e2e:smoke
npx playwright test src/test/e2e/report-journeys.spec.mjs --project=chromium
npx playwright show-report
```

Run the journeys against a disposable database, never the development one: the
critical journey bootstraps the first administrator, so it needs an empty schema. A
throwaway container on another port works, with the application pointed at it through
`LAB_DB_URL`, `LAB_DB_USERNAME` and `LAB_DB_PASSWORD`:

```bash
docker run -d --name labtimesheet-e2e-postgres -e POSTGRES_DB=labtimesheet \
  -e POSTGRES_USER=labtimesheet -e POSTGRES_PASSWORD=e2e-disposable \
  -p 127.0.0.1:55433:5432 --tmpfs /var/lib/postgresql postgres:18.4
```

The correction journey seeds its own precondition, a previous-workday attendance row
with no checkout, from `src/test/e2e/fixtures/missed-checkout.sql`. Set
`E2E_DB_CONTAINER=labtimesheet-e2e-postgres` so it can, or it is skipped. Because a
correction opens after the checkout cutoff and closes at the next day's scheduled end,
run it with `E2E_BUSINESS_DATE` on a Tuesday to Friday and the application's `e2e` clock
before that day's scheduled end, for example
`LAB_E2E_START_INSTANT=<business date>T01:00:00Z` (08:00 in Vietnam).

Set `PLAYWRIGHT_BASE_URL` only when the application is not at
`http://127.0.0.1:8080`. The credential-gated report journey reads `E2E_EMAIL` and
`E2E_PASSWORD` from the shell and never stores a password in the repository.
Project/Task XLSX and PDF downloads need complete inclusive date ranges of at
most 366 days. Traces and screenshots of failures go to `test-results/playwright/`
and the HTML report to `playwright-report/`. No browser extension
is required. Real browser journeys belong under `src/test/e2e`, not in web tests.

### What CI runs

Gitea runs the frontend tests and build, the complete Maven and PostgreSQL suite,
Javadoc, the generated-asset check, and the whitespace check on every pull request
and push. The container workflow runs only on manual dispatch or a push to `main`
and repeats that verification first. CI confirms a change; it does not replace
running the focused and affected tests locally.

### Good practice

- Test behavior and stored results, not private methods.
- Test PostgreSQL behavior against PostgreSQL, never H2.
- Test denied actions as well as allowed ones, including guessed IDs and wrong roles.
- Include boundary values for dates, times, grace periods, passwords, and status transitions.
- Use the injectable `Clock`, never the real current time.
- Keep tests independent of each other and of run order.
- Fake only external services such as SMTP and HolidayAPI.
- Never put real passwords, keys, activation links, or reset links in tests or their output.

## 3. The workflow

Test-driven development is required. A change that arrives without a test that
failed first is rejected and redone.

### Branch

```bash
git checkout main && git pull
git checkout -b work/fix/<area>/<what>
```

Start from the latest verified `main`. `<area>` is a module of `ARC-005` (`attendance`,
`calendar`, `identity`, `internship`, `notification`, `project`, `reporting`), `platform`,
`architecture` for a change that spans modules, or `docs` for documentation only
(`OPS-019`). A branch name is invalid when it begins with an existing branch name followed by `/`, or when an existing branch name begins with it followed by `/`: `work/<area>/fix/<what>` is invalid where a persistent `work/<area>` exists, and where a branch named `work/fix/<area>` exists the change uses `work/fix/<area>-<what>` instead. Git refuses such a name.

### Red

Pick a numbered requirement and its acceptance scenario. Write the smallest test
that would catch the real break, run it, and confirm it fails for the reason you
intended. A failure caused by a stopped Docker, a wrong class name, or broken
setup is not a valid RED.

### Trace

Name the requirements the test protects, in the test itself:

```java
/**
 * Protects {@code ATT-009}, {@code ATT-010}.
 *
 * <p>Under default policy, 09:00:00 is on time and 09:00:00.001 is late;
 * checkout through 16:00:00 succeeds and the first later instant is rejected.
 */
```

Record the identifiers, the observable production break, and the expected value.
Derive that value by hand from the spec before looking at what the code returns.
Report the RED and GREEN command output from the run itself; nothing is written
to a separate evidence file. The reasons are in
[`.sdd/rfcs/ADR-004-test-evidence-moves-into-the-test.md`](.sdd/rfcs/ADR-004-test-evidence-moves-into-the-test.md).

### Green, refactor, commit, review

Write the minimum production code that passes, with Javadoc on new or changed
public and protected members in the same step. Run the focused test, then the
affected suite. Refactor without weakening any assertion and run the suite again.

Commit with Conventional Commits (`feat`, `fix`, `docs`, `test`, `refactor`,
`perf`, `chore`), explaining why in the body and naming the requirement IDs.
Commit the test and its implementation together. Every change gets an
independent review and is merged with an ordinary non-force merge only when
separately authorized.

## 4. What gets a change rejected

- Production code written before its failing test.
- A weakened or deleted assertion used to make a suite pass.
- A PostgreSQL-specific behavior tested against H2.
- An expected value read off the implementation rather than derived from the spec.
- A new dependency, framework, or datastore without an ADR.
- Cross-feature repository or entity access; `LayerStructureTest` catches it.
- Business SQL inside a service.
- An edit to an already-applied Flyway migration. Add a new one instead.
- A test that names no requirement, or names one it does not exercise.

## 5. Changing a rule or a requirement

If the code and the spec disagree, that is a decision for a person, not a silent
edit in either direction.

Record the decision and its evidence in [`.sdd/decisions.md`](.sdd/decisions.md).
Change the rule in the spec that holds it and add an entry to that spec's
`CHANGELOG.md`. Write an ADR under [`.sdd/rfcs/`](.sdd/rfcs) only when the change
sets or moves an architectural boundary. `D12` in the decision record is an
example of a business rule change;
[`ADR-005`](.sdd/rfcs/ADR-005-one-authorization-policy.md) is one of an
architectural decision.

## 6. Working with AI agents

Agents follow [AGENTS.md](AGENTS.md) and read [CLAUDE.md](CLAUDE.md) for context.
The same standards apply to their output: a test that failed first, a rule trace,
Javadoc, and an independent review. An agent saying a task is done is not
evidence; a green suite is.

## 7. Common problems

| Symptom | Check |
|---|---|
| The application cannot connect to PostgreSQL | `docker ps` and `docker logs labtimesheet-postgres`; `.env` must use port `55432`, database and user `labtimesheet`, and the password the volume was created with |
| Port 8080, 55432, 1025 or 8025 is in use | Stop the other program, or change `.env` and the Docker port mapping together |
| Mail does not reach Mailpit | Mailpit is running, and the active SMTP configuration is `localhost`, `1025`, `NONE`; use the Admin SMTP test |
| Spring reports an unresolved `LAB_*` placeholder | The working directory is the repository root and `.env` exists there, or `LAB_DEV_ENV_FILE` points to it |
| Styles or icons are missing | `npm ci` and `npm run build` |
| The wrong Java version is used | `java -version` and `./mvnw -version` both report 25; in IntelliJ check both Project SDK and the run configuration's JRE |
| Testcontainers cannot find Docker | Docker Desktop is running and `docker version` answers; the Gitea runner sets `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` for the same reason |
| Most integration tests fail with `invalid value for parameter "TimeZone"` | `src/test/resources/META-INF/spring.factories` still registers `LegacyTimeZoneTestListener`; it canonicalizes only `Asia/Saigon` |
| The suite stops with `The forked VM terminated without properly saying goodbye` and an `hs_err_pid*.log` reporting insufficient memory | The machine ran out of memory, not a test; close browsers and other heavy programs and run it again |
| A test passes alone but fails in the full suite | Shared state, fixed ports, run-order assumptions, or data the test did not create; do not hide it with retries |
| Build output looks stale | `./mvnw clean test`, only after confirming the normal command used stale output |
