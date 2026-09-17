# Internship Spec

**Version:** 1.0.1 · **Owner:** Loc-LX · **Status:** APPROVED · **Date:** 2026-09-17

Part of the Lab Timesheet specification. Rules every feature shares, including the
glossary, the authorization model, the domain model, and failure handling, are in
[the platform spec](../feature-platform/SPEC.md). Section numbers marked `§` are one
numbering shared by all the specs, so a reference such as §5.2 names the same section
wherever it appears.

## 1. Context & Goal

The internship lifecycle and the responsible Mentor, part of the first area named by the product objective (§1.2 of the platform spec). Accounts and sign-in, the other part of that area, are in [the identity spec](../feature-identity/SPEC.md).
Its module is `internship` (`ARC-005`): Intern profiles, internship transitions, the responsible Mentor, and the Admin's Intern administration composed over identity.

## 2. Actors & Roles

Primary actors named by this feature's use cases:

- **UC-18 — Administer the internship lifecycle:** Admin

Every capability by role is in the permission matrix, [platform spec](../feature-platform/SPEC.md) §5.2. Authorization is resolved from stored context, never from the global role alone (§5.1).

## 3. Functional Requirements

### §4. Accounts, bootstrap, and internship lifecycle

`ACC-001`–`ACC-018`, bootstrap and accounts under §4.1, are in [the identity spec](../feature-identity/SPEC.md).

#### §4.2 Internship lifecycle

| ID | Requirement |
|---|---|
| ACC-019 | THE system SHALL give every `INTERN` account exactly one Intern profile carrying a case-insensitively unique Student Code, internship start and end dates, and an internship status, and SHALL NOT create a profile for a non-Intern account. An Admin MAY correct the Student Code WHILE the internship is `NOT_STARTED` or `ACTIVE`, and MAY correct the dates only WHILE it is `NOT_STARTED`. WHILE a profile is `COMPLETED` or `WITHDRAWN`, THE system SHALL keep it read-only. |
| ACC-020 | THE system SHALL permit only these internship transitions: `NOT_STARTED → ACTIVE → COMPLETED` and `NOT_STARTED/ACTIVE → WITHDRAWN`. A `SUSPENDED` state SHALL NOT exist. |
| ACC-021 | WHEN the configured internship start date is reached, THE system SHALL activate an eligible `NOT_STARTED` Intern through both a scheduled guard and a guard applied at request time, so that correctness does not depend on scheduler timing. WHILE an Intern has no responsible Mentor under `ACC-026`, THE system SHALL NOT activate the internship. |
| ACC-022 | WHEN an Admin marks an Intern `COMPLETED` or `WITHDRAWN`, THE system SHALL apply it only through that explicit action. WHILE that Intern holds a current leadership term or owns an unfinished Task, THE system SHALL refuse both actions until the leader-transfer and Task-reassignment workflows have succeeded. |
| ACC-023 | WHILE an Intern is `COMPLETED`, THE system SHALL allow authentication in read-only mode to view retained history and manage password and session security, and SHALL refuse any attempt to create or mutate attendance, leave, correction, Project, Task, comment, or work-log data. |
| ACC-024 | WHEN an Admin withdraws an Intern, THE system SHALL set the account to `DEACTIVATED` in the same transaction, SHALL end every session of that account, SHALL thereafter refuse its login under `ACC-016`, SHALL issue it no password-reset token and refuse any it already holds, and SHALL keep their historical memberships, Tasks, work logs, attendance, leave, and corrections attributable. |
| ACC-025 | WHEN a terminal lifecycle action is applied, THE system SHALL enforce it for authorization from that instant. Attendance already recorded on that local date SHALL remain reportable, and an otherwise empty terminal date SHALL NOT be newly classified as an absence. |
| ACC-026 | THE system SHALL let an Admin assign one active Mentor as an Intern's responsible Mentor and replace that assignment. WHEN the assignment changes, THE system SHALL move every pending or overdue leave, correction, and attendance exception request of that Intern to the new Mentor, SHALL leave every earlier decision attributed to the Mentor who made it, and SHALL NOT make the Admin an approver. WHILE an Intern's responsible Mentor is `LOCKED` or `DEACTIVATED`, THE system SHALL show that Intern to Admins as needing a new responsible Mentor, and SHALL NOT let an Admin or any other Mentor decide the Intern's requests. |

