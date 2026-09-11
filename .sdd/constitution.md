# Project Constitution

| Field | Value |
|---|---|
| Version | 1.0.0 |
| Status | `DRAFT`, awaiting sign-off |
| Applies to | every developer, every AI agent, every pull request |
| Maintainer | Loc-LX |
| Supervisor | not yet assigned |
| Signed by | — |
| Last updated | 2026-09-12 |
| Amendment | requires a new ADR under [`.sdd/rfcs/`](rfcs) and the `GOV-001` authority order |
| Full rule text | [`.sdd/requirements.md`](requirements.md) |

**Status is `DRAFT` on purpose.** The playbook makes a signed constitution the
first milestone of week one, and the risk it names for skipping it is a team
working to several conventions at once. This project reached iteration four
before the document existed, so the signature is outstanding rather than early.
It becomes `LOCKED` when the maintainer accepts the rules below; until then a
rule here describes current practice rather than agreed law.

This document is an index, not a copy. Each row names a rule that already exists
in the requirements specification, states how strictly it binds, and names the
test that enforces it. The full normative wording stays in one place so the two
documents cannot drift apart.

Three layers, from strictest to most negotiable:

| Layer | Meaning | Bypass |
|---|---|---|
| 1 | Never violated. A breach is a defect regardless of deadline. | None |
| 2 | Architectural boundary. | Approved ADR only |
| 3 | Engineering standard. | Documented reason in the change itself |

---

## Layer 1 — Hard rules

### Domain invariants

| ID | Rule | Enforced by |
|---|---|---|
| `GOV-004` | Attendance time and Task work time are separate domains. Neither proves nor derives the other. | No single test. Upheld by keeping `attendance_records` and `task_work_logs` in separate features with no shared read path. |
| `GOV-005` | Historical business results must not change because an Admin later edits workdays, schedule, grace, quota, penalty, or the calendar. | `AttendancePersistenceIntegrationTest#approvedLeaveBlocksOnlyItsFrozenAllocatedDates`, `#calendarDayOffBlocksCheckInAndPastEventsAreImmutable` |
| `GOV-011` | Business dates resolve in the attendance policy version's timezone. Persisted instants are `timestamptz` and treated as UTC. | `ApplicationTimeZoneIntegrationTest` covers alias canonicalization before startup only; resolution against the policy version is a gap |
| `GOV-012` | Server time is authoritative for check-in, checkout, submission, decision, activation, expiry, and lifecycle timestamps. Browser timestamps are never trusted. | `AttendanceServiceTest#exactCheckInGraceBoundaryIsOnTimeAndFirstLaterInstantIsLate`, `#checkoutIsInclusiveAtCutoffAndCannotBeOverwritten` |
| `GOV-013` | Mutable aggregate updates are transactional and use optimistic locking. Quota, daily work totals, bootstrap, and transfer workflows serialize further. | `BootstrapIntegrationTest#concurrentBootstrapCreatesExactlyOneAdminAndPermanentlyCloses`, `AccountRecoveryLockOrderIntegrationTest#concurrentAccountFirstConsumptionAndIssuanceComplete` |
| `GOV-014` | Historical records are retained behind restrictive foreign keys and lifecycle or soft-delete fields. Normal UI operations never physically delete accounts, Projects, memberships, or Tasks. | No single test. Enforced by schema constraints in `V1__baseline.sql`. |

### Security

