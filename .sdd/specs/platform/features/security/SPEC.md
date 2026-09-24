# Security Spec

**Version:** 1.0.0 · **Owner:** Loc-LX · **Status:** APPROVED BUSINESS BASELINE · **Date:** 2026-09-23

**Module:** `platform` · **Shared contract:** [MODULE.md](../../MODULE.md)

## 1. Context & Goal

Keep the web security controls on under every profile, harden the production profile before it
reports itself ready, and let no development relaxation reach production.

This is one cohesive planning scope. Operations below are sections of this feature,
not separate modules or mandatory branches. Its rules moved here from the platform shared
contract unchanged (`D40`).

## 2. Actors & Roles

Every client of the application, signed in or not, and the operator who deploys it. No use case
is defined here: these controls apply to every request of every feature.

The [authorization model](../authorization/SPEC.md#5-authorization-model) decides who may do
what; this feature keeps the transport, session and response controls around it.

## 3. Functional Requirements

The operation summaries below are navigation. The numbered rules remain authoritative;
they do not acquire additional behavior from a heading or summary.

### Keep controls on in every profile

SEC-001, SEC-008 and SEC-009 keep session authentication, CSRF, validation, escaping and hashing on, allow only local redirect targets, and keep error pages non-disclosing.

### Harden the production profile

SEC-010 to SEC-014 require HTTPS origins, trusted proxies, security headers and secure cookies before production is ready, and confine the development relaxations to development and test.

### Canonical feature rules

The numbered rules of this feature are non-functional. They stay in section 4 under §13,
where the platform shared contract held them.

## 4. Non-functional Requirements

### §13. Authentication and security

**Part F2.**

#### §13.1 Controls active in every profile

`SEC-002`–`SEC-007`, which concern accounts, are in [the identity spec](../../../identity/MODULE.md).

| ID | Requirement |
|---|---|
| SEC-001 | THE system SHALL keep Spring Security session authentication, server-side authorization, object ownership checks, CSRF protection, Bean Validation, output escaping, and password hashing enabled under every profile, including development and test. |
| SEC-008 | THE system SHALL restrict redirect targets to an allow-listed local set. WHERE a state-changing endpoint receives an open redirect, a user-selected class name, an arbitrary template, or an arbitrary URL, THE system SHALL reject it. |
| SEC-009 | WHERE an error page or an authorization failure is rendered, THE system SHALL NOT expose a stack trace, SQL, a secret, an internal identifier from an unauthorized record, or an existence distinction useful for enumeration. |

#### §13.2 Profile-dependent hardening

| ID | Requirement |
|---|---|
| SEC-010 | WHILE running under the production profile, THE system SHALL require an HTTPS public base URL and origin and an explicit trusted-proxy configuration before it reports itself ready. |
| SEC-011 | WHILE running under the production profile, THE system SHALL send security headers that force HTTPS for at least one year including subdomains, restrict every resource, form target, and base URI to the application itself, forbid the application from being framed, and suppress referrer disclosure. THE system SHALL mark session cookies `Secure`, `HttpOnly`, and `SameSite=Strict`, and SHALL apply strict configured-origin checks. The exact directive values are fixed by `AC-SEC-008`. |
| SEC-012 | THE system SHALL trust forwarded headers only WHERE the deployment explicitly enables and constrains the known reverse-proxy path. THE system SHALL NOT let an arbitrary client-forwarded header define the scheme, host, or source IP. |
| SEC-013 | WHILE running under a development or test profile, THE system MAY use HTTP, localhost origins, `SameSite=Lax`, and neither HSTS nor Secure cookies. THE system SHALL activate those relaxations only from development or test profile state, and SHALL NOT let production inherit them. |
| SEC-014 | WHERE the master key, public origin, datasource, or explicit proxy policy required by production is absent or malformed, THE system SHALL fail production readiness. SMTP MAY remain absent, and WHILE it is absent THE system SHALL remain visibly restricted as specified. |

Inherit [platform constraints](../../MODULE.md#4-non-functional-requirements).

## 5. Data

No table of its own. `SEC-014` checks the master key, the public origin, the datasource and
the proxy policy that production requires.

### Required contracts

These are required facts or behaviors, not prescribed cross-module imports or an
automatic implementation order.

| Provider | Required fact or behavior | Rule trace |
|---|---|---|
| [platform](../../MODULE.md) | A deployment-provided master key under the production profile | [INT-002](../../MODULE.md) |

### Related workflows and joint checks

- `AUTH-002` in the [Authorization](../authorization/SPEC.md) feature fixes the refusal responses whose pages `SEC-009` keeps non-disclosing.

## 6. Error Handling

Where a production requirement of `SEC-014` is absent or malformed, readiness fails. A refused
redirect target is refused before the mutation (`AC-SEC-006`). Apply
[module error handling](../../MODULE.md#6-error-handling).

## 7. Acceptance Criteria

### Operation and acceptance map

This map traces existing behavior; its gap column does not define a new rule or
claim full test coverage. Actors and outcomes are summaries of the canonical rules.

| Operation | Actor and observable outcome | Canonical rules | Existing acceptance scenarios | Acceptance boundary or open decision |
|---|---|---|---|---|
| [Keep controls on in every profile](#keep-controls-on-in-every-profile) | Any client: a forged, redirecting or failing request is refused without disclosure in every profile | [SEC-001](SPEC.md), [SEC-008](SPEC.md), [SEC-009](SPEC.md) | [AC-SEC-001](SPEC.md), [AC-SEC-006](SPEC.md), [AC-SEC-007](SPEC.md) | CSRF stays on under the relaxed development profile. |
| [Harden the production profile](#harden-the-production-profile) | The operator: production is ready only with its required configuration, and serves the fixed headers and cookie attributes | [SEC-010](SPEC.md), [SEC-011](SPEC.md), [SEC-012](SPEC.md), [SEC-013](SPEC.md), [SEC-014](SPEC.md) | [AC-SEC-004](SPEC.md), [AC-SEC-005](SPEC.md), [AC-SEC-008](SPEC.md) | The exact header values are those `AC-SEC-008` fixes. |

### Canonical acceptance scenarios

| Scenario | Requirements | Given / when | Expected result |
|---|---|---|---|
| AC-SEC-001 | SEC-001, SEC-013 | Dev/test request a state-changing form without CSRF | Request is denied despite relaxed transport/cookie settings. |
| AC-SEC-004 | SEC-010–SEC-014 | Production starts without public origin/master key or with untrusted forwarded headers | Readiness/startup fails for missing required config; client headers cannot spoof origin/scheme/IP. |
| AC-SEC-008 | SEC-011 | Production responses are inspected for security headers | `Strict-Transport-Security` carries `max-age=31536000`, `includeSubDomains`, and `preload`; `Content-Security-Policy` is `default-src 'self'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'`; `Referrer-Policy` is `no-referrer` in every profile; the session cookie carries `Secure`, `HttpOnly`, and `SameSite=Strict`. |
| AC-SEC-005 | SEC-013 | Dev/test run over localhost HTTP | Session works with Lax/no-HSTS profile while hashing, CSRF, validation, and authorization remain active. |
| AC-SEC-006 | SEC-008 | A state-changing request supplies an absolute external redirect target, a second supplies a user-chosen class name, and a third supplies an arbitrary template path | All three are refused before the mutation; the response redirects only to an allow-listed local path and never to the supplied value. |
| AC-SEC-007 | SEC-009 | An unauthenticated client requests a record that does not exist and then one that exists but belongs to another user; a controller then throws an unexpected exception | Both record requests produce the same non-disclosing response, so existence cannot be inferred; the exception page shows no stack trace, SQL, secret, or internal identifier. |

Shared and cross-feature scenarios in [MODULE.md](../../MODULE.md#7-acceptance-criteria)
also apply.

## 8. Out of Scope

Inherit [module exclusions](../../MODULE.md#8-out-of-scope). The account rules `SEC-002`–`SEC-007`
and `SEC-015` belong to identity; business permissions belong to the Authorization feature.

## Notes / Open Questions

No question affecting this feature is open. Its technical design is [PLAN.md](PLAN.md),
with tasks in [TASKS.md](TASKS.md).

Read [shared open questions](../../MODULE.md#notes--open-questions) before approving
the technical plan. [plan.md](../../../../../plan.md) is the only progress tracker.
