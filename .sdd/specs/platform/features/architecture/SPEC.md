# Architecture Spec

**Version:** 1.0.1 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-24

**Module:** `platform` · **Shared contract:** [MODULE.md](../../MODULE.md)

## 1. Context & Goal

Keep the product one modular monolith whose runtime, module boundaries and schema authority
are checked by the build rather than by review, and keep the work a request does independent
of the number of rows it renders.

This is one cohesive planning scope. Operations below are sections of this feature,
not separate modules or mandatory branches. Its rules moved here from the platform shared
contract unchanged (`D40`).

## 2. Actors & Roles

The maintainer and every contributor, person or agent, who changes code, the build or the
schema. No use case is defined here: these rules bind the structure of the code, and the
running system offers no actor workflow for them.

## 3. Functional Requirements

The operation summaries below are navigation. The numbered rules remain authoritative;
they do not acquire additional behavior from a heading or summary.

### Pin the stack and toolchain

ARC-001 to ARC-004 fix the runtime, the build, the database and the UI toolchain.

### Keep the module structure

ARC-005 and ARC-006 fix the packages, the acyclic module graph and the one interface exception.

### Own the schema through Flyway

ARC-007 to ARC-009 make Flyway the only schema authority and keep an applied migration unchanged.

### Bound the work per request

ARC-010 keeps authorization decisions and queries independent of the rows a request renders.

### Canonical feature rules

The numbered rules of this feature are non-functional. They stay in section 4 under §3,
where the platform shared contract held them.

## 4. Non-functional Requirements

### §3. Architecture and runtime

**Part F1.**

| ID | Requirement |
|---|---|
| ARC-001 | THE system SHALL be one server-rendered modular monolith on Java 25 and Spring Boot 4.1.0. |
| ARC-002 | THE system SHALL build with Maven and use Spring MVC, Spring Security, Spring Data JPA, Bean Validation, Thymeleaf, Spring Mail, and Flyway. |
| ARC-003 | THE system SHALL use PostgreSQL 18.4 for production, development, and integration testing. WHERE a test exercises a PostgreSQL-specific constraint, it SHALL run against PostgreSQL rather than H2. |
| ARC-004 | THE system SHALL pin Node 24 LTS and Tailwind CSS 4 for the UI toolchain, SHALL install with `npm ci`, and SHALL commit the npm lockfile. THE system SHALL constrain test tooling to a compatible range rather than pinning it by assertion, and every verification run SHALL record the version it actually resolved. A test SHALL NOT assert equality against a tool version, because that encodes a decision in a place no decision record can reach. |
| ARC-005 | THE system SHALL keep `LabtimesheetApplication` in the root package `com.lab.labtimesheet`, shared wiring in `config`, and code that belongs to no single feature in `platform`. Business code SHALL be grouped below `feature.<name>` for `attendance`, `calendar`, `identity`, `internship`, `notification`, `project`, and `reporting`, each adding only the layer subpackages it needs from `controller`, `model`, `model.dto`, `model.entity`, `repository`, `service`, and `exception`. THE dependencies among `platform` and those features SHALL form a directed acyclic graph in which `platform` depends on no feature. `config` MAY depend on `platform` and on any feature, and neither `platform` nor any feature SHALL depend on `config`. Tests SHALL mirror those packages, except the `architecture` and `ui` test packages, and Thymeleaf templates and built static assets SHALL remain under `src/main/resources/templates` and `src/main/resources/static`. |
| ARC-006 | THE system SHALL bind validated DTOs in a feature's controllers and delegate business transactions to that feature's services, which use its repositories and entities. A feature MAY call another feature's service contract and DTOs. WHERE code reaches into another feature's repository or JPA entity, or places business SQL in a service, THE build SHALL fail. The same boundary holds for `platform`: a feature MAY call its service contracts and DTOs, and SHALL NOT reach into its repositories or JPA entities. Flyway and schema verification SHALL be the only direct-SQL boundary, and THE system SHALL NOT introduce network boundaries, empty utility packages, or one-implementation abstraction layers. The one exception is an interface that a module declares and itself calls, every implementation of which lives in a different module that would still depend on the declaring module under `ARC-005` if that implementation were removed, so that data or behavior owned by that dependent module reaches the declaring module without a dependency cycle. |
| ARC-007 | THE system SHALL treat Flyway as the sole production schema authority, and SHALL restrict JPA schema generation to validation outside disposable tests. |
| ARC-008 | The reviewed `database-schema.sql` is a design baseline rather than an executable artifact. The platform owner adapts it into Flyway migrations; it is never executed against production. |
| ARC-009 | A Flyway migration that has been applied is never edited. A schema change adds a new migration. |
| ARC-010 | THE system SHALL keep the number of authorization decisions and database queries a request performs independent of the number of rows it renders. WHERE a page, report, or export renders more rows, THE system SHALL NOT ask the authorization policy of `AUTH-012` once per row and SHALL NOT issue a query per row. THE system SHALL NOT state a time budget while no deployment environment exists to measure one; `RPT-008` bounds what a single request may demand until then. |

