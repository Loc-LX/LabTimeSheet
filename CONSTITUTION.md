# Project Constitution

| Field | Value |
|---|---|
| Version | 1.0.0 |
| Status | `LOCKED` |
| Applies to | every developer, every AI agent, every pull request |
| Amendment | requires a new ADR under [`docs/adr/`](docs/adr/) and the `GOV-001` authority order |
| Full rule text | [`docs/requirements/requirements-specification.md`](docs/requirements/requirements-specification.md) |

This document is an index, not a copy. Each row names a rule that already exists
in the requirements specification, states how strictly it binds, and names the
test that enforces it. The full normative wording stays in one place so the two
documents cannot drift apart.

Three layers, from strictest to most negotiable:

| Layer | Meaning | Bypass |
|---|---|---|
| 1 | Never violated. A breach is a defect regardless of deadline. | None |
| 2 | Architectural boundary. | Approved ADR only |
| 3 | Engineering standard. | Documented reason in the evidence record |

---

## Layer 1 — Hard rules

### Domain invariants

| ID | Rule | Enforced by |
|---|---|---|
| `GOV-004` | Attendance time and Task work time are separate domains. Neither proves nor derives the other. | No single test. Upheld by keeping `attendance_records` and `task_work_logs` in separate features with no shared read path. |
| `GOV-005` | Historical business results must not change because an Admin later edits workdays, schedule, grace, quota, penalty, or the calendar. | `AttendancePersistenceIntegrationTest` |
| `GOV-011` | Business dates resolve in the attendance policy version's timezone. Persisted instants are `timestamptz` and treated as UTC. | `ApplicationTimeZoneIntegrationTest` |
| `GOV-012` | Server time is authoritative for check-in, checkout, submission, decision, activation, expiry, and lifecycle timestamps. Browser timestamps are never trusted. | `AttendanceServiceTest` |
| `GOV-013` | Mutable aggregate updates are transactional and use optimistic locking. Quota, daily work totals, bootstrap, and transfer workflows serialize further. | Covered per workflow in the concurrency evidence under `docs/tests/integration/`. |
| `GOV-014` | Historical records are retained behind restrictive foreign keys and lifecycle or soft-delete fields. Normal UI operations never physically delete accounts, Projects, memberships, or Tasks. | No single test. Enforced by schema constraints in `V1__baseline.sql`. |

### Security

| ID | Rule | Enforced by |
|---|---|---|
| `SEC-001` | Session authentication, server-side authorization, object ownership checks, CSRF protection, Bean Validation, and output escaping are always on. | `BootstrapIntegrationTest` |
| `SEC-002` | Passwords are 12 to 128 characters and use the delegating adaptive encoder. No composition rules. | `AccountActivationIntegrationTest` |
| `SEC-003` | Activation and reset tokens use cryptographically secure random bytes. Only the 32-byte SHA-256 hash is stored. | `AccountRecoveryIntegrationTest` |
| `SEC-004` | Activation tokens expire after 24 hours, reset tokens after 30 minutes. Issuing a token invalidates the older one. | `AccountRecoveryIntegrationTest` |
| `SEC-005` | Login and password-reset responses are generic and never reveal whether an email exists, is pending, or is locked. | `AccountRecoveryLockOrderIntegrationTest` |
| `SEC-006` | Login throttling keys on normalized email plus source IP. Five failures in 15 minutes create a 15-minute throttle; a success clears it. | `LoginThrottleTest` |
| `SEC-008` | Redirect targets are allow-listed and local. State-changing endpoints reject open redirects and user-supplied class names. | `SecurityResponseIntegrationTest` |
| `SEC-009` | Error pages never expose stack traces, SQL, secrets, internal IDs from unauthorized records, or existence signals. | `UserActionTokenCleanupIntegrationTest`, `SecurityResponseIntegrationTest` |
| `SEC-010` | Production requires an HTTPS public base URL and explicit trusted-proxy configuration before it is considered ready. | `ProductionReadinessTest` |
| `SEC-011` | Production enables HSTS, `Secure` and `HttpOnly` session cookies, `SameSite=Strict`, and strict origin checks. | `ProductionReadinessTest` |
| `SEC-012` | Forwarded headers are trusted only on an explicitly enabled and constrained proxy path. | `TrustedForwardedHeaderFilterTest` |
| `SEC-014` | Production startup fails when the master key, public origin, datasource, or proxy policy is absent or unsafe. | `ProductionReadinessTest` |

### Secrets

Secrets never enter the repository. SMTP and HolidayAPI credentials are
Admin-managed and encrypted with a deployment-provided master key. The tracked
`.env.example` holds placeholders only; the real `.env` is git-ignored.

---

## Layer 2 — Architectural constraints

