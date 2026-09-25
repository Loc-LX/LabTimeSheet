# Changelog — Data model

## 1.0.2 — 2026-09-25

§19.4 adds the six V3 tables and the foreign keys V3 adds, and names the four `task_remaining_effort_forecasts` foreign keys that V2 created and the diagram omitted (`DB-010`). No rule changes.

## 1.0.1 — 2026-09-25

`AC-DB-001` replays the Flyway migrations of this repository instead of the two review DDL files, which were never in it (`ARC-008`), under `D45`. Its expected table count and diagram comparison are unchanged. No rule changes.

## 1.0.0 — 2026-09-23

Extracted from the platform 1.10.0 shared contract under `D40`: `DB-001`, `DB-003`–`DB-008` and
`DB-010` with §19, and the scenarios `AC-DB-001` and `AC-DB-004`. Every moved rule and scenario
row is unchanged, and the platform catalogue still holds 311 rules and 167 scenarios. The
operation headings and the acceptance map are new navigation; they define no behavior. Earlier
history of these rows: [platform changelog](../../CHANGELOG.md).
