# Password management Spec

**Version:** 1.1.0 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-21

**Module:** `identity` · **Shared contract:** [MODULE.md](../../MODULE.md)

## 1. Context & Goal

Change an authenticated password or recover access using a single-use reset token.

This is one cohesive planning scope. Operations below are sections of this feature,
not separate modules or mandatory branches. The split changes document ownership only.

## 2. Actors & Roles

Account holder; reset requester, including an unknown email address.

The [platform permission matrix](../../../platform/MODULE.md#52-permission-matrix)
and the module's shared authorization rules apply to every operation.

## 3. Functional Requirements

The operation summaries below are navigation. The numbered rules remain authoritative;
they do not acquire additional behavior from a heading or summary.

### Change password

ACC-017 permits self-service password management; SEC-002 constrains the password; ACC-018 invalidates existing sessions on success.

### Request password reset

ACC-011 requires active tested SMTP; SEC-005 requires a generic response. NOT-008 excludes secret reset links from ordinary queued retry.

### Complete password reset

SEC-003 and SEC-004 govern token hashing, expiry and replacement. ACC-018 governs session invalidation.

### Canonical feature rules

No additional feature-owned rule row. The operations above reference the canonical shared identity rules in [MODULE.md](../../MODULE.md).



## 4. Non-functional Requirements

Inherit [module constraints](../../MODULE.md#4-non-functional-requirements) and
[platform constraints](../../../platform/MODULE.md#4-non-functional-requirements),
including authorization, history, concurrency, server time and test evidence.

## 5. Data

`app_users`, `user_action_tokens` under the shared identity contract.

The [module data contract](../../MODULE.md#5-data) retains ownership, constraints and
checks across features. The full physical model stays in platform §19. A feature folder
does not grant repository/entity access across the module boundaries of ARC-006.

### Required contracts

These are required facts or behaviors, not prescribed cross-module imports or an
automatic implementation order. PLAN resolves delivery predecessors against what
already exists and preserves ARC-005/ARC-006.

| Provider | Required fact or behavior | Rule trace |
|---|---|---|
| [identity](../../MODULE.md) | Password, token lifetime/hash, identity eligibility and session changes | [ACC-011](../../MODULE.md), [ACC-017](../../MODULE.md), [ACC-018](../../MODULE.md), [SEC-002](../../MODULE.md), [SEC-003](../../MODULE.md), [SEC-004](../../MODULE.md), [SEC-005](../../MODULE.md) |
| [platform/smtp-configuration](../../../platform/features/smtp-configuration/SPEC.md) | Active tested SMTP for reset delivery | [INT-006](../../../platform/features/smtp-configuration/SPEC.md), [INT-007](../../../platform/features/smtp-configuration/SPEC.md), [INT-008](../../../platform/features/smtp-configuration/SPEC.md) |
| [notification/email-delivery](../../../notification/features/email-delivery/SPEC.md) | Secret-link failure and explicit regeneration contract | [NOT-008](../../../notification/features/email-delivery/SPEC.md) |

### Related workflows and joint checks

- [Authentication](../authentication/SPEC.md): Session invalidation and generic responses.
- [Account lifecycle](../account-lifecycle/SPEC.md): Account eligibility and activation share token/password constraints.

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
| [Change password](#change-password) | Authenticated user changes a password and prior sessions are invalidated | [ACC-017](../../MODULE.md), [ACC-018](../../MODULE.md), [SEC-002](../../MODULE.md) | [AC-SEC-002](SPEC.md), [AC-SEC-009](SPEC.md) | Covered: current-password verification, the length bounds, session invalidation and the refusal to change another user's password. |
| [Request password reset](#request-password-reset) | Reset requester receives the non-disclosing response and eligible delivery follows the SMTP contract | [ACC-011](../../MODULE.md), [SEC-005](../../MODULE.md), [SEC-015](../../MODULE.md), [NOT-008](../../../notification/features/email-delivery/SPEC.md) | [AC-SEC-002](SPEC.md), [AC-SEC-010](SPEC.md), [AC-NOT-003](../../../notification/features/email-delivery/SPEC.md) | Covered: eligibility in each account state behind one generic response. `SEC-015` settles which states are eligible. |
| [Complete password reset](#complete-password-reset) | Eligible token holder sets a new password once within the reset-token lifetime | [SEC-002](../../MODULE.md), [SEC-003](../../MODULE.md), [SEC-004](../../MODULE.md), [SEC-015](../../MODULE.md), [ACC-018](../../MODULE.md) | [AC-SEC-011](SPEC.md) | Covered: expiry, reuse, supersession, token hashing, session invalidation, and that completing a reset never releases a lock. |

### Canonical acceptance scenarios

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-SEC-002 | SEC-002–SEC-005 | Password is 11, 12, 128, then 129 characters; reset email is unknown | Only 12 and 128 pass length validation; response for unknown email remains generic. |
| AC-SEC-009 | SEC-002, ACC-017, ACC-018 | A signed-in user changes their own password, first supplying a wrong current password, then a correct one, then a new password failing the length bounds; a second user attempts to change someone else's | The wrong current password and the out-of-bounds new password are both refused and the stored hash is unchanged. The valid change replaces the hash, keeps the global role and account status, and invalidates that user's existing sessions. Changing another user's password is refused without revealing whether that account exists. |
| AC-SEC-010 | SEC-015, SEC-005, ACC-011, ACC-014 | A password reset is requested for an account in each state — `PENDING_ACTIVATION`, `ACTIVE`, `LOCKED`, `DEACTIVATED` — for an address that does not exist, and once while no SMTP configuration is active | Only the `ACTIVE` and `LOCKED` accounts receive a reset token; the pending, deactivated and unknown cases issue none. Every one of the six requests returns the same generic response, so the caller cannot tell the states apart, and no response reveals that SMTP was unavailable. |
| AC-SEC-011 | SEC-015, SEC-003, SEC-004, ACC-018 | A reset token is used after 30 minutes, used twice, used after a newer token was issued for the same account, and used correctly on a `LOCKED` account | The expired, reused and superseded attempts are each refused and change no password. The valid attempt replaces the password, marks that token used and invalidates the account's sessions; only the 32-byte hash is ever persisted. The `LOCKED` account stays `LOCKED` and is still refused authentication after its password changes. |

Shared and cross-feature scenarios in [MODULE.md](../../MODULE.md#7-acceptance-criteria)
also apply. Scenario ownership follows the behavior exercised, not every prerequisite
named by its Requirements cell. Relocation preserves every existing expected result.

## 8. Out of Scope

Inherit [module exclusions](../../MODULE.md#8-out-of-scope). Adjacent feature behavior
is a dependency, not a duplicated contract. This split creates no new product behavior,
schema, dependency, PLAN.md or TASKS.md.

## Notes / Open Questions

This feature references shared identity rules rather than duplicating them. `D38` completes the operation-level set: `AC-SEC-009` covers current-password verification, `AC-SEC-010` covers reset eligibility in each account state behind one generic response, and `AC-SEC-011` covers reset completion. `SEC-015` states the eligibility rule itself; it describes behavior the code already has and has not been validated against it.

Read [shared open questions](../../MODULE.md#notes--open-questions) before approving
the technical plan. [plan.md](../../../../../plan.md) is the only progress tracker.
