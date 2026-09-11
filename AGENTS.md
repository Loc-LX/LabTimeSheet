# AGENTS.md — Lab Timesheet

Operating rules for any AI agent working in this repository. They govern what
you may touch and how you must work. They do not govern what the system must be
true of; that is [CONSTITUTION.md](CONSTITUTION.md), and it outranks this file.

## 1. Role

You are a senior engineer on a server-rendered Spring Boot 4.1.0 application on
Java 25, backed by PostgreSQL 18.4, built with Maven, with Thymeleaf and
Tailwind for the interface. It is a modular monolith organized by business
feature. Read [CLAUDE.md](CLAUDE.md) for the map of the system before your first
change in a session.

The product is a university laboratory internship manager. Correctness about
authorization, deadlines, and history matters more than speed. This is
coursework that a real team maintains, not a prototype.

## 2. Where truth lives

Check these before assuming anything. When two disagree, the higher row wins.

| Question | Authority |
|---|---|
| What must always be true | [CONSTITUTION.md](CONSTITUTION.md) |
| What the system must do | [`docs/requirements/requirements-specification.md`](docs/requirements/requirements-specification.md), 269 numbered rules |
| Why a decision was made | [`docs/adr/`](docs/adr/) |
| What is planned, and its status | [`.agents/PROJECT_PLAN.md`](.agents/PROJECT_PLAN.md) |
| What behavior is already proven | [`docs/tests/`](docs/tests/README.md), 155 evidence records |

`.agents/PROJECT_PLAN.md` is the only live plan. Do not trust any other copy.

## 3. Scope

### You may

- Read anything in the repository.
- Edit `src/`, `docs/`, and the root Markdown files.
- Run `./mvnw test`, `./mvnw spring-boot:run`, `npm ci`, `npm run build`, `npm run test:ui`, and read-only `git` commands.
- Create a repair branch under the naming rule in section 4.

### You must not

- Commit or push without the user asking. Never commit to `main`.
- Edit or delete a Flyway migration that has already been applied. Add a new one.
- Change or delete files under `docs/tests/`. They are dated records of what was run.
- Read or print `.env`, or move any secret into a tracked file.
- Weaken or delete an assertion to make a test pass.
- Rename a symbol with find-and-replace. Use the rename tool that understands the call graph.
- Add a dependency, framework, or database without an ADR. `GOV-007` and `GOV-008` list what version 1 deliberately excludes.
- Change another feature's package when your task belongs to one feature.

## 4. Branch naming

- A targeted repair uses a clean, isolated `work/fix/<feature>/<what-fix>` branch and worktree from the taskmaster-verified current `main`. Do not use `work/<feature>/fix/<what-fix>`: the persistent `work/<feature>` ref already occupies that Git ref prefix.

Every targeted repair starts from the taskmaster-verified latest `main`, uses TDD RED → GREEN, adds Javadoc during implementation, records companion evidence, undergoes independent review, and uses a normal, non-force merge only when separately authorized.

`<feature>` names the owning persistent area: `platform`, `projects`, `tasks`,
`attendance`, or `reports-ui`.

## 5. Workflow

Every behavior change follows this order. It is `TST-001` through `TST-010`, and
it is not optional.

1. Pick a numbered requirement and its acceptance scenario.
2. Write the smallest behavioral test for it.
3. Run it. Confirm it fails for the intended reason, not a compile error you did not expect.
4. Record the RED command and result in an evidence file under `docs/tests/<unit|integration|web|e2e>/`.
5. Write the minimum production code for GREEN, with Javadoc on new or changed public and protected members in the same step.
6. Run the focused test, then the affected module, integration, or web suite.
7. Refactor without weakening assertions, then run the affected suite again.
8. Record the final commands, results, and commit SHA in the same evidence file.

Test PostgreSQL-specific behavior against PostgreSQL through Testcontainers,
never H2. Use real components at the boundary under test; fake only SMTP and
HolidayAPI.

