# Account lifecycle Spec

**Version:** 1.2.0 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-21

**Module:** `identity` · **Shared contract:** [MODULE.md](../../MODULE.md)

## 1. Context & Goal

Administer an account from creation and activation through lock, unlock and deactivation.

This is one cohesive planning scope. Operations below are sections of this feature,
not separate modules or mandatory branches. The split changes document ownership only.

## 2. Actors & Roles

Admin manages accounts; an invited user activates; a signed-in user manages their permitted profile fields.

Use cases defined here: UC-03.

The [platform permission matrix](../../../platform/MODULE.md#52-permission-matrix)
and the module's shared authorization rules apply to every operation.

## 3. Functional Requirements

The operation summaries below are navigation. The numbered rules remain authoritative;
they do not acquire additional behavior from a heading or summary.

### Create and activate

ACC-008, ACC-010, ACC-012 and ACC-013 define pending accounts, first-password activation, delivery failure and explicit resend.

### Profile, directory and email correction

The shared ACC-009, ACC-017 and ACC-018 contract governs identity normalization, permitted edits, directory filters and atomic email correction.

### Lock, unlock, deactivate and reinstate

ACC-014 through ACC-016 constrain state and historical attribution; ACC-028 and ACC-029 add the two edges `D38` settled, so no account state is a dead end; DB-022 holds the schema to the same set; ACC-018 supplies the session invalidation guard.

### Canonical feature rules

| ID | Requirement |
|---|---|
| ACC-008 | WHILE an Admin account is active, THE system SHALL permit it to create further Admin, Mentor, and Intern accounts. THE system SHALL treat the role chosen at creation as immutable. |
| ACC-010 | WHEN an Admin creates an account, THE system SHALL store it as `PENDING_ACTIVATION` with no password hash and SHALL issue one single-use activation link that sets the first password. |
| ACC-012 | WHERE activation delivery fails after an account is created, THE system SHALL leave the account pending, SHALL invalidate the failed token, and SHALL show the Admin a visible failure carrying an explicit Resend Activation action. |
| ACC-013 | WHEN an Admin resends activation, THE system SHALL invalidate any prior unused activation token before generating and sending the fresh one. |
| ACC-014 | THE system SHALL hold an account in exactly one of `PENDING_ACTIVATION`, `ACTIVE`, `LOCKED`, or `DEACTIVATED`. Lock, deactivation and reinstatement SHALL occur only through an explicit Admin action. THE system SHALL permit only these account transitions: `PENDING_ACTIVATION → ACTIVE` when the activation link of `ACC-010` sets the first password, `ACTIVE → LOCKED`, `LOCKED → ACTIVE`, `ACTIVE → DEACTIVATED`, `LOCKED → DEACTIVATED`, `PENDING_ACTIVATION → DEACTIVATED` under `ACC-028`, and `DEACTIVATED → ACTIVE`, `DEACTIVATED → LOCKED` or `DEACTIVATED → PENDING_ACTIVATION` under `ACC-029`. WHERE any other transition is requested, THE system SHALL reject it and leave the account unchanged. |
| ACC-015 | WHEN an Admin unlocks a previously activated account, THE system SHALL return it to `ACTIVE` without changing its global role or recreating its credentials. |
| ACC-016 | WHILE an account is `DEACTIVATED`, THE system SHALL keep its historical attribution visible and SHALL refuse authentication for it under `ACC-030`. WHEN a `LOCKED` account is deactivated, THE system SHALL retain its lock timestamp, so that reinstatement under `ACC-029` restores the lock rather than silently lifting it. THE system SHALL NOT treat `DEACTIVATED` as permanent: an Admin MAY reinstate the account under `ACC-029`. |
| ACC-028 | WHILE an account is `PENDING_ACTIVATION`, THE system SHALL permit an Admin to deactivate it. WHEN that happens, THE system SHALL set the account to `DEACTIVATED`, invalidate every unused activation and password-reset token it holds, and leave its identity, global role and historical attribution unchanged. THE system SHALL NOT create a password hash or an activation timestamp for such an account. |
| ACC-029 | WHILE an account is `DEACTIVATED`, THE system SHALL permit an Admin to reinstate it without recreating its credentials, and SHALL choose the resulting state from what the account holds: `LOCKED` WHERE it holds a lock timestamp, so that a lock applied before deactivation survives; `ACTIVE` WHERE it holds an activation timestamp and no lock timestamp; and `PENDING_ACTIVATION` WHERE it holds neither, in which case a usable activation link SHALL require an explicit resend under `ACC-013`. THE system SHALL NOT restore an internship, a Project membership or a leadership term by reinstating an account. |
| DB-022 | THE schema SHALL constrain `app_users.account_status` to `PENDING_ACTIVATION`, `ACTIVE`, `LOCKED` or `DEACTIVATED`. THE schema SHALL require a non-blank password hash and an activation timestamp together or not at all: neither WHILE the status is `PENDING_ACTIVATION`, both WHILE it is `ACTIVE` or `LOCKED`, and both or neither WHILE it is `DEACTIVATED`. THE schema SHALL refuse an update that sets an activation timestamp other than the one that moves an account from `PENDING_ACTIVATION` to `ACTIVE`, and SHALL refuse every update that clears or changes an activation timestamp once set, so that a `DEACTIVATED` row without one can only be an account that was never activated. THE schema SHALL require a lock timestamp WHILE the status is `LOCKED`, SHALL permit one WHILE it is `DEACTIVATED` only beside an activation timestamp, and SHALL refuse one in every other status. THE schema SHALL require a deactivation timestamp exactly WHILE the status is `DEACTIVATED`. |

#### State transitions: account

Every transition is an explicit Admin action except activation, which the invitee performs.
The table adds no permission; `ACC-014` remains authoritative.

| From | Action | To | Who | Rules |
|---|---|---|---|---|
| (none) | create with an immutable global role | `PENDING_ACTIVATION` | Admin | `ACC-008`, `ACC-010` |
| `PENDING_ACTIVATION` | set the first password through the activation link | `ACTIVE` | intended user | `ACC-010`, `ACC-014` |
| `PENDING_ACTIVATION` | deactivate an account created in error | `DEACTIVATED` | Admin | `ACC-028`, `DB-022` |
| `ACTIVE` | lock | `LOCKED` | Admin | `ACC-014`, `ACC-018` |
| `LOCKED` | unlock | `ACTIVE` | Admin | `ACC-015` |
| `ACTIVE`, `LOCKED` | deactivate; a lock timestamp is kept | `DEACTIVATED` | Admin | `ACC-014`, `ACC-016`, `ACC-018` |
| `DEACTIVATED` | reinstate an account that was locked when it was deactivated | `LOCKED` | Admin | `ACC-029`, `DB-022` |
| `DEACTIVATED` | reinstate an account that was activated and not locked | `ACTIVE` | Admin | `ACC-029` |
| `DEACTIVATED` | reinstate an account that was never activated | `PENDING_ACTIVATION` | Admin | `ACC-029`, `ACC-013` |

#### UC-03 — Administer accounts

| Field | Specification |
|---|---|
| Primary actor(s) | Admin |
| Trigger | An Admin creates an account or changes account lifecycle state. |
| Preconditions | The Admin is active; SMTP is active for non-bootstrap account creation. |
| Postconditions | Account state changes transactionally while historical attribution remains. |
| Traced requirements | ACC-008–ACC-018, ACC-028, ACC-029, DB-022, AUTH-001–AUTH-002 |

**Main success flow**

1. Choose immutable global role and enter identity fields.
2. Create a pending account and deliver a 24-hour activation link.
3. Inspect activation, account, and session context.
4. Lock, unlock, deactivate, or reinstate only when guards pass.

**Alternatives and exceptions**

- Failed activation delivery retains the pending account, invalidates the token, and exposes explicit resend.
- An account created in error is deactivated while still pending; its unused tokens stop working and nothing is physically deleted.
- A reinstated account returns to the state its own history allows, and carries back no internship, membership or leadership.

## 4. Non-functional Requirements

Inherit [module constraints](../../MODULE.md#4-non-functional-requirements) and
[platform constraints](../../../platform/MODULE.md#4-non-functional-requirements),
including authorization, history, concurrency, server time and test evidence.

## 5. Data

`app_users`, `user_action_tokens`; Intern profile fields belong to internship.

The [module data contract](../../MODULE.md#5-data) retains ownership, constraints and
checks across features. The full physical model stays in platform §19. A feature folder
does not grant repository/entity access across the module boundaries of ARC-006.

### Required contracts

These are required facts or behaviors, not prescribed cross-module imports or an
automatic implementation order. PLAN resolves delivery predecessors against what
already exists and preserves ARC-005/ARC-006.

| Provider | Required fact or behavior | Rule trace |
|---|---|---|
| [identity](../../MODULE.md) | Shared identity, token and session invariants | [ACC-009](../../MODULE.md), [ACC-011](../../MODULE.md), [ACC-017](../../MODULE.md), [ACC-018](../../MODULE.md), [SEC-002](../../MODULE.md), [SEC-003](../../MODULE.md), [SEC-004](../../MODULE.md), [SEC-005](../../MODULE.md) |
| [platform/smtp-configuration](../../../platform/features/smtp-configuration/SPEC.md) | Tested active mail revision for mandatory delivery | [INT-006](../../../platform/features/smtp-configuration/SPEC.md), [INT-007](../../../platform/features/smtp-configuration/SPEC.md), [INT-008](../../../platform/features/smtp-configuration/SPEC.md) |
| [internship/lifecycle](../../../internship/features/lifecycle/SPEC.md) | Permitted Intern fields and lifecycle restrictions | [ACC-019](../../../internship/features/lifecycle/SPEC.md) |

### Related workflows and joint checks

- [Authentication](../authentication/SPEC.md): State gates sign-in.
- [Password management](../password-management/SPEC.md): Shared password and token contract.
- [First Admin bootstrap](../first-admin-bootstrap/SPEC.md): Only installation creates the first Admin without SMTP.

## 6. Error Handling

Apply each refusal, deadline, conflict and delivery-failure clause in the numbered rules
above, together with [module error handling](../../MODULE.md#6-error-handling).
Platform §21 supplies common failure behavior; an operation summary does not override it.

## 7. Acceptance Criteria

### Operation and acceptance map

This map traces existing behavior; its gap column does not define a new rule or
claim full test coverage. Actors and outcomes are summaries of the canonical rules.

| Operation | Actor and observable outcome | Canonical rules | Existing acceptance scenarios | Acceptance boundary or open decision |
|---|---|---|---|---|
| [Create and activate](#create-and-activate) | Admin creates a pending account; invitee activates using the current token | [ACC-008](SPEC.md), [ACC-010](SPEC.md), [ACC-011](../../MODULE.md), [ACC-012](SPEC.md), [ACC-013](SPEC.md), [SEC-003](../../MODULE.md), [SEC-004](../../MODULE.md) | [AC-ACC-004](SPEC.md), [AC-ACC-005](SPEC.md), [AC-ACC-006](SPEC.md), [AC-ACC-007](SPEC.md), [AC-ACC-008](SPEC.md), [AC-ACC-011](SPEC.md) | Retain SMTP absence/failure/resend and immutable-role negatives; define any missing activation-state edges. |
| [Profile, directory and email correction](#profile-directory-and-email-correction) | User edits permitted profile fields; Admin finds accounts and corrects eligible email identities | [ACC-009](../../MODULE.md), [ACC-017](../../MODULE.md), [ACC-018](../../MODULE.md) | [AC-ACC-011](SPEC.md), [AC-ACC-012](SPEC.md) | Directory/email scenarios do not fully exercise self-profile editing; complete its permitted-field/denied-field cases. |
| [Lock, unlock, deactivate and reinstate](#lock-unlock-deactivate-and-reinstate) | Admin changes account availability while retaining identity and attribution, and no state is a dead end | [ACC-014](SPEC.md), [ACC-015](SPEC.md), [ACC-016](SPEC.md), [ACC-028](SPEC.md), [ACC-029](SPEC.md), [DB-022](SPEC.md), [ACC-017](../../MODULE.md), [ACC-018](../../MODULE.md) | [AC-ACC-009](SPEC.md), [AC-ACC-020](SPEC.md), [AC-ACC-021](SPEC.md), [AC-DB-009](SPEC.md) | Settled by `D38`: the transition table above is complete and the schema holds the same set. Implementation and its migration are not covered by these documents. |

### Canonical acceptance scenarios

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-ACC-004 | ACC-008–ACC-011 | Admin attempts to create Admin/Mentor/Intern without active SMTP | All three creation attempts are blocked before account/token creation with an actionable SMTP message. |
| AC-ACC-005 | ACC-010, SEC-003–SEC-004 | Admin creates each role with active SMTP | Each account is pending, has no password, receives one 24-hour single-use link, and stores only a 32-byte hash. |
| AC-ACC-006 | ACC-012–ACC-013 | SMTP send fails during activation delivery | Pending account remains; first token is invalidated; resend creates a different token and successful activation can use only the new token. |
| AC-ACC-007 | ACC-008, ACC-014 | Admin creates another Admin and it activates | New account authenticates as Admin; original Admin remains unchanged. |
| AC-ACC-008 | DB-005 | Any code path attempts to update an existing `global_role` | PostgreSQL rejects the update even if application authorization is bypassed. |
| AC-ACC-009 | ACC-014–ACC-018 | Admin locks, unlocks, then deactivates a user | Sessions are invalidated; login follows state; attribution remains; role never changes. |
| AC-ACC-011 | ACC-008, ACC-017, ACC-019, UI-014 | Admin switches account creation between Intern and non-Intern roles, then submits a crafted non-Intern request containing Intern fields | The browser disables and clears inapplicable fields; the server independently rejects crafted incompatible data; role remains immutable. |
| AC-ACC-012 | ACC-009, ACC-017–ACC-018 | Admin searches and filters the account directory, then corrects the email identity of pending, active, locked, and deactivated accounts | Search matches normalized display name/email/Student Code and role filtering is exact; pending email change replaces activation safely; active/locked email change invalidates sessions; SMTP/delivery/uniqueness failure leaves identity unchanged; deactivated account data and role/display name remain read-only. |
| AC-ACC-020 | ACC-014, ACC-016, ACC-028, SEC-015 | An Admin deactivates an account that is still `PENDING_ACTIVATION` and holds an unused activation token and an unused reset token; the intended user then opens both links, and a reset is requested for that account | The account becomes `DEACTIVATED` with no password hash and no activation timestamp; its display name, email, global role and attribution are unchanged and no row is removed. Both links are refused because their tokens were invalidated, and the later reset request is refused behind the generic response of `SEC-005`. |
| AC-ACC-021 | ACC-013, ACC-014, ACC-016, ACC-029, ACC-024 | An Admin reinstates three `DEACTIVATED` accounts: one deactivated after activation, including one deactivated by internship withdrawal; one that was `LOCKED` when it was deactivated; and one deactivated while still pending. Sign-in is attempted for each before and after an activation resend | The first returns to `ACTIVE` and signs in with its existing credentials, which are not recreated. The previously locked one returns to `LOCKED`, keeps its lock timestamp, still refuses sign-in, and needs an explicit unlock. The never-activated one returns to `PENDING_ACTIVATION` and cannot sign in until the Admin resends activation and the user sets a first password. No reinstatement restores an internship, a Project membership or a leadership term, and the withdrawn internship stays withdrawn. |
| AC-DB-009 | DB-022, ACC-014, ACC-016, ACC-028 | Each account status is written directly to the schema with every combination of password hash, activation timestamp, lock timestamp and deactivation timestamp, including a blank password hash. Then, bypassing the application, one update tries to clear and another to change the activation timestamp of an activated account in each status, and a third tries to set one on a pending account without moving it to `ACTIVE` | The schema accepts exactly the lawful combinations: neither hash nor activation timestamp for `PENDING_ACTIVATION`; both for `ACTIVE` and `LOCKED`; both or neither for `DEACTIVATED`; a lock timestamp while `LOCKED`, and while `DEACTIVATED` only beside an activation timestamp; a deactivation timestamp only while `DEACTIVATED`. A blank hash, a half-populated row, every attempt to clear or change a set activation timestamp and every attempt to set one outside activation are refused, even when application authorization is bypassed. |

Shared and cross-feature scenarios in [MODULE.md](../../MODULE.md#7-acceptance-criteria)
also apply. Scenario ownership follows the behavior exercised, not every prerequisite
named by its Requirements cell. Relocation preserves every existing expected result.

## 8. Out of Scope

Inherit [module exclusions](../../MODULE.md#8-out-of-scope). Adjacent feature behavior
is a dependency, not a duplicated contract. This split creates no new product behavior,
schema, dependency, PLAN.md or TASKS.md.

## Notes / Open Questions

`D38` settles the complete transition table, adds the two edges that removed the dead ends
(`ACC-028`, `ACC-029`) and holds the schema to the same set (`DB-022`). `ACC-028`, `ACC-029`
and `DB-022` need a migration and are not implemented; nothing here claims the running
application already behaves this way.

Read [shared open questions](../../MODULE.md#notes--open-questions) before approving
the technical plan. [plan.md](../../../../../plan.md) is the only progress tracker.
