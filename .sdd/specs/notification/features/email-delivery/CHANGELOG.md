# Changelog — Notification email delivery

## 1.1.0 — 2026-09-21

`D38` settles the delivery status set. `NOT-012` names `NOT_REQUIRED`, `PENDING`, `SENT`,
`FAILED` and `UNAVAILABLE` with the invariant each carries and the transitions permitted
between them, and the delivery state transition table is added. `AC-NOT-007` covers all
five, the refusal to send an `UNAVAILABLE` row after SMTP returns, and the manual retry.
It records what the schema and the existing scenarios already required; no delivery
behavior is new and none is validated against the running application.


## 1.0.1 — 2026-09-21

Review correction: add per-operation actor/outcome, canonical rule and acceptance
trace with explicit gaps; distinguish required contracts from related workflows
and joint checks. Follow the public Azure/Jira guidance mapped in the
[specification README](../../../README.md). No numbered rule or acceptance row changed.

## 1.0.0 — 2026-09-21

Extracted from the notification 1.1.7 baseline under `D34`; existing rule and
acceptance rows are unchanged. [Earlier history](../../CHANGELOG.md#history-before-the-d34-feature-split)
remains available. Scenarios here exercise this feature; cross-feature scenarios
remain in [MODULE.md](../../MODULE.md). No new business approval is implied.
