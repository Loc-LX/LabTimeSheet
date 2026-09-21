# Changelog — Account lifecycle

## 1.1.0 — 2026-09-21

`D38` completes the account state machine. `ACC-014` now names every permitted transition
and `ACC-016` states that deactivation is reversible. `ACC-028` lets an Admin deactivate an
account created in error while it is still pending; `ACC-029` lets an Admin reinstate a
deactivated account to the state its own history allows. `DB-022` holds the schema to the
same set and tightens the timestamp pairings rather than relaxing them. `AC-ACC-020`,
`AC-ACC-021` and `AC-DB-009` cover them, and the account state transition table is added.
These three rules need a migration and are not implemented.


## 1.0.1 — 2026-09-21

Review correction: add per-operation actor/outcome, canonical rule and acceptance
trace with explicit gaps; distinguish required contracts from related workflows
and joint checks. Follow the public Azure/Jira guidance mapped in the
[specification README](../../../README.md). No numbered rule or acceptance row changed.

## 1.0.0 — 2026-09-21

Extracted from the identity 1.1.5 baseline under `D34`; existing rule and
acceptance rows are unchanged. [Earlier history](../../CHANGELOG.md#history-before-the-d34-feature-split)
remains available. Scenarios here exercise this feature; cross-feature scenarios
remain in [MODULE.md](../../MODULE.md). No new business approval is implied.
