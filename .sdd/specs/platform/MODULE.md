# Platform Module

<a id="platform-spec"></a>

**Version:** 1.11.0 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-23

Part of the Lab Timesheet specification. Rules every feature shares, including the
glossary and failure handling, are in [the platform spec](MODULE.md); the architecture, the
authorization model, the security controls and the domain model are its features
[Architecture](features/architecture/SPEC.md), [Authorization](features/authorization/SPEC.md),
[Security](features/security/SPEC.md) and [Data model](features/data-model/SPEC.md) (`D40`). Section numbers marked `§` are one
numbering shared by all the specs, so a reference such as §5.2 names the same section
wherever it appears.

This spec holds what every feature shares. It is not a feature in the code; the name keeps the
`feature-{name}` convention of the other specs so every spec is found and tagged the same way.

## 1. Context & Goal

| Field | Value |
|---|---|
| Document type | Requirements, domain design, database reference, and acceptance catalogue, split into eight specs |
| Audience | Maintainer, contributors, instructor/reviewer |
| Product language | English |
| Business timezone | `Asia/Ho_Chi_Minh` |
| Database baseline | PostgreSQL 18.4, 30 application tables |
| Companion DDL | `database-schema.sql`, not tracked in this repository |
| Review state | Approved rule baseline; later clarification gaps in §22.3 prevent approval of the affected plans. Each spec's version and changes are in its header and `CHANGELOG.md`. |
| Normative rules | 311 across the eight module trees |
| Acceptance scenarios | 167 across the eight module trees |

> **Companion files.** The companion `database-schema.sql` and the reference images
> under `assets/` are not tracked in this repository. Every image embed below is
> therefore written as a named reference rather than a link, so nothing renders as a
> broken image.
> The Flyway migrations under [`db/migration`](../../../src/main/resources/db/migration) are the schema's
> SQL; §19.4 draws the tables they create. Ask the document owner for the reference images if you need them.

### Parts within platform

These six planning scopes organize the shared contracts in this spec (`D33`). They are
not six new business modules or six independent release gates. Rules stay in the eight
standard SPEC sections; the labels below connect their scope, data, dependencies and
acceptance evidence. A feature consumes a platform contract in its own part, and does
not copy the contract into a second source. Progress belongs only in [plan.md](../../../plan.md).

