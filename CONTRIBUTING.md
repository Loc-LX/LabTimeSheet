# Contributing to Lab Timesheet

This is the practical guide: how to set up, how to work, how to get a change
accepted. It covers procedure. For the rules a change must satisfy, read
[`.sdd/constitution.md`](.sdd/constitution.md).

## Before you start

Read three things, in this order. It takes about twenty minutes and saves days.

1. [README.md](README.md) for what the system does and where documentation lives.
2. [`.sdd/constitution.md`](.sdd/constitution.md) for the rules that are not negotiable.
3. The spec under [`.sdd/specs/`](.sdd/specs) for the feature you will touch; [`.sdd/requirements.md`](.sdd/requirements.md) says which one.

Two ideas are worth internalizing early. Attendance and Task work are separate
domains and neither proves the other. Historical results must not change when
configuration changes later. Most surprising design choices follow from those.

## Prerequisites

| Tool | Version | Why |
|---|---|---|
| JDK | 25 | Project language baseline; the build refuses older |
| Node | 24 LTS | Builds Tailwind and static assets only |
| Docker | any current | PostgreSQL Testcontainers; tests cannot run without it |
| Git | any current | |

[DEVELOPMENT.md](DEVELOPMENT.md) has the full walkthrough including container
commands and an IntelliJ IDEA run configuration.

## First-time setup

```bash
cp .env.example .env
# Edit .env. Generate LAB_SECURITY_MASTER_KEY with: openssl rand -base64 32

export JAVA_HOME=/path/to/jdk-25
npm ci
npm run build
./mvnw spring-boot:run
```

Open `http://localhost:8080`. An empty database redirects to `/bootstrap` where
you create the first Admin. You then either configure and test SMTP or
acknowledge all five deferral warnings.

Never put a real credential in a tracked file. `.env` is git-ignored and stays
that way. Product SMTP and HolidayAPI secrets are entered in the Admin console
and stored encrypted, not in environment variables.

## Running tests

```bash
export JAVA_HOME=/path/to/jdk-25
./mvnw test          # full suite, needs Docker
npm run test:ui      # UI contract tests
```

To run one class:

```bash
./mvnw test -Dtest=LoginThrottleTest
```

If tests fail immediately with a container error, Docker is not running or
`DOCKER_HOST` is wrong. [TESTING.md](TESTING.md) lists the common failures.

## The workflow

Test-driven development is required here, not encouraged. A change that arrives
without a test that failed first is rejected and reimplemented.

### 1. Branch

```bash
git checkout main && git pull
git checkout -b work/fix/<feature>/<what-fix>
```

`<feature>` is one of `platform`, `projects`, `tasks`, `attendance`,
`reports-ui`. Do not write `work/<feature>/fix/<what-fix>`; the persistent
`work/<feature>` refs already occupy that prefix and Git will refuse.

Stay inside your feature's ownership area. Touching another area needs
coordination first.

### 2. Red

Pick a numbered requirement and its acceptance scenario. Write the smallest test
that would catch the real break. Run it and confirm it fails for the reason you
intended, not because of a typo or a missing import.

Keep the exact command and its output; the run records them, not a file you write afterwards.

### 3. Trace

Name the numbered requirements the test protects, in the test itself:

```java
/**
 * Protects {@code ATT-009}, {@code ATT-010}.
 *
 * <p>Under default policy, 09:00:00 is on time and 09:00:00.001 is late;
 * checkout through 16:00:00 succeeds and the first later instant is rejected.
 */
```

Record three things: the identifiers, the observable production break the test
catches, and the expected value. Derive that value by hand from
[the specification](.sdd/specs) before looking at what the code
produces. A test written from the implementation proves only that the code agrees
with itself.

The trace lives in the source so that it moves under a rename and can be read
back mechanically, which is what makes the report of untested rules generated
rather than maintained.

This replaced a Markdown evidence file per feature. The measurements behind the
change are in
[`.sdd/rfcs/ADR-004-test-evidence-moves-into-the-test.md`](.sdd/rfcs/ADR-004-test-evidence-moves-into-the-test.md).
Eighty-eight existing classes predate the rule and carry no identifiers yet;
until they are back-filled their mapping lives in
[`.sdd/reviews/traceability.md`](.sdd/reviews/traceability.md).

### 4. Green

Write the minimum production code that passes. Add Javadoc to new or changed
public and protected members in the same step, not later. Run the focused test,
then the affected module, integration, or web suite.

### 5. Refactor

Improve the code without weakening any assertion. Run the affected suite again.

### 6. Commit

Conventional Commits, matching what the repository already uses:

```
type(scope): imperative subject under 72 characters

Body explaining why, and what a reviewer should check. Reference the
requirement IDs the change satisfies.
```

Types in use: `feat`, `fix`, `docs`, `test`, `refactor`, `perf`, `chore`.
Scopes in use: `platform`, `projects`, `tasks`, `attendance`, `reporting`,
`web`, `seed`.

Commit the test and its implementation together on the same branch. A milestone
is committable only when the narrow and affected suites are green and no
unexplained warning remains.

### 7. Review

Gitea Actions verifies every push and pull request. A change also needs an
independent human review before merge, and merges are ordinary non-fast-forward
merges, never forced.

## What gets a change rejected

- Production code written before its failing test. It is discarded and redone from the test.
- A weakened or deleted assertion used to make a suite pass.
- A PostgreSQL-specific behavior tested against H2.
- A test whose expected value was read off the implementation rather than derived from the specification.
- A new dependency, framework, or datastore without an ADR.
- Cross-feature repository or entity access. `LayerStructureTest` catches this.
- Business SQL inside a service.
- An edit to an already-applied Flyway migration. Add a new migration instead.
- A test that names no requirement identifier, or names one it does not actually exercise.

## Changing a rule or a requirement

If the code and the requirements disagree, that is a decision for a person, not
a silent edit in either direction.

Write an ADR under [`.sdd/rfcs/`](.sdd/rfcs) stating the decision, the reasoning,
and the consequences. Update the requirements specification. Then update
[`.sdd/constitution.md`](.sdd/constitution.md) if the rule index changed. Supersede old
wording with a dated note rather than rewriting it, so the document keeps its
own history.

[`.sdd/rfcs/ADR-002-attendance-report-scope.md`](.sdd/rfcs/ADR-002-attendance-report-scope.md)
is a worked example.

## Working with AI agents

Agents operating in this repository follow [AGENTS.md](AGENTS.md) and read
[CLAUDE.md](CLAUDE.md) for context. The same standards apply to their output as
to yours: a test that failed first, a rule trace, Javadoc, and an independent
review. An agent saying a task is complete is not evidence; a green suite is.

## Getting oriented

New to the codebase? [CLAUDE.md](CLAUDE.md) is the fastest map, including the
one thing that catches everybody: Project Leader is not a global role, it is a
dated term in `project_leadership_terms`.
