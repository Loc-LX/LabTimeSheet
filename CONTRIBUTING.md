# Contributing to Lab Timesheet

This is the practical guide: how to set up, how to work, how to get a change
accepted. It covers procedure. For the rules a change must satisfy, read
[`.sdd/constitution.md`](.sdd/constitution.md).

## Before you start

Read three things, in this order. It takes about twenty minutes and saves days.

1. [README.md](README.md) for what the system does and where documentation lives.
2. [`.sdd/constitution.md`](.sdd/constitution.md) for the rules that are not negotiable.
3. The section of [`.sdd/requirements.md`](.sdd/requirements.md) covering the area you will touch.

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

Copy the failing command and its output into an evidence file.

### 3. Evidence

Copy the `_TEMPLATE.md` from the directory matching your test type and keep
every heading:

| Directory | For |
|---|---|
| `docs/tests/unit/` | isolated state, calculation, domain behavior |
| `docs/tests/integration/` | PostgreSQL, Flyway, repository, transaction, module integration |
| `docs/tests/web/` | MockMvc, Thymeleaf, validation, security |
| `docs/tests/e2e/` | cross-module, browser, full journeys |

Required headings: protected behavior, test method, hand-derived expected
result, RED, GREEN, affected suite, external-test boundaries.

Copy commands and output exactly. "Passed locally" is not evidence. Derive the
expected value by hand rather than by reading what the code produces, or the
test proves only that the code agrees with itself.

One file covers one cohesive feature, not one Java class. Name every requirement
ID it protects.

> **Under review.** A proposal would replace this step with rule identifiers
> written into the test class itself, so that the trace survives a rename and can
> be read back mechanically. It is not adopted, because `TST-005` and `TST-007`
> require the Markdown file and `AC-TST-001` checks for it. Changing it is an
> amendment under `GOV-001`, needing an ADR, not an edit to this guide.

### 4. Green

Write the minimum production code that passes. Add Javadoc to new or changed
public and protected members in the same step, not later. Run the focused test,
then the affected module, integration, or web suite.

### 5. Refactor

Improve the code without weakening any assertion. Run the affected suite again.
Record the final commands, results, and commit SHA in the evidence file.

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

Commit the evidence file together with its test and implementation, on the same
branch. A milestone is committable only when evidence is current, narrow and
affected suites are green, and no unexplained warning remains.

### 7. Review

Gitea Actions verifies every push and pull request. A change also needs an
independent human review before merge, and merges are ordinary non-fast-forward
merges, never forced.

## What gets a change rejected

- Production code written before its failing test. It is discarded and redone from the test.
- A weakened or deleted assertion used to make a suite pass.
- A PostgreSQL-specific behavior tested against H2.
- A missing or vague evidence record.
- A new dependency, framework, or datastore without an ADR.
- Cross-feature repository or entity access. `LayerStructureTest` catches this.
- Business SQL inside a service.
- An edit to an already-applied Flyway migration. Add a new migration instead.
- A change to files under `docs/tests/` other than adding your own record. Those are dated records of what was run.

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
to yours: a test that failed first, an evidence record, Javadoc, and an
independent review. An agent saying a task is complete is not evidence; a green
suite and a filled-in evidence record are.

## Getting oriented

New to the codebase? [CLAUDE.md](CLAUDE.md) is the fastest map, including the
one thing that catches everybody: Project Leader is not a global role, it is a
dated term in `project_leadership_terms`.
