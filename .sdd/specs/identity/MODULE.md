# Identity Module

<a id="identity-spec"></a>

**Version:** 1.5.0 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-21

Part of the Lab Timesheet specification. Rules every feature shares, including the
glossary, the authorization model, the domain model, and failure handling, are in
[the platform spec](../platform/MODULE.md). Section numbers marked `§` are one
numbering shared by all the specs, so a reference such as §5.2 names the same section
wherever it appears.

## 1. Context & Goal

Accounts, installation, and sign-in, part of the first area named by the product objective (§1.2 of the platform spec). The internship lifecycle, the other part of that area, is in [the internship spec](../internship/MODULE.md).
Its module is `identity` (`ARC-005`): users, activation and reset tokens, bootstrap, the login throttle, and the SMTP administration screen.


### Feature index

Read this shared contract together with the relevant feature SPEC. Rules are canonical
in exactly one of these documents; references do not create copies. Cross-feature use
cases, shared data constraints and unresolved decisions remain here (`D34`).

| Feature | Operations |
|---|---|
| [Authentication](features/authentication/SPEC.md) | Login; Logout; Session invalidation; Failed login and throttling |
| [Account lifecycle](features/account-lifecycle/SPEC.md) | Create and activate; Profile, directory and email correction; Lock, unlock, deactivate and reinstate |
| [Password management](features/password-management/SPEC.md) | Change password; Request password reset; Complete password reset |
| [First Admin bootstrap](features/first-admin-bootstrap/SPEC.md) | Initialize once; Configure or defer SMTP |


## 2. Actors & Roles

Primary actors named by this feature's use cases:

- **UC-01 — Initialize the installation:** First Admin (installer)
- **UC-02 — Activate, authenticate, and recover an account:** Admin-created user
- **UC-03 — Administer accounts:** Admin

Every capability by role is in the permission matrix, [platform spec](../platform/MODULE.md) §5.2. Authorization is resolved from stored context, never from the global role alone (§5.1).

## 3. Functional Requirements

### §4. Accounts, bootstrap, and internship lifecycle

#### §4.1 Bootstrap and accounts

`ACC-019`–`ACC-026`, the internship lifecycle of §4.2, are in [the internship spec](../internship/MODULE.md).

| ID | Requirement |
|---|---|
| ACC-009 | THE system SHALL use email as the only login identifier, unique case-insensitively after trimming and normalization. WHERE an Admin corrects an email identity, THE system SHALL require a tested active SMTP configuration, SHALL allow it only for a `PENDING_ACTIVATION`, `ACTIVE`, or `LOCKED` account, and SHALL commit only when the required delivery succeeds. A `DEACTIVATED` account SHALL remain read-only. |
| ACC-011 | WHILE no tested SMTP configuration is active, THE system SHALL refuse account creation, activation resend, and password-reset delivery. Bootstrap creation of the first Admin is the only exception. |
| ACC-017 | THE system SHALL allow a user to manage their own display and profile fields and their password. THE system SHALL give an Admin a server-filtered account directory searchable by display name, email, or Student Code and filterable by immutable global role. An Admin MAY correct the email identity and the permitted Intern fields; THE system SHALL NOT allow an Admin to edit a display name or a global role, SHALL change account and internship state only through the explicit lifecycle actions, and SHALL NOT fabricate session history it never stored. |
| ACC-018 | WHEN a password change, password reset, Admin email correction, lock, or deactivation succeeds, THE system SHALL invalidate that user's existing authenticated sessions. WHERE the corrected account is pending, THE system SHALL invalidate prior activation tokens and deliver a fresh activation to the corrected address; WHERE it is active or locked, THE system SHALL deliver notice to the corrected address. The identity change and its required delivery SHALL succeed or fail together. |

Feature contracts: [First Admin bootstrap](features/first-admin-bootstrap/SPEC.md), [Account lifecycle](features/account-lifecycle/SPEC.md).

### Authentication controls (from platform §13.1)

| ID | Requirement |
|---|---|
| SEC-002 | THE system SHALL require a password of 12 through 128 characters and SHALL hash it with Spring Security's delegating adaptive encoder. THE system SHALL NOT impose composition rules in v1. |
| SEC-003 | THE system SHALL generate activation and reset tokens from cryptographically secure random bytes suitable for URL-safe encoding, and SHALL persist only their 32-byte SHA-256 hashes. |
| SEC-004 | THE system SHALL expire an activation token after 24 hours and a password-reset token after 30 minutes. WHEN a token is issued, THE system SHALL invalidate that user's older unused token of the same purpose. |
| SEC-005 | WHEN a login or password-reset form is submitted, THE system SHALL return a generic response that does not reveal whether the email exists, is pending, is locked, or lacks SMTP delivery. |
| SEC-015 | THE system SHALL issue and accept a password-reset token only WHILE the account is `ACTIVE` or `LOCKED`. WHERE the account is `PENDING_ACTIVATION` or `DEACTIVATED`, THE system SHALL refuse both, and SHALL answer with the generic response of `SEC-005` rather than revealing the state. WHEN a reset completes, THE system SHALL replace the password, mark the token used, invalidate that account's sessions, and clear the login throttle of `SEC-006` for that normalized email on every source address. THE system SHALL NOT change the account status: a `LOCKED` account SHALL remain `LOCKED` and SHALL still be refused authentication under `ACC-030`, because a manual lock is kept apart from the throttle (`SEC-007`) and only an Admin lifts it. |

