# Account Spec

**Version:** 1.0.1 · **Owner:** Loc-LX · **Status:** APPROVED · **Date:** 2026-09-14

Part of the Lab Timesheet specification. Rules every feature shares, including the
glossary, the authorization model, the domain model, and failure handling, are in
[the platform spec](../feature-platform/SPEC.md). Section numbers marked `§` are the
numbers the rules carried in the single-file specification and are kept so existing
references still resolve.

## 1. Context & Goal

Account and internship administration, the first area named by the product objective (§1.2 of the platform spec).
In code this is `feature/account`: users, internships, activation and reset tokens, bootstrap, and the login throttle.

## 2. Actors & Roles

Primary actors named by this feature's use cases:

- **UC-01 — Initialize the installation:** First Admin (installer)
- **UC-02 — Activate, authenticate, and recover an account:** Admin-created user
- **UC-03 — Administer accounts and internship lifecycle:** Admin

Every capability by role is in the permission matrix, [platform spec](../feature-platform/SPEC.md) §5.2. Authorization is resolved from stored context, never from the global role alone (§5.1).

## 3. Functional Requirements

### §4. Accounts, bootstrap, and internship lifecycle

#### §4.1 Bootstrap and accounts

| ID | Requirement |
|---|---|
| ACC-001 | WHILE the installation is uninitialized, THE system SHALL expose only the health endpoints, the static bootstrap assets, and the one-time bootstrap workflow. |
| ACC-002 | WHEN a valid first-Admin bootstrap is submitted, THE system SHALL create that `ADMIN` with a password and mark the singleton system state initialized in one transaction. WHERE two bootstrap submissions arrive concurrently, exactly one SHALL create an Admin and the other SHALL be told setup is already complete. |
| ACC-003 | WHEN initialization completes, THE system SHALL make the bootstrap route unavailable, and it SHALL remain unavailable across restarts. |
| ACC-004 | Bootstrap runs on a private interface before the installation is exposed publicly. The temporary unguarded bootstrap route is an accepted and documented operational risk, not a defect. |
| ACC-005 | WHILE bootstrap is open, THE system SHALL offer SMTP setup. WHERE the Admin defers it, THE system SHALL require five sequential distinct confirmations, SHALL offer Back and Configure SMTP on every one, and SHALL permit Finish without SMTP only on the fifth. |
| ACC-006 | THE system SHALL present the five deferral warnings in this order: account onboarding disabled, activation resend disabled, password recovery disabled, reduced email immediacy for workflow events, and final acknowledgement of a restricted installation. |
| ACC-007 | WHILE no tested SMTP configuration is active, THE system SHALL keep a persistent warning visible to every Admin. |
| ACC-008 | WHILE an Admin account is active, THE system SHALL permit it to create further Admin, Mentor, and Intern accounts. THE system SHALL treat the role chosen at creation as immutable. |
| ACC-009 | THE system SHALL use email as the only login identifier, unique case-insensitively after trimming and normalization. WHERE an Admin corrects an email identity, THE system SHALL require a tested active SMTP configuration, SHALL allow it only for a `PENDING_ACTIVATION`, `ACTIVE`, or `LOCKED` account, and SHALL commit only when the required delivery succeeds. A `DEACTIVATED` account SHALL remain read-only. |
| ACC-010 | WHEN an Admin creates an account, THE system SHALL store it as `PENDING_ACTIVATION` with no password hash and SHALL issue one single-use activation link that sets the first password. |
| ACC-011 | WHILE no tested SMTP configuration is active, THE system SHALL refuse account creation, activation resend, and password-reset delivery. Bootstrap creation of the first Admin is the only exception. |
| ACC-012 | WHERE activation delivery fails after an account is created, THE system SHALL leave the account pending, SHALL invalidate the failed token, and SHALL show the Admin a visible failure carrying an explicit Resend Activation action. |
| ACC-013 | WHEN an Admin resends activation, THE system SHALL invalidate any prior unused activation token before generating and sending the fresh one. |
| ACC-014 | THE system SHALL hold an account in exactly one of `PENDING_ACTIVATION`, `ACTIVE`, `LOCKED`, or `DEACTIVATED`. Lock and deactivation SHALL occur only through an explicit Admin action. |
| ACC-015 | WHEN an Admin unlocks a previously activated account, THE system SHALL return it to `ACTIVE` without changing its global role or recreating its credentials. |
| ACC-016 | WHILE an account is `DEACTIVATED`, THE system SHALL refuse authentication for it and SHALL keep its historical attribution visible. |
| ACC-017 | THE system SHALL allow a user to manage their own display and profile fields and their password. THE system SHALL give an Admin a server-filtered account directory searchable by display name, email, or Student Code and filterable by immutable global role. An Admin MAY correct the email identity and the permitted Intern fields; THE system SHALL NOT allow an Admin to edit a display name or a global role, SHALL change account and internship state only through the explicit lifecycle actions, and SHALL NOT fabricate session history it never stored. |
| ACC-018 | WHEN a password change, password reset, Admin email correction, lock, or deactivation succeeds, THE system SHALL invalidate that user's existing authenticated sessions. WHERE the corrected account is pending, THE system SHALL invalidate prior activation tokens and deliver a fresh activation to the corrected address; WHERE it is active or locked, THE system SHALL deliver notice to the corrected address. The identity change and its required delivery SHALL succeed or fail together. |

