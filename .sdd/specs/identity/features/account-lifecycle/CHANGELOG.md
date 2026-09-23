# Changelog — Account lifecycle

## 1.3.1 — 2026-09-22

Note only (`D38`, third review). The lock that `ACC-029` restores survives only for
accounts deactivated after the migration: V1 and the current code clear the lock timestamp on
deactivation. No rule or scenario changed.

## 1.3.0 — 2026-09-22

Corrected on second review (`D38`). `DB-022` protected a kept lock only while the row
stayed `DEACTIVATED`, so one update could still move a locked `DEACTIVATED` account to
`ACTIVE` and clear the lock. The lock timestamp is now set only by locking and cleared only by
unlocking, which with the write-once activation timestamp makes the schema itself force the
three outcomes of `ACC-029`. The "neither" clause is restated so it stands alone.
`AC-DB-009` covers the one-update move and the other lock updates.

## 1.2.0 — 2026-09-21

Corrected on review (`D38`). `DB-022` is restated as what it is: it loosens V1 by one
row `ACC-028` needs and by a lock timestamp kept through deactivation, keeps V1's non-blank
hash, and makes the activation timestamp write-once so a check that cannot see history no
longer lets an update erase an activation. `ACC-016` keeps a lock through deactivation and
`ACC-029` restores it, adding `DEACTIVATED → LOCKED` to `ACC-014` and the state table.
`ACC-014` and the table cite `ACC-010` for activation, not `ACC-011`. `AC-ACC-021` and
`AC-DB-009` cover the new cases.

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
acceptance rows are unchanged. [Earlier history](../../CHANGELOG.md#retained-history)
remains available. Scenarios here exercise this feature; cross-feature scenarios
remain in [MODULE.md](../../MODULE.md). No new business approval is implied.