| Part and outcome | Canonical material in this spec | Use cases and evidence |
|---|---|---|
| [F1 — Shared rules and architecture](MODULE.md#platform-f1): common terminology, time/history invariants and module boundaries | §1, §2 and §1.3: `GOV-001`–`GOV-016`; §3 in the [Architecture](features/architecture/SPEC.md) feature: `ARC-001`–`ARC-010` | No standalone actor workflow; `AC-GOV-001`–`AC-GOV-002`, `AC-ARC-001`–`AC-ARC-002`; exclusions below remain explicit |
| [F2 — Authorization and security](MODULE.md#platform-f2): authorize each capability from current stored context | §5 in the [Authorization](features/authorization/SPEC.md) feature: `AUTH-001`–`AUTH-003`, `AUTH-010`, `AUTH-012`; §13 in the [Security](features/security/SPEC.md) feature: `SEC-001`, `SEC-008`–`SEC-014` | Applied in every feature use case; `AC-AUTH-*` and `AC-SEC-*` in those two features |
| [F3 — Integration secrets and SMTP](MODULE.md#platform-f3): configure, test and activate SMTP without exposing secrets | §12.1: `INT-001`–`INT-008`, `INT-010` | `UC-19`; `AC-INT-001`–`AC-INT-002`, `AC-INT-004`–`AC-INT-005` |
| [F4 — Shared interface](MODULE.md#platform-f4): accessible shell, navigation, forms and history surfaces | §15, Appendix D and Appendix F; `UI-001`–`UI-019`, `ERR-001` | No extra use case; feature workflows consume these contracts; `AC-UI-001`–`AC-UI-005`, `AC-ERR-001` |
| [F5 — Operations](MODULE.md#platform-f5): reproducible local/runtime delivery and bounded failure recovery | §16 and §18: `OPS-001`–`OPS-021`; `ERR-004`–`ERR-005`, `ERR-007` | Operator/contributor scenarios `AC-OPS-001`–`AC-OPS-006`, `AC-ERR-004`–`AC-ERR-005`, `AC-ERR-007` |
| [F6 — Test evidence and data integrity](MODULE.md#platform-f6): verify requirements and shared persistence invariants | §17: `TST-001`–`TST-011`; `ERR-002`–`ERR-003`; §19 in the [Data model](features/data-model/SPEC.md) feature: `DB-001`, `DB-003`–`DB-008`, `DB-010` | `AC-TST-001`, `AC-DB-001`, `AC-DB-004`, `AC-ERR-002`–`AC-ERR-003`; feature-owned database scenarios stay in their feature specs |

#### Ownership and dependencies by part

| Part | Data authority | Dependencies and boundary checks |
|---|---|---|
| F1 | Defines common invariants; does not take ownership of feature history or policy tables | Calendar supplies policy/time facts; features retain their own history. `AC-ATT-001` in calendar checks frozen policy effects. `ARC-005`/`ARC-006` govern every part. |
| F2 | Uses actor and resource facts owned by identity, internship and the affected feature; adds no authorization-history table | The plan must specify who resolves scope and how the policy receives it without a platform → feature dependency. Exercise report reads with `RPT-005` and task transitions with `TSK-023`, as well as the permission matrix. |
| F3 | SMTP revisions belong to platform; HolidayAPI configuration remains calendar-owned | Shared encryption/redaction serves both. Notification/identity consume SMTP; activation races keep one active revision, and a failed test leaves the current one usable. `INT-009` stays in calendar. |
| F4 | Presentation only; no new domain tables or account theme preference | Navigation asks F2; screens consume feature data. `UI-019` names screens across modules: each screen is planned with its owning feature, while shell, accessibility and message contracts stay here. |
| F5 | Deployment, secrets and recovery configuration; no transfer of domain-table ownership | F3 external outages must not stop local attendance/report reads (`AC-ERR-005`); failed migrations must fail readiness (`AC-ERR-007`). Worker retry checks include notification deduplication. |
| F6 | Shared constraint conventions and the §19 inventory; each feature still owns its rows and migrations' behavior | Plans tie each changed constraint to the owning feature's scenario and PostgreSQL evidence. TDD and integrity checks accompany each part; they are not a final phase after implementation. |

The part labels describe requirement responsibility, not a service call graph. The technical
plan must preserve the acyclic dependencies of `ARC-005`, including the interface exception
in `ARC-006`. Approval of a part needs its cross-part checks, not just its local scenario count.

### How to read a rule

Every rule that describes system behavior is written in EARS notation, which
fixes the shape of the sentence so that the triggering condition cannot be left
implicit.

| Opening | Meaning |
|---|---|
| `THE system SHALL …` | always true, no trigger |
| `WHEN <event>, THE system SHALL …` | triggered by an event |
| `WHILE <state>, THE system SHALL …` | holds continuously while a state holds |
| `WHERE <condition>, THE system SHALL …` | applies under a condition, including an error condition |

The point of the form is what it prevents. "Export failure shall return an
error" can be written and approved without anyone deciding what the error is;
`WHERE report generation fails, THE system SHALL return an error` forces the
sentence to name it.

**Thirty rules are deliberately not in EARS**, because they bind people
rather than the system and no `THE system SHALL` sentence would be true of them:

| Rules | What they bind |
|---|---|
| `GOV-001`, `GOV-002`, `GOV-003`, `GOV-006`, `GOV-016` | how requirements are decided, named, and changed |
| `ARC-008`, `ARC-009`, `ACC-004`, `OPS-010` | a design baseline, a migration discipline, an operational instruction, a recovery procedure |
| `AUTH-010`, `TSK-017` | pointers to the rule that actually decides, kept so a reader arrives there |
| `OPS-001`–`OPS-004` | the development environment a contributor sets up |
| `OPS-019` | file ownership and branch naming |
| `OPS-018`, `OPS-020`, `OPS-021` | nothing: withdrawn, and kept so that their identifiers are never reused |
| `TST-001`–`TST-011` | the test-driven workflow a contributor follows |

Forcing those into the notation would make the document look uniform and say
something false.

<a id="platform-f1"></a>

### §1. Authority, purpose, and scope

**Part F1.**

#### §1.1 Decision authority

When statements conflict, use this precedence from highest to lowest:

1. [`.sdd/constitution.md`](../../constitution.md), for what it states canonically: each indexed rule's layer and exception, the standing deviations, the definition of done, and the AI agent policy. The wording of a rule is in its spec.
2. The feature specs under `.sdd/specs/`, where each rule has its one canonical location (`GOV-016`).
3. `AGENTS.md`, `CONTRIBUTING.md`, and `CLAUDE.md`.
4. Code and tests. Where they disagree with a spec, the code changes.

[`.sdd/decisions.md`](../../decisions.md) and the ADRs under `.sdd/rfcs/` record why a rule reads as it does; they do not outrank the spec. Where one is newer than the spec it concerns, the spec is stale and is corrected. The maintainer decides a change under the constitution's Amendment section. The instructor reviews those decisions and is not a gate on them (`D26`): a revision they ask for arrives as a new decision that supersedes the one it replaces.

| ID | Requirement |
|---|---|
| GOV-001 | Implementers shall use the authority order above and shall not revive a lower-authority rule that conflicts with an approved higher-authority decision. |
| GOV-002 | The term **Project** replaces the obsolete term **Group** throughout code, schema, UI, and documentation. |
| GOV-003 | v1 shall be a working attendance and project/task-management system, not a prototype or static demonstration. |
| GOV-004 | THE system SHALL hold attendance time and Task work time as separate domains, and SHALL NOT derive, prove, or update either from the other. |
| GOV-005 | WHEN an Admin changes global workdays, scheduled start or end, check-in grace, checkout grace, monthly leave quota, violation penalty, or calendar configuration, THE system SHALL leave every previously computed attendance, leave, and compliance result unchanged. |
| GOV-006 | Features not specified here require a new reviewed decision; this draft does not silently authorize adjacent scope. |
| GOV-016 | Every requirement shall have exactly one canonical location, and a change in business behavior shall update that location. A new feature shall be documented in the same form as the features already documented: numbered rules under an applicable prefix in the spec of the feature it belongs to, and acceptance scenarios in section 7 of that same spec. Splitting the specification across separate documents is an organizational choice made when it helps, never a goal, and no separate document shall override the canonical location of a rule. |

#### §1.2 Product objective

The system supports a university laboratory or internship program in four connected areas:

- account and internship administration;
- global attendance, leave, and missed-checkout correction;
- Mentor-owned projects with contextual Intern leadership and single-assignee tasks;
- authorized dashboards and consistent HTML, Excel, and PDF reports.


### Feature index

Read this shared contract together with the relevant feature SPEC. Rules are canonical
in exactly one of these documents; references do not create copies. Cross-feature use
cases, shared data constraints and unresolved decisions remain here (`D34`).

| Feature | Operations |
|---|---|
| [SMTP configuration](features/smtp-configuration/SPEC.md) | Save draft; Test and activate |
| [Shared interface](features/shared-interface/SPEC.md) | Shell, navigation and theme; Accessible components and charts; Role-oriented screens |
| [Architecture](features/architecture/SPEC.md) | Pin the stack and toolchain; Keep the module structure; Own the schema through Flyway; Bound the work per request |
| [Authorization](features/authorization/SPEC.md) | Authorize each request; Decide through one policy |
| [Security](features/security/SPEC.md) | Keep controls on in every profile; Harden the production profile |
| [Data model](features/data-model/SPEC.md) | Define the physical schema; Guard integrity in transactions |

The earlier A/P/F labels remain navigation aliases for the same scopes, not extra features.

## 2. Actors & Roles

Primary actors named by this spec's use cases:

- **UC-19 — Configure SMTP:** Admin

### §5. Authorization model

**Part F2.** Canonical in the [Authorization](features/authorization/SPEC.md#5-authorization-model) feature: `AUTH-001`–`AUTH-003`, `AUTH-010`, `AUTH-012` and the §5.2 permission matrix.

## 3. Functional Requirements

### §2. Terminology and system-wide rules

**Part F1.**

| Term | Meaning |
|---|---|
| Global role | Exactly one immutable account role: `ADMIN`, `MENTOR`, or `INTERN`. |
| Project Leader | An Intern with the current leadership term for one Project; not a global role. |
| Active member | A project membership whose `left_at` is null and whose Intern is eligible to participate. |
| Project invitation | A current Leader's non-expiring invitation that only its authenticated intended Intern may accept or decline. |
| Membership exit request | A Leader's request to remove another member or a member's request to leave. Membership remains open until the owning Mentor approves it, while unfinished Tasks may be redistributed beforehand. |
| Pending exit target | The current member named by a pending exit request. Existing rights remain, but the target cannot receive newly created/reassigned Tasks or create a self-Task until the request is cancelled, rejected, superseded, or approved. |
| Project History | The authorized read-only Project view assembled from retained memberships, leadership terms, invitations, exit requests, completed/soft-deleted Tasks, comments, work logs, and attribution already stored by the approved schema. |
| Task creator | The membership that created a Task; creator authority is narrower than current-Leader authority and is retained historically. |
| Current assignee | The active Project member referenced by a Task's single assignee field. |
| Global day off | An Admin-authorized calendar date with `is_day_off=true`; it affects attendance and leave for all active Interns. |
| Eligible workday | A configured policy workday within the Intern's applicable internship interval that is not a global day off. |
| Expected workday | An eligible workday not covered by approved leave. |
| Raw checkout | The server timestamp recorded by normal checkout; it is never overwritten by correction. |
| Effective checkout | Raw checkout when present, otherwise an approved correction's requested checkout. |
| Task work log | A dated number of minutes spent on a Task; it is independent from attendance. |
| Task estimate | The planned effort in minutes for one whole Task. It belongs to the Task rather than to an assignee, and is independent of attendance, calendar duration, and elapsed time. It is the Task's baseline: once work is retained it never changes. |
| Actual Task effort | The lifetime sum of retained work-log minutes for one Task across every author and assignment. It does not reset when the Task is reassigned. |
| Task effort variance | Current Work minus Task estimate, a signed planning difference. It is undefined for an unestimated Task, and for an unfinished Task with no Remaining effort forecast. |
| Remaining effort forecast | A current Leader's dated prediction of the additional effort needed to finish an unfinished Task, recorded at a reassignment or whenever that prediction changes. It never replaces the estimate. |
| Current Remaining effort | Zero for a `DONE` Task; otherwise the latest effective Remaining effort forecast, the most recent one no correction has superseded, less the Actual Task effort added since that forecast was recorded, never below zero. |
| Current Work | Actual Task effort plus current Remaining effort. |
| Attendance period | One Intern's attendance for one calendar month. It finalizes at 23:59 on the fifth day of the next month once no request affecting it is pending or overdue, and after that changes only inside a range reopened when an Admin approves a reopen request (`ATT-019`–`ATT-024`). |
| Overdue request | A leave, correction, or attendance exception request submitted in time whose approver missed the decision deadline. It is neither approved nor rejected and is never held against the Intern. |
| Attendance adjustment request | A missed-checkout correction or an attendance exception request. Both are raised after the attendance event, both are decided by the responsible Mentor, and both share one submission and decision window (`COR-003`, `COR-004`, `EXC-002`, `EXC-003`). What differs is the evidence each carries. |
| Decision amendment | A new decision entry that changes the content of the current decision on a leave, correction, or attendance exception request, such as its note or the dates an approved leave still covers. It never overwrites the earlier entry (`ATT-024`). |
| Decision reversal | A new decision entry that reverses the outcome of the previous decision, such as excused to unexcused. It never returns a request to pending, and a leave decision is never reversed (`ATT-024`, `LEV-011`). |
| Report date | A local business date used to select dated Task work logs for reporting. Attendance policy and calendar context may describe the date but never remove otherwise valid Task work from the report. |
| Daily Project Work Report | An authorized view of retained Task work logs for one Report date, shown by Task, with each Task's planning values once, and by work-log author, with minutes only. It may show attendance context and each Task's current status, but asserts neither attendance nor that a Task was completed on that date. |

#### §2.1 Words this product does not use

Six terms above have near-synonyms that carry a different meaning. Using one of
them in interface copy, a report label, or a rule turns a planning measurement
into a judgement about a person, which is the one thing `GOV-004` and the
separation of attendance from Task work exist to prevent.

| Term | Never call it |
|---|---|
| Task estimate | estimated hours, assignee estimate, time budget |
| Actual Task effort | current-assignee effort, attendance duration, selected-period effort |
| Task effort variance | efficiency score, productivity score, remaining effort |
| Remaining effort forecast | replacement estimate, assignee estimate, revised Task estimate |
| Report date | Task workday, reporting workday, attendance date |
| Daily Project Work Report | daily completion report, daily attendance report, daily productivity report |

| ID | Requirement |
|---|---|
| GOV-011 | THE system SHALL evaluate business dates and schedule boundaries in the timezone of the applicable attendance policy version, and SHALL persist instants as `timestamptz` treated as UTC. |
| GOV-012 | WHEN a check-in, checkout, submission, decision, activation, expiry, or lifecycle transition occurs, THE system SHALL record server time as the event time. THE system SHALL NOT accept a browser-supplied timestamp as event time. |
| GOV-013 | THE system SHALL perform every mutable aggregate update inside a transaction with optimistic locking. WHERE the update affects leave quota, a daily work total, bootstrap, or a Task transfer, THE system SHALL additionally serialize on the narrow affected record. |
| GOV-014 | THE system SHALL retain historical records behind restrictive foreign keys and lifecycle or soft-delete fields. THE system SHALL NOT physically delete an account, Project, membership, Task, attendance row, leave request, correction, comment, work log, or notification through a normal interface operation, except the deletion of a `PLANNED` Project and the rows it owns that `PRJ-002` permits. |

<a id="platform-f3"></a>

### §12. Integrations and notifications

**Part F3.**

#### §12.1 Secret-bearing integration configuration

`INT-009`, which applies these rules to HolidayAPI, is in [the calendar spec](../calendar/MODULE.md).

| ID | Requirement |
|---|---|
| INT-001 | THE system SHALL administer SMTP and HolidayAPI settings through the application. THE system SHALL NOT take SMTP configuration from a deployment environment variable, and an operator SHALL NOT configure either by editing the database directly. |
| INT-002 | WHILE running under the production profile, THE system SHALL require a deployment-provided 256-bit application master key. WHILE running under a development or test profile, THE system SHALL take an explicit non-production key from Spring configuration. |
| INT-003 | THE system SHALL store an SMTP password or HolidayAPI key only as AES-256-GCM ciphertext with a fresh 96-bit nonce and key-version metadata. THE system SHALL NOT store the master key in PostgreSQL. |
| INT-004 | THE system SHALL NOT redisplay a secret after it is submitted, and SHALL require a new value to replace a saved one. THE system SHALL omit ciphertext, nonce, passwords, API keys, tokens, and master-key material from every Configuration History view. |
| INT-005 | THE system SHALL NOT place a raw integration secret or authentication token in a log, exception message, rendered page, export, notification body, pipeline artifact, or database diagnostic view. |
| INT-010 | THE system SHALL NOT provide master-key rotation or external secret-store integration in v1. THE system SHALL carry key-version metadata in every cipher envelope so that an operator-led migration remains possible later. |

Feature contracts: [SMTP configuration](features/smtp-configuration/SPEC.md).

#### State transitions: SMTP revision

Canonical workflow: [feature contract](features/smtp-configuration/SPEC.md).

### Use cases

#### UC-19 — Configure SMTP

Canonical workflow: [feature contract](features/smtp-configuration/SPEC.md).

## 4. Non-functional Requirements

### §3. Architecture and runtime

**Part F1.** Canonical in the [Architecture](features/architecture/SPEC.md#3-architecture-and-runtime) feature: `ARC-001`–`ARC-010`.

<a id="platform-f2"></a>

### §13. Authentication and security

**Part F2.** Canonical in the [Security](features/security/SPEC.md#13-authentication-and-security) feature: `SEC-001` and `SEC-008`–`SEC-014`. `SEC-002`–`SEC-007` and `SEC-015` concern accounts and are in [the identity spec](../identity/MODULE.md).

<a id="platform-f4"></a>

### §15. User interface and accessibility

**Part F4.**

#### §15.1 Normative visual references

The following supplied screenshots are visual-direction references. Their example branding/content and surrounding documentation-site chrome are not product requirements.

> **Reference image not tracked here:** `ui-reference-light.png` — Light dashboard and sidebar reference. Held in the documentation repository; ask the document owner for the image.

> **Reference image not tracked here:** `ui-reference-dark-shell.png` — Dark sidebar/shell reference. Held in the documentation repository; ask the document owner for the image.

> **Reference image not tracked here:** `ui-reference-dark-dashboard.png` — Dark dashboard reference. Held in the documentation repository; ask the document owner for the image.

Feature contracts: [Shared interface](features/shared-interface/SPEC.md).

Primary UI dependency references:

- [Lucide static assets](https://lucide.dev/guide/static)
- [Lucide 1.27.0 release](https://github.com/lucide-icons/lucide/releases/tag/1.27.0)
- [Chart.js 4.5.1 release](https://github.com/chartjs/Chart.js/releases/tag/v4.5.1)
- [Chart.js accessibility guidance](https://www.chartjs.org/docs/latest/general/accessibility.html)
- [WCAG 2.2 contrast minimum](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html)
- [WCAG 2.2 target size minimum](https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html)

#### §15.2 Role-oriented page map

| Role/context | Required pages |
|---|---|
| Bootstrap | First Admin; optional SMTP configuration/test; five-step defer acknowledgement; completion. |
| Admin | Account/configuration-only Dashboard (active accounts, pending activations, active internships, and system/configuration guidance); users/internships; SMTP and History; HolidayAPI and History; policy versions and History; global calendar/import and History; all-Project overview and Project History; system status; notifications. Read-only navigation, HTML pages, and XLSX/PDF exports for the Attendance report per `RPT-004`, the Project/Task report per `RPT-005`, and the Daily Project Work Report per `RPT-011`. |
| Mentor | Dashboard; owned Projects; direct membership/leadership; pending membership-exit decisions/readiness; Project/task progress and History; comments; global leave queue; correction queue; authorized Intern attendance; notifications. |
| Intern | Dashboard/check-in; own attendance; correction requests; leave; memberships; Project invitations; own leave requests; authorized Projects and History; assigned/self-created Tasks; comments; work logs; notifications; profile/security. |
| Leader context | All Intern pages plus invitation issue/revoke, removal requests, persistent exit-readiness warnings, repeated multi-Task/one-recipient transfer batches, Task creation for eligible active members, assignment/edit/delete, detailed Project/member progress inside currently led Projects, and Daily Project Work Report navigation for each currently-led `PLANNED` or `ACTIVE` Project. |
| Completed Intern | Read-only own history, notifications, profile, password/session security. |

### Appendix D. Screen inventory

Every row is a route that exists in a controller. Routes are named by their paths, such as
`/admin/accounts` and `/leave`, so a reader can find each one in the source.

The **Filter chain** column is what `SecurityConfiguration` admits before a
controller runs. It is coarse on purpose. Whether a particular Mentor may open a
particular Project is decided inside the transaction from stored context, never
from the URL, so a row admitting `authenticated` is not a claim that every signed
in user sees data.

#### D.1 Reachable without signing in

| Route | Feature | Renders |
|---|---|---|
| `/login` | account | sign-in form |
| `/forgot-password` | account | recovery request form |
| `/reset-password` | account | new-password form |
| `/activate` | account | activation landing |
| `/bootstrap`, `/bootstrap/**` | account | first-Admin installation flow, closed permanently after it completes |
| `/error` | shared | the shared error shell, which discloses nothing about the record that failed |
| `/assets/**` | shared | compiled stylesheet and icon sprite |
| `/actuator/health`, `/actuator/health/**` | platform | liveness and readiness |

#### D.2 Admin only

`SecurityConfiguration` gates the whole `/admin/**` prefix on the `ADMIN` role.

| Route | Feature | Renders |
|---|---|---|
| `/admin/accounts` | account | account directory with server-side search and role filter |
| `/admin/accounts/new` | account | create an Admin, Mentor, or Intern |
| `/admin/accounts/{targetUserId}` | account | one account with its lifecycle actions |
| `/admin/accounts/{targetUserId}/edit` | account | controlled identity correction |
| `/admin/attendance-policies` | attendance | effective-dated policy versions and their history |
| `/admin/settings` | reporting | configuration landing with account and internship counts |
| `/admin/smtp` | integration | SMTP draft, connection test, activation |
| `/admin/smtp/defer` | integration | the deferral path used during installation |

#### D.3 Reports, gated by role at the filter chain

Two report families are separated in the filter chain rather than only in a
service, so a guessed URL cannot cross the boundary.

| Route | Filter chain admits | Rules |
|---|---|---|
| `/reports/attendance`, `/reports/attendance.xlsx`, `/reports/attendance.pdf` | `ADMIN`, `MENTOR`, `INTERN` | `RPT-004`, and `ADR-002` for why Admin is present |
| `/reports/project-tasks`, `/reports/project-tasks.xlsx`, `/reports/project-tasks.pdf` | `MENTOR`, `INTERN`, and explicitly not `ADMIN` | `RPT-005`, `AUTH-010` |
| `/reports/daily`, `/reports/daily.xlsx`, `/reports/daily.pdf` | any signed-in user; scope resolved in the service | `RPT-011` through `RPT-013` |

The Daily report is deliberately not in the filter chain. Its audience is the
owning Mentor and whoever currently leads an eligible Project, and current
leadership is a row in `project_leadership_terms` rather than a role, so it
cannot be expressed as a URL rule.

#### D.4 Signed in, scope resolved from stored context

| Route | Feature | Renders |
|---|---|---|
| `/` | account | redirect to the right landing page for the account |
| `/dashboard` | reporting | role-aware dashboard |
| `/notifications` | notification | own inbox only |
| `/projects` | project | the Projects this account may see |
| `/projects/new` | project | create a Project, owning Mentor only |
| `/projects/{projectId}` | project | Project detail |
| `/projects/{projectId}/members` | project | membership, with direct add and removal for the owning Mentor |
| `/projects/{projectId}/leadership` | project | leadership terms and reassignment |
| `/projects/{projectId}/invitations/{invitationId}` | project | one invitation, answerable only by its intended Intern |
| `/projects/invitations` | project | invitations addressed to this account |
| `/projects/{projectId}/workflows` | project | exit requests and Task redistribution |
| `/projects/{projectId}/history` | project | retained Project history |
| `/projects/{projectId}/tasks` | task | Task list for the Project |
| `/projects/{projectId}/tasks/new` | task | create a Task, self-assigned or Leader-assigned |
| `/projects/{projectId}/tasks/{taskId}` | task | Task detail, comments, work logs, estimates |
| `/attendance` | attendance | own check-in and checkout |
| `/attendance/requests` | attendance | Mentor decision queues |
| `/attendance/leave`, `/attendance/leave/{requestId}` | attendance | own leave, and Mentor decisions |
| `/attendance/corrections`, `/attendance/corrections/{correctionId}` | attendance | own corrections, and Mentor decisions |
| `/attendance/calendar` | attendance | global calendar and Vietnam holiday import, Admin actions guarded in the service |
| `/attendance/interns/{internId}` | attendance | one Intern's attendance, for authorized readers |

#### D.5 What this appendix does not claim

It lists forty-five `GET` routes. It does not list the `POST` endpoints that
mutate state, and it is not a UI specification: field labels, table columns, and
secondary actions come from the numbered rules in sections 4 through 15, not from
here.

A row proves that a path exists and which coarse gate it sits behind. It proves
nothing about whether a given account may see a given record.

<a id="platform-f5"></a>

### §16. Development, containers, and delivery

**Part F5.**

#### §16.1 Spring profiles and local development

| ID | Requirement |
|---|---|
| OPS-001 | `dev` shall support running Spring Boot directly from an IDE. The IDE run configuration shall pass Spring properties through environment variables, including dev profile, datasource, and application encryption key. |
| OPS-002 | The recommended local loop shall start PostgreSQL 18.4 and Mailpit through development Compose while the IDE runs the application. SMTP `NONE` shall be allowed only for dev/test Mailpit. |
| OPS-003 | `test` shall use a PostgreSQL Testcontainer and explicit test-only cryptographic configuration. Automated tests shall not depend on a developer's database, SMTP server, HolidayAPI key, or production settings. |
| OPS-004 | Committed examples shall contain placeholders only. Real `.env` files, IDE-private run configurations, master keys, database passwords, SMTP credentials, HolidayAPI keys, activation links, and reset links shall remain untracked. |

#### §16.2 Production container topologies

| ID | Requirement |
|---|---|
| OPS-005 | THE container build SHALL compile the frontend assets and the Spring Boot artifact in separate stages and SHALL run the result on a minimal Java 25 runtime as a non-root user. |
| OPS-006 | THE application image SHALL expose liveness and readiness health endpoints. THE system SHALL report ready only WHILE the datasource is reachable and the Flyway migration set has completed, and SHALL NOT require SMTP or HolidayAPI availability to report ready. |
| OPS-007 | THE recommended production Compose file SHALL run the application together with a pinned PostgreSQL 18.4 service, with health-gated startup and a persistent named volume. |
| OPS-008 | THE same application image SHALL run against an externally managed PostgreSQL database, taking the datasource URL, username, and password from the environment, without starting a bundled database. |
| OPS-009 | THE production configuration SHALL carry the datasource, application master key, public base URL and origin, and an explicit proxy policy. SMTP and HolidayAPI credentials SHALL remain Admin-console configuration rather than deployment configuration. |
| OPS-010 | Database backup, restore, and PostgreSQL minor-upgrade procedures shall preserve the named volume or external database. Container replacement shall never be treated as a database backup. |

#### §16.3 Gitea Actions

| ID | Requirement |
|---|---|
| OPS-011 | THE delivery pipeline SHALL verify Maven tests, PostgreSQL and Flyway integration, frontend assets, and workflow contracts on a trusted repository-scoped runner for every pull request and push. THE container workflow SHALL run only on manual dispatch or a push to `main`, and its own verification job SHALL succeed before any image is built. |
| OPS-012 | THE delivery pipeline SHALL publish an image to the registry only from `main`, and every publication SHALL carry the immutable commit SHA tag. A moving `main` tag MAY be published only as a convenience alias. |
| OPS-013 | THE delivery pipeline SHALL stop at build and publish, and SHALL NOT connect to an unprovisioned production host. |
| OPS-014 | THE SSH deployment job SHALL remain a dormant template, and SHALL run only on `main`, only WHILE the repository variable `DEPLOY_ENABLED` equals `true`, and only WHERE the required host, user, private-key, and known-host secrets exist. |
| OPS-015 | WHEN the deployment template is eventually enabled, it SHALL pull the selected immutable SHA image, execute the remote Compose rollout, wait for health, and retain the previous SHA for rollback. |
| OPS-016 | THE workflow SHALL take variables and secrets from the Gitea `vars` and `secrets` contexts, and SHALL NOT rely on `jobs.<job_id>.environment` as an approval boundary, because Gitea currently ignores that syntax. |
| OPS-017 | THE runner SHALL expose a Docker socket only to trusted repositories and operators, and untrusted fork code SHALL NOT receive publication or deployment secrets. |

Gitea references: [variables](https://docs.gitea.com/1.24/usage/actions/actions-variables), [secrets](https://docs.gitea.com/next/usage/actions/secrets), [workflow differences](https://docs.gitea.com/usage/actions/comparison), and [runner security](https://docs.gitea.com/1.24/usage/actions/act-runner).

<a id="platform-f6"></a>

### §17. Development process and test evidence

**Part F6.**

| ID | Requirement |
|---|---|
| TST-001 | Every feature, bug fix, refactor, or behavior change shall follow strict RED → verify expected failure → minimal GREEN → verify narrow and affected suites → refactor while green. |
| TST-002 | Production behavior written before its failing test shall be discarded and reimplemented from the failing test; tests written only after implementation do not satisfy TDD. |
| TST-003 | Each test shall name the observable production break it catches and derive expected values independently. Tests shall not merely mirror private implementation or assert that a mock was called. |
| TST-004 | Real components shall be used at the relevant boundary. Mock or fake only slow/external dependencies such as SMTP or HolidayAPI, with complete realistic response shapes. |
| TST-005 | Each automated test shall name, in its own source, the numbered requirements it protects. The trace shall live with the test so that it survives a rename and can be read back mechanically. |
| TST-006 | One test class shall cover one cohesive behavior rather than one production class or one entire test category. Test packages shall mirror the feature packages they exercise. |
| TST-007 | The trace shall record the requirement and scenario identifiers, the observable production break the test catches, and the hand-derived expected result. |
| TST-008 | A verification run shall record its own commands, results, and resolved tool versions. A written claim shall never replace an executable run. |
| TST-009 | Human prose and simple configuration shall not receive artificial unit tests. Their evidence shall be the smallest executable validation, such as migration replay, `docker compose config`, workflow validation, or container health smoke test. |
| TST-010 | A medium milestone may be committed only when its evidence is current, narrow and affected suites are green, and no unexplained error or warning remains. |
| TST-011 | A test assertion is never weakened or deleted to make a test pass. A failing assertion is either a defect in the code or a decision recorded before the assertion changes. |

Trace convention. The rules a test protects are named in the test source, so the
trace moves with the class and can be read back mechanically:

```java
/**
 * Protects {@code ATT-009}, {@code ATT-010}.
 *
 * <p>Under default policy, 09:00:00 is on time and 09:00:00.001 is late;
 * checkout through 16:00:00 succeeds and the first later instant is rejected.
 */
```

This replaced a convention of one Markdown file per feature under `docs/tests/`.
The reasoning, and the measurements behind it, are in
[`rfcs/ADR-004-test-evidence-moves-into-the-test.md`](../../rfcs/ADR-004-test-evidence-moves-into-the-test.md).

## 5. Data

### §19. Domain model

**Part F6.** Canonical in the [Data model](features/data-model/SPEC.md#19-domain-model) feature: the conceptual ERD, the physical table inventory, the integrity rules `DB-001`, `DB-003`–`DB-008` and `DB-010`, and the physical diagram (§19.1–§19.4). A `DB-*` rule owned by a business module stays in that module's spec.

## 6. Error Handling

### §21. Failure handling and observable behavior

`ERR-006`, which concerns report exports, is in [the reporting spec](../reporting/MODULE.md).

| ID | Requirement |
|---|---|
| ERR-001 | WHERE a submitted form fails validation, THE system SHALL re-render that same form with the submitted values retained, SHALL mark every invalid field, and SHALL commit no part of the requested change. |
| ERR-002 | WHERE an optimistic-lock conflict occurs, THE system SHALL return a clear stale-data response inviting reload, and SHALL NOT silently overwrite a concurrent Admin, Mentor, or Leader decision. |
| ERR-003 | THE system SHALL execute deadline guards, lifecycle guards, and authorization inside the same transaction as the mutation they protect, so that no decision can be made on state that changes before the write. |
| ERR-004 | THE system SHALL make every scheduled worker idempotent and SHALL bound the batch it processes. WHERE a worker runs late or runs twice, THE system SHALL NOT duplicate a state transition or a notification. |
| ERR-005 | WHERE HolidayAPI or ordinary SMTP delivery fails, THE system SHALL keep local attendance and Project data available. WHERE the action depends on identity mail, THE system SHALL keep it blocked or explicitly failed as specified, because it cannot complete safely without delivery. |
| ERR-007 | WHERE a database migration fails, THE system SHALL fail readiness and SHALL NOT serve requests against a partially migrated schema. |

Outside this catalogue, `INT-007` in section 3 also names a rejection.

### Appendix F. Interface message families

What follows is not a rule set. It is guidance for whoever writes interface copy,
stating what each family of message has to communicate. The behavior behind these
messages is normative and lives in the `ERR` rules and in each feature section;
the wording is not.

#### Message families

| Message family | Example required information |
|---|---|
| Authorization denial | The action is unavailable without disclosing whether an unauthorized identifier exists. |
| Optimistic conflict | The record changed since it was opened; reload current state before deciding again. |
| SMTP restricted | State which onboarding/recovery action is blocked and link Admin to SMTP configuration. |
| Integration unavailable | Explain that manual calendar configuration remains available. |
| Deadline closed | Show the authoritative local deadline and the request's current state. |
| Period finalized | State that the attendance period is finalized and that a change needs a reopen request with a reason (`ATT-021`, `ATT-022`). |
| Quota/overlap rejection | Identify month-level counted days or the conflicting range without exposing another Intern’s data. |
| Membership removal guard | Identify the unfinished Task count, state that worked unfinished Tasks must be transferred with a forecast before direct removal, and that the rest move to the current Leader. |
| Invitation conflict | Explain that eligibility, leadership, or membership changed and reload current state without leaking another user’s protected details. |
| Membership-exit decision | Show requester, target, reason, the remaining unfinished Task count, whether a replacement Leader is still required, and whether the request is ready for approval. |
| Project completion guard | Identify remaining non-deleted Tasks not in DONE. |
| No-data metric | Render `N/A` and explain the zero denominator; do not render a misleading 0%. |
| Successful mutation | Confirm the resulting state and the next available action without implying email delivery unless it succeeded. |

## 7. Acceptance Criteria

### §20. Acceptance catalogue

These scenarios define reviewable behavior. During implementation, each scenario shall map to automated tests at the narrowest useful layer that name the scenario and its rules in their own source (`TST-005`).

**Where a scenario lives.** A scenario lives in the spec of the module that owns the rules its Given and When actually exercise. Steps that only set up or trigger the behavior under test, rules cited only in the expected result, and shared platform rules do not decide where it lives; a scenario whose exercised rules are platform rules lives in the platform spec. Where a reading could still place it in two specs, the CHANGELOG entry that places it records the reason.

**Rules with no system-level acceptance criterion.** Nineteen requirements have no direct
rule-ID reference in a scenario's Requirements cell. This is a trace count, not proof that
all nineteen are unobservable or deliberately excluded. The distinction is recorded below.

| Group | IDs | Why no scenario |
|---|---|---|
| Governance and scope | `GOV-001`, `GOV-002`, `GOV-003`, `GOV-006`–`GOV-010`, `GOV-015`–`GOV-016` | Authority, naming, product intent and scope declarations are reviewed at document/architecture level. They have no dedicated system scenario here; a broad statement such as working product is not established by one scenario. |
| Observable behavior with a trace gap | `GOV-005`, `GOV-012`, `GOV-014` | Historical stability, server event time and retention are testable. Related scenarios include calendar's `AC-ATT-001`, platform's `AC-GOV-001`, and project's `AC-PRJ-014`–`AC-PRJ-015`; none directly names these three rules, and those scenarios alone do not establish all their clauses. The plan must complete their rule-to-test trace; these are not exempt behaviors. |
| Engineering discipline | `ARC-009`, `TST-011` | Migration immutability and assertion integrity bind contributors; no running system can observe them. |
| Coordination | `OPS-018`–`OPS-021` | `OPS-019`, file ownership and branch naming, is enforced by review and by branch policy, not by the running application. `OPS-018`, `OPS-020` and `OPS-021` are withdrawn and bind nothing. |

Process/scope exclusions and missing behavioral trace are different. The three observable
rules above stay mandatory. The counts below measure document references, not implementation
coverage, and do not justify omitting their tests.

Scenarios for a feature are in section 7 of that feature's spec. The scenarios below cover the shared rules.

#### F1 — Shared rules and architecture

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-GOV-001 | GOV-011 | The server runs with a JVM default timezone other than the policy timezone, an Intern checks in, and the attendance row is read back | The business date is derived from the applicable policy version's timezone rather than the JVM default; the persisted instant is `timestamptz` and reads back as the same moment in UTC. |
| AC-GOV-002 | GOV-013 | Two clients concurrently submit leave that would exceed the monthly quota, and separately two clients concurrently log Task work on the same Intern and date | Exactly one leave request commits within quota and the other fails with an explicit conflict; the daily work total never exceeds 1 440 minutes and no partial row survives either race. |

Feature contracts: [Architecture](features/architecture/SPEC.md).

#### F2 — Authorization and security

Every scenario of this part is in its features.

Feature contracts: [Authorization](features/authorization/SPEC.md), [Security](features/security/SPEC.md).

#### F3 — Integration secrets and SMTP

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-INT-001 | INT-002–INT-005, UI-019 | Database, logs, and Admin History pages are inspected after saving SMTP/HolidayAPI secrets | Persistence contains only AES-GCM envelopes/nonces/version; History pages show user-meaningful non-secret metadata and never expose raw secret, token, ciphertext, nonce, password, API key, master key, bootstrap state, or internal retry records. |
| AC-INT-002 | INT-006–INT-009, UI-019 | Admin tests a bad draft while active config exists, then tests a valid draft and opens both integration History tabs; Mentor/Intern request those tabs | Failure leaves active config untouched; success atomically activates draft and retires old revision; Admin sees non-secret revision/outcome/actor metadata while non-Admins are denied without record disclosure. |
| AC-INT-004 | INT-010 | An operator inspects a stored SMTP and a stored HolidayAPI cipher envelope | Each envelope carries key-version metadata alongside the ciphertext; no rotation or external secret-store endpoint exists in the application. |

Feature contracts: [SMTP configuration](features/smtp-configuration/SPEC.md).

#### F4 — Shared interface

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-ERR-001 | ERR-001 | An Intern submits a Task work log with 0 minutes and a blank note | The same form redisplays with the submitted values retained, a field error on minutes, and an error summary; `task_work_logs` gains no row and the Task's daily total is unchanged. |

Feature contracts: [Shared interface](features/shared-interface/SPEC.md).

#### F5 — Operations

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-OPS-001 | OPS-001–OPS-004 | Developer starts PostgreSQL/Mailpit and runs IDE `dev` profile | Application connects using environment-backed Spring properties without production transport hardening. |
| AC-OPS-002 | OPS-003 | CI executes tests on clean runner | PostgreSQL Testcontainer supplies database; no host database, SMTP, or API key is required. |
| AC-OPS-003 | OPS-005–OPS-009 | App runs through bundled Compose and against external PostgreSQL | Same non-root image becomes healthy in both topologies with no embedded database assumption. |
| AC-OPS-004 | OPS-011–OPS-017 | A work branch, pull request, `main` push, and manual container dispatch occur while deployment is disabled | Every pull request and push verifies; work branches and pull requests do not schedule container builds; manual dispatch verifies then builds without publishing; `main` verifies then builds and publishes SHA/main image tags; SSH is skipped and receives no deployment secrets. |
| AC-OPS-005 | OPS-014–OPS-016 | Future operator enables deployment with all secrets | Job selects immutable SHA, verifies known host, rolls Compose, checks health, and records previous SHA for rollback. |
| AC-OPS-006 | OPS-010 | An operator replaces the application container while the named volume or external database is retained, then performs a documented restore | Data survives container replacement, and the restore procedure reproduces the database independently of the container lifecycle; documentation states that container replacement is not a backup. |
| AC-ERR-004 | ERR-004 | A scheduled worker runs, is interrupted, and is invoked again over the same window | Each affected row transitions once and each recipient receives one notification; the second invocation finds nothing left to do and processes no more than its bounded batch size. |
| AC-ERR-005 | ERR-005 | SMTP and HolidayAPI are both unreachable, then an Intern checks in, a Mentor opens an attendance report, and an Admin attempts to create an account | Check-in and the report succeed from local data; account creation is blocked before token creation with an actionable message naming the unavailable dependency. |
| AC-ERR-007 | ERR-007 | The application starts against a database whose Flyway migration fails | Readiness reports down and stays down; no request is served against the partially migrated schema; the failure names the migration that stopped. |

#### F6 — Test evidence and data integrity

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-TST-001 | TST-001–TST-010 | Contributor implements a feature | A failing test naming the requirements it protects precedes production code; the run records its own commands, results, and tool versions; the milestone is not green without affected suites. |
| AC-ERR-002 | ERR-002 | A Mentor loads an exit request, a second actor approves it, then the first Mentor submits the stale form | The stale submission is rejected with a reload invitation; the first decision stands unmodified; no second `project_membership_exit_requests` transition and no duplicate notification. |
| AC-ERR-003 | ERR-003 | A correction is submitted at the exact instant its submission deadline passes, with the deadline check and the insert in one transaction | Either the correction commits with its deadline satisfied or the whole transaction rolls back; no correction row exists whose recorded deadline had already passed at commit time. |

Feature contracts: [Data model](features/data-model/SPEC.md).

## 8. Out of Scope

### §1.3 Explicit non-goals

**Part F1.**

| ID | Requirement |
|---|---|
| GOV-007 | THE system SHALL NOT use a SPA framework, JWT authentication, microservices, Redis, Kafka, a generic workflow engine, or a persisted `Report` entity in v1. |
| GOV-008 | THE system SHALL NOT provide project-level days off, multiple Task assignees, unconditional self-service Project joining or leaving, Task dependencies, epics, sprints, story points, labels, watchers, reactions, attachments, nested subtasks, or burndown charts. Authenticated invitation acceptance and Mentor-approved exit requests SHALL be the only member-initiated boundary workflows. |
| GOV-009 | THE system SHALL NOT persist generic domain events, login-attempt history, daily calendar materializations, or Task-assignment history. Narrow correction, leave and attendance exception decision, leadership, Task block, unblock and reopen, and attendance period reopen request and decision history SHALL be retained because current requirements depend on them. |
| GOV-010 | WHILE the deployment host and its secrets do not exist, THE delivery pipeline SHALL keep the SSH deployment job disabled and SHALL NOT attempt to connect to a deployment target. |
| GOV-015 | THE system SHALL keep the Task effort-planning slice local to this product. It SHALL NOT integrate with external Jira or Tempo, SHALL NOT mirror Jira issues, sprints, or story points, SHALL NOT hold Tempo accounts or synchronization, and SHALL NOT add a `SUBMITTED`, `ACCEPTED`, or `REJECTED` state or any acceptance and rejection state machine for Tasks. Reopening a `DONE` Task under `TSK-023` so that its assignee corrects it is a status transition, not such a workflow. THE system SHALL NOT re-baseline a Task: once work is retained the estimate stays as `TSK-020` fixes it, and recording a Remaining effort forecast under `TSK-022` or `TSK-024` is not re-baselining. Any of these requires a new numbered requirement and a recorded decision. |

Exclusions stated inside this spec's other rules: `INT-010`.

**Deferred, which is not the same as excluded.** Weekly and Monthly report
presets sit outside the v1 acceptance scope and are postponed for later
consideration. The rules above forbid their subjects; this paragraph does not.
Deferral is equally not a promise: building one later needs a numbered
requirement and an acceptance scenario like any other feature.

## Notes / Open Questions

The feature split is organizational; read the feature-specific notes as well as the shared
notes below. No question affecting this module is open (`D38`). Should one be recorded here
later, this module and every feature it affects return to the inherited-baseline status.


### §22. Review and implementation gate

What approval means for a specification that documents a working product.

#### §22.1 What this document currently holds

| Measure | Value |
|---|---:|
| Normative rules, sections 1–22 | 311 |
| Rules with a §20 acceptance scenario | 292 |
| Rules declared without one, with reason | 19 |
| Acceptance scenarios | 167 |
| Application tables | 30 |

#### §22.2 What approval requires

- The holder of the highest applicable authority in §1.1 accepts the rule set, the acceptance catalogue, and the declared exclusions.
- No question in §22.3 is open.
- Every disagreement between the code and this specification found during review is resolved and recorded, either by correcting the specification to match a decision that already exists or by a new decision stating which of the two is intended.

Integrated reviews and demonstrations of the code are not a condition of approval. They
validate the code against the specification, so they need the specification fixed first
and follow approval rather than preceding it (`D11` in [`decisions.md`](../../decisions.md)).

Each spec under `.sdd/specs/` is approved by Loc-LX, the maintainer. Decisions the code does
not implement yet are recorded in [`decisions.md`](../../decisions.md) and tracked in
[`plan.md`](../../../plan.md). A change to a rule changes that spec's version and is recorded
in its `CHANGELOG.md`.

A document may
satisfy every mechanical check and still be wrong about the business; only a person
with authority can close that gap.

#### §22.3 Open questions

An open question means the specification cannot yet answer something an implementer
needs. The approved baseline records decisions already made; it does not settle gaps found
later. Before approving a plan for an affected part, the maintainer resolves its business
questions and the canonical rule/scenario is amended. Part organization is not that decision.

| Scope | Clarification still required | Effect on planning |
|---|---|---|
| None | No clarification is open. `D38` settled the last two, and the password-management coverage gap that this register had not carried. | Every part may proceed to its technical plan under the approved contracts. |

A closed register is not a claim that the rules are right for the laboratory. §22.2 still
requires a person with authority to accept them, and no mechanical check can supply that.

`D38` settles the account transition table (`ACC-014`, `ACC-028`, `ACC-029`, `DB-022`),
sign-in by account state (`ACC-030`), the email delivery states (`NOT-012`), and
password-reset eligibility (`SEC-015`) with the three scenarios that complete password
coverage. `D39` records the status predicates the migration must change beyond the status
constraints. It also removes the parallel `feature-*` document
tree after folding its history into each module.
`D37` settles assignee Task status transitions to require non-deleted Tasks (`TSK-007`),
adds `AC-TSK-021`, constrains `DB-011` and `DB-012` status sets to match `V1__baseline.sql`,
aligns invitation resolution codes to revoked status, and establishes the Project invitation
and exit-request state transition tables. `D35` settles initial Task status, uniform pre-block
restoration, cancelled-Project readiness and current-session logout. `D36` adds the
soft-deleted Task exclusion and its lifecycle acceptance scenario.

F2 also needs a technical design that states who resolves resource scope before policy
evaluation; `AUTH-012`, `ARC-005` and `ARC-006` constrain that design. F6 needs explicit
behavioral evidence for `GOV-005`, `GOV-012` and `GOV-014`. Those are plan work under
existing rules, not permission to change them. Scheduling and the superseded sections of
the existing platform plan are tracked only in [plan.md](../../../plan.md).

### §18. Ownership and branches

**Part F5.**

| ID | Requirement |
|---|---|
| OPS-018 | Withdrawn by `D30`. It required a reviewed platform baseline before parallel feature work began, which is complete. The entry keeps the identifier so that it is never reused. |
| OPS-019 | Shared build, migration, security, navigation, and base-template files shall have one named owner at a time, whether that owner is a person or an agent session. A targeted change shall use a clean, isolated `work/fix/<area>/<what>` branch from the current `main`, where `<area>` is a module of `ARC-005`, `platform`, `architecture` for a change that spans modules, or `docs` for documentation only. A branch name is invalid when it begins with an existing branch name followed by `/`, or when an existing branch name begins with it followed by `/`: `work/<area>/fix/<what>` is invalid where a persistent `work/<area>` exists, and where a branch named `work/fix/<area>` exists the change uses `work/fix/<area>-<what>` instead. Contributors shall not revert or rewrite another branch's work. |
| OPS-020 | Withdrawn by `D30`. It set the integration order of the team's feature branches; the order that binds now is the dependency layering of `ARC-005`, recorded in `ADR-006`. The entry keeps the identifier so that it is never reused. |
| OPS-021 | Withdrawn by `D30`. Its limit on push, merge, deployment and publication is held by the constitution's AI agent policy and `AGENTS.md`. The entry keeps the identifier so that it is never reused. |

### Appendix A. Implementation dependency notes

The implementation plan shall pin exact dependency versions in Maven/npm lockfiles at the approved baseline. Expected present choices are:

- Spring Boot 4.1.0 / Java 25;
- PostgreSQL 18.4;
- Node 24 LTS / Tailwind CSS 4;
- Chart.js 4.5.1;
- Lucide Static 1.27.0;
- current compatible Apache POI 5.5.x;
- current compatible OpenPDF 3.0.x `openpdf-html`.

Patch upgrades after review require normal dependency verification but do not change domain requirements. Major upgrades or library substitutions require a documented compatibility decision.
