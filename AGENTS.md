# AGENTS.md — Lab Timesheet

Operating rules for any AI agent working in this repository. They govern what
you may touch and how you must work. They do not govern what the system must be
true of; that is [`.sdd/constitution.md`](.sdd/constitution.md), and it outranks this file.

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
| What must always be true | [`.sdd/constitution.md`](.sdd/constitution.md) |
| What the system must do | [Specification map](.sdd/specs/README.md): module `MODULE.md` plus `features/<feature>/SPEC.md`; system-wide rules in `platform/MODULE.md` and its features; each `PLAN.md` and `TASKS.md` beside its feature `SPEC.md` (`D40`) |
| Why a decision was made | [`.sdd/rfcs/`](.sdd/rfcs) |
| What is being worked on now, and what waits | [`plan.md`](plan.md) |

`plan.md` is the only progress tracker. A feature's `PLAN.md` under `.sdd/specs/` is its technical design, not a second tracker.

## 3. Scope

The full permission list is the AI agent policy in
[`.sdd/constitution.md`](.sdd/constitution.md). The short form:

### You may, without asking

- Read and search anything tracked.
- Run `./mvnw test`, `./mvnw spring-boot:run`, `npm ci`, `npm run build`, `npm run test:ui`, and read-only `git` commands.

Investigation needs no permission and is usually what makes a proposal worth
approving.

### You must agree a plan before

Changing any file, including documentation. Name the files, say how to undo it,
then wait. A question is not permission: "what does this folder do" asks for an
answer, not for the folder to be tidied.

### You must not

- Delete a file, commit, push, add or upgrade a dependency, or change the schema without the maintainer saying so for that specific action.
- Commit or push without the user asking. Never commit to `main`.
- Edit or delete a Flyway migration that has already been applied. Add a new one.
- Weaken a rule trace in a test, or remove the requirement identifiers it names.
- Read or print `.env`, or move any secret into a tracked file.
- Weaken or delete an assertion to make a test pass.
- Rename a symbol with find-and-replace. Use the rename tool that understands the call graph.
- Add a dependency, framework, or database without an ADR. `GOV-007` and `GOV-008` list what version 1 deliberately excludes.
- Change another feature's package when your task belongs to one feature.

## 4. Branch naming

- A targeted change uses a clean, isolated `work/fix/<area>/<what>` branch and worktree from the latest verified `main`, where `<area>` is a module of `ARC-005`, `platform`, `architecture` for a change that spans modules, or `docs` for documentation only (`OPS-019`). A branch name is invalid when it begins with an existing branch name followed by `/`, or when an existing branch name begins with it followed by `/`: `work/<area>/fix/<what>` is invalid where a persistent `work/<area>` exists, and where a branch named `work/fix/<area>` exists the change uses `work/fix/<area>-<what>` instead.

Every targeted repair starts from the latest verified `main`, follows TDD from RED to GREEN,
adds Javadoc as it goes, names the rules each test protects in that test's Javadoc,
gets an independent review, and is merged with an ordinary non-force merge only
when separately authorized.

## 5. Workflow

Every behavior change follows this order. It is `TST-001` through `TST-010`, and
it is not optional.

1. Pick a numbered requirement and its acceptance scenario.
2. Write the smallest behavioral test for it.
3. Run it. Confirm it fails for the intended reason, not a compile error you did not expect.
4. Name the numbered requirements the test protects in its Javadoc, with the observable break and the hand-derived expected value.
5. Write the minimum production code for GREEN, with Javadoc on new or changed public and protected members in the same step.
6. Run the focused test, then the affected module, integration, or web suite.
7. Refactor without weakening assertions, then run the affected suite again.
8. Report the commands you ran and their results, including the resolved tool versions.

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
to match code. That disagreement is a decision for a person. It is recorded in
`.sdd/decisions.md`, and produces an ADR only when it moves an
architectural boundary.

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