Reference versions and primary documentation:

- [Spring Boot 4.1.0 release](https://spring.io/blog/2026/06/10/spring-boot-4/)
- [Spring Boot 4.1 code-structure guidance](https://docs.spring.io/spring-boot/4.1/reference/using/structuring-your-code.html)
- [Spring Boot SQL and Spring Data JPA guidance](https://docs.spring.io/spring-boot/reference/data/sql.html)
- [PostgreSQL support/versioning](https://www.postgresql.org/support/versioning/)
- [Tailwind CLI](https://tailwindcss.com/docs/installation/tailwind-cli)
- [Node release schedule](https://nodejs.org/en/about/previous-releases)

Inherit [platform constraints](../../MODULE.md#4-non-functional-requirements), including
security, history, concurrency, server time and test evidence.

## 5. Data

No table of its own. `ARC-007` to `ARC-009` govern how every module's tables change; the
physical model is §19 in the [Data model](../data-model/SPEC.md#5-data) feature.

### Required contracts

These are required facts or behaviors, not prescribed cross-module imports or an
automatic implementation order.

| Provider | Required fact or behavior | Rule trace |
|---|---|---|
| [Authorization](../authorization/SPEC.md) | One authorization policy, whose calls per request `ARC-010` bounds | [AUTH-012](../authorization/SPEC.md) |

### Related workflows and joint checks

- Every module is shaped by `ARC-005` and `ARC-006`; automated structural tests check them,
  as `AC-ARC-001` requires.

## 6. Error Handling

Where code crosses a module boundary, `ARC-006` makes the build fail; this feature adds no
runtime failure behavior. Apply [module error handling](../../MODULE.md#6-error-handling).

## 7. Acceptance Criteria

### Operation and acceptance map

This map traces existing behavior; its gap column does not define a new rule or
claim full test coverage. Actors and outcomes are summaries of the canonical rules.

| Operation | Actor and observable outcome | Canonical rules | Existing acceptance scenarios | Acceptance boundary or open decision |
|---|---|---|---|---|
| [Pin the stack and toolchain](#pin-the-stack-and-toolchain) | A contributor builds and tests on the pinned runtime, database and toolchain, and each run records what it resolved | [ARC-001](SPEC.md), [ARC-002](SPEC.md), [ARC-003](SPEC.md), [ARC-004](SPEC.md) | [AC-ARC-001](SPEC.md) | A test does not assert equality against a tool version (`ARC-004`). |
| [Keep the module structure](#keep-the-module-structure) | The build fails when code crosses a module boundary or closes a cycle | [ARC-005](SPEC.md), [ARC-006](SPEC.md) | [AC-ARC-001](SPEC.md) | Each boundary is asserted by a structural test, not by review. |
| [Own the schema through Flyway](#own-the-schema-through-flyway) | Only a Flyway migration changes the schema, and an applied one is never edited | [ARC-007](SPEC.md), [ARC-008](SPEC.md), [ARC-009](SPEC.md) | [AC-ARC-001](SPEC.md) | `ARC-009` binds contributors and has no system scenario (§20). |
| [Bound the work per request](#bound-the-work-per-request) | Rendering more rows asks the policy and the database no more often | [ARC-010](SPEC.md) | [AC-ARC-002](SPEC.md) | No time budget is stated until a deployment exists to measure one. |

### Canonical acceptance scenarios

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-ARC-001 | ARC-001–ARC-008 | The architecture and persistence structure suites run against the compiled application | Package layout, layer subpackages, test packages that mirror production except `architecture` and `ui`, the absence of repository and entity imports across modules including into `platform`, an acyclic dependency graph among `platform` and the features with no dependency into `config`, the four conditions of the `ARC-006` exception for every interface that uses it, the absence of business SQL in services, and Flyway-only schema authority are each asserted by an automated structural test rather than by review. |
| AC-ARC-002 | ARC-010, AUTH-012 | The same authorized list page and the same report are rendered twice, once with one row and once with fifty, counting the authorization decisions and the statements the data source runs | Both counts are equal at both sizes, so neither grows with the rows rendered; the rendered rows themselves differ only in number. |

Shared and cross-feature scenarios in [MODULE.md](../../MODULE.md#7-acceptance-criteria)
also apply.

## 8. Out of Scope

Inherit [module exclusions](../../MODULE.md#8-out-of-scope), including `GOV-007`. Code
packages that mirror the features of the specification are not part of `ARC-005` and are
not introduced by this document.

## Notes / Open Questions

No question affecting this feature is open. Its technical design is [PLAN.md](PLAN.md), with
tasks in [TASKS.md](TASKS.md). Part A, which builds `ARC-005` and `ARC-006`, now lives in this
feature's `PLAN.md` and `TASKS.md` (`D40`).

Read [shared open questions](../../MODULE.md#notes--open-questions) before approving
the technical plan. [plan.md](../../../../../plan.md) is the only progress tracker.
