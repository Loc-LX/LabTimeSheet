# Testing Guide

This guide explains how to prepare the test environment, run each type of test,
and follow the project's required test-driven development workflow.

## 1. What you need

Install these tools before running tests:

- Java 25
- Docker Desktop or OrbStack
- Node.js 24 and npm 11
- Git

Confirm the tools are available:

```bash
java -version
docker version
node --version
npm --version
```

On macOS with Homebrew, the project normally uses:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="/opt/homebrew/opt/node@24/bin:$JAVA_HOME/bin:$PATH"
```

If you use OrbStack and Testcontainers cannot find Docker, set:

```bash
export DOCKER_HOST=unix:///Users/your-name/.orbstack/run/docker.sock
```

Replace `your-name` with your macOS account name. Docker Desktop users normally
do not need this setting.

Tests use temporary PostgreSQL 18.4 containers. They do not use the development
database, Mailpit, or the local `.env` file.

## 2. First test run

From the repository root, run:

```bash
./mvnw test
```

The first run may take longer because Docker downloads PostgreSQL and
Testcontainers support images. A successful run ends with `BUILD SUCCESS`.

Frontend assets have a separate check:

```bash
npm ci
npm run build
```

## 3. Test types used by this project

### Unit tests

Unit tests check a small rule or calculation without starting the full
application. Examples include Task status transitions and progress calculations.

Run one class:

```bash
./mvnw -Dtest=TaskDomainRulesTest test
```

### Integration tests

Integration tests check real Spring services, Flyway migrations, JPA mappings,
transactions, and PostgreSQL constraints. Docker must be running.

Run one integration class:

```bash
./mvnw -Dtest=AttendancePersistenceIntegrationTest test
```

### Web tests

Web tests send requests through Spring MVC and check security, validation,
Thymeleaf pages, redirects, and error messages without opening a browser.

```bash
./mvnw -Dtest=TaskControllerTest test
```

### End-to-end checks

End-to-end checks use the running application in a real desktop browser. They
cover complete journeys such as bootstrap, login, SMTP setup, Projects, Tasks,
and attendance.

Current end-to-end checks are guided manual checks:

1. Prepare `.env` by following the main README.
2. Start PostgreSQL 18.4 and Mailpit.
3. Run `./mvnw spring-boot:run`.
4. Follow the scenario written in `docs/tests/e2e/`.
5. Record the browser, viewport, result, and any boundary that was not tested.

Do not record a real browser journey as a web test. Use `docs/tests/e2e/`.

### Structure and configuration checks

Structure tests protect package boundaries and prevent one feature from reading
another feature's repositories or database entities.

```bash
./mvnw -Dtest=LayerStructureTest test
```

Simple configuration or documentation changes use the smallest useful shell
check, followed by the affected Maven suite. Do not create an artificial Java
test only to check that a text file exists.

Run that check from the clean targeted-fix branch named
`work/fix/<feature>/<what-fix>` when repairing one feature. Do not use
`work/<feature>/fix/<what-fix>`: a persistent `work/<feature>` ref already
occupies that Git ref prefix. Record the expected RED and the matching GREEN
shell output in the evidence record.

Every targeted repair starts from the taskmaster-verified latest `main`, uses TDD RED → GREEN, adds Javadoc during implementation, records companion evidence, undergoes independent review, and uses a normal, non-force merge only when separately authorized.

## 4. Useful commands

Run one test method:

```bash
./mvnw '-Dtest=TaskControllerTest#validCreateFormUsesAuthenticatedIdentityAndRedirectsToCreatedTask' test
```

Run tests for one feature by name:

```bash
./mvnw -Dtest='*Attendance*Test' test
```

Run the complete backend suite:

```bash
./mvnw test
```

Check compilation and Javadoc:

```bash
./mvnw -DskipTests compile
./mvnw -DskipTests -Ddoclint=all javadoc:javadoc
```

Check whitespace and patch formatting:

```bash
git diff --check
```

Maven test reports are written to `target/surefire-reports/`.

## 5. Required TDD workflow

TDD means writing the test before writing the production behavior.

1. Choose the requirement and acceptance-scenario IDs.
2. Copy the matching template from `docs/tests/unit`, `integration`, `web`, or `e2e`.
3. Write the smallest test that proves the missing behavior.
4. Run that test and confirm it fails for the expected reason. This is **RED**.
5. Record the exact command and useful failure output in the evidence file.
6. Write the minimum production code and its Javadoc. Do not add unrelated work.
7. Run the same test again. It must pass. This is **GREEN**.
8. Run the affected feature tests, then the full suite when the milestone is complete.
9. Refactor only while the tests stay green.
10. Update the evidence file and commit the complete milestone.

If the first test fails because Docker is stopped, a class name is wrong, or the
test setup is broken, that is not a valid RED. Fix the environment or test first.

## 6. Evidence records

Every behavior test needs one Markdown record in the matching directory:

```text
docs/tests/unit/
docs/tests/integration/
docs/tests/web/
docs/tests/e2e/
```

Keep every heading from `_TEMPLATE.md`. Record:

- requirement and scenario IDs;
- the behavior being protected;
- how the expected result was calculated;
- exact RED and GREEN commands and results;
- the affected-suite result;
- anything the test did not prove.

One record may cover a closely related parameterized scenario set. A written
claim never replaces a test command and result.

## 7. Testing best practices

- Test user-visible behavior and stored results, not private method details.
- Use PostgreSQL 18.4 for persistence tests. Do not replace it with H2.
- Test allowed actions and denied actions, including guessed IDs and wrong roles.
- Include boundary values for dates, times, grace periods, passwords, and status transitions.
- Use the project's injectable `Clock`; do not make tests depend on the real current time.
- Keep each test independent. Do not rely on another test running first.
- Use real Spring and database components at the boundary being tested. Mock only external services such as SMTP or HolidayAPI when appropriate.
- Never put real passwords, API keys, activation links, or reset links in test code or evidence.
- Do not remove assertions, catch errors, or disable security simply to make a test pass.
- Run the focused test first so feedback is fast, then run the broader suite before committing.
- Give tests names that describe the rule and expected result.
- Clean up temporary browser data, application processes, and manually started containers after end-to-end work.

### What CI runs

Gitea runs the frontend tests/build, complete Maven/PostgreSQL suite, Javadoc,
generated-asset check, and whitespace check for every pull request and push.
The separate container workflow runs only when manually dispatched or when
`main` is pushed. It repeats the verification job before building either image.
Manual runs do not publish; only a push to `main` publishes.

Run focused and affected tests locally before pushing. CI is the shared
confirmation, not a substitute for local RED and GREEN evidence.

## 8. Common problems

### Testcontainers cannot find Docker

Start Docker Desktop or OrbStack. Run `docker version`. OrbStack users should
also check the `DOCKER_HOST` command shown in Section 1.

The Gitea Docker runner exposes the daemon through Docker Desktop, so its jobs
set `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`. Keep that override if
the runner stays containerized; otherwise Ryuk may try an unreachable bridge IP.

### The wrong Java version is used

Run `java -version` and `./mvnw -version`. Both should report Java 25. Set
`JAVA_HOME` again if Maven uses another JDK.

### The application cannot start for a manual browser check

Confirm the process working directory is the repository root so
`application-dev.yaml` can import `.env`, PostgreSQL is reachable, and
`LAB_SECURITY_MASTER_KEY` decodes from Base64 to 32 bytes. Automated tests do
not need this local file.

### A test passes alone but fails in the full suite

Check for shared state, fixed ports, assumptions about test order, or data that
was not created by the test itself. Do not hide the failure with retries.

### Build output looks stale

Use this only after confirming the ordinary command is using stale compiled output:

```bash
./mvnw clean test
```