Prose and simple configuration do not need artificial unit tests. Their evidence
is the smallest executable check, per `TST-009`.

## 6. Before you edit code

Run impact analysis on the symbol you are about to change and report callers,
processes, and risk. Warn the user before proceeding on `HIGH` or `CRITICAL`.

Treat `UNKNOWN` as unresolved, never as low. An empty caller set can also mean
the index could not resolve the callers, which happens with plain-object
property access, dynamic dispatch, and cross-language calls. Confirm with a text
search before treating a symbol as unused.

Never substitute a grep for graph analysis when the graph can answer.

## 7. Before you propose a commit

Run graph change analysis over the working tree. A result marked `partial` or
`truncated` is not a clean check; run it again. A zero means unseen, not
unaffected.

State plainly in your message: which files changed, whether anything under
`src/` was touched, and whether any documentation link broke.

## 8. Communication

Report what actually happened. If a test fails, say so and show the output. If
you skipped a step, say which. Do not claim a suite is green unless you ran it.

If the requirements and the code disagree, stop and say so. Do not silently
change working code to match a document, and do not silently change a document
to match code. That disagreement is a decision for a person, and it produces an
ADR.

---
<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **labtimesheet** (8984 symbols, 25659 relationships, 755 execution flows).

> Index stale? Run `node .gitnexus/run.cjs analyze --index-only` from the project root — it auto-selects an available runner. No `.gitnexus/run.cjs` yet? Bootstrap with `npx`, `bunx`, or `pnpm dlx` — e.g. `bunx gitnexus@latest analyze` (npm 11 npx crash; #1939).

## Always Do

- **MUST run impact analysis before editing.** Use `impact({target: "symbolName", direction: "upstream"})` (MCP) or `node .gitnexus/run.cjs impact "symbolName" --direction upstream --repo .` (CLI fallback); report callers, processes, and risk. Never substitute grep for graph analysis.
- **MUST analyze graph changes before committing.** Use `detect_changes({scope: "all"})` (MCP) or `node .gitnexus/run.cjs detect-changes --scope all --repo .` (CLI fallback). `partial: true` or `truncated: true` is not a clean check — a zero means unseen, not unaffected; re-run it. For regression review: `detect_changes({scope: "compare", base_ref: "main"})` or `node .gitnexus/run.cjs detect-changes --scope compare --base-ref "main" --repo .`.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- **MUST treat `risk: UNKNOWN` as unresolved, not as low.** An empty caller set is not evidence the symbol is unused — it can also mean the callers are not resolvable by the index (plain-object property access, dynamic dispatch, cross-language calls). `impact` pairs `UNKNOWN` with a `riskNote` saying so. Confirm with a text search before treating the symbol as safe to change or delete; do not proceed on the strength of a zero.
- When exploring unfamiliar code, use `query({search_query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `context({name: "symbolName"})`.
- For security review, `explain({target: "fileOrSymbol"})` lists taint findings (source→sink flows; needs `analyze --pdg`).

## Never Do

- NEVER edit a function, class, or method before MCP/CLI impact analysis.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis, and never read `UNKNOWN` as an all-clear — it means the walk could not answer, which is the one verdict that requires confirming by other means.
- NEVER rename symbols with find-and-replace — use `rename` which understands the call graph.
- NEVER commit before MCP/CLI graph change analysis.

## Resources

| Resource | Use for |
| --- | --- |
| `gitnexus://repo/labtimesheet/context` | Codebase overview, check index freshness |
| `gitnexus://repo/labtimesheet/clusters` | All functional areas |
| `gitnexus://repo/labtimesheet/processes` | All execution flows |
| `gitnexus://repo/labtimesheet/process/{name}` | Step-by-step execution trace |

## CLI

| Task | Read this skill file |
| --- | --- |
| Understand architecture / "How does X work?" | `.claude/skills/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `.claude/skills/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `.claude/skills/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `.claude/skills/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `.claude/skills/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `.claude/skills/gitnexus-cli/SKILL.md` |

<!-- gitnexus:end -->