#### §4.2 Internship lifecycle

| ID | Requirement |
|---|---|
| ACC-019 | THE system SHALL give every `INTERN` account exactly one Intern profile carrying a case-insensitively unique Student Code, internship start and end dates, and an internship status, and SHALL NOT create a profile for a non-Intern account. An Admin MAY correct the Student Code WHILE the internship is `NOT_STARTED` or `ACTIVE`, and MAY correct the dates only WHILE it is `NOT_STARTED`. WHILE a profile is `COMPLETED` or `WITHDRAWN`, THE system SHALL keep it read-only. |
| ACC-020 | THE system SHALL permit only these internship transitions: `NOT_STARTED → ACTIVE → COMPLETED` and `NOT_STARTED/ACTIVE → WITHDRAWN`. A `SUSPENDED` state SHALL NOT exist. |
| ACC-021 | WHEN the configured internship start date is reached, THE system SHALL activate an eligible `NOT_STARTED` Intern through both a scheduled guard and a guard applied at request time, so that correctness does not depend on scheduler timing. |
| ACC-022 | WHEN an Admin marks an Intern `COMPLETED` or `WITHDRAWN`, THE system SHALL apply it only through that explicit action. WHILE that Intern holds a current leadership term or owns an unfinished Task, THE system SHALL refuse both actions until the leader-transfer and Task-reassignment workflows have succeeded. |
| ACC-023 | WHILE an Intern is `COMPLETED`, THE system SHALL allow authentication in read-only mode to view retained history and manage password and session security, and SHALL refuse any attempt to create or mutate attendance, leave, correction, Project, Task, comment, or work-log data. |
| ACC-024 | WHEN an Admin withdraws an Intern, THE system SHALL set the account to `DEACTIVATED` in the same transaction, SHALL end every session of that account, SHALL thereafter refuse its login under `ACC-016`, SHALL issue it no password-reset token and refuse any it already holds, and SHALL keep their historical memberships, Tasks, work logs, attendance, leave, and corrections attributable. |
| ACC-025 | WHEN a terminal lifecycle action is applied, THE system SHALL enforce it for authorization from that instant. Attendance already recorded on that local date SHALL remain reportable, and an otherwise empty terminal date SHALL NOT be newly classified as an absence. |

### Use cases

#### UC-01 — Initialize the installation

| Field | Specification |
|---|---|
| Primary actor(s) | First Admin (installer) |
| Trigger | The uninitialized installation is opened on a private interface. |
| Preconditions | No bootstrap has completed; the singleton system state is uninitialized. |
| Postconditions | Exactly one first Admin exists and bootstrap cannot be reopened. |
| Traced requirements | ACC-001–ACC-007, INT-001–INT-003, SEC-001 |

**Main success flow**

1. Enter the first Admin identity and password.
2. Optionally configure and test an SMTP draft.
3. If SMTP is deferred, pass through five distinct warning screens in order.
4. Submit the final bootstrap action.
5. Create the first Admin and close bootstrap atomically.
6. Enter the initialized Admin workspace.

**Alternatives and exceptions**

- A competing bootstrap submission loses the atomic race and cannot create another first Admin.
- An SMTP test failure leaves the draft inactive and allows correction.
- Before the fifth warning, the user may go back or configure SMTP but may not finish the deferral.

#### UC-02 — Activate, authenticate, and recover an account

| Field | Specification |
|---|---|
| Primary actor(s) | Admin-created user |
| Trigger | A user receives an activation link, signs in, or requests password recovery. |
| Preconditions | The account and token state permit the selected action. |
| Postconditions | The user has a valid authorized session, or the attempt fails without changing protected state. |
| Traced requirements | ACC-009–ACC-018, NOT-005–NOT-008, SEC-002–SEC-010 |

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

#### UC-03 — Administer accounts and internship lifecycle

| Field | Specification |
|---|---|
| Primary actor(s) | Admin |
| Trigger | An Admin creates an account or changes account/internship lifecycle state. |
| Preconditions | The Admin is active; SMTP is active for non-bootstrap account creation. |
| Postconditions | Account and internship state change transactionally while historical attribution remains. |
| Traced requirements | ACC-008–ACC-025, AUTH-001–AUTH-002 |

**Main success flow**

1. Choose immutable global role and enter identity fields.
2. For an Intern, enter unique student code and internship dates.
3. Create a pending account and deliver a 24-hour activation link.
4. Inspect activation, account, session, membership, leadership, and unfinished-task context.
5. Lock, unlock, deactivate, complete, or withdraw only when guards pass.