| ID | Rule | Enforced by |
|---|---|---|
| `SEC-001` | Session authentication, server-side authorization, object ownership checks, CSRF protection, Bean Validation, and output escaping are always on. | no single test; see the note below |
| `SEC-002` | Passwords are 12 to 128 characters and use the delegating adaptive encoder. No composition rules. | `PasswordResetIntegrationTest#resetPostRejectsPasswordsOutsideTheTwelveToOneTwentyEightCharacterBounds` |
| `SEC-003` | Activation and reset tokens use cryptographically secure random bytes. Only the 32-byte SHA-256 hash is stored. | `PasswordResetIntegrationTest#resetTokenPersistsOnlyItsHashExpiresExclusivelyAndIsSingleUse` |
| `SEC-004` | Activation tokens expire after 24 hours, reset tokens after 30 minutes. Issuing a token invalidates the older one. | `PasswordResetIntegrationTest#resetTokenExpiresAtExactlyThirtyMinutes`, `AccountRecoveryIntegrationTest#resendInvalidatesPriorActivationTokenBeforeSendingFreshLink` |
| `SEC-005` | Login and password-reset responses are generic and never reveal whether an email exists, is pending, or is locked. | `PasswordResetIntegrationTest#forgotPostUsesOneGenericResponseForActivePendingLockedAndUnknownAccounts`, `PasswordRecoveryWebIntegrationTest` |
| `SEC-006` | Login throttling keys on normalized email plus source IP. Five failures in 15 minutes create a 15-minute throttle; a success clears it. | `LoginThrottleTest#fiveFailuresWithinFifteenMinutesThrottleTheSixthAttempt` |
| `SEC-008` | Redirect targets are allow-listed and local. State-changing endpoints reject open redirects and user-supplied class names. | `NotificationActionContractTest#acceptsOnlySafeRelativeApplicationRoutes`, `OriginEnforcementFilterTest#mismatchedOriginIsRejectedForStateChangingRequests` |
| `SEC-009` | Error pages never expose stack traces, SQL, secrets, internal IDs from unauthorized records, or existence signals. | `SharedErrorTemplateWebTest` |
| `SEC-010` | Production requires an HTTPS public base URL and explicit trusted-proxy configuration before it is considered ready. | `ProductionReadinessTest#productionOriginIsCanonicalAndRejectsBracketedIpv6Loopback` |
| `SEC-011` | Production responses carry HSTS, a restrictive content policy, frame denial, and referrer suppression. Session cookies are `Secure`, `HttpOnly`, `SameSite=Strict`. | referrer only, in `SecurityResponseIntegrationTest#authenticationAndActivationResponsesDoNotSendReferrers`; the rest is a gap |
| `SEC-012` | Forwarded headers are trusted only on an explicitly enabled and constrained proxy path. | `TrustedForwardedHeaderFilterTest#untrustedSocketWithForwardedHeadersIsRejectedBeforeHeaderAdaptation` |
| `SEC-014` | Production startup fails when the master key, public origin, datasource, or proxy policy is absent or unsafe. | `ProductionReadinessTest#unsafeProductionInputsFailWithoutEchoingSecrets` |

`SEC-001` is too broad for one test. `SecurityConfiguration` builds the filter
chain, and every `*WebTest` that asserts a denial exercises one slice of it. No
test asserts the rule as a whole, so treat the row as a description of intent
rather than a claim of coverage.

`SEC-011` is the sharpest gap on this page. `AC-SEC-008` fixes exact directive
values for `Strict-Transport-Security`, the content policy, frame ancestors, and
the cookie attributes, and no test reads a single one of them back. The headers
are configured in `SecurityConfiguration`; nothing proves they survive a change
to it.

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
| `ARC-001` | One server-rendered modular monolith on Java 25 and Spring Boot 4.1.0. | the build refuses to compile on another version; no test asserts the shape |
| `ARC-002` | Backend uses Maven, Spring MVC, Security, Data JPA, Bean Validation, Thymeleaf, Spring Mail, and Flyway. | `ReportingDependencyContractTest` covers the reporting libraries only |
| `ARC-003` | PostgreSQL 18.4 is the database family for production, development, and integration tests. PostgreSQL-specific rules are tested against PostgreSQL, never H2. | Testcontainers configuration in `TestcontainersConfiguration` |
| `ARC-004` | The UI toolchain pins Node 24 LTS and Tailwind CSS 4, uses `npm ci`, and commits the lockfile. Test-tool versions are not pinned by assertion; each run records the version it used. | the `Set up Node 24`, `npm ci`, and `Verify generated assets are committed` steps of `.gitea/workflows/verify.yml` |
| `ARC-005` | `LabtimesheetApplication` stays in the root package. Shared wiring lives in `config`. Business code groups under `feature.<name>` for `account`, `attendance`, `integration`, `notification`, `project`, `reporting`, and `task`, each adding only the layer subpackages it needs. Tests mirror those packages. | `LayerStructureTest`, `AttendanceLayerStructureTest` |
| `ARC-006` | Controllers bind validated DTOs and delegate to services; services use repositories and entities. A feature may call another feature's service contract and DTOs but never its repositories or entities. No business SQL. Flyway and schema verification are the only direct-SQL boundary. | `LayerStructureTest` |
| `ARC-007` | Flyway is the sole production schema authority. JPA schema generation is validation-only outside disposable tests. | `PlatformFoundationTest#flywayCreatesApprovedPostgresCatalog` |
| `ARC-008` | The reviewed design DDL is a baseline, adapted into Flyway migrations rather than executed directly. | `PlatformFoundationTest#flywayCreatesApprovedPostgresCatalog` |

