# Changelog — Authentication

## 1.3.0 — 2026-09-21

`D38`, corrected on review. `ACC-030` states that only an `ACTIVE` account signs in and
that the three other states are refused behind the generic response of `SEC-005`; no
numbered rule had said so for `LOCKED` or `PENDING_ACTIVATION`. `AC-ACC-022` covers it and
closes the login gap this feature recorded.

## 1.2.0 — 2026-09-21

Status only (`D38`). No question affecting this document is open, so its Status
moves from the inherited baseline to `APPROVED BUSINESS BASELINE`. The specification
map now defines what that field means and when it must change. No numbered rule,
acceptance row, schema or dependency changed.

## 1.1.0 — 2026-09-21

D35 adds ACC-027 and AC-ACC-016/017: logout invalidates only the current session and redirects to login; independently authenticated sessions remain subject to existing guards. Covers old-session rejection and CSRF/GET negatives. ACC-018 security-triggered invalidation is unchanged.

Approved business defaults are recorded in `D35`; this revision does not implement
Java behavior or approve a technical plan.

## 1.0.1 — 2026-09-21

Review correction: add per-operation actor/outcome, canonical rule and acceptance
trace with explicit gaps; distinguish required contracts from related workflows
and joint checks. Follow the public Azure/Jira guidance mapped in the
[specification README](../../../README.md). No numbered rule or acceptance row changed.

## 1.0.0 — 2026-09-21

Extracted from the identity 1.1.5 baseline under `D34`; existing rule and
acceptance rows are unchanged. [Earlier history](../../CHANGELOG.md#retained-history)
remains available. Scenarios here exercise this feature; cross-feature scenarios
remain in [MODULE.md](../../MODULE.md). No new business approval is implied.