**Alternatives and exceptions**

- Failed activation delivery retains the pending account, invalidates the token, and exposes explicit resend.
- Completion or withdrawal is blocked while the Intern is a Leader or owns unfinished Tasks.
- Completed Interns retain read-only historical access; withdrawal deactivates the account, ends its sessions, and refuses further login.

## 4. Non-functional Requirements

System-wide non-functional rules apply unchanged: architecture §3 (`ARC`), authentication and security §13 (`SEC`), interface and accessibility §15 (`UI`), delivery §16 (`OPS`), and test evidence §17 (`TST`), all in the [platform spec](../feature-platform/SPEC.md).

## 5. Data

Tables this feature's entities map to: `system_state`, `app_users`, `user_action_tokens`, `intern_profiles`.

The conceptual model, the table inventory, the integrity rules (`DB`), and both diagrams are §19 of the [platform spec](../feature-platform/SPEC.md).

## 6. Error Handling

Rules in section 3 whose text names a refusal, rejection, denial, or failure: `ACC-011`, `ACC-012`, `ACC-016`, `ACC-018`, `ACC-022`, `ACC-023`, `ACC-024`.

System-wide failure behavior is §21 (`ERR-001`–`ERR-007`), and the interface message families are Appendix F, both in the [platform spec](../feature-platform/SPEC.md).

## 7. Acceptance Criteria

Scenarios from the §20 acceptance catalogue whose identifiers start with `AC-ACC`. The catalogue's introduction, including the rules deliberately written without a scenario, is in the [platform spec](../feature-platform/SPEC.md).

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-ACC-001 | ACC-001–ACC-004 | Two clients submit valid first-Admin bootstrap concurrently | Exactly one Admin and initialized singleton commit; the other request sees setup already complete; no second bootstrap Admin is created. |
| AC-ACC-002 | ACC-003 | A client requests bootstrap after initialization and restart | Route is unavailable and cannot mutate system state. |
| AC-ACC-003 | ACC-005–ACC-007 | Admin repeatedly chooses Defer SMTP | Five different confirmations appear sequentially; only the fifth permits completion; persistent warning remains. |
| AC-ACC-004 | ACC-008–ACC-011 | Admin attempts to create Admin/Mentor/Intern without active SMTP | All three creation attempts are blocked before account/token creation with an actionable SMTP message. |
| AC-ACC-005 | ACC-010, SEC-003–SEC-004 | Admin creates each role with active SMTP | Each account is pending, has no password, receives one 24-hour single-use link, and stores only a 32-byte hash. |
| AC-ACC-006 | ACC-012–ACC-013 | SMTP send fails during activation delivery | Pending account remains; first token is invalidated; resend creates a different token and successful activation can use only the new token. |
| AC-ACC-007 | ACC-008, ACC-014 | Admin creates another Admin and it activates | New account authenticates as Admin; original Admin remains unchanged. |
| AC-ACC-008 | DB-005 | Any code path attempts to update an existing `global_role` | PostgreSQL rejects the update even if application authorization is bypassed. |
| AC-ACC-009 | ACC-014–ACC-018 | Admin locks, unlocks, then deactivates a user | Sessions are invalidated; login follows state; attribution remains; role never changes. |
| AC-ACC-010 | ACC-019–ACC-025 | Start date arrives, then Admin completes an Intern | Scheduler/access guard activates once; completion is blocked until transfer guards pass; completed login is read-only. |
| AC-ACC-011 | ACC-008, ACC-017, ACC-019, UI-014 | Admin switches account creation between Intern and non-Intern roles, then submits a crafted non-Intern request containing Intern fields | The browser disables and clears inapplicable fields; the server independently rejects crafted incompatible data; role remains immutable. |
| AC-ACC-012 | ACC-009, ACC-017–ACC-019 | Admin searches and filters the account directory, then corrects account data in pending, active, locked, and terminal states | Search matches normalized display name/email/Student Code and role filtering is exact; pending email change replaces activation safely; active/locked email change invalidates sessions; SMTP/delivery/uniqueness failure leaves identity unchanged; Student Code and date edits obey their lifecycle boundaries; deactivated/terminal data and role/display name remain read-only. |

## 8. Out of Scope

System-wide exclusions are §1.3 of the [platform spec](../feature-platform/SPEC.md): `GOV-007` through `GOV-010` and `GOV-015`.

Exclusions stated inside this spec's own rules: `ACC-020`.

## Notes / Open Questions

- `ACC-023` and `ACC-024` were confirmed by the instructor on 14 September 2026: a withdrawn Intern cannot sign in; a completed Intern keeps a read-only account and may still change their password and manage sessions, as `ACC-023` states.
- `D14` needs a responsible Mentor on the Intern profile, assigned by an Admin, to approve attendance exceptions. It is not yet written into `ACC-019`; see the attendance spec.