### Delivery

| ID | Rule | Enforced by |
|---|---|---|
| `OPS-011` | A trusted repository-scoped runner verifies Maven tests, PostgreSQL and Flyway integration, and the frontend build before anything is published. | the job steps in `.gitea/workflows/verify.yml` |
| `OPS-013` | The pipeline stops at build and publish. It does not connect to an unprovisioned production host. | the deployment job is gated on `DEPLOY_ENABLED` |
| `OPS-014` | The SSH deployment job is a dormant template, active only on `main` and only when the repository variable enables it. | the same gate, plus `GOV-010` naming it as not yet active |

### Excluded by decision

`GOV-007` and `GOV-008` list what version 1 deliberately does not contain: no
SPA framework, JWT authentication, microservices, Redis, Kafka, generic workflow
engine, or persisted `Report` entity; no project-level days off, multiple Task
assignees, unconditional self-service Project joining, Task dependencies, epics,
sprints, or story points. Adding any of them needs a new ADR, not a pull request.

`GOV-015` adds the Task effort-planning boundary: no external Jira or Tempo
integration, no Jira mirroring, no Tempo accounts or synchronization, no
`SUBMITTED` Leader acceptance workflow, and no continuous replanning unrelated to
worked reassignment.

Weekly and Monthly report presets are **deferred, not excluded**. They sit
outside the v1 acceptance scope and may be built later, which needs a numbered
requirement and an acceptance scenario rather than an ADR. The distinction is
recorded because `GOV-015` once forbade them while the decision behind it only
postponed them.

`GOV-016` governs the documents themselves: every requirement has exactly one
canonical location, a change in business behavior updates that location, and a
new feature is documented in the same form as the features already there. A split
across separate documents is an organizational choice, never a goal, and no split
document overrides the canonical location of a rule.

---

## Layer 3 — Engineering standards

| ID | Rule | Bypass |
|---|---|---|
| `TST-001` | Every feature, fix, refactor, or behavior change follows strict RED, verified failure, minimal GREEN, verified narrow pass. | None in practice; see `TST-009` |
| `TST-002` | Production behavior written before its failing test is discarded and reimplemented from the failing test. | None |
| `TST-003` | Each test names the observable break it catches and derives expected values independently, never mirroring production code. | None |
| `TST-004` | Real components are used at the relevant boundary. Only slow or external dependencies such as SMTP and HolidayAPI are faked. | Documented reason |
| `TST-005` | Each test names, in its own source, the numbered requirements it protects. | None |
| `TST-006` | One test class covers one cohesive behavior, not one production class. Test packages mirror the feature packages they exercise. | None |
| `TST-007` | The trace records the requirement and scenario identifiers, the observable break, and the hand-derived expected result. | None |
| `TST-008` | Evidence is updated on the same branch as its tests and implementation. A Markdown claim never replaces executable CI evidence. | None |
| `TST-009` | Human prose and simple configuration do not receive artificial unit tests. Their evidence is the smallest executable validation. | This rule is itself the documented exception to `TST-001` |
| `TST-010` | A milestone is committed only when evidence is current, narrow and affected suites are green, and no unexplained error or warning remains. | None |
| `GOV-006` | A feature absent from the specification needs a new reviewed decision. Adjacent scope is never silently authorized. | None |
| `OPS-019` | Shared build, migration, security, navigation, and base-template files have one named owner at a time. | None |

`TST-008` is the rule that the errors in this document's own enforcement columns
violated: a Markdown claim that a test protects a rule is not evidence that it
does. Three rows named a class that exists and tests something else, which is
exactly the failure `TST-008` describes, committed by the document that indexes
it.

### Definition of done

**This is the canonical list.** It used to live in `plan.md` and be copied here,
and the copy had already drifted: it said `public methods` where the original said
`public/protected methods`, and it had dropped the Iteration 1 exception
altogether. That is `GOV-016` broken by the document that states it, so the list
moved here and the tracker now points at it.

A tracked item is `DONE` only when all of the following hold.

- The numbered requirement and its acceptance behavior are satisfied.
- The test existed and failed for the intended reason before production code.
- Focused and affected suites pass.
- Authorization and negative cases are covered where applicable.
- PostgreSQL-specific rules are tested against PostgreSQL, not H2.
- Concurrency, deadline, and history behavior has proportionate evidence.
- UI behavior uses server-side authorization and shared fragments.
- Documentation and evidence paths are recorded in the tracker.
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

