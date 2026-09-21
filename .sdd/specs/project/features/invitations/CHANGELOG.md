# Changelog — Project invitations

## 1.1.0 — 2026-09-21

D37 constrains invitation status in DB-011 to PENDING, ACCEPTED, DECLINED, REVOKED, SUPERSEDED matching V1__baseline.sql, aligns PROJECT_COMPLETED and PROJECT_CANCELLED to revoked status, and adds the Project invitation state transition table with public Azure Boards/Atlassian comparison points.

## 1.0.1 — 2026-09-21

Review correction: add per-operation actor/outcome, canonical rule and acceptance
trace with explicit gaps; distinguish required contracts from related workflows
and joint checks. Follow the public Azure/Jira guidance mapped in the
[specification README](../../../README.md). No numbered rule or acceptance row changed.

## 1.0.0 — 2026-09-21

Extracted from the project 1.4.2 baseline under `D34`; existing rule and
acceptance rows are unchanged. [Earlier history](../../CHANGELOG.md#history-before-the-d34-feature-split)
remains available. Scenarios here exercise this feature; cross-feature scenarios
remain in [MODULE.md](../../MODULE.md). No new business approval is implied.
