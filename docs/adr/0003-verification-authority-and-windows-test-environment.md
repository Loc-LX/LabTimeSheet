# ADR 0003 — What counts as a live check, and why the suite does not run on Windows

- **Status:** Accepted
- **Date:** 2026-09-11
- **Requirements touched:** `ARC-003`, `GOV-011`, `TST-004`, `TST-008`
- **Supersedes:** nothing. Records a distinction that had never been written down.

## Context

Two separate problems surfaced while restructuring documentation, and they share a
root cause: text that records a past verification was treated as if it enforced
something today.

### Archival records were read as live constraints

Structural decisions were held back because commands appeared inside
`docs/tests/` evidence records, for example:

```text
rg -n 'CONTAINER_IMAGE|REGISTRY_USERNAME' .gitea/workflows/container.yml DEPLOYMENT.md
rg -n -F 'work/fix/<feature>/<what-fix>' AGENTS.md README.md DEVELOPMENT.md TESTING.md
```

Both sit inside evidence sections. The first is under `## Affected suite`, the
second under `## RED`, where the command is expected to fail. Neither is executed
by anything. `.gitea/workflows/verify.yml` runs `npm run test:ui`,
`npm run build`, a generated-asset diff, `./mvnw -B test`, Javadoc, and
`git diff --check`. That is the whole list.

`scripts/verify-fix-branch-workflow.cjs` is a real committed script, but nothing
invokes it: no Java test references it, it is absent from `package.json`, and it
is absent from CI. It also cannot run, because it reads two
`docs/superpowers/` files that have never existed in this repository's history.

The script asserts that four documents contain an approved sentence verbatim and
mention the branch form nowhere else. Because the required sentence contains no
newline, the documents cannot wrap it, which is why `README.md`,
`DEVELOPMENT.md`, and `TESTING.md` each carry a 258-character line. A check that
freezes prose makes documentation worse the longer it stands.

### The Maven suite cannot pass on Windows

Running `./mvnw -B test` on a Windows workstation produced 747 tests with 4
failures and 282 errors. The dominant root cause was one line:

```text
FATAL: invalid value for parameter "TimeZone": "Asia/Saigon"
```

The JVM reports the legacy alias `Asia/Saigon` on this platform. PostgreSQL
rejects it. `LabtimesheetApplication.main` already normalizes the alias to
`Asia/Ho_Chi_Minh` before Spring starts, recorded in
`docs/tests/integration/windows-timezone-alias.md`. Spring test contexts never
call `main`, so the normalization does not apply to the suite.

Re-running with `-DargLine="-Duser.timezone=Asia/Ho_Chi_Minh"` cleared 178 of the
282 errors. The remaining 104 were Testcontainers failures caused by the
workstation's system drive being completely full, which had put the Docker
metadata store into read-only mode. That is an environment fault, not a defect.

Every evidence record in this repository was written on macOS; several embed
`/opt/homebrew` and `/Users/sechmachine` paths. The platform assumption was never
stated.

## Decision

**A constraint is live only if CI runs it, a test asserts it, or a build step
depends on it.** Text inside `docs/tests/` is an archival record of a command
that was run on a date. It is evidence, never enforcement. Structural decisions
are not held back by it, and it is not edited to match later refactors.

**Documentation structure is not shaped to satisfy a check that does not run.**
Where a check protects a real intent but its implementation is brittle, the check
is repaired rather than the documents deformed.

**The Windows test-environment requirement is recorded rather than patched.** No
production or test source is changed under this record. Running the suite on
Windows requires `-DargLine="-Duser.timezone=Asia/Ho_Chi_Minh"` and sufficient
free space on the Docker storage drive.

## Consequences

- `DEVELOPMENT.md`, `TESTING.md`, and `DEPLOYMENT.md` are free to move under
  `docs/` whenever that is wanted. The evidence records that name them keep their
  historical paths.
- `scripts/verify-fix-branch-workflow.cjs` is known-dead. Repairing it means
  asserting intent instead of verbatim text, dropping the two `docs/superpowers/`
  entries, and wiring it into `.gitea/workflows/verify.yml` so it becomes real.
  That is source work and needs its own RED-to-GREEN cycle and evidence record.
- Until that happens, the 258-character lines in three documents stay. They are a
  symptom, and removing them before fixing the cause would hide it.
- Two genuine regressions on `main` were found while running the suite and are
  recorded here for tracking, not fixed under this ADR:
  - `6d2c967` removed `min-width: 64rem` from `src/main/frontend/app.css`, which
    `f5eeb0b` had restored one commit earlier and guarded with
    `FrontendSourceContractTest.desktopLayoutContainsPageLevelOverflowContainment`.
    That guard has been red ever since.
  - `73df96b` changed `@playwright/test` from the pinned `1.55.0` to `^1.62.1`
    while `src/test/js/playwright-contract.test.mjs` still asserts `1.55.0`. CI
    runs `npm run test:ui`, so `main` has been failing CI since 29 August 2026.
- A future decision should state whether the suite is expected to pass on
  Windows. If it is, the timezone normalization belongs somewhere every Spring
  test context reaches, and the line-ending contract belongs in `.gitattributes`,
  which currently governs only `mvnw` and `*.cmd`.
