# Changelog — Notification email delivery

## 1.1.1 — 2026-09-22

Note only (`D38`, third review). The note no longer says the schema already requires what
`NOT-012` states: `ck_notifications_email_payload` only exempts `NOT_REQUIRED` and
`UNAVAILABLE` from carrying a payload, and the migration must make it forbid one. No rule or
scenario changed.

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
acceptance rows are unchanged. [Earlier history](../../CHANGELOG.md#retained-history)
remains available. Scenarios here exercise this feature; cross-feature scenarios
remain in [MODULE.md](../../MODULE.md). No new business approval is implied.