### Use cases

#### UC-18 — Administer the internship lifecycle

| Field | Specification |
|---|---|
| Primary actor(s) | Admin |
| Trigger | An Admin creates an Intern or changes internship lifecycle state. |
| Preconditions | The Admin is active; SMTP is active for Intern account creation. |
| Postconditions | Internship state changes transactionally while historical attribution remains. |
| Traced requirements | ACC-019–ACC-025, AUTH-001–AUTH-002 |

**Main success flow**

1. For an Intern, enter unique student code and internship dates.
2. Inspect membership, leadership, and unfinished-task context.
3. Complete or withdraw only when guards pass.

**Alternatives and exceptions**

- Completion or withdrawal is blocked while the Intern is a Leader or owns unfinished Tasks.
- Completed Interns retain read-only historical access; withdrawal deactivates the account, ends its sessions, and refuses further login.

## 4. Non-functional Requirements

System-wide non-functional rules apply unchanged: architecture §3 (`ARC`), authentication and security §13 (`SEC`), interface and accessibility §15 (`UI`), delivery §16 (`OPS`), and test evidence §17 (`TST`), all in the [platform spec](../feature-platform/SPEC.md).

## 5. Data

Tables this feature's entities map to: `intern_profiles`. The Intern profile will also need the responsible Mentor of `ACC-026`.

The conceptual model, the table inventory, the integrity rules (`DB`), and both diagrams are §19 of the [platform spec](../feature-platform/SPEC.md).

## 6. Error Handling

Rules in section 3 whose text names a refusal, rejection, denial, or failure: `ACC-022`, `ACC-023`, `ACC-024`.

System-wide failure behavior is §21 (`ERR-001`–`ERR-007`), and the interface message families are Appendix F, both in the [platform spec](../feature-platform/SPEC.md).

## 7. Acceptance Criteria

Scenarios from the §20 acceptance catalogue that cover only internship rules. They use the `AC-ACC` prefix because the rules they cover carry the `ACC` prefix. The catalogue's introduction, including the rules deliberately written without a scenario, is in the [platform spec](../feature-platform/SPEC.md).

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-ACC-010 | ACC-019–ACC-025 | Start date arrives, then Admin completes an Intern | Scheduler/access guard activates once; completion is blocked until transfer guards pass; completed login is read-only. |
| AC-ACC-013 | ACC-021, ACC-026 | An Intern's start date arrives before an Admin assigns a responsible Mentor; the Admin then assigns one, and later replaces them after that Mentor has decided a leave request | The internship stays `NOT_STARTED` until a responsible Mentor is assigned and activates once one is; after replacement, new requests go to the new Mentor and the earlier decision still names the Mentor who made it. |
| AC-ACC-014 | ACC-026 | An Intern's responsible Mentor is locked while the Intern has a pending leave request, a pending correction, and an overdue exception request | The Intern appears to Admins as needing a new responsible Mentor; neither an Admin nor another Mentor can decide the requests; after an Admin assigns a new Mentor all three move to that Mentor, and decisions made earlier still name the original Mentor. |
| AC-ACC-015 | ACC-019 | Admin corrects the Student Code and the internship dates of Interns whose internships are `NOT_STARTED`, `ACTIVE`, `COMPLETED`, and `WITHDRAWN` | Student Code edits succeed only while `NOT_STARTED` or `ACTIVE`; date edits succeed only while `NOT_STARTED`; completed and withdrawn profiles remain read-only and unchanged. |

## 8. Out of Scope

System-wide exclusions are §1.3 of the [platform spec](../feature-platform/SPEC.md): `GOV-007` through `GOV-010` and `GOV-015`.

Exclusions stated inside this spec's own rules: `ACC-020`.

## Notes / Open Questions

- `ACC-026` follows `D14`. When a responsible Mentor becomes unavailable, an Admin reassigns the Intern and the pending requests move with them; the Admin never becomes an approver.
- `ACC-026` has no use case of its own; `UC-15` and `UC-17` in [the attendance spec](../feature-attendance/SPEC.md) trace it.