**Must do.**

- State the plan, name the files it will change, say how to undo it, then stop and wait.
- When the specification does not answer something the work needs, list what is unclear, state the assumption that would be made, say what that assumption changes, and stop. Do not pick an interpretation quietly.
- Name the rule identifiers a change touches, so the reviewer can check the change against the rule rather than against the diff.
- Never read or print `.env`.

**Never read `.env`.** The committed `.env.example` files hold placeholders only
under `OPS-004`; the real file holds a database password and the AES-256 master
key.

This section exists because the rule was broken before it was written. During the
September 2026 documentation work an agent deleted a duplicated skills directory
while the maintainer was still asking what the directory was for. The deletion
was correct and the timing was not, and no written rule forbade it at the time.

## Amendment

`GOV-001` sets the authority order. When statements conflict, the highest
applicable authority wins and a lower-authority rule may not be revived against
it. In practice an amendment means: write an ADR under [`.sdd/rfcs/`](rfcs)
stating the decision, its rationale, and its consequences; update the
requirements specification; then update this index.

That process has been exercised once. The Admin Attendance report scope was
granted on 17 August 2026, withdrawn on 27 August, and restored on 30 August.
The reasoning is recorded in
[`.sdd/rfcs/ADR-002-attendance-report-scope.md`](rfcs/ADR-002-attendance-report-scope.md),
and the superseded wording was left in place rather than rewritten, which is
`GOV-005` applied to the document itself.

## Known enforcement gaps

Every row above was checked against the test it names on 11 September 2026. Three
named a class that exists and tests something else, and six more overstated how
much their test covers. Those rows were corrected; the gaps they were hiding are
listed here.

A constitution that claims enforcement it does not have is worse than one that
names its own gaps, and worse still when the claim survives because the class
name looks plausible.

| Rule | What is missing | What would close it |
|---|---|---|
| `SEC-011` | No test reads back any security header. `AC-SEC-008` fixes exact values for HSTS, the content policy, frame ancestors, and the cookie attributes, and none is asserted. | One web test that inspects the response headers against `AC-SEC-008`. |
| `SEC-001` | The rule is broader than any single test. Slices are covered by the denial assertions scattered through the web tests. | Accept it as an intent statement, or narrow it into rules that can each be asserted. |
| `GOV-004` | Relies on the two domains staying in separate features with no shared read path. | A test asserting that no reporting query joins attendance to work logs. |
| `GOV-011` | The cited test only canonicalizes a timezone alias before startup. Nothing asserts that a business date resolves against the applicable policy version's timezone. | A test that sets a JVM default different from the policy timezone and checks the resulting business date. |
| `GOV-014` | Relies on schema constraints in `V1__baseline.sql`. | A test asserting that no repository exposes a hard-delete method for accounts, Projects, memberships, or Tasks. |
| `ARC-001` | The build fails on the wrong Java version, which proves the version and not the architecture. | An architecture test asserting the module shape, alongside `LayerStructureTest`. |
| `ARC-002` | `ReportingDependencyContractTest` covers the reporting libraries only. | Extend it to the rest of the declared stack, or accept the narrower claim. |
| `OPS-016`, `OPS-017` | Both describe runner configuration. A repository cannot verify its own runner. | An operator confirms it outside this repository; nothing here can. |

### A defect found while checking these

`.gitea/workflows/container.yml` line 264 guards a rollback by requiring the
previous image tag to match `:sha-[0-9a-f]{40}`. This repository uses SHA-256
object names, so its commit identifiers are 64 characters and no legitimate tag
this pipeline produces can satisfy that pattern. The deployment job is dormant
behind `DEPLOY_ENABLED`, so the guard has never run. It is recorded here rather
than fixed, because it is a pipeline change and not a documentation one.

## Provenance

The three-layer format, the `LOCKED` status, and the amendment-by-consensus idea
come from Constitution-Driven Development as described in *Spec-Driven &
Agent-Driven Development* by LinhNDM, chapter 7, with the student-project
template in section 13.5. It is a methodology choice rather than an industry
standard; most projects distribute the same content across `CONTRIBUTING.md`,
architecture decision records, and CI configuration.

The rules themselves are not new. They were written by the project team across
Iterations 1 to 4 and already live in the requirements specification, the
project plan, and the enforcing tests. This document only gathers them and says
which ones have teeth.
