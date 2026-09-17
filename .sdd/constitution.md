# Project Constitution

| Field | Value |
|---|---|
| Version | `2.0.1` |
| Status | `LOCKED` |
| Applies to | every developer, every AI agent, every pull request |
| Maintainer | Loc-LX |
| Business reviewer | the instructor; reviews the maintainer's business decisions in [`.sdd/decisions.md`](decisions.md) and does not sign this document |
| Signed by | Loc-LX, 2026-09-15 |
| Last updated | 2026-09-17 |
| Amendment | see [Amendment](#amendment); the mechanism depends on the kind of change |
| Full rule text | [`.sdd/specs/`](specs) |

**Locked.** This document changes only through the last row of the table under Amendment, and a rule it indexes changes through the row that fits that change.

This document is canonical for four things only: each indexed rule's layer and
exception, the standing deviations, the definition of done, and the AI agent policy.
For the wording of a rule it is an index. Each row summarizes a rule whose full text
is in its spec, states how strictly it binds, and names the test that enforces it.
Where a row and its spec differ, the spec's wording is the rule and the row is
corrected.

A rule is indexed here when it is a hard rule, an architectural constraint, or an
engineering standard that holds across features. Feature business rules, such as who
approves a request, a deadline, or a quota, are not indexed. They change under
Amendment in their own spec, and the tests that protect them name them in their own
source (`TST-005`).

Three layers, ranked by how strictly a rule binds. Within a layer, rules are grouped
by subject.

| Layer | Meaning | Exception |
|---|---|---|
| 1 | Hard rule. A breach is a defect regardless of deadline. | None, and nobody may grant one. |
| 2 | Architectural constraint. | Only through an approved ADR. |
| 3 | Engineering standard that holds across features. | A documented deviation, as Layer 3 describes. |

---

## Layer 1 — Hard rules

### Domain invariants

| ID | Rule | Enforced by |
|---|---|---|
| `GOV-004` | Attendance time and Task work time are separate domains. Neither proves nor derives the other. | `AttendanceAndTaskWorkSeparationTest#noProductionSourceReachesBothAttendanceRecordsAndTaskWorkLogs`, which fails when any production source names the persistence identifiers of both domains |
| `GOV-005` | Historical business results must not change because an Admin later edits workdays, schedule, grace, quota, penalty, or the calendar. | `AttendancePersistenceIntegrationTest#approvedLeaveBlocksOnlyItsFrozenAllocatedDates`, `#calendarDayOffBlocksCheckInAndPastEventsAreImmutable` |
| `GOV-011` | Business dates resolve in the attendance policy version's timezone. Persisted instants are `timestamptz` and treated as UTC. | `PlatformFoundationTest#theSchemaUsesNoPostgresEnumsAndStoresEveryInstantWithItsZone` for instants stored with their zone; `ApplicationTimeZoneIntegrationTest` only canonicalizes the JVM's timezone alias; resolution against the policy version is a gap |
| `GOV-012` | Server time is authoritative for check-in, checkout, submission, decision, activation, expiry, and lifecycle timestamps. Browser timestamps are never trusted. | `AttendanceServiceTest#exactCheckInGraceBoundaryIsOnTimeAndFirstLaterInstantIsLate`, `#checkoutIsInclusiveAtCutoffAndCannotBeOverwritten` |
| `GOV-013` | Mutable aggregate updates are transactional and use optimistic locking. Quota, daily work totals, bootstrap, and transfer workflows serialize further. | `BootstrapIntegrationTest#concurrentBootstrapCreatesExactlyOneAdminAndPermanentlyCloses`, `AccountRecoveryLockOrderIntegrationTest#concurrentAccountFirstConsumptionAndIssuanceComplete` |
| `GOV-014` | Historical records are retained behind restrictive foreign keys and lifecycle or soft-delete fields. The interface never physically deletes a record that carries business history; the only deletion it offers is an empty `PLANNED` Project under `PRJ-002`. | No single test. Schema constraints in `V1__baseline.sql`; the emptiness condition of `PRJ-002` is not yet implemented. |

### Security

| ID | Rule | Enforced by |
|---|---|---|
| `AUTH-002` | A hidden control is never authorization. An unauthenticated caller is sent to sign-in; a record the caller may not see and a record that does not exist return the same not-found response. | Partly. `ProjectControllerTest#guessedProjectIdReturnsTheSameNotFoundResponseAsAMissingProject` shows a denied Project read becomes a 404, but it mocks the denial and never compares it with a missing Project. Nothing asserts the hidden-control clause. |
| `SEC-001` | Session authentication, server-side authorization, object ownership checks, CSRF protection, Bean Validation, output escaping, and password hashing stay on under every profile, including development and test. | no single test; see [Known enforcement gaps](#known-enforcement-gaps) |
| `SEC-002` | Passwords are 12 to 128 characters and use the delegating adaptive encoder. No composition rules. | `PasswordResetIntegrationTest#resetPostRejectsPasswordsOutsideTheTwelveToOneTwentyEightCharacterBounds` |
| `SEC-003` | Activation and reset tokens use cryptographically secure random bytes. Only the 32-byte SHA-256 hash is stored. | `PasswordResetIntegrationTest#resetTokenPersistsOnlyItsHashExpiresExclusivelyAndIsSingleUse` |
| `SEC-004` | Activation tokens expire after 24 hours, reset tokens after 30 minutes. Issuing a token invalidates the older one. | `PasswordResetIntegrationTest#resetTokenExpiresAtExactlyThirtyMinutes`, `AccountRecoveryIntegrationTest#resendInvalidatesPriorActivationTokenBeforeSendingFreshLink` |
| `SEC-005` | Login and password-reset responses are generic and never reveal whether an email exists, is pending, is locked, or lacks SMTP delivery. | `PasswordResetIntegrationTest#forgotPostUsesOneGenericResponseForActivePendingLockedAndUnknownAccounts`, `PasswordRecoveryWebIntegrationTest` |
| `SEC-006` | Login throttling keys on normalized email plus source IP. Five failures in 15 minutes create a 15-minute throttle; a success clears it. | `LoginThrottleTest#fiveFailuresWithinFifteenMinutesThrottleTheSixthAttempt` |
| `SEC-008` | Redirect targets are allow-listed and local. State-changing endpoints reject open redirects, user-selected class names, arbitrary templates, and arbitrary URLs. | `NotificationActionContractTest#acceptsOnlySafeRelativeApplicationRoutes`, `OriginEnforcementFilterTest#mismatchedOriginIsRejectedForStateChangingRequests` |
| `SEC-009` | Error pages never expose stack traces, SQL, secrets, internal IDs from unauthorized records, or existence signals. | `SharedErrorTemplateWebTest` |
| `SEC-010` | Production requires an HTTPS public base URL and explicit trusted-proxy configuration before it is considered ready. | `ProductionReadinessTest#productionOriginIsCanonicalAndRejectsBracketedIpv6Loopback` |
| `SEC-011` | Production responses carry HSTS, a restrictive content policy, frame denial, and referrer suppression. Session cookies are `Secure`, `HttpOnly`, `SameSite=Strict`. | referrer only, in `SecurityResponseIntegrationTest#authenticationAndActivationResponsesDoNotSendReferrers`; see [Known enforcement gaps](#known-enforcement-gaps) |
| `SEC-012` | Forwarded headers are trusted only on an explicitly enabled and constrained proxy path. | `TrustedForwardedHeaderFilterTest#untrustedSocketWithForwardedHeadersIsRejectedBeforeHeaderAdaptation` |
| `SEC-013` | Development and test profiles may relax HTTPS, localhost origins, `SameSite`, HSTS, and `Secure` cookies. The relaxations come only from development or test profile state and never reach production. | Partly. `ProductionReadinessTest#unsafeProductionInputsFailWithoutEchoingSecrets` shows production readiness refusing a localhost origin, a non-`Secure` cookie, and `lax` cookies together with other unsafe inputs, so no single relaxation is shown to be refused alone. Nothing checks HSTS under the production profile. |
| `SEC-014` | Production startup fails when the master key, public origin, datasource, or proxy policy is absent or unsafe. | `ProductionReadinessTest#unsafeProductionInputsFailWithoutEchoingSecrets` |

Gaps in the rows above are listed under [Known enforcement gaps](#known-enforcement-gaps).

### Process integrity

These bind the people who build the system rather than the running system, and no
deadline or documented reason excuses a breach.

| ID | Rule | Enforced by |
|---|---|---|
| `TST-011` | A test assertion is never weakened or deleted to make a test pass. | review; nothing automated can tell a weakened assertion from a corrected one |
| `ARC-009` | An applied Flyway migration is never edited; a schema change adds a new migration. | Flyway's checksum validation, which neither `application-dev.yaml` nor `application-prod.yaml` disables; no test edits a migration to show it |
| `GOV-006` | A feature absent from the specification needs a new reviewed decision. Adjacent scope is never silently authorized. | review; nothing checks it |

### Secrets

| ID | Rule | Enforced by |
|---|---|---|
| `OPS-004` | Committed examples hold placeholders only. Real `.env` files, private run configurations, and master keys stay untracked. | `.gitignore` and `.dockerignore`; no test reads the example files back |

SMTP and HolidayAPI credentials are Admin-managed and encrypted with a
deployment-provided master key.

### Supply chain

Application security and delivery security are different problems, and the rules
above only cover the first.

| ID | Rule | Enforced by |
|---|---|---|
| `OPS-012` | Only `main` publishes an image to the registry, and every publication carries the immutable commit SHA tag. A moving `main` tag is a convenience alias only. | the `gitea.ref == 'refs/heads/main'` conditions and the `sha-${GITEA_SHA}` tags in `.gitea/workflows/container.yml` |
| `OPS-016` | The workflow uses the `vars` and `secrets` contexts and does not treat a job environment as a security boundary. | reading the workflow; nothing checks it |
| `OPS-017` | Runner access to a Docker socket is limited to trusted repositories and operators. Untrusted fork code never receives publication or deployment secrets. | runner configuration outside this repository; nothing here can prove it |

`OPS-016` and `OPS-017` are the weakest rows on this page. Both describe how the
runner is configured, and a repository cannot verify its own runner. They are
stated so that a reviewer knows to ask, not because anything here enforces them.

---

## Layer 2 — Architectural constraints

| ID | Rule | Enforced by |
|---|---|---|
| `ARC-001` | One server-rendered modular monolith on Java 25 and Spring Boot 4.1.0. | the compiler release is 25, so an older JDK fails the build while a newer one is not refused; the Spring Boot parent pins 4.1.0; no test asserts the shape |
| `ARC-002` | Backend uses Maven, Spring MVC, Security, Data JPA, Bean Validation, Thymeleaf, Spring Mail, and Flyway. | `ReportingDependencyContractTest` covers the reporting libraries only |
| `ARC-003` | PostgreSQL 18.4 is the database family for production, development, and integration tests. PostgreSQL-specific rules are tested against PostgreSQL, never H2. | Testcontainers configuration in `TestcontainersConfiguration` |
| `ARC-004` | The UI toolchain pins Node 24 LTS and Tailwind CSS 4, uses `npm ci`, and commits the lockfile. Test tooling is constrained to a compatible range instead, no test asserts equality against a tool version, and each run records the version it resolved. | the `Set up Node 24`, `npm ci`, and `Verify generated assets are committed` steps of `.gitea/workflows/verify.yml`; the compatible-range rule, the ban on version equality, and the recording rule by `src/test/js/playwright-contract.test.mjs` |
| `ARC-005` | `LabtimesheetApplication` stays in the root package. Shared wiring lives in `config`; code that belongs to no single feature lives in `platform`. Business code groups under `feature.<name>` for `attendance`, `calendar`, `identity`, `internship`, `notification`, `project`, and `reporting`, each adding only the layer subpackages it needs. Dependencies among `platform` and the features form a directed acyclic graph in which `platform` depends on no feature; `config` may depend on `platform` and on any feature, and nothing depends on `config`. Tests mirror those packages, except `architecture` and `ui`. | None yet. `LayerStructureTest` still approves the package list from before `D28`, and `AttendanceLayerStructureTest` still requires `AttendancePolicy` inside `attendance`; both guard the layout this row replaces, and protect this rule again only once step 6 of `D28` updates them. Nothing checks the graph. See [Known enforcement gaps](#known-enforcement-gaps) |
| `ARC-006` | Controllers bind validated DTOs and delegate to services; services use repositories and entities. A feature may call another feature's or `platform`'s service contract and DTOs but never its repositories or entities. No business SQL. Flyway and schema verification are the only direct-SQL boundary. No one-implementation abstraction layer, except an interface a module declares and calls whose every implementation lives in another module that would depend on the declaring module even without that implementation. | `LayerStructureTest` covers cross-feature imports only; nothing fails the build on business SQL, on imports into `platform` internals, or on the conditions of the exception. See [Known enforcement gaps](#known-enforcement-gaps) |
| `ARC-007` | Flyway is the sole production schema authority. JPA schema generation is validation-only outside disposable tests. | `PlatformFoundationTest#flywayCreatesApprovedPostgresCatalog` |
| `ARC-008` | The reviewed `database-schema.sql` is a design baseline rather than an executable artifact. It is adapted into Flyway migrations and never executed against production. | nothing here; the artifact is not in this repository and the adaptation is already done. `ARC-007` carries the obligation that still binds |
| `AUTH-012` | Every business permission is decided by one authorization policy from the actor's role, the actor's scope, the record's current state, and the target state. No role check outside the policy decides a business permission; route protection may stay coarse, and a template only asks the policy what to show. | nothing yet; not implemented. `ADR-005` records the decision and `AC-AUTH-011` the scenario |
| `SEC-007` | Login throttle state may live in bounded memory and resets on restart, because production runs one application instance. A manual account lock is persisted separately. | `LoginThrottleTest#capacityPressureNeverEvictsAnActiveBlock` covers the bounded memory; nothing shows a manual lock is kept apart from throttle state |

### Delivery

| ID | Rule | Enforced by |
|---|---|---|
| `OPS-011` | A trusted repository-scoped runner verifies Maven tests, PostgreSQL and Flyway integration, frontend assets, and workflow contracts on every pull request and push. The container workflow runs only on manual dispatch or a push to `main`, and its own verification job succeeds before any image is built. | the job steps in `.gitea/workflows/verify.yml`; the `on` block and the `needs: verify` of both image jobs in `.gitea/workflows/container.yml` |
| `OPS-013` | The pipeline stops at build and publish. It does not connect to an unprovisioned production host. | the deployment job is gated on `DEPLOY_ENABLED` |
| `OPS-014` | The SSH deployment job is a dormant template, active only on `main`, only when the repository variable enables it, and only when the required host, user, private-key, and known-host secrets exist. | the same gate, plus `GOV-010` naming it as not yet active |

### Excluded by decision

`GOV-007` and `GOV-008` list what version 1 deliberately does not contain: no
SPA framework, JWT authentication, microservices, Redis, Kafka, generic workflow
engine, or persisted `Report` entity; no project-level days off, multiple Task
assignees, unconditional self-service Project joining, Task dependencies, epics,
sprints, or story points. Crossing any of them is the third row of the table
under Amendment: a recorded decision to lift the exclusion, with an ADR when the
exclusion is architectural, then a numbered requirement and an acceptance
scenario to define what replaces it.

`GOV-015` adds the Task effort-planning boundary: no external Jira or Tempo
integration, no Jira mirroring, no Tempo accounts or synchronization, no
acceptance or rejection states for Tasks, and no re-baselining once work is retained.

Weekly and Monthly report presets are **deferred, not excluded**. They sit
outside the v1 acceptance scope and may be built later, which is the second row
under Amendment and needs no ADR.

`GOV-016` governs the documents themselves: every requirement has exactly one
canonical location, a change in business behavior updates that location, and a
new feature is documented in the same form as the features already there. A split
across separate documents is an organizational choice, never a goal, and no split
document overrides the canonical location of a rule.

---

## Layer 3 — Engineering standards

These are engineering standards that hold across features. They carry no enforcement
column, because review and the definition of done below are what hold them.

A contributor may deviate from one only with a documented reason. A one-off deviation
names the rule and the reason in its commit message or pull request. A standing
deviation is listed under [Standing deviations](#standing-deviations) with its owner,
its reason, and the condition that ends it. A rule that must never be deviated from
belongs in Layer 1.

| ID | Rule | Deviation |
|---|---|---|
| `TST-001` | Every feature, fix, refactor, or behavior change follows strict RED, verified failure, minimal GREEN, verified narrow pass. | Documented reason; `TST-009` names where it does not apply |
| `TST-002` | Production behavior written before its failing test is discarded and reimplemented from the failing test. A test written only after implementation does not satisfy it. | Documented reason |
| `TST-003` | Each test names the observable break it catches and derives expected values independently, never mirroring production code or merely asserting that a mock was called. | Documented reason |
| `TST-004` | Real components are used at the relevant boundary. Only slow or external dependencies such as SMTP and HolidayAPI are faked. | Documented reason |
| `TST-005` | Each test names, in its own source, the numbered requirements it protects. | Documented reason; see [Standing deviations](#standing-deviations) |
| `TST-006` | One test class covers one cohesive behavior, not one production class. Test packages mirror the feature packages they exercise. | Documented reason |
| `TST-007` | The trace records the requirement and scenario identifiers, the observable break, and the hand-derived expected result. | Documented reason; see [Standing deviations](#standing-deviations) |
| `TST-008` | A verification run records its own commands, results, and resolved tool versions. A written claim never replaces an executable run. | Documented reason |
| `TST-009` | Human prose and simple configuration do not receive artificial unit tests. Their evidence is the smallest executable validation. | This rule names where `TST-001` does not apply |
| `TST-010` | A milestone is committed only when evidence is current, narrow and affected suites are green, and no unexplained error or warning remains. | Documented reason |
| `OPS-019` | Shared build, migration, security, navigation, and base-template files have one named owner at a time. A targeted fix uses a clean, isolated `work/fix/<feature>/<what-fix>` branch from the current `main`, never `work/<feature>/fix/<what-fix>`. Contributors do not revert or rewrite another branch's work. | Documented reason, coordinated with the owner |

### Standing deviations

| Rule | Deviation | Owner | Reason | Ends when |
|---|---|---|---|---|
| `TST-005`, `TST-007` | Test classes written before `ADR-004` do not name the rules they protect or record their trace; `plan.md` tracks how many still do not. | Loc-LX | Retrofitting every class at once would rewrite tests without changing what they prove. | Each class names its rules and records its trace the next time it is changed. |

### Definition of done

**This is the canonical list.** No other document restates it.

A tracked item is `DONE` only when all of the following hold.

- The numbered requirement and its acceptance behavior are satisfied.
- The test existed and failed for the intended reason before production code.
- Focused and affected suites pass.
- Authorization and negative cases are covered where applicable.
- PostgreSQL-specific rules are tested against PostgreSQL, not H2.
- Concurrency, deadline, and history behavior has proportionate evidence.
- UI behavior is authorized on the server, never by hiding a control (`AUTH-002`), and pages use the reusable fragments of `UI-008` rather than page-local copies.
- Each test names the numbered rules it protects in its own source (`TST-005`), and the run that shows it passing records its commands, results, and tool versions (`TST-008`). The tracker records the item's state, never its evidence.
- Where the item changes business behavior, the spec that holds the rule already says so, with its `CHANGELOG.md` entry (`GOV-016`).
- New or changed production types and public or protected methods carry accurate Javadoc written during implementation. The Iteration 1 retrofit is the only exception.
- No unrelated files or another branch's ownership area were changed without coordination, which is `OPS-019`.
- The final branch head is green.
- Integration does not alter totals, state graphs, or historical meaning.

---

## AI agent policy

An agent working here holds narrower permission than a developer, because it acts
faster than a person can read what it did.

**Allowed without asking.** Reading and searching any tracked file. Running
`./mvnw test`, `./mvnw spring-boot:run`, `npm ci`, `npm run build`,
`npm run test:ui`, the GitNexus commands, and read-only git. Investigation is
always allowed and is usually what makes a proposal worth approving.

**Requires the maintainer's agreement first.** Any change to any file, including
documentation. Specifically and without exception: deleting a file, editing this
constitution, committing, pushing to any branch, adding or upgrading a
dependency, and changing the database schema.

A question from the maintainer is not permission. "What does this folder do" asks
for an answer, not for the folder to be tidied.

**Never, with or without agreement.** These are not the maintainer's to grant in
passing. They are stated here rather than only in `AGENTS.md`, because this
document outranks that one, and an absolute prohibition held only by the lower
authority dissolves against the higher one under `GOV-001`.

- Commit to `main`. Work lands on a `work/fix/<feature>/<what-fix>` branch under `OPS-019` and reaches `main` by review.
- Read or print `.env`. The committed `.env.example` files hold placeholders only under `OPS-004`; the real file holds a database password and the AES-256 master key.

**Must do.**

- State the plan, name the files it will change, say how to undo it, then stop and wait.
- When the specification does not answer something the work needs, list what is unclear, state the assumption that would be made, say what that assumption changes, and stop. Do not pick an interpretation quietly.
- Name the rule identifiers a change touches, so the reviewer can check the change against the rule rather than against the diff.

## Amendment

`GOV-001` sets the authority order. When statements conflict, the highest
applicable authority wins and a lower-authority rule may not be revived against
it.

Each kind of change has one mechanism, stated here and referenced from everywhere else.

| Change | What it needs |
|---|---|
| Change or withdraw a rule that already exists | The spec that holds the rule and an entry in its `CHANGELOG.md`, with the decision and its evidence recorded in `.sdd/decisions.md`. An ADR under [`.sdd/rfcs/`](rfcs) only when the change sets or moves an architectural boundary. |
| Add a rule inside the scope already agreed | A numbered requirement in the spec of the feature it belongs to and an acceptance scenario in section 7 of that spec. No ADR. |
| Cross something `GOV-007`, `GOV-008` or `GOV-015` declares excluded | A recorded decision lifting the exclusion, and an ADR when the exclusion is architectural (`GOV-007`); then the numbered requirement and scenario. |
| Change this document: a rule's layer or exception, an index row, a standing deviation, the definition of done, or the agent policy | The maintainer's agreement to the wording, the decision recorded in `.sdd/decisions.md`, and a new version of this document: major when an obligation is removed or weakened, minor when one is added, patch for wording. |

A pull request is how any of these reaches the repository. It is the delivery
mechanism, never an alternative to them.

## Known enforcement gaps

Every row below is a rule whose enforcement this repository cannot yet show. A row names a
test only for what that test asserts, never because its class name looks right. The Layer 3
table names no test, so its rules are not listed here. `OPS-016` and `OPS-017` are listed
because a repository cannot verify its own runner.

A constitution that claims enforcement it does not have is worse than one that
names its own gaps, and worse still when the claim survives because the class
name looks plausible.

| Rule | What is missing | What would close it |
|---|---|---|
| `SEC-011` | The highest-priority gap on this page. No test reads back any security header. `AC-SEC-008` fixes exact values for HSTS, the content policy, frame ancestors, and the cookie attributes, and none is asserted. | One web test that inspects the response headers against `AC-SEC-008`. |
| `SEC-001` | The rule is broader than any single test. `SecurityConfiguration` builds the filter chain, and every web test that asserts a denial exercises one slice of it. | Accept it as an intent statement, or narrow it into rules that can each be asserted. |
| `AUTH-002` | No test compares the response for a record the caller may not see with the response for a record that does not exist, and none asserts that a hidden control grants nothing. | A web test per protected record type that requests an existing unauthorized identifier and an absent one and asserts the same status, view, and model. |
| `SEC-013` | Production readiness is tested only against all development values at once, and nothing checks HSTS or `Secure` cookies under the production profile. | Fold into the `SEC-011` header test: under the production profile, assert each relaxation is absent. |
| `GOV-011` | Only the storage half is tested. Nothing asserts that a business date resolves against the applicable policy version's timezone. | A test that sets a JVM default different from the policy timezone and checks the resulting business date. |
| `GOV-014` | Relies on schema constraints in `V1__baseline.sql`. | A test asserting that only an eligible empty `PLANNED` draft can be physically deleted, and that no other route, service, or interface operation physically deletes the Project data `GOV-014` protects. |
| `ARC-001` | An older JDK fails the build, which proves the minimum version and not the architecture. | An architecture test asserting the module shape, alongside `LayerStructureTest`. |
| `ARC-002` | `ReportingDependencyContractTest` covers the reporting libraries only. | Extend it to the rest of the declared stack, or accept the narrower claim. |
| `ARC-006` | `LayerStructureTest` checks imports only. The rule requires the build to fail on business SQL in a service, and `ProjectService#nativeDelete` runs native SQL today (`D18`). | Move that SQL behind the data-access layer under `D18`, and add a check that fails the build on `createNativeQuery` or `JdbcTemplate` outside a `repository` package. |
| `ARC-005` | `LayerStructureTest` approves the build-order packages `account`, `integration`, `project`, `task`, `attendance`, `notification` and `reporting`, and `AttendanceLayerStructureTest` requires `AttendancePolicy` inside `attendance`, which this rule places in `calendar`; both pass on the layout the rule replaced. Nothing checks that the dependencies among `platform` and the features form a directed acyclic graph, or that nothing depends on `config`. | Update both tests to the modules of this rule in the same change as the code they check, and add a structural test over type references, with comments removed and string literals kept so that entity names inside JPQL count, that fails on any cycle and on any reference into `config` (`D28`). |
| `ARC-006` | The boundary does not reach `platform`. `LayerStructureTest` matches `feature\.([^.]+)\.(repository\|model\.entity)` only, so a repository or entity moved into `platform` loses the protection it has today under `feature.integration`; and its approved root packages are `config` and `feature`, so creating `platform` fails the test. | Extend both in the change that creates `platform`, in step 6 of `D28`. |
| `ARC-006` | Nothing checks the four conditions of the exception: the interface is declared and called by one module, every implementation lives in another module, and that module would still depend on the declaring module without the implementation. Without this row the exception would read as guarded. | The cycle test written in step 6 of `D28` checks the conditions for every interface with an implementation outside its module, against the list in [`ADR-006`](rfcs/ADR-006-module-boundaries.md). |
| `AUTH-012` | Not implemented. Role checks in `SecurityConfiguration`, services, and templates still decide business permissions. | The matrix-driven test of `AC-AUTH-011`, run through the policy `ADR-005` describes. |
| `SEC-007` | Nothing shows that a manual account lock is kept apart from the in-memory throttle state. | A test that recreates the throttle and asserts a manually locked account is still refused. |
| `TST-011`, `GOV-006` | Review is the only control. | Nothing automated can close it; a reviewer checks that a changed assertion follows a recorded decision and that new scope has one. |
| `ARC-009` | Relies on Flyway checksum validation staying enabled; no test shows an edited migration is refused. | A test that applies the migrations, alters one, and expects validation to fail. |
| `ARC-008` | Nothing in this repository can enforce it: `database-schema.sql` is not tracked here, as the specification header states. | Nothing, and that is the point. The rule records a completed one-time adaptation. `ARC-007` is what binds future schema work, and it is enforced. |
| `OPS-016`, `OPS-017` | Both describe runner configuration. A repository cannot verify its own runner. | An operator confirms it outside this repository; nothing here can. |

## Provenance

The three-layer format and the `LOCKED` status are a methodology choice rather
than an industry standard; most projects distribute the same content across `CONTRIBUTING.md`,
architecture decision records, and CI configuration.

The rules themselves are not new. They live in the feature specs and the tests that enforce them. This document only gathers them and says
which ones have teeth.