Feature contracts: [Authentication](features/authentication/SPEC.md).

### Use cases

#### UC-01 — Initialize the installation

Canonical workflow: [feature contract](features/first-admin-bootstrap/SPEC.md).

#### UC-02 — Activate, authenticate, and recover an account

| Field | Specification |
|---|---|
| Primary actor(s) | Admin-created user |
| Trigger | A user receives an activation link, signs in, or requests password recovery. |
| Preconditions | The account and token state permit the selected action. |
| Postconditions | The user has a valid authorized session, or the attempt fails without changing protected state. |
| Traced requirements | ACC-009–ACC-018, ACC-030, NOT-005–NOT-008, SEC-002–SEC-010, SEC-015 |

**Main success flow**

1. Open a single-use activation or reset link.
2. Set a policy-compliant password before token expiry.
3. Sign in with normalized email and password.
4. Open only the workspace and records permitted by role and context.
5. Manage profile, password, sessions, and local theme preference.

**Alternatives and exceptions**

- Expired, used, invalidated, or superseded tokens fail without revealing secret data.
- Five failed sign-ins in 15 minutes produce a 15-minute temporary throttle.
- Password recovery delivery is unavailable without active SMTP.
- Pending, locked, and deactivated accounts cannot sign in, and withdrawing an Intern deactivates the account; a completed Intern signs in read-only.

#### UC-03 — Administer accounts

Canonical workflow: [feature contract](features/account-lifecycle/SPEC.md).

## 4. Non-functional Requirements

System-wide non-functional rules apply unchanged: architecture §3 (`ARC`), authentication and security §13 (`SEC`), interface and accessibility §15 (`UI`), delivery §16 (`OPS`), and test evidence §17 (`TST`), all in the [platform spec](../platform/MODULE.md).

## 5. Data

Tables this feature's entities map to: `system_state`, `app_users`, `user_action_tokens`.

The conceptual model, the table inventory, the integrity rules (`DB`), and both diagrams are §19 of the [platform spec](../platform/MODULE.md).

## 6. Error Handling

Rules in section 3 whose text names a refusal, rejection, denial, or failure: `ACC-011`, `ACC-012`, `ACC-016`, `ACC-018`, `SEC-006`.

System-wide failure behavior is §21 (`ERR-001`–`ERR-007`), and the interface message families are Appendix F, both in the [platform spec](../platform/MODULE.md).

## 7. Acceptance Criteria

Scenarios from the §20 acceptance catalogue whose identifiers start with `AC-ACC` or `AC-SEC`, except `AC-ACC-010`, `AC-ACC-013`, `AC-ACC-014`, `AC-ACC-015`, `AC-ACC-018` and `AC-ACC-019`, which exercise internship lifecycle or its readiness dependencies and are in [the internship spec](../internship/MODULE.md). The catalogue's introduction, including the rules deliberately written without a scenario, is in the [platform spec](../platform/MODULE.md).

Feature contracts: [First Admin bootstrap](features/first-admin-bootstrap/SPEC.md), [Account lifecycle](features/account-lifecycle/SPEC.md), [Password management](features/password-management/SPEC.md), [Authentication](features/authentication/SPEC.md).

## 8. Out of Scope

System-wide exclusions are §1.3 of the [platform spec](../platform/MODULE.md): `GOV-007` through `GOV-010` and `GOV-015`.

No rule in this spec states an exclusion of its own.

## Notes / Open Questions

The feature split is organizational; read the feature-specific notes as well as the shared
notes below. No question affecting this module is open (`D38`). Should one be recorded here
later, this module and every feature it affects return to the inherited-baseline status.


No clarification of this module is open. `D35` settles current-session logout in
Authentication (ACC-027, AC-ACC-016/017) and security-triggered invalidation retains its
existing scope. `D38` settles the complete activation, lock, deactivation and reinstatement
edges in [Account lifecycle](features/account-lifecycle/SPEC.md), adds `SEC-015` for
password-reset eligibility, and completes the operation-level acceptance coverage of
[Password management](features/password-management/SPEC.md).

`ACC-028`, `ACC-029` and `DB-022` need a migration, and `ACC-030` and the throttle clause of
`SEC-015` need code that does not exist yet; none is implemented. The rest of `SEC-015` states
behavior the code already has and has not been validated against it; a documentation check
establishes consistency, not runtime conformance.
