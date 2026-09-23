# Changelog — Password management

## 1.2.0 — 2026-09-21

`D38`, corrected on review. `AC-SEC-011` also covers the throttle a completed reset clears,
on two source addresses, and cites `ACC-030` for the locked account still refused.

## 1.1.0 — 2026-09-21

`D38` completes this feature's operation-level acceptance coverage with `AC-SEC-009`,
`AC-SEC-010` and `AC-SEC-011`, and records `SEC-015` in MODULE.md as the eligibility rule
the three exercise. A reset never releases a lock. No rule of this feature changed its
meaning; the recorded gap is closed rather than reinterpreted.


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
