# Internship Module

<a id="internship-spec"></a>

**Version:** 1.4.0 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-21

Part of the Lab Timesheet specification. Rules every feature shares, including the
glossary, the authorization model, the domain model, and failure handling, are in
[the platform spec](../platform/MODULE.md). Section numbers marked `§` are one
numbering shared by all the specs, so a reference such as §5.2 names the same section
wherever it appears.

## 1. Context & Goal

The internship lifecycle and the responsible Mentor, part of the first area named by the product objective (§1.2 of the platform spec). Accounts and sign-in, the other part of that area, are in [the identity spec](../identity/MODULE.md).
Its module is `internship` (`ARC-005`): Intern profiles, internship transitions, the responsible Mentor, and the Admin's Intern administration composed over identity.


### Feature index

Read this shared contract together with the relevant feature SPEC. Rules are canonical
in exactly one of these documents; references do not create copies. Cross-feature use
cases, shared data constraints and unresolved decisions remain here (`D34`).

| Feature | Operations |
|---|---|
| [Internship lifecycle](features/lifecycle/SPEC.md) | Prepare and correct profile; Start internship; Complete or withdraw |
| [Responsible Mentor](features/responsible-mentor/SPEC.md) | Assign Mentor; Replace unavailable Mentor |


## 2. Actors & Roles

Primary actors named by this feature's use cases:

- **UC-18 — Administer the internship lifecycle:** Admin

Every capability by role is in the permission matrix, [platform spec](../platform/MODULE.md) §5.2. Authorization is resolved from stored context, never from the global role alone (§5.1).

## 3. Functional Requirements

### §4. Accounts, bootstrap, and internship lifecycle

`ACC-001`–`ACC-018`, bootstrap and accounts under §4.1, are in [the identity spec](../identity/MODULE.md).

#### §4.2 Internship lifecycle

Feature contracts: [Internship lifecycle](features/lifecycle/SPEC.md), [Responsible Mentor](features/responsible-mentor/SPEC.md).

#### State transitions: internship

Canonical workflow: [feature contract](features/lifecycle/SPEC.md).

### Integrity (from platform §19.3)

Feature contracts: [Responsible Mentor](features/responsible-mentor/SPEC.md).

### Use cases

#### UC-18 — Administer the internship lifecycle

Canonical workflow: [feature contract](features/lifecycle/SPEC.md).

## 4. Non-functional Requirements

System-wide non-functional rules apply unchanged: architecture §3 (`ARC`), authentication and security §13 (`SEC`), interface and accessibility §15 (`UI`), delivery §16 (`OPS`), and test evidence §17 (`TST`), all in the [platform spec](../platform/MODULE.md).

## 5. Data

Tables this feature's entities map to: `intern_profiles`, which references the responsible Mentor of `ACC-026` (`DB-021`).

The conceptual model, the table inventory, the integrity rules (`DB`), and both diagrams are §19 of the [platform spec](../platform/MODULE.md).

## 6. Error Handling

Rules in section 3 whose text names a refusal, rejection, denial, or failure: `ACC-022`, `ACC-023`, `ACC-024`.

System-wide failure behavior is §21 (`ERR-001`–`ERR-007`), and the interface message families are Appendix F, both in the [platform spec](../platform/MODULE.md).

## 7. Acceptance Criteria

Scenarios from the §20 acceptance catalogue that cover only internship rules. They use the `AC-ACC` prefix because the rules they cover carry the `ACC` prefix, except `AC-DB-008`, which covers `DB-021`. The catalogue's introduction, including the rules deliberately written without a scenario, is in the [platform spec](../platform/MODULE.md).

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-ACC-013 | ACC-021, ACC-026 | An Intern's start date arrives before an Admin assigns a responsible Mentor; the Admin then assigns one, and later replaces them after that Mentor has decided a leave request | The internship stays `NOT_STARTED` until a responsible Mentor is assigned and activates once one is; after replacement, new requests go to the new Mentor and the earlier decision still names the Mentor who made it. |

Feature contracts: [Internship lifecycle](features/lifecycle/SPEC.md), [Responsible Mentor](features/responsible-mentor/SPEC.md).

## 8. Out of Scope

System-wide exclusions are §1.3 of the [platform spec](../platform/MODULE.md): `GOV-007` through `GOV-010` and `GOV-015`.

Exclusions stated inside this spec's own rules: `ACC-020`.

## Notes / Open Questions

The feature split is organizational; read the feature-specific notes as well as the shared
notes below. No question affecting this module is open (`D38`). Should one be recorded here
later, this module and every feature it affects return to the inherited-baseline status.


- `ACC-026` follows `D14`. When a responsible Mentor becomes unavailable, an Admin reassigns the Intern and the pending requests move with them; the Admin never becomes an approver.
- `ACC-026` has no use case of its own; `UC-15` and `UC-17` in [the attendance spec](../attendance/MODULE.md) trace it.