| ID | Rule | Enforced by |
|---|---|---|
| `ARC-001` | One server-rendered modular monolith on Java 25 and Spring Boot 4.1.0. | `ApplicationTimeZoneIntegrationTest` boots the real context |
| `ARC-002` | Backend uses Maven, Spring MVC, Security, Data JPA, Bean Validation, Thymeleaf, Spring Mail, and Flyway. | `pom.xml`, `ReportingDependencyContractTest` |
| `ARC-003` | PostgreSQL 18.4 is the database family for production, development, and integration tests. PostgreSQL-specific rules are tested against PostgreSQL, never H2. | Testcontainers configuration in `TestcontainersConfiguration` |
| `ARC-004` | The UI toolchain pins Node 24 LTS and Tailwind CSS 4, uses `npm ci`, and commits the lockfile. | `UiContractWebTest` |
| `ARC-005` | `LabtimesheetApplication` stays in the root package. Shared wiring lives in `config`. Business code groups under `feature.<name>` for `account`, `attendance`, `integration`, `notification`, `project`, `reporting`, and `task`, each adding only the layer subpackages it needs. Tests mirror those packages. | `LayerStructureTest`, `AttendanceLayerStructureTest` |
| `ARC-006` | Controllers bind validated DTOs and delegate to services; services use repositories and entities. A feature may call another feature's service contract and DTOs but never its repositories or entities. No business SQL. Flyway and schema verification are the only direct-SQL boundary. | `LayerStructureTest` |
| `ARC-007` | Flyway is the sole production schema authority. JPA schema generation is validation-only outside disposable tests. | `ProjectPersistenceStructureTest` |
| `ARC-008` | The reviewed design DDL is a baseline, adapted into Flyway migrations rather than executed directly. | `PlatformFoundationTest` |

### Excluded by decision

`GOV-007` and `GOV-008` list what version 1 deliberately does not contain: no
SPA framework, JWT authentication, microservices, Redis, Kafka, generic workflow
engine, or persisted `Report` entity; no project-level days off, multiple Task
assignees, unconditional self-service Project joining, Task dependencies, epics,
sprints, or story points. Adding any of them needs a new ADR, not a pull request.

`GOV-015` adds the Task effort-planning boundary: no external Jira or Tempo
integration, no Jira mirroring, no Tempo accounts or synchronization, no
`SUBMITTED` Leader acceptance workflow, no Weekly or Monthly report presets, and
no continuous replanning unrelated to worked reassignment.

`GOV-016` governs the documents themselves. The requirements specification is the
system-wide layer. A later feature is not appended to it; it gets its own document
naming the specification as parent, inheriting without restating, recording only
what it adds, and never claiming precedence over it.

---

## Layer 3 — Engineering standards

| ID | Rule | Bypass |
|---|---|---|
| `TST-001` | Every feature, fix, refactor, or behavior change follows strict RED, verified failure, minimal GREEN, verified narrow pass. | None in practice; see `TST-009` |
| `TST-002` | Production behavior written before its failing test is discarded and reimplemented from the failing test. | None |
| `TST-003` | Each test names the observable break it catches and derives expected values independently, never mirroring production code. | None |
| `TST-004` | Real components are used at the relevant boundary. Only slow or external dependencies such as SMTP and HolidayAPI are faked. | Documented reason |
| `TST-005` | A tracked Markdown evidence file is created with the first failing test, under `docs/tests/<type>/`. | None |
| `TST-006` | Evidence directories are exactly `unit`, `integration`, `web`, and `e2e`. One file covers one feature, not one Java class. | None |
| `TST-007` | Evidence records requirement IDs, protected behavior, preconditions, the automated class and method, hand-derived expected results, and commands run. | None |
| `TST-009` | Human prose and simple configuration do not receive artificial unit tests. Their evidence is the smallest executable validation. | This rule is itself the documented exception to `TST-001` |
| `TST-010` | A milestone is committed only when evidence is current, narrow and affected suites are green, and no unexplained error or warning remains. | None |

### Definition of done

A tracked item is `DONE` only when all of the following hold. The list is
maintained in [`.agents/PROJECT_PLAN.md`](.agents/PROJECT_PLAN.md).

- The numbered requirement and its acceptance behavior are satisfied.
- The test existed and failed for the intended reason before production code.
- Focused and affected suites pass.
- Authorization and negative cases are covered where applicable.
- PostgreSQL-specific rules are tested against PostgreSQL, not H2.
- Concurrency, deadline, and history behavior has proportionate evidence.
- UI behavior uses server-side authorization and shared fragments.
- Documentation and evidence paths are recorded in the tracker.
- New or changed production types and public methods carry accurate Javadoc written during implementation.
- No unrelated files or another branch's ownership area were changed without coordination.
- The final branch head is green.
- Integration does not alter totals, state graphs, or historical meaning.

---

## Amendment

`GOV-001` sets the authority order. When statements conflict, the highest
applicable authority wins and a lower-authority rule may not be revived against
it. In practice an amendment means: write an ADR under [`docs/adr/`](docs/adr/)
stating the decision, its rationale, and its consequences; update the
requirements specification; then update this index.

That process has been exercised once. The Admin Attendance report scope was
granted on 17 August 2026, withdrawn on 27 August, and restored on 30 August.
The reasoning is recorded in
[`docs/adr/0002-admin-attendance-report-scope.md`](docs/adr/0002-admin-attendance-report-scope.md),
and the superseded wording was left in place rather than rewritten, which is
`GOV-005` applied to the document itself.

## Known enforcement gaps

Two Layer 1 rules have no dedicated automated test.

- `GOV-004` relies on the two domains staying in separate features with no shared read path. A test asserting that no reporting query joins attendance to work logs would close this.
- `GOV-014` relies on schema constraints in `V1__baseline.sql`. A test asserting that no repository exposes a hard-delete method for accounts, Projects, memberships, or Tasks would close this.

Listing them is deliberate. A constitution that claims enforcement it does not
have is worse than one that names its own gaps.

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
